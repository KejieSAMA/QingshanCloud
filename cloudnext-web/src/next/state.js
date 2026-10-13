import { reactive } from 'vue';
import * as api from './api';
import { defaultPreferences, normalizePreferences } from './preferences';
import { getStoredSession, getStoredToken, clearStoredSession, emitUnauthorizedSignal, emitDriveRefreshSignal } from '../session';
export const ui = reactive({
 session: getStoredSession(), prefs: defaultPreferences(), effectivePrimaryColor: 'blue', accountRevision: 0,
 ready: false, admin: false, recent: [], usage: null, error: '', dialogs: [], popup: null, detail: null, preview: null,
 toast: '', queue: [], processing: false, revision: 0, context: { scopeType: 'PERSONAL', scopeId: '', parentId: '' }
});
let toastTimer, nextId = 0, currentToken = getStoredToken();
const uploads = new Set();
export function syncSession(session) {
 const token = session?.token || '';
 if (token === currentToken) { ui.session = session; return; }
 currentToken = token;
 const dialogs = ui.dialogs.splice(0);
 ui.accountRevision++;
 for (const xhr of uploads) xhr.abort();
 uploads.clear(); clearTimeout(toastTimer);
 Object.assign(ui, { session, prefs: defaultPreferences(), ready:false, admin:false, recent:[], usage:null,
  error:'', popup:null, detail:null, preview:null, toast:'', queue:[], processing:false, revision:0,
  context:{scopeType:'PERSONAL',scopeId:'',parentId:''} });
 // Unblock suspended old operations without executing their close hooks or restoring stale focus.
 for (const d of dialogs) d.resolve(null);
 applyPrefs();
}
export function notify(message) { ui.toast = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => ui.toast = '', 4200); }
export async function run(action) {
 const generation = ui.accountRevision;
 try { return await action(); } catch (e) { if(generation === ui.accountRevision && e.name !== 'AbortError') notify(e.message || '操作失败，请重试'); }
}
export function applyPrefs(p = ui.prefs) {
 const prefs = normalizePreferences(p);
 ui.effectivePrimaryColor = prefs.primaryColor;
 document.documentElement.dataset.primaryColor = prefs.primaryColor;
 document.body.dataset.density = prefs.density;
 document.body.classList.toggle('body-no-motion', !prefs.motion);
}
export function dialog(config) {
 return new Promise(resolve => { ui.dialogs.push({ id: ++nextId, title: '', fields: [], values: {}, records: [], ...config, resolve, focus: document.activeElement, error: '', busy: false }); });
}
export function closeDialog(result = null, expectedId) {
 const d = ui.dialogs.at(-1);
 if (!d || (expectedId != null && d.id !== expectedId)) return;
 ui.dialogs.pop(); d.onClose?.(result); d.resolve(result);
 queueMicrotask(() => d.focus?.isConnected && d.focus.focus({ preventScroll: true }));
}
export async function confirm(title, description) { return Boolean(await dialog({ title, description, submitLabel: '确认', danger: true, onSubmit: () => true })); }
export async function refreshShell() {
 if(!currentToken)return;
 const generation=ui.accountRevision;
 const results = await Promise.allSettled([api.get('/api/drive/me/recent-opened/query', { size: 3 }),api.get('/api/drive/me/storage-usage')]);
 if(generation!==ui.accountRevision)return;
 if(results[0].status === 'fulfilled') ui.recent = results[0].value.items || [];
 if(results[1].status === 'fulfilled') ui.usage = results[1].value;
 const failure = results.find(r => r.status === 'rejected');
 ui.error = failure ? failure.reason.message : '';
}
export async function init() {
 if(!currentToken)return;
 const generation=ui.accountRevision;
 const results = await Promise.allSettled([api.get('/api/drive/preferences/ui'), api.getCurrentAdminStatus()]);
 if(generation!==ui.accountRevision)return;
 if(results[0].status==='fulfilled') ui.prefs = normalizePreferences(results[0].value); else ui.error = results[0].reason.message;
 if(results[1].status==='fulfilled') ui.admin = Boolean(results[1].value.admin);
 applyPrefs(); ui.ready = true; await refreshShell();
}
export function changed() { ui.revision++; emitDriveRefreshSignal(); void refreshShell(); }
export function batchResult(result) {
 if (result?.totalFailed) {
  const failures = Object.entries(result.failedItems || {}).map(([id, error]) => ({ id, name: id, description: error }));
  void dialog({ title: '部分项目未完成', description: '成功 ' + result.totalSucceeded + ' 项，失败 ' + result.totalFailed + ' 项', records: failures });
 } else notify('操作成功');
 changed(); return result;
}
function upload(entry) {
 return new Promise((resolve, reject) => {
  const xhr = new XMLHttpRequest(), body = new FormData(), token=getStoredToken();
  const finish=(fn,value)=>{uploads.delete(xhr);fn(value);};
  const aborted=()=>{const e=new Error('上传会话已结束');e.name='AbortError';finish(reject,e);};
  uploads.add(xhr);
  body.append('file', entry.file); body.append('parentId', entry.target.parentId || ''); body.append('conflictStrategy', entry.strategy);
  const url = entry.target.scopeType === 'WORKSPACE' ? '/api/workspaces/' + encodeURIComponent(entry.target.scopeId) + '/files' : '/api/drive/me/files';
  xhr.open('POST', url); xhr.setRequestHeader('X-CloudNext-Token', token);
  xhr.upload.onprogress = e => { if(e.lengthComputable) entry.progress = Math.round(e.loaded / e.total * 100); };
  xhr.onabort=aborted;
  xhr.onerror = () => finish(reject,new Error('网络中断，请重试'));
  xhr.onload = () => {
   if(token!==getStoredToken()){aborted();return;}
   let result; try { result = JSON.parse(xhr.responseText || '{}'); } catch { result = {}; }
   if(xhr.status >= 200 && xhr.status < 300) finish(resolve,result);
   else { if(xhr.status===401){ clearStoredSession(); emitUnauthorizedSignal(); } const error = new Error(result.message || '上传失败'); error.status = xhr.status; finish(reject,error); }
  }; xhr.send(body);
 });
}
export async function processQueue() {
 if(ui.processing || !currentToken || currentToken !== getStoredToken()) return;
 const generation=ui.accountRevision;
 ui.processing = true;
 try { for (const entry of ui.queue) {
  if(generation!==ui.accountRevision || currentToken !== getStoredToken())break;
  if(entry.status !== 'waiting') continue;
  entry.status = 'uploading'; entry.error = '';
  try {
   await upload(entry);
   if(generation!==ui.accountRevision)break;
   entry.status = 'done'; entry.file = null; changed();
  } catch(error) {
   if(generation!==ui.accountRevision)break;
   if(error.status === 409) {
    const strategy = await dialog({ title: '存在同名文件：' + entry.name, fields: [{ key: 'strategy', label: '处理方式', type: 'select', options: [['keep_both','保留两份'],['replace','覆盖并保留历史版本'],['abort','停止此项']] }], values: {strategy:'keep_both'}, submitLabel:'继续', onSubmit:v=>v.strategy });
    if(generation!==ui.accountRevision)break;
    if(strategy && strategy !== 'abort') { entry.strategy=strategy; entry.status='waiting'; } else { entry.status='failed'; entry.error='存在同名文件'; }
   } else {entry.status='failed';entry.error=error.message;}
  }
 } } finally {
  if(generation===ui.accountRevision){ui.processing = false;if(ui.queue.some(e=>e.status==='waiting'))void processQueue();}
 }
}
export function enqueue(files, target = ui.context, strategy = 'abort') {
 for (const file of files) ui.queue.push({ id: ++nextId, file, name:file.name, sizeBytes:file.size, target:{...target}, strategy, status:'waiting', progress:0, error:'' });
 void processQueue();
}
export function busyUploads() { return ui.queue.some(e => ['waiting','uploading'].includes(e.status)); }
