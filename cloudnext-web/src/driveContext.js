import { embeddedQuery } from './embedded/context.js';
const DRIVE_ID = /^[a-f0-9]{64}$/;
const ENTRY_ID = /^[a-zA-Z0-9_-]{1,80}$/;
const initial = new URLSearchParams(globalThis.window?.location?.search || '');
let driveId = initial.get('driveId') || '';
let entryId = initial.get('entryId') || '';

export function getActiveDriveId() { return DRIVE_ID.test(driveId) ? driveId : ''; }
export function getActiveEntryId() {
  if (entryId) return entryId;
  if (getActiveDriveId()) {
    try {
      const stored = JSON.parse(globalThis.window?.localStorage?.getItem('cloudnext.session.' + driveId) || 'null');
      if (stored?.drive?.id === driveId && ENTRY_ID.test(stored.drive.entryId)) return stored.drive.entryId;
    } catch { /* An invalid cache never selects a different drive. */ }
  }
  return 'default';
}
export function getDriveStorageScope() {
  if (driveId) return DRIVE_ID.test(driveId) ? driveId : 'invalid';
  return entryId && entryId !== 'default' ? (ENTRY_ID.test(entryId) ? `entry:${entryId}` : 'invalid') : 'default';
}
export function setDriveRouteContext(query = {}) {
  const nextDrive = typeof query.driveId === 'string' ? query.driveId : '';
  const nextEntry = typeof query.entryId === 'string' ? query.entryId : (nextDrive && nextDrive === driveId ? entryId : '');
  if (!nextDrive && !nextEntry) return false;
  const changed = driveId !== nextDrive || entryId !== nextEntry;
  driveId = nextDrive; entryId = nextEntry; return changed;
}
export function acceptSessionDrive(drive) {
  if (!drive) {
    if (getDriveStorageScope() !== 'default') throw new Error('当前服务尚不支持专属云盘隔离');
    return;
  }
  if (!DRIVE_ID.test(drive.id) || !ENTRY_ID.test(drive.entryId)
    || (driveId && driveId !== drive.id) || (entryId && entryId !== drive.entryId)) {
    throw new Error('登录会话与当前云盘不一致，请重新从入口进入');
  }
  driveId = drive.id; entryId = drive.entryId;
}
export function driveRouteQuery() {
  return { ...embeddedQuery(), ...(driveId ? { driveId } : {}), ...(entryId ? { entryId } : {}) };
}
export function withDriveContext(path, allowConfiguredAbsoluteUrl = false) {
  const absolute = allowConfiguredAbsoluteUrl && typeof path === 'string' && /^https?:\/\//i.test(path);
  const candidate = absolute || (typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')) ? path : '/';
  const url = new URL(candidate, 'https://cloudnext.invalid');
  for (const [key, value] of Object.entries(driveRouteQuery())) url.searchParams.set(key, value);
  return absolute ? url.toString() : url.pathname + url.search + url.hash;
}
