param([string]$BaseUrl='http://localhost:8080')
$ErrorActionPreference='Stop'
function Login([string]$Name) {
    $password=[Environment]::GetEnvironmentVariable('DEMO_'+$Name.ToUpperInvariant()+'_PASSWORD')
    if(!$password){throw "Set DEMO_$($Name.ToUpperInvariant())_PASSWORD in your environment."}
    $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf=Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session
    Invoke-WebRequest "$BaseUrl/api/auth/login" -Method Post -WebSession $session -Headers @{$csrf.headerName=$csrf.token} -Body @{username="$Name@example.test";password=$password} -UseBasicParsing | Out-Null
    $csrf=Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session
    return @{Session=$session;Headers=@{$csrf.headerName=$csrf.token}}
}
function Api($Login,[string]$Method,[string]$Path,$Body) {
    $params=@{Uri="$BaseUrl$Path";Method=$Method;WebSession=$Login.Session;Headers=$Login.Headers}
    if($null-ne $Body){$params.ContentType='application/json';$params.Body=ConvertTo-Json $Body -Depth 10 -Compress}
    Invoke-RestMethod @params
}
function ExpectStatus($Login,[string]$Method,[string]$Path,$Body,[int]$Expected) {
    try{Api $Login $Method $Path $Body | Out-Null;throw 'Expected request to fail'}catch{if(!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne $Expected){throw}}
}
$alice=Login 'alice';$manager=Login 'm1';$unassigned=Login 'm2';$finance=Login 'f1'
$today=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,'India Standard Time').ToString('yyyy-MM-dd')
$report=Api $alice Post '/api/reports' @{title='Demo travel claim'}
$travel=Api $alice Post "/api/reports/$($report.id)/items" @{expectedRevision=0;category='TRAVEL';expenseDate=$today;merchant='Demo Railway';description='Travel';amount=600.00;receiptReference='R-TRAVEL'}
$meal=Api $alice Post "/api/reports/$($report.id)/items" @{expectedRevision=1;category='MEALS';expenseDate=$today;merchant='Demo Cafe';description='Meal';amount=450.00;receiptReference='R-MEAL'}
$report=Api $alice Post "/api/reports/$($report.id)/submit" @{expectedRevision=2}
ExpectStatus $unassigned Get "/api/manager/reports/$($report.id)" $null 404
$review=Api $manager Post "/api/manager/reports/$($report.id)/decision" @{expectedRevision=3;decision='RETURN';comment='Clarify travel purpose'}
$report=Api $alice Post "/api/reports/$($report.id)/reopen" @{expectedRevision=4}
$travel=Api $alice Put "/api/reports/$($report.id)/items/$($travel.item.id)" @{expectedRevision=5;category='TRAVEL';expenseDate=$today;merchant='Demo Railway';description='Travel to training';amount=600.00;receiptReference='R-TRAVEL'}
$report=Api $alice Post "/api/reports/$($report.id)/submit" @{expectedRevision=6}
ExpectStatus $manager Post "/api/manager/reports/$($report.id)/decision" @{expectedRevision=3;decision='APPROVE'} 409
$review=Api $manager Post "/api/manager/reports/$($report.id)/decision" @{expectedRevision=7;decision='APPROVE'}
$review=Api $finance Post "/api/finance/reports/$($report.id)/decision" @{expectedRevision=8;decision='ACCEPT'}
$reference='DEMO-'+[Guid]::NewGuid().ToString('N').Substring(0,16).ToUpperInvariant()
$reimbursement=Api $finance Post "/api/finance/reports/$($report.id)/reimbursement" @{expectedRevision=9;reference=$reference}
ExpectStatus $finance Post "/api/finance/reports/$($report.id)/reimbursement" @{expectedRevision=9;reference=$reference} 409
$invalid=Api $alice Post '/api/reports' @{title='Empty policy failure'}
ExpectStatus $alice Post "/api/reports/$($invalid.id)/submit" @{expectedRevision=0} 409
$to=[DateTime]::ParseExact($today,'yyyy-MM-dd',$null).AddDays(1).ToString('yyyy-MM-dd')
Api $finance Get "/api/finance/summary?from=$today&to=$to" $null | ConvertTo-Json -Depth 12
Write-Output "PASS report=$($report.id), submissions=2, reimbursement=1050.00 INR, reference=$reference; cross-manager denial, stale decision, duplicate recording and policy failure verified."
