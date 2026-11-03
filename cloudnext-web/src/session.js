import { acceptSessionDrive, getActiveDriveId, getActiveEntryId, getDriveStorageScope } from './driveContext.js';
import { embedded } from './embedded/context.js';
const memory = new Map();
const storage = () => embedded ? {
  getItem: key => memory.get(key) || null,
  setItem: (key, value) => memory.set(key, value),
  removeItem: key => memory.delete(key)
} : window.localStorage;
export const CLOUDNEXT_TOKEN_KEY = 'cloudnext.token';
export const CLOUDNEXT_SESSION_KEY = 'cloudnext.session';
export const CLOUDNEXT_DRIVE_REFRESH_KEY = 'cloudnext.drive.refresh';
export const CLOUDNEXT_DRIVE_REFRESH_EVENT = 'cloudnext:drive-refresh';
export const CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT = 'cloudnext:open-upload-modal';
export const CLOUDNEXT_AUTH_UNAUTHORIZED_EVENT = 'cloudnext:auth-unauthorized';
export const CLOUDNEXT_SESSION_CHANGED_EVENT = 'cloudnext:session-changed';

export function normalizeIdentity(identity) {
  if (!identity || typeof identity !== 'object') {
    return identity;
  }
  const identityKey = identity.identityKey || identity.id || '';
  const unitKey = identity.unitKey || identity.unit || '';
  return {
    ...identity,
    identityKey,
    unitKey,
    id: identity.id || identityKey,
    unit: identity.unit || unitKey,
    displayPath: identity.displayPath || identity.distinguishedName || ''
  };
}

export function normalizeSession(session) {
  if (!session || typeof session !== 'object') {
    return session;
  }
  const personKey = session.personKey || session.personOaId || '';
  return {
    ...session,
    personKey,
    // Compatibility only: this remains the canonical key, never employeeNo.
    personOaId: session.personOaId || personKey,
    authProvider: session.authProvider || '',
    displayPath: session.displayPath || session.personDistinguishedName || '',
    identityList: Array.isArray(session.identityList)
      ? session.identityList.map(normalizeIdentity)
      : []
  };
}

export function getSessionPersonKey(session = getStoredSession()) {
  return session?.personKey || session?.personOaId || '';
}

export function getSessionEmployeeNo(session = getStoredSession()) {
  return session?.employeeNo || '';
}

export function getStoredToken() {
  return storage().getItem(sessionStorageKey(CLOUDNEXT_TOKEN_KEY))
    || (isDefaultScope() ? storage().getItem(CLOUDNEXT_TOKEN_KEY) : '') || '';
}

export function sessionStorageKey(key) { return `${key}.${getDriveStorageScope()}`; }
function isDefaultScope() { return getDriveStorageScope() === 'default' || getActiveDriveId() === storage().getItem('cloudnext.default-drive-id'); }
export function isCurrentSessionStorageEvent(event) {
  if (embedded) return false;
  return event.key === null || event.key === sessionStorageKey(CLOUDNEXT_TOKEN_KEY)
    || (isDefaultScope() && event.key === CLOUDNEXT_TOKEN_KEY);
}
export function sessionContextKey() { return `${getDriveStorageScope()}:${getStoredToken()}`; }

export function setStoredSession(session) {
  const normalized = normalizeSession(session);
  if (!normalized || !normalized.token) {
    clearStoredSession();
    return;
  }
  acceptSessionDrive(normalized.drive);
  storage().setItem(sessionStorageKey(CLOUDNEXT_SESSION_KEY), JSON.stringify(normalized));
  storage().setItem(sessionStorageKey(CLOUDNEXT_TOKEN_KEY), normalized.token);
  if (normalized.drive?.entryId === 'default') {
    storage().setItem('cloudnext.default-drive-id', normalized.drive.id);
    // Legacy keys are a default-drive compatibility alias only.
    storage().setItem(CLOUDNEXT_SESSION_KEY, JSON.stringify(normalized));
    storage().setItem(CLOUDNEXT_TOKEN_KEY, normalized.token);
  }
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_SESSION_CHANGED_EVENT));
}

export function getStoredSession() {
  const raw = storage().getItem(sessionStorageKey(CLOUDNEXT_SESSION_KEY))
    || (isDefaultScope() ? storage().getItem(CLOUDNEXT_SESSION_KEY) : null);
  if (!raw) {
    return null;
  }
  try {
    const session = normalizeSession(JSON.parse(raw));
    if (getDriveStorageScope() !== 'default' && (!session?.drive || (getActiveDriveId() && session.drive.id !== getActiveDriveId()))) return null;
    if (session?.drive && session.drive.entryId !== getActiveEntryId()) return null;
    return session;
  } catch (error) {
    clearStoredSession();
    return null;
  }
}

export function clearStoredSession() {
  storage().removeItem(sessionStorageKey(CLOUDNEXT_SESSION_KEY));
  storage().removeItem(sessionStorageKey(CLOUDNEXT_TOKEN_KEY));
  if (isDefaultScope()) {
    storage().removeItem(CLOUDNEXT_SESSION_KEY);
    storage().removeItem(CLOUDNEXT_TOKEN_KEY);
  }
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_SESSION_CHANGED_EVENT));
}

export function emitUnauthorizedSignal() {
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_AUTH_UNAUTHORIZED_EVENT));
}

export function emitDriveRefreshSignal() {
  const value = String(Date.now());
  storage().setItem(sessionStorageKey(CLOUDNEXT_DRIVE_REFRESH_KEY), value);
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_DRIVE_REFRESH_EVENT, { detail: { value, driveId: getActiveDriveId() } }));
  return value;
}

export function emitOpenUploadModalSignal() {
  const value = String(Date.now());
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT, { detail: value }));
  return value;
}
