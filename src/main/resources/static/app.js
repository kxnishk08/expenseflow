'use strict';
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const cash = n => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(Number(n || 0));
const words = s => String(s || '').toLowerCase().replaceAll('_',' ').replace(/\b\w/g,c=>c.toUpperCase());
const date = value => value ? new Intl.DateTimeFormat('en-IN',{day:'2-digit',month:'short',year:'numeric'}).format(new Date(value)) : '—';
const stamp = value => value ? new Intl.DateTimeFormat('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}).format(new Date(value)) : '—';
const today = () => new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const icons = {
 grid:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
 file:'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h5"/>',
 check:'<path d="m5 12 4 4L19 6"/><path d="M21 12a9 9 0 1 1-7-8.7"/>',
 clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
 shield:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z"/><path d="m8 12 3 3 5-6"/>',
 box:'<path d="m12 2 9 5v10l-9 5-9-5V7zM3 7l9 5 9-5M12 12v10M7 4.8l9 5"/>',
 plus:'<path d="M12 5v14M5 12h14"/>', arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',
 refresh:'<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6 7a7 7 0 0 1 12-1l2 3M4 15l2 3a7 7 0 0 0 12-1"/>',
 close:'<path d="m6 6 12 12M6 18 18 6"/>', bag:'<rect x="5" y="6" width="14" height="16" rx="3"/><path d="M9 6V4a3 3 0 0 1 6 0v2M5 12h14M9 15v3h6v-3"/>',
 headphones:'<path d="M4 13V9a8 8 0 0 1 16 0v4"/><rect x="2" y="11" width="5" height="10" rx="2"/><rect x="17" y="11" width="5" height="10" rx="2"/>',
 laptop:'<rect x="4" y="3" width="16" height="13" rx="1.5"/><path d="m4 16-2 4h20l-2-4M9 20h6"/>',
 money:'<rect x="2" y="5" width="20" height="14" rx="3"/><circle cx="12" cy="12" r="3"/><path d="M5 12h.01M19 12h.01"/>'
};
const icon = name => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (icons[name] || icons.file) + '</svg>';
const badge = state => '<span class="badge ' + (['APPROVED','FINANCE_ACCEPTED','REIMBURSEMENT_RECORDED','DELIVERED','CONFIRMED'].includes(state)?'green':['SUBMITTED','PLACED','PACKED','SHIPPED'].includes(state)?'blue':['ACTIVE','RETURNED'].includes(state)?'amber':['REJECTED','CANCELLED','EXPIRED','WITHDRAWN'].includes(state)?'red':'') + '">' + esc(state === 'REIMBURSEMENT_RECORDED' ? 'Reimbursed' : words(state)) + '</span>';
const button = (label,action,id='',style='',disabled=false) => '<button type="button" class="btn '+style+'" data-action="'+action+'" data-id="'+esc(id)+'" '+(label.startsWith('<svg')&&label.endsWith('</svg>')?'aria-label="'+(action==='refresh'?'Refresh workspace':action==='dismiss'?'Dismiss notice':words(action))+'" ':'')+(disabled?'disabled':'')+'>'+label+'</button>';
const heading = (tag,title,desc,action='') => '<div class="page-heading"><div><div class="eyebrow">'+esc(tag)+'</div><h1>'+esc(title)+'</h1><p class="subtitle">'+esc(desc)+'</p></div><div class="actions">'+action+button(icon('refresh'),'refresh','','','')+'</div></div>';
const stat = (label,value,foot,symbol) => '<div class="stat"><div><div class="stat-label">'+esc(label)+'</div><div class="stat-value">'+value+'</div><div class="stat-foot">'+esc(foot)+'</div></div><div class="stat-icon">'+icon(symbol)+'</div></div>';
const empty = (title,text,action='') => '<div class="empty">'+icon(APP==='expenseflow'?'file':'box')+'<h3>'+esc(title)+'</h3><p>'+esc(text)+'</p>'+action+'</div>';
const pager = (data) => '<div class="pager"><span>'+data.totalElements+' records · Page '+(data.page+1)+'</span><div class="actions">'+button('← Previous','page',data.page-1,'small',data.page===0)+button('Next →','page',data.page+1,'small',(data.page+1)*data.size>=data.totalElements)+'</div></div>';
const field = (name,label,value='',type='text',extra='') => '<div class="field"><label for="'+name+'">'+esc(label)+'</label><input id="'+name+'" name="'+name+'" type="'+type+'" value="'+esc(value)+'" '+extra+'></div>';
const formButtons = (label='Save') => '<div class="form-actions">'+button('Cancel','close')+'<button class="btn primary" type="submit">'+esc(label)+'</button></div>';
let csrf, accounts = {}, me, busy = false, page = 0, view = 'overview', data, selected, policy, filter = '';
const retryKeys = new Map();
function notice(message,success=false) {
 $('notice').className = success ? 'success' : '';
 $('notice').innerHTML = '<p>'+esc(message)+'</p>'+button(icon('close'),'dismiss','','ghost small');
}
async function api(method,path,body,key) {
 const headers = {};
 if(method!=='GET') {
   if(!csrf) csrf=await api('GET','/api/auth/csrf');
   headers[csrf.headerName]=csrf.token;
 }
 if(body!==undefined)headers['Content-Type']='application/json';
 if(key)headers['Idempotency-Key']=key;
 let response;
 try { response=await fetch(path,{method,credentials:'same-origin',headers,body:body===undefined?undefined:JSON.stringify(body)}); }
 catch { throw new Error('Connection interrupted. Retry the same action. For a stock adjustment, inspect movement history before retrying.'); }
 const raw=await response.text();
 let result; try{result=raw?JSON.parse(raw):null;}catch{result={message:'The service is starting or unavailable. Please retry in a moment.'};}
 if(!response.ok) {
   if(response.status===401&&me){me=null;selected=null;data=null;shell();loginScreen();}
   const details=(result?.fieldErrors||[]).map(e=>words(e.field.split('.').at(-1))+': '+e.message).join(' ');
   const messages={INSUFFICIENT_STOCK:'There is not enough stock available for that quantity.',INVALID_STOCK_ADJUSTMENT:'This adjustment would remove more stock than is available. Check the quantity and try again.',POLICY_VIOLATION:'Some expenses do not meet the policy limits.',RESERVATION_EXPIRED:'This reservation has expired. Create a new hold.'};
   throw new Error((messages[result?.code]||result?.message||'Request failed')+(details?' '+details:'')+(result?.code==='STALE_REPORT_REVISION'?' Refresh to see the latest revision before editing again.':''));
 }
 return result;
}
async function idempotent(path,body,scope) {
 const fingerprint=scope+JSON.stringify(body??null);
 if(!retryKeys.has(fingerprint))retryKeys.set(fingerprint,crypto.randomUUID());
 const result=await api('POST',path,body,retryKeys.get(fingerprint));
 retryKeys.delete(fingerprint);
 return result;
}
async function signIn(email,password) {
 csrf=await api('GET','/api/auth/csrf');
 // A role change invalidates the previous session before authenticating.
 if(me){await api('POST','/api/auth/logout');me=null;data=null;selected=null;csrf=await api('GET','/api/auth/csrf');}
 let response;
 try{response=await fetch('/api/auth/login',{method:'POST',credentials:'same-origin',headers:{[csrf.headerName]:csrf.token},body:new URLSearchParams({username:email,password})});}
 catch{if(!me){shell();loginScreen();}throw new Error('Sign-in could not reach the server. Please try again.');}
 if(!response.ok){shell();loginScreen();throw new Error('Could not sign in. Check your email and password.');}
 csrf=await api('GET','/api/auth/csrf');
 me=await api('GET','/api/me');view=defaultView();page=0;filter='';selected=null;
 $('notice').replaceChildren();shell();await load();
}
function navItems(){return APP==='expenseflow'?[['overview','grid','Overview'],['reports','file',me?.role==='EMPLOYEE'?'My expenses':'Review queue'],['policy','shield','Expense policy']]:[['catalog','grid',me?.role==='STAFF'?'Inventory':'Product catalog'],...(me?.role==='STAFF'?[]:[['reservations','clock','Reservations']]),['orders','box','Orders']];}
function shell(){
 $('brand').innerHTML='<span class="brandmark">'+icon(APP==='expenseflow'?'file':'box')+'</span>'+TITLE;
 $('nav').innerHTML=navItems().map(([key,symbol,label])=>'<button data-action="nav" data-id="'+key+'" class="'+(view===key?'active':'')+'">'+icon(symbol)+label+'</button>').join('');
 $('crumb').innerHTML=esc(TITLE)+' <span class="muted">/</span> <strong>'+esc(me?(navItems().find(n=>n[0]===view)?.[2]||'Workspace'):'Welcome')+'</strong>';
 const roleNames=APP==='expenseflow'?{'alice':'Employee','m1':'Manager','f1':'Finance'}:{'alice':'Customer','staff':'Staff'};
 $('user').innerHTML=me?'<span class="avatar">'+esc(me.displayName.slice(0,1).toUpperCase())+'</span>'+(Object.keys(accounts).length?'<label class="sr-only" for="role">Demo role</label><select id="role" class="role-select">'+Object.keys(accounts).map(email=>'<option value="'+esc(email)+'" '+(email===me.email?'selected':'')+'>'+esc(roleNames[email.split('@')[0]]||email)+' · '+esc(email.split('@')[0])+'</option>').join('')+'</select>':'<span>'+esc(words(me.role))+'</span>')+button('Sign out','logout','','ghost small'):'<span class="badge blue">Portfolio demo</span>';
}
function loginScreen(){
 const roles=APP==='expenseflow'?{'alice':'Employee','m1':'Manager','f1':'Finance'}:{'alice':'Customer','staff':'Staff'};
 $('content').innerHTML='<div class="login"><span class="eyebrow">Explore '+TITLE+'</span><h1>'+ (APP==='expenseflow'?'Expenses, with a clear path.':'Every unit, accounted for.')+'</h1><p class="muted">'+(APP==='expenseflow'?'From your first draft to the final approval. A workspace for employees, managers, and finance.':'Browse products, hold stock, and follow an order all the way to delivery.')+'</p>'+(Object.keys(accounts).length?'<div class="demo-roles">'+Object.keys(accounts).map(email=>button(esc(roles[email.split('@')[0]]||email),'login-demo',email,'primary')).join('')+'</div><p class="help">Choose a demo role. Shared accounts use synthetic data only.</p><div class="divider">or sign in with your account</div>':'<p class="help">Use a provisioned account to sign in.</p>')+'<form data-form="login">'+field('email','Email','','email','required autocomplete="username"')+field('password','Password','','password','required autocomplete="current-password"')+'<button class="btn primary" type="submit">Sign in '+icon('arrow')+'</button></form></div>';
}
function modal(title,content){
 $('modal-title').textContent=title;$('modal-body').innerHTML=content;$('modal-error').textContent='';$('modal').showModal();
}
function closeModal(){ $('modal').close(); }
function formData(form){return Object.fromEntries(new FormData(form));}
function renderLoading(){ $('content').innerHTML='<div class="loading"><div class="spinner"></div>Loading your workspace…</div>'; }
async function task(work){
 if(busy)return;busy=true;
 document.querySelectorAll('button:not([data-action="dismiss"]),select.role-select').forEach(el=>{if(!el.disabled){el.disabled=true;el.dataset.busy='true';}});
 try{await work();}catch(error){notice(error.message);if($('modal').open)$('modal-error').textContent=error.message;else if($('content').querySelector('.loading'))$('content').innerHTML='<section class="panel">'+empty('Could not load this view.',error.message,button('Try again','refresh','','primary'))+'</section>';}
 finally{busy=false;document.querySelectorAll('[data-busy]').forEach(el=>{el.disabled=false;delete el.dataset.busy;});if($('role')&&me&&accounts[me.email])$('role').value=me.email;}
}
document.addEventListener('click',event=>{
 const target=event.target.closest('[data-action]');if(!target)return;
 event.preventDefault();
 const action=target.dataset.action,id=target.dataset.id;
 if(action==='dismiss'){$('notice').replaceChildren();return;}
 if(action==='close'){closeModal();return;}
 task(async()=>{
 if(action==='login-demo'){await signIn(id,accounts[id]);return;}
 if(action==='logout'){await api('POST','/api/auth/logout');me=null;data=null;selected=null;csrf=null;shell();loginScreen();return;}
 if(!me)return;
 if(action==='nav'){view=id;page=0;filter='';selected=null;shell();await load();return;}
 if(action==='page'){page=Number(id);selected=null;await load();return;}
 if(action==='refresh'){$('notice').replaceChildren();await load();return;}
 await onAction(action,id,target);
 });
});
document.addEventListener('submit',event=>{
 const form=event.target.closest('form[data-form]');if(!form)return;event.preventDefault();
 task(async()=>{if(form.dataset.form==='login'){const body=formData(form);await signIn(body.email,body.password);return;}await onForm(form.dataset.form,formData(form));});
});
document.addEventListener('change',event=>{
 if(event.target.id==='role')task(async()=>{const email=event.target.value;await signIn(email,accounts[email]);});
 if(event.target.id==='filter')task(async()=>{filter=event.target.value;page=0;selected=null;await load();});
});
async function boot(){
 shell();renderLoading();
 try{const response=await fetch('/demo/config');if(response.ok)accounts=await response.json();}catch{/* Standard sign-in remains available without the demo profile. */}
 try{me=await api('GET','/api/me');csrf=await api('GET','/api/auth/csrf');view=defaultView();shell();await load();}
 catch{me=null;shell();loginScreen();}
}

const APP='expenseflow', TITLE='ExpenseFlow';
const defaultView=()=> 'overview';
const reportId=()=>selected.id||selected.reportId;
const listPath=()=>me.role==='EMPLOYEE'?'/api/reports/mine':me.role==='MANAGER'?'/api/manager/reports':'/api/finance/reports';
const detailPath=id=>me.role==='EMPLOYEE'?'/api/reports/'+id:me.role==='MANAGER'?'/api/manager/reports/'+id:'/api/finance/reports/'+id;
async function load(){
 if(selected){selected=await api('GET',detailPath(reportId()));renderDetail();return;}
 renderLoading();
 if(view==='policy'){policy=(await api('GET','/api/policies/current')).policy;renderPolicy();return;}
 const params=new URLSearchParams({page:String(page),size:'10'});if(filter)params.set('state',filter);
 data=await api('GET',listPath()+'?'+params);
 renderList();
}
function reportTable(){
 const rows=data.content;
 const filters=me.role==='EMPLOYEE'?['DRAFT','SUBMITTED','RETURNED','APPROVED','FINANCE_ACCEPTED','REIMBURSEMENT_RECORDED','REJECTED','WITHDRAWN']:me.role==='FINANCE'?['APPROVED','FINANCE_ACCEPTED']:[];
 return '<section class="panel"><div class="panel-head"><h2>'+(me.role==='EMPLOYEE'?'Your expense reports':'Reports awaiting review')+'</h2>'+(filters.length?'<label class="sr-only" for="filter">Filter reports</label><select id="filter" class="filter"><option value="">All statuses</option>'+filters.map(s=>'<option value="'+s+'" '+(s===filter?'selected':'')+'>'+words(s)+'</option>').join('')+'</select>':'<span class="muted">Assigned to you</span>')+'</div>'+
 (rows.length?'<div class="table-wrap"><table><thead><tr><th>Report</th><th>Created</th><th>Status</th><th>Submitted total</th><th></th></tr></thead><tbody>'+rows.map(r=>'<tr><td><div class="record"><span class="record-icon">'+icon('file')+'</span><div><div class="record-title">'+esc(r.title)+'</div><span class="secondary">EF-'+esc(r.id.slice(0,8).toUpperCase())+'</span></div></div></td><td>'+date(r.createdAt)+'</td><td>'+badge(r.state)+'</td><td class="money">'+(r.total===null?'—':cash(r.total))+'</td><td>'+button('View '+icon('arrow'),'report',r.id,'small')+'</td></tr>').join('')+'</tbody></table></div>':empty(me.role==='EMPLOYEE'?'Your next claim starts here.':'You’re all caught up.',me.role==='EMPLOYEE'?'Create a report, add your expenses, and send it for approval.':'Submitted reports appear here when they are ready for your review.',me.role==='EMPLOYEE'?button('Create expense report','new-report','','primary'):''))+pager(data)+'</section>';
}
function renderList(){
 shell();
 const employee=me.role==='EMPLOYEE';
 const actions=employee?button(icon('plus')+' New expense','new-report','','primary'):'';
 let html=heading(employee?'Personal workspace':words(me.role)+' workspace',view==='overview'?'A clearer view of your expenses.':employee?'My expenses':'Review queue',employee?'Keep your claims organized, from draft to reimbursement.':'Review the submitted details and keep approvals moving.',actions);
 if(view==='overview'){
 html+='<section class="hero"><div><h2>'+ (employee?'Less paperwork. More progress.':'Thoughtful reviews. Clear decisions.')+'</h2><p>'+ (employee?'Add an expense, attach a receipt reference, and submit. Every revision and approval stays in one place.':'Every submission is a frozen snapshot. Review amounts, leave a note, and give employees a clear next step.')+'</p>'+button(employee?'Create your next report '+icon('arrow'):'Open review queue '+icon('arrow'),employee?'new-report':'nav',employee?'':'reports','primary')+'</div><div class="hero-art">'+icon(employee?'file':'shield')+'</div></section>';
 const loaded=data.content;
 html+='<div class="stats">'+stat(employee?'Total reports':'Awaiting review',String(data.totalElements),'Across your accessible reports','file')+stat('Submitted value',cash(loaded.reduce((sum,r)=>sum+Number(r.total||0),0)),'On this page · excludes drafts','money')+stat(employee?'Needs your attention':'Ready for a decision',String(loaded.filter(r=>employee?['DRAFT','RETURNED'].includes(r.state):true).length),'On this page','clock')+'</div>';
 }
 html+=reportTable()+'<p class="footnote">Amounts in INR. Receipt references are text labels; reimbursements record an external payment reference. Shared demo accounts contain synthetic data.</p>';
 $('content').innerHTML=html;
}
function itemsTable(items,editable=false){
 return items.length?'<div class="table-wrap"><table><thead><tr><th>Expense</th><th>Date</th><th>Category</th><th>Amount</th>'+(editable?'<th></th>':'')+'</tr></thead><tbody>'+items.map(item=>'<tr><td><strong>'+esc(item.merchant)+'</strong><span class="secondary">'+esc(item.description)+'</span><span class="secondary">Receipt: '+esc(item.receiptReference)+'</span></td><td>'+date(item.expenseDate)+'</td><td>'+esc(words(item.category))+'</td><td class="money">'+cash(item.amount)+'</td>'+(editable?'<td><div class="actions">'+button('Edit','edit-item',item.id,'small')+button('Remove','delete-item',item.id,'small danger')+'</div></td>':'')+'</tr>').join('')+'</tbody></table></div>':empty('No expenses added yet.','Add your travel, meals, or office supplies to this report.',editable?button(icon('plus')+' Add expense','add-item','','primary'):'');
}
function renderDetail(){
 shell();
 const employee=me.role==='EMPLOYEE',draft=employee&&selected.state==='DRAFT';
 const snapshots=selected.submissions||[],current=snapshots.at(-1),items=employee?selected.items:current?.items||[];
 const title=employee?selected.title:current?.title||'Expense report';
 const total=items.reduce((sum,i)=>sum+Number(i.amount),0);
 let actions='';
 if(draft)actions=button(icon('plus')+' Add expense','add-item','','primary')+button('Submit report','submit-report','','',!items.length);
 if(employee&&selected.state==='RETURNED')actions=button('Reopen for edits','reopen-report','','primary');
 if(employee&&['DRAFT','SUBMITTED','RETURNED'].includes(selected.state))actions+=button('Withdraw','withdraw-report','','danger');
 if(me.role==='MANAGER'&&selected.state==='SUBMITTED')actions=button('Approve','decision','APPROVE','primary')+button('Return','decision','RETURN')+button('Reject','decision','REJECT','danger');
 if(me.role==='FINANCE'&&selected.state==='APPROVED')actions=button('Accept claim','decision','ACCEPT','primary')+button('Return','decision','RETURN');
 if(me.role==='FINANCE'&&selected.state==='FINANCE_ACCEPTED')actions=button('Record reimbursement','reimburse','','primary');
 const events=selected.events||[];
 $('content').innerHTML=heading('Expense detail',title,'EF-'+reportId().slice(0,8).toUpperCase()+' · Revision '+selected.revision,button('← Back to reports','back'))+
 '<div class="actions" style="margin-bottom:24px">'+badge(selected.state)+actions+(draft?button('Rename report','rename','','ghost small'):'')+'</div>'+
 '<div class="grid-two"><div><section class="panel"><div class="panel-head"><h2>'+(draft?'Draft expenses':'Expense items')+'</h2><span class="muted">'+items.length+' items</span></div>'+itemsTable(items,draft)+'<div class="total-row"><span>Total amount</span><strong>'+cash(total)+'</strong></div></section>'+
 '<section class="panel"><div class="panel-head"><h2>Submission history</h2><span class="muted">'+snapshots.length+' versions</span></div><div class="panel-body">'+(snapshots.length?snapshots.map(s=>'<details class="snapshot"><summary>Version '+s.submissionNumber+' · '+date(s.submittedAt)+' · '+cash(s.total)+'</summary><p class="help">'+esc(s.title)+' · Immutable snapshot</p>'+itemsTable(s.items)+'</details>').join(''):'<p class="muted">A snapshot is saved each time this report is submitted.</p>')+'</div></section></div>'+
 '<div><section class="panel"><div class="panel-head"><h2>Report summary</h2></div><div class="panel-body"><div class="summary-row"><span class="muted">Current status</span>'+badge(selected.state)+'</div><div class="summary-row"><span class="muted">Currency</span><strong>INR</strong></div><div class="summary-row"><span class="muted">Submission versions</span><strong>'+snapshots.length+'</strong></div>'+
 (selected.reimbursement?'<div class="readout" style="margin-top:18px">'+icon('check')+'<div><strong>'+cash(selected.reimbursement.amount)+' recorded</strong><p>'+esc(selected.reimbursement.reference)+'</p><span>'+date(selected.reimbursement.recordedAt)+'</span></div></div>':'<p class="help" style="margin-top:18px">Review actions apply to the latest submitted version.</p>')+'</div></section>'+
 '<section class="panel"><div class="panel-head"><h2>Activity</h2></div><div class="panel-body"><ol class="timeline">'+events.map(e=>'<li><strong>'+esc(words(e.eventType))+'</strong><time>'+stamp(e.createdAt)+'</time>'+(e.comment?'<p>'+esc(e.comment)+'</p>':'')+'</li>').join('')+'</ol></div></section></div></div>';
}
function renderPolicy(){
 shell();
 $('content').innerHTML=heading('Company policy','Spend with confidence.','Current policy · '+policy.version)+
 '<section class="hero"><div><h2>A few simple rules. No surprises.</h2><p>Submit 1–50 items per report, up to ₹25,000 in total. Expense dates must fall within the last 90 calendar days in India time.</p></div><div class="hero-art">'+icon('shield')+'</div></section>'+
 '<section class="panel"><div class="panel-head"><h2>Category limits</h2><span class="badge green">Active policy</span></div><div class="table-wrap"><table><thead><tr><th>Category</th><th>Per-item maximum</th><th>Receipt policy</th></tr></thead><tbody>'+Object.entries(policy.limits).map(([category,limit])=>'<tr><td><div class="record"><span class="record-icon">'+icon(category==='TRAVEL'?'bag':category==='MEALS'?'money':'file')+'</span><strong>'+words(category)+'</strong></div></td><td class="money">'+cash(limit.cap)+'</td><td>'+(limit.receiptRequired?'Required':'Optional by policy')+'</td></tr>').join('')+'</tbody></table></div></section><p class="footnote">The current item API requires a receipt reference for every item. Policy checks run on submission, and the applied rules are saved with each snapshot.</p>';
}
function itemForm(item={}){
 const cats=['TRAVEL','MEALS','OFFICE_SUPPLIES'];
 modal(item.id?'Edit expense':'Add an expense','<form data-form="item"><input type="hidden" name="itemId" value="'+esc(item.id||'')+'"><div class="fields"><div class="field"><label for="category">Category</label><select id="category" name="category">'+cats.map(c=>'<option '+(item.category===c?'selected':'')+' value="'+c+'">'+words(c)+'</option>').join('')+'</select></div>'+field('amount','Amount (INR)',item.amount??'','number','required min="0.01" step="0.01"')+field('expenseDate','Expense date',item.expenseDate||today(),'date','required max="'+today()+'"')+field('merchant','Merchant',item.merchant||'','text','required maxlength="120"')+'<div class="wide">'+field('description','Description',item.description||'','text','required maxlength="500"')+'</div><div class="wide">'+field('receiptReference','Receipt reference',item.receiptReference||'','text','required maxlength="100"')+'</div></div><p class="help">Use a synthetic reference such as DEMO-TRAIN-001. No file upload is needed.</p>'+formButtons('Save expense')+'</form>');
}
async function onAction(action,id){
 if(action==='new-report'){modal('Create an expense report','<form data-form="report">'+field('title','Report title','','text','required maxlength="120" placeholder="e.g. Bangalore training trip"')+'<p class="help">Group expenses for a trip, event, or purchase.</p>'+formButtons('Create report')+'</form>');return;}
 if(action==='report'){selected=await api('GET',detailPath(id));view='reports';renderDetail();return;}
 if(action==='back'){selected=null;view='reports';shell();await load();return;}
 if(action==='add-item'){itemForm();return;}
 if(action==='edit-item'){itemForm(selected.items.find(item=>item.id===id));return;}
 if(action==='rename'){modal('Rename report','<form data-form="rename">'+field('title','Report title',selected.title,'text','required maxlength="120"')+formButtons()+'</form>');return;}
 if(action==='delete-item'){modal('Remove this expense?','<p>The draft item will be removed. Submitted snapshots remain in the history.</p><form data-form="delete-item"><input type="hidden" name="itemId" value="'+esc(id)+'">'+formButtons('Remove expense')+'</form>');return;}
 if(action==='withdraw-report'){modal('Withdraw report','<form data-form="withdraw"><label for="comment">Reason (optional)</label><textarea id="comment" name="comment" maxlength="1000"></textarea><p class="help">This is a terminal action. Create another report if you need to submit again.</p>'+formButtons('Withdraw report')+'</form>');return;}
 if(action==='decision'){modal(words(id)+' report','<form data-form="decision"><input type="hidden" name="decision" value="'+id+'"><label for="comment">Review note'+(['RETURN','REJECT'].includes(id)?' (required)':' (optional)')+'</label><textarea id="comment" name="comment" maxlength="1000" '+(['RETURN','REJECT'].includes(id)?'required':'')+'></textarea>'+formButtons(words(id))+'</form>');return;}
 if(action==='reimburse'){modal('Record reimbursement','<form data-form="reimburse">'+field('reference','External payment reference','','text','required minlength="8" maxlength="64" pattern="[A-Za-z0-9-]{8,64}" placeholder="DEMO-PAYMENT-001"')+'<p class="help">Records the approved amount. This application does not transfer money.</p>'+formButtons('Record reimbursement')+'</form>');return;}
 if(action==='submit-report'||action==='reopen-report'){
 await api('POST','/api/reports/'+reportId()+(action==='submit-report'?'/submit':'/reopen'),{expectedRevision:selected.revision});await load();notice(action==='submit-report'?'Report submitted. Switch to Manager to review it.':'Report reopened. You can now edit the draft.',true);
 }
}
async function onForm(type,body){
 if(type==='report'){selected=await api('POST','/api/reports',{title:body.title});closeModal();view='reports';renderDetail();notice('Report created. Add your first expense.',true);return;}
 const id=reportId(),revision=selected.revision;
 if(type==='item'){
 const itemId=body.itemId;delete body.itemId;body.amount=Number(body.amount);body.expectedRevision=revision;
 await api(itemId?'PUT':'POST','/api/reports/'+id+'/items'+(itemId?'/'+itemId:''),body);
 }else if(type==='rename'){await api('PATCH','/api/reports/'+id,{title:body.title,expectedRevision:revision});}
 else if(type==='delete-item'){await api('DELETE','/api/reports/'+id+'/items/'+body.itemId+'?expectedRevision='+revision);}
 else if(type==='withdraw'){await api('POST','/api/reports/'+id+'/withdraw',{expectedRevision:revision,comment:body.comment||null});}
 else if(type==='decision'){
 await api('POST',detailPath(id)+'/decision',{expectedRevision:revision,decision:body.decision,comment:body.comment||null});
 closeModal();selected=null;await load();notice('Decision saved. The report history has been updated.',true);return;
 }else if(type==='reimburse'){await api('POST',detailPath(id)+'/reimbursement',{expectedRevision:revision,reference:body.reference});}
 closeModal();await load();notice('Changes saved.',true);
}
boot();
