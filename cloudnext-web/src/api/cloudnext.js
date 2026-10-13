import { clearStoredSession, emitUnauthorizedSignal, getStoredToken } from '../session';

/**
 * N+2.7 F5: Blob URL 保留时长（毫秒）。openBlobPreview 创建的 object URL
 * 在新窗口加载完成前需要保持有效；60s 是经验值，覆盖绝大多数浏览器装载耗时。
 */
const BLOB_URL_RETENTION_MS = 60_000;

let sharedAdminConfigPromise = null;
let sharedAdminConfigToken = '';

function parseJsonSafely(text) {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    return null;
  }
}

async function createHttpError(response) {
  const text = await response.text();
  const data = parseJsonSafely(text);
  const message =
    data && typeof data.message === 'string' && data.message.trim()
      ? data.message
      : `请求失败: ${response.status}`;
  const error = new Error(message);
  error.status = response.status;
  error.data = data;
  return error;
}

function getAuthHeaders(headers = {}) {
  const token = getStoredToken();
  return {
    ...(token ? { 'X-CloudNext-Token': token } : {}),
    ...headers
  };
}

function handleUnauthorized() {
  clearStoredSession();
  emitUnauthorizedSignal();
}

function assertRequestSession(token) {
  if (token === getStoredToken()) return;
  const error = new Error('账号已切换，忽略上一会话的请求');
  error.name = 'AbortError';
  throw error;
}

function buildQuery(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

export async function fetchJson(url, options = {}) {
  const requestToken = getStoredToken();
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...getAuthHeaders(options.headers || {})
    }
  });

  assertRequestSession(requestToken);
  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorized();
    }
    throw await createHttpError(response);
  }

  const text = await response.text();
  assertRequestSession(requestToken);
  return parseJsonSafely(text);
}

async function fetchUnauthenticatedJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });

  if (!response.ok) {
    throw await createHttpError(response);
  }

  const text = await response.text();
  return parseJsonSafely(text);
}

export async function fetchMultipart(url, formData) {
  const requestToken = getStoredToken();
  const response = await fetch(url, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: formData
  });

  assertRequestSession(requestToken);
  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorized();
    }
    throw await createHttpError(response);
  }

  const text = await response.text();
  assertRequestSession(requestToken);
  return parseJsonSafely(text);
}

export async function fetchBlob(url, options = {}) {
  const requestToken = getStoredToken();
  const response = await fetch(url, {
    ...options,
    headers: getAuthHeaders(options.headers || {})
  });

  assertRequestSession(requestToken);
  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorized();
    }
    throw await createHttpError(response);
  }

  const blob = await response.blob();
  assertRequestSession(requestToken);
  return blob;
}

export function isAbortError(error) {
  return error?.name === 'AbortError';
}

function openBlobPreview(blob) {
  const objectUrl = window.URL.createObjectURL(blob);
  window.open(objectUrl, '_blank', 'noopener');
  window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), BLOB_URL_RETENTION_MS);
}

export function getStatus() {
  return fetchJson('/api/status');
}

export function searchFiles(params = {}) {
  return fetchJson(`/api/search/files${buildQuery(params)}`);
}

export function getAdminConfig(options = {}) {
  return fetchJson('/api/admin/config', options);
}

export function getSharedAdminConfig(options = {}) {
  const token = getStoredToken() || '';
  const force = options?.force === true;
  if (!sharedAdminConfigPromise || force || sharedAdminConfigToken !== token) {
    sharedAdminConfigToken = token;
    sharedAdminConfigPromise = getAdminConfig(options).catch((error) => {
      sharedAdminConfigPromise = null;
      throw error;
    });
  }
  return sharedAdminConfigPromise;
}

export function getSyncStatus() {
  return fetchJson('/api/admin/sync/status');
}

export function getSyncStats() {
  return fetchJson('/api/admin/sync/stats');
}

