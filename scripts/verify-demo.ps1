param([Parameter(Mandatory=$true)][string]$PostgresBin,[int]$DatabasePort=55417,[string]$DatabaseUser='portfolio_test',[string]$Database=('expenseflow_demo_'+[Guid]::NewGuid().ToString('N').Substring(0,8)),[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if($Database -notmatch '^expenseflow_[a-z0-9_]+$'){throw 'Use an isolated expenseflow_* demo database.'}
function Sql([string]$Db,[string]$Query) {
    $result=& "$PostgresBin/psql.exe" -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser -d $Db -At -v ON_ERROR_STOP=1 -c $Query
    if($LASTEXITCODE-ne 0){throw 'SQL failed'};return $result
}
$exists=Sql postgres "select count(*) from pg_database where datname='$Database'"
if($exists-eq '0'){& "$PostgresBin/createdb.exe" -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser $Database;if($LASTEXITCODE-ne 0){throw 'Database creation failed'}}
$env:DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/$Database";$env:DB_USER=$DatabaseUser
if(!$env:DB_PASSWORD){$env:DB_PASSWORD=''}
$env:SPRING_PROFILES_ACTIVE='dev';$env:DEMO_PROVISION='true';$env:PORT='18080'
foreach($name in @('ALICE','BOB','M1','M2','F1')) {if(![Environment]::GetEnvironmentVariable("DEMO_${name}_PASSWORD")){[Environment]::SetEnvironmentVariable("DEMO_${name}_PASSWORD",[Guid]::NewGuid().ToString('N')+'-Demo!')}}
function Start-App {
    $process=Start-Process -FilePath "$JavaHome/bin/java.exe" -ArgumentList @('-jar',"$project/target/expenseflow-1.0.0.jar") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$project/target/demo-app.log" -RedirectStandardError "$project/target/demo-app-error.log"
    for($n=0;$n-lt 60;$n++){try{Invoke-RestMethod 'http://localhost:18080/actuator/health' | Out-Null;return $process}catch{if($process.HasExited){throw 'Application exited; inspect target/demo-app*.log'};Start-Sleep -Seconds 1}}
    Stop-Process -Id $process.Id;throw 'Application startup timed out'
}
$app=$null;$restore='expenseflow_restore_'+[Guid]::NewGuid().ToString('N').Substring(0,8)
try {
    $app=Start-App
    $demo=& "$PSScriptRoot/demo.ps1" -BaseUrl 'http://localhost:18080';$demo | Write-Output
    $report=([regex]::Match(($demo -join "`n"),'PASS report=([a-f0-9-]+)')).Groups[1].Value
    if(!$report){throw 'Demo failed to produce report ID'}
    $before=Sql $Database "select (select count(*) from expense_reports)||','||(select count(*) from report_submissions)||','||(select count(*) from report_events)||','||(select count(*) from reimbursements)"
    Stop-Process -Id $app.Id -Force;$app.WaitForExit();$app=Start-App
    $after=Sql $Database "select (select count(*) from expense_reports)||','||(select count(*) from report_submissions)||','||(select count(*) from report_events)||','||(select count(*) from reimbursements)"
    if($before-ne $after){throw 'Restart changed durable record counts'}
    $state=Sql $Database "select state from expense_reports where id='$report'"
    if($state-ne 'REIMBURSEMENT_RECORDED'){throw 'Restart lost final state'}
    $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf=Invoke-RestMethod 'http://localhost:18080/api/auth/csrf' -WebSession $session
    Invoke-WebRequest 'http://localhost:18080/api/auth/login' -Method Post -WebSession $session -Headers @{$csrf.headerName=$csrf.token} -Body @{username='alice@example.test';password=$env:DEMO_ALICE_PASSWORD} -UseBasicParsing | Out-Null
    $persisted=Invoke-RestMethod "http://localhost:18080/api/reports/$report" -WebSession $session
    if($persisted.state-ne 'REIMBURSEMENT_RECORDED' -or $persisted.submissions.Count-ne 2){throw 'Restart API lost snapshots or state'}
    & "$PostgresBin/pg_dump.exe" -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser -d $Database -Fc -f "$project/target/demo-backup.dump"
    if($LASTEXITCODE-ne 0){throw 'Backup failed'}
    & "$PostgresBin/createdb.exe" -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser $restore
    & "$PostgresBin/pg_restore.exe" -h 127.0.0.1 -p $DatabasePort -U $DatabaseUser -d $restore --exit-on-error "$project/target/demo-backup.dump"
    if($LASTEXITCODE-ne 0){throw 'Restore failed'}
    $restored=Sql $restore "select (select count(*) from expense_reports)||','||(select count(*) from report_submissions)||','||(select count(*) from report_events)||','||(select count(*) from reimbursements)"
    if($restored-ne $before){throw 'Restored counts differ'}
    $relationship=Sql $restore "select r.amount||','||count(i.id)||','||sum(i.amount) from reimbursements r join report_submissions s on s.id=r.submission_id join submitted_expense_items i on i.submission_id=s.id where r.report_id='$report' group by r.id,r.amount"
    if($relationship-ne '1050.00,2,1050.00'){throw "Restored relationship mismatch: $relationship"}
    Write-Output "PASS restart counts=$before; backup restored to $restore; reimbursement/snapshot relationship=$relationship"
} finally {if($app-and !$app.HasExited){Stop-Process -Id $app.Id -Force}}
