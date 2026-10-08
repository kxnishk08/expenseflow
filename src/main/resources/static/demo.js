'use strict';
const $ = id => document.getElementById(id);
let accounts, csrf, signedIn = false;
function log(label, value) {
  const entry = document.createElement('details');
  entry.open = value === undefined;
  const title = document.createElement('summary');
  title.textContent = label;
  entry.append(title);
  if (value !== undefined) {
    const pre = document.createElement('pre');
    pre.textContent = JSON.stringify(value, null, 2);
    entry.append(pre);
  }
  $('trace').append(entry);
}
async function request(method, path, body, key, expected) {
  const headers = {};
  if (method !== 'GET' && csrf) headers[csrf.headerName] = csrf.token;
  if (key) headers['Idempotency-Key'] = key;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(path, {method, credentials:'same-origin', headers,
    body: body === undefined ? undefined : JSON.stringify(body)});
  const raw = await response.text();
  let data;
  try { data = raw ? JSON.parse(raw) : null; } catch { data = {message:raw.slice(0,500)}; }
  log(method + ' ' + path + ' → ' + response.status, data);
  if (expected !== undefined) {
    if (response.status !== expected) throw new Error('Expected HTTP ' + expected + ', received ' + response.status);
  } else if (!response.ok) throw new Error(data?.message || 'HTTP ' + response.status);
  return data;
}
async function login(name) {
  if (signedIn) {
    await request('POST', '/api/auth/logout');
    signedIn = false;
  }
  csrf = await (await fetch('/api/auth/csrf', {credentials:'same-origin'})).json();
  const response = await fetch('/api/auth/login', {
    method:'POST', credentials:'same-origin',
    headers:{[csrf.headerName]:csrf.token},
    body:new URLSearchParams({username:name + '@example.test', password:accounts[name + '@example.test']})
  });
  if (!response.ok) throw new Error('Demo login failed for ' + name + ' (HTTP ' + response.status + ')');
  signedIn = true;
  csrf = await (await fetch('/api/auth/csrf', {credentials:'same-origin'})).json();
  log('Signed in as ' + name);
}
function assert(condition, message) { if (!condition) throw new Error(message); }
$('run').addEventListener('click', async () => {
  $('run').disabled = true;
  $('trace').replaceChildren();
  $('status').textContent = 'Running against the real backend…';
  try {
    // Start with a fresh browser session, including after a previous partial demo.
    csrf = await (await fetch('/api/auth/csrf', {credentials:'same-origin'})).json();
    await fetch('/api/auth/logout', {method:'POST', credentials:'same-origin', headers:{[csrf.headerName]:csrf.token}});
    signedIn = false;
    await workflow();
    $('status').textContent = 'Demo passed. Open a request below to inspect its JSON response.';
  } catch (error) {
    $('status').textContent = error.message + '. Inspect the trace. If the service just woke up, retry once it is ready.';
  } finally { $('run').disabled = false; }
});
(async () => {
  try {
    const response = await fetch('/demo/config', {credentials:'same-origin'});
    if (!response.ok) throw new Error('This page needs the public-demo profile and provisioned demo accounts.');
    accounts = await response.json();
    for (const [email, password] of Object.entries(accounts)) {
      const line = document.createElement('p');
      line.textContent = email + ' / ' + password;
      $('accounts').append(line);
    }
    $('run').disabled = false;
    $('status').textContent = 'Ready. The workflow creates a fresh synthetic example.';
  } catch (error) { $('status').textContent = error.message; }
})();
async function workflow() {
  const run = crypto.randomUUID().slice(0,8);
  const date = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  await login('alice');
  let report = await request('POST','/api/reports',{title:'Recruiter travel demo ' + run});
  const path = '/api/reports/' + report.id;
  const item = {category:'TRAVEL', expenseDate:date, merchant:'Demo Railway', description:'Travel', amount:600, receiptReference:'DEMO-TRAVEL'};
  const travel = await request('POST', path + '/items', {...item, expectedRevision:0});
  await request('POST', path + '/items', {expectedRevision:1,category:'MEALS',expenseDate:date,merchant:'Demo Cafe',description:'Meal',amount:450,receiptReference:'DEMO-MEAL'});
  await request('POST',path + '/submit',{expectedRevision:2});
  await login('m1');
  await request('POST','/api/manager/reports/' + report.id + '/decision',{expectedRevision:3,decision:'RETURN',comment:'Clarify travel purpose'});
  await login('alice');
  await request('POST',path + '/reopen',{expectedRevision:4});
  await request('PUT',path + '/items/' + travel.item.id,{...item,description:'Travel to training',expectedRevision:5});
  await request('POST',path + '/submit',{expectedRevision:6});
  await login('m1');
  await request('POST','/api/manager/reports/' + report.id + '/decision',{expectedRevision:3,decision:'APPROVE'},undefined,409);
  await request('POST','/api/manager/reports/' + report.id + '/decision',{expectedRevision:7,decision:'APPROVE'});
  await login('f1');
  await request('POST','/api/finance/reports/' + report.id + '/decision',{expectedRevision:8,decision:'ACCEPT'});
  const reimbursement = {expectedRevision:9,reference:'RECRUITER-' + crypto.randomUUID()};
  await request('POST','/api/finance/reports/' + report.id + '/reimbursement',reimbursement);
  await request('POST','/api/finance/reports/' + report.id + '/reimbursement',reimbursement,undefined,409);
  await login('alice');
  report = await request('GET',path);
  assert(report.state === 'REIMBURSEMENT_RECORDED' && report.submissions.length === 2 && Number(report.reimbursement.amount) === 1050,'Final report verification failed');
  log('PASS: two immutable submissions, stale-write rejection, and INR 1,050 recorded once.');
}