export function triggerSync() {
  return fetchJson('/api/admin/sync/trigger', {
    method: 'POST'
  });
}

export function searchAllItems(params = {}, options = {}) {
  return fetchJson(`/api/search${buildQuery(params)}`, options);
}

export function triggerFormDirectorySync() {
  return fetchJson('/api/admin/form-sync/trigger', { method: 'POST' });
}

export function getFormDirectorySyncStatus() {
  return fetchJson('/api/admin/form-sync/status');
}

export function getFormMigrationOverview() {
  return fetchJson('/api/admin/form-migration/overview');
}

export function listFormMigrationBindings(kind) {
  const allowed = new Set([
    'principals', 'units', 'identities',
    'legacy/principals', 'legacy/units', 'legacy/identities'
  ]);
  if (!allowed.has(kind)) {
    return Promise.reject(new Error('不支持的迁移映射类型'));
  }
  return fetchJson(`/api/admin/form-migration/${kind}`);
}

export function updateFormMigrationDecision(kind, formId, payload) {
  const allowed = new Set([
    'principals', 'units', 'identities',
    'legacy/principals', 'legacy/units', 'legacy/identities'
  ]);
  if (!allowed.has(kind)) {
    return Promise.reject(new Error('不支持的迁移映射类型'));
  }
  return fetchJson(
    `/api/admin/form-migration/${kind}/${encodeURIComponent(formId)}/decision`,
    { method: 'PUT', body: JSON.stringify(payload) }
  );
}

export function validateFormMigration() {
  return fetchJson('/api/admin/form-migration/validation');
}

export function importFormUnitMappings(file) {
  return file.text().then((csv) => fetchJson('/api/admin/form-migration/units/import.csv', {
    method: 'POST',
    headers: { 'Content-Type': 'text/csv;charset=UTF-8' },
    body: csv
  }));
}

export async function exportFormUnitMappings() {
  const blob = await fetchBlob('/api/admin/form-migration/units/export.csv');
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = 'cloudnext-form-unit-crosswalk.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
}

export function getAuthStatus() {
  return fetchUnauthenticatedJson('/api/auth/status');
}

