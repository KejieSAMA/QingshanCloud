import { fetchJson, fetchUnauthenticatedJson, fetchBlob, previewAuthHeaders } from '../api/cloudnext.js';
export const previewHeaders = ticket => previewAuthHeaders({ 'X-CloudNext-Preview': ticket });
export async function describePreview(target, signal) {
  try {
  if (target.ticket) return inspectPreview(target.ticket, false, false, signal);
  if (target.publicUrl) return await fetchUnauthenticatedJson(target.publicUrl, { signal, headers: target.publicHeaders || {} });
  const base = '/api/drive/items/' + encodeURIComponent(target.item.id);
  return await fetchJson(base + (target.version ? '/versions/' + encodeURIComponent(target.version.id) : '') + '/preview', { signal });
  } catch(error) { throw previewError(error); }
}
export async function inspectPreview(ticket, generate, retry, signal) {
  try { return await fetchUnauthenticatedJson('/api/previews' + (retry ? '?retry=true' : ''), {
    method: generate ? 'POST' : 'GET', signal, headers: previewHeaders(ticket),
  }); } catch(error) { throw previewError(error); }
}
export async function previewBlob(ticket, signal) {
  const response = await fetch('/api/previews/content', { signal, headers: previewHeaders(ticket) });
  if (!response.ok) { const body = await response.json().catch(() => null); throw previewError(Object.assign(new Error(body?.message || '预览内容读取失败。'), { status: response.status })); }
  return response.blob();
}
function previewError(error) {
  const messages={401:'预览已失效，请重新打开文件。',403:'无权预览该文件。',404:'文件不存在或已被删除。',409:'文件已更新，请重新打开预览。',410:'在线编辑已停用，请重新打开文件预览。'};
  if(messages[error.status]&&!/[\u3400-\u9fff]/.test(error.message||''))error.message=messages[error.status];
  return error;
}