export function login(payload) {
  return fetchUnauthenticatedJson('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getCurrentSession() {
  return fetchJson('/api/auth/me');
}

export function completeSsoLogin() {
  return fetchUnauthenticatedJson('/api/auth/sso/complete', {
    method: 'POST',
    credentials: 'same-origin'
  });
}

export function buildSsoStartUrl(returnPath = '/') {
  return `/api/auth/sso/start${buildQuery({ returnPath })}`;
}

export function logout() {
  return fetchJson('/api/auth/logout', {
    method: 'DELETE'
  });
}

export function getMyDriveItems(parentId = '') {
  return fetchJson(`/api/drive/me/items${buildQuery({ parentId })}`);
}

export function getMyDriveItemsQuery(params = {}, options = {}) {
  return fetchJson(`/api/drive/me/items/query${buildQuery(params)}`, options);
}

export function getRecentDriveItems() {
  return fetchJson('/api/drive/me/recent');
}

export function getRecentOpenedDriveItems(limit = 6, options = {}) {
  return fetchJson(`/api/drive/me/recent-opened${buildQuery({ limit })}`, options);
}

export function getDrivePath(itemId = '', options = {}) {
  return fetchJson(`/api/drive/me/path${buildQuery({ itemId })}`, options);
}

export function createFolder(payload) {
  return fetchJson('/api/drive/me/folders', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function uploadDriveFile({ parentId = '', file, conflictStrategy = 'abort' }) {
  const formData = new FormData();
  if (parentId) {
    formData.append('parentId', parentId);
  }
  if (conflictStrategy) {
    formData.append('conflictStrategy', conflictStrategy);
  }
  formData.append('file', file);
  return fetchMultipart('/api/drive/me/files', formData);
}

export function getDriveItemPreview(id) {
  return fetchJson(`/api/drive/items/${encodeURIComponent(id)}/preview`);
}

export function getDriveItemEdit(id) {
  return fetchJson(`/api/drive/items/${encodeURIComponent(id)}/edit`);
}

export function forceSaveDriveItem(editTicket) {
  return fetchJson(`/api/onlyoffice/force-save${buildQuery({ editTicket })}`, {
    method: 'POST'
  });
}

export function getOnlyofficeSaveStatus(editTicket) {
  return fetchJson(`/api/onlyoffice/save-status${buildQuery({ editTicket })}`);
}

export function getOnlyofficeHealth() {
  return fetchJson('/api/onlyoffice/health');
}

export function getCurrentAdminStatus() {
  return fetchJson('/api/admin/whoami');
}

export function getAuditLogs(filters = {}) {
  return fetchJson(`/api/admin/audit-logs${buildQuery(filters)}`);
}

export function getLinkAccessLogs(linkId, page = 1, size = 20) {
  return fetchJson(`/api/links/${encodeURIComponent(linkId)}/access-logs${buildQuery({ page, size })}`);
}

export async function editDriveItem(item) {
  const edit = await getDriveItemEdit(item.id);
  if (!edit || edit.supported === false) {
    throw new Error(edit?.unavailableMessage || '当前文件暂不支持在线编辑。');
  }
  if (!edit.editPageUrl) {
    throw new Error('Office 编辑地址未配置。');
  }
  window.open(edit.editPageUrl, '_blank', 'noopener');
  return edit;
}

export async function previewDriveItem(item) {
  const preview = await getDriveItemPreview(item.id);
  if (!preview || preview.supported === false) {
    if (preview?.kind === 'office' && preview?.unavailableCode === 'onlyoffice_not_configured') {
      throw new Error(preview.unavailableMessage || 'OnlyOffice preview is not configured.');
    }
    if (preview?.allowDownload === false) {
      throw new Error('当前共享权限不允许下载此文件。');
    }
    await downloadDriveItem(item);
    return preview;
  }
  if (preview.kind === 'office') {
    if (!preview.previewPageUrl) {
      throw new Error('Office 预览地址未配置');
    }
    window.open(preview.previewPageUrl, '_blank', 'noopener');
    return preview;
  }
  const blob = await fetchBlob(preview.previewUrl || `/api/drive/items/${encodeURIComponent(item.id)}/download`);
  openBlobPreview(blob);
  return preview;
}

export function deleteDriveItem(id) {
  return fetchJson(`/api/drive/items/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  });
}

export function renameDriveItem(id, payload) {
  return fetchJson(`/api/drive/items/${encodeURIComponent(id)}/name`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function moveDriveItems(payload) {
  return fetchJson('/api/drive/items/move', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function searchOrgTargets(type, keyword) {
  return fetchJson(`/api/org/search${buildQuery({ type, keyword })}`);
}

export function createDriveShares(payload) {
  return fetchJson('/api/drive/shares', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getDriveShareSummary(itemId) {
  return fetchJson(`/api/drive/shares/summary${buildQuery({ itemId })}`);
}

export function getOutgoingDriveShares(params = {}) {
  return fetchJson(`/api/drive/shares/outgoing${buildQuery(params)}`);
}

export function getIncomingDriveShares(params = {}) {
  return fetchJson(`/api/drive/shares/incoming${buildQuery(params)}`);
}

export function getIncomingDriveSharePath(shareId, itemId = '') {
  return fetchJson(`/api/drive/shares/incoming/path${buildQuery({ shareId, itemId })}`);
}

export function updateDriveShare(id, payload) {
  return fetchJson(`/api/drive/shares/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function revokeDriveShare(id) {
  return fetchJson(`/api/drive/shares/${encodeURIComponent(id)}/revoke`, {
    method: 'POST'
  });
}

export function revokeDriveShares(payload) {
  return fetchJson('/api/drive/shares/revoke', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function batchRenameDriveItems(payload) {
  return fetchJson('/api/drive/items/batch-rename', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getTrashItems() {
  return fetchJson('/api/drive/trash/items');
}

export function getTrashItemsQuery(params = {}) {
  return fetchJson(`/api/drive/trash/items/query${buildQuery(params)}`);
}

export function restoreDriveItem(id) {
  return fetchJson(`/api/drive/trash/items/${encodeURIComponent(id)}/restore`, {
    method: 'POST'
  });
}

export function restoreDriveItems(payload) {
  return fetchJson('/api/drive/trash/items/restore', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function purgeTrashItems(payload) {
  return fetchJson('/api/drive/trash/items/purge', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function clearTrashItems() {
  return fetchJson('/api/drive/trash/clear', {
    method: 'POST'
  });
}

export async function downloadDriveItem(item) {
  const blob = await fetchBlob(`/api/drive/items/${encodeURIComponent(item.id)}/download`);
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = item.name || 'download';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
}

export async function batchDownloadDriveItems(itemIds, zipName) {
  const ids = (itemIds || []).filter(Boolean);
  if (!ids.length) {
    throw new Error('请选择要打包下载的项目');
  }
  const blob = await fetchBlob('/api/drive/items/batch-download', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ itemIds: ids, zipName: zipName || '' })
  });
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  const fallback = zipName && zipName.toLowerCase().endsWith('.zip')
    ? zipName
    : (zipName ? `${zipName}.zip` : 'cloudnext-files.zip');
  link.download = fallback;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
}

// ----------------------------------------------------------------
// Workspace (iteration 1)
// ----------------------------------------------------------------

export function getWorkspaces() {
  return fetchJson('/api/workspaces');
}

export function copyDriveItems(payload) {
  return fetchJson('/api/drive/items/copy', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getDriveSortPreference(context) {
  return fetchJson(`/api/drive/preferences/sort${buildQuery({ context })}`);
}

export function updateDriveSortPreference(payload) {
  return fetchJson('/api/drive/preferences/sort', {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function getWorkspaceSortPreference() {
  return fetchJson('/api/workspaces/sort-preference');
}

export function updateWorkspaceSortPreference(sortMode) {
  return fetchJson('/api/workspaces/sort-preference', {
    method: 'PUT',
    body: JSON.stringify({ sortMode })
  });
}

export function createWorkspace(payload) {
  return fetchJson('/api/workspaces', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getWorkspace(id) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}`);
}

export function updateWorkspace(id, payload) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function deleteWorkspace(id) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  });
}

export function getWorkspaceItems(id, parentId = '') {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/items${buildQuery({ parentId })}`);
}

export function getWorkspaceItemsQuery(id, params = {}, options = {}) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/items/query${buildQuery(params)}`, options);
}

export function createWorkspaceFolder(id, payload) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/folders`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function uploadWorkspaceFile(id, { parentId = '', file, conflictStrategy = 'abort' }) {
  const formData = new FormData();
  if (parentId) {
    formData.append('parentId', parentId);
  }
  if (conflictStrategy) {
    formData.append('conflictStrategy', conflictStrategy);
  }
  formData.append('file', file);
  return fetchMultipart(`/api/workspaces/${encodeURIComponent(id)}/files`, formData);
}

export function listWorkspaceMembers(id) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/members`);
}

export function addWorkspaceMember(id, payload) {
  return fetchJson(`/api/workspaces/${encodeURIComponent(id)}/members`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function updateWorkspaceMember(id, memberId, payload) {
  return fetchJson(
    `/api/workspaces/${encodeURIComponent(id)}/members/${encodeURIComponent(memberId)}`,
    {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }
  );
}

export function removeWorkspaceMember(id, memberId) {
  return fetchJson(
    `/api/workspaces/${encodeURIComponent(id)}/members/${encodeURIComponent(memberId)}`,
    {
      method: 'DELETE'
    }
  );
}

// ----------------------------------------------------------------
// iteration 2: public links
// ----------------------------------------------------------------

/**
 * GET /api/links — list active public links owned by the caller.
 * @returns {Promise<Array<object>>}
 */
export function listMyLinks() {
  return fetchJson('/api/links');
}

/**
 * POST /api/links — create a new public link.
 * @param {{
 *   itemId: string,
 *   permission: 'preview' | 'download',
 *   password?: string,
 *   downloadLimit?: number | null,
 *   expiresAt?: string
 * }} body
 */
export function createLink(body) {
  return fetchJson('/api/links', {
    method: 'POST',
    body: JSON.stringify(body)
  });
}

/**
 * PATCH /api/links/:id — partial update; null/undefined fields are left unchanged
 * by the server.
 */
export function updateLink(id, body) {
  return fetchJson(`/api/links/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(body)
  });
}

/**
 * POST /api/links/:id/revoke — mark the link revoked. Idempotent server-side.
 * Returns the updated link projection.
 */
export function revokeLink(id) {
  return fetchJson(`/api/links/${encodeURIComponent(id)}/revoke`, {
    method: 'POST'
  });
}

/**
 * Anonymous fetch helper for `/public/links/...` endpoints.
 *
 * Unlike `fetchJson`, this MUST NOT attach `X-CloudNext-Token`: public-link
 * endpoints are reachable without a CloudNext session, and leaking the user's
 * token to them would let the server tie an anonymous access back to the
 * logged-in user. We also do not clear stored session on 401 here — a 401
 * from a public endpoint does not invalidate the user's CloudNext session.
 *
 * Reuses `createHttpError` / `parseJsonSafely` so callers get the same error
 * shape as authenticated calls.
 */
async function fetchPublicJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });

  if (!response.ok) {
    throw await createHttpError(response);
  }

  const text = await response.text();
  return parseJsonSafely(text);
}

/**
 * GET /public/links/:token/meta — anonymous, sanitized projection.
 * @returns {Promise<{ fileName: string, fileType: string, permission: 'preview' | 'download', requirePassword: boolean, isOpen: boolean }>}
 */
export function getPublicLinkMeta(token) {
  return fetchPublicJson(`/public/links/${encodeURIComponent(token)}/meta`);
}

/**
 * POST /public/links/:token/verify — anonymous; submits `{ password }` and
 * returns `{ accessTicket }` on success.
 *
 * @param {string} token
 * @param {string} [password]
 * @returns {Promise<{ accessTicket: string }>}
 */
export function verifyPublicLink(token, password) {
  const body = { password: password == null ? '' : String(password) };
  return fetchPublicJson(`/public/links/${encodeURIComponent(token)}/verify`, {
    method: 'POST',
    body: JSON.stringify(body)
  });
}

/**
 * Build the URL for opening a public-link preview in a new tab. Does NOT
 * perform any HTTP request; the caller is expected to feed the result to
 * `window.open` or an `<a href>`. If `accessTicket` is non-blank, it is
 * appended as `?accessTicket=...`; otherwise no query string is added.
 *
 * @param {string} token
 * @param {string} [accessTicket]
 * @returns {string}
 */
export function publicLinkPreviewUrl(token, accessTicket) {
  const base = `/public/links/${encodeURIComponent(token)}/preview`;
  if (accessTicket) {
    return `${base}?accessTicket=${encodeURIComponent(accessTicket)}`;
  }
  return base;
}

/**
 * Build the URL for triggering a public-link download. Does NOT perform any
 * HTTP request. Same `accessTicket` handling as `publicLinkPreviewUrl`.
 *
 * @param {string} token
 * @param {string} [accessTicket]
 * @returns {string}
 */
export function publicLinkDownloadUrl(token, accessTicket) {
  const base = `/public/links/${encodeURIComponent(token)}/download`;
  if (accessTicket) {
    return `${base}?accessTicket=${encodeURIComponent(accessTicket)}`;
  }
  return base;
}
