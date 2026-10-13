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
  return window.localStorage.getItem(CLOUDNEXT_TOKEN_KEY) || '';
}

export function setStoredSession(session) {
  const normalized = normalizeSession(session);
  if (!normalized || !normalized.token) {
    clearStoredSession();
    return;
  }
  window.localStorage.setItem(CLOUDNEXT_SESSION_KEY, JSON.stringify(normalized));
  window.localStorage.setItem(CLOUDNEXT_TOKEN_KEY, normalized.token);
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_SESSION_CHANGED_EVENT));
}

export function getStoredSession() {
  const raw = window.localStorage.getItem(CLOUDNEXT_SESSION_KEY);
  if (!raw) {
    return null;
  }
  try {
    return normalizeSession(JSON.parse(raw));
  } catch (error) {
    clearStoredSession();
    return null;
  }
}

export function clearStoredSession() {
  window.localStorage.removeItem(CLOUDNEXT_SESSION_KEY);
  window.localStorage.removeItem(CLOUDNEXT_TOKEN_KEY);
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_SESSION_CHANGED_EVENT));
}

export function emitUnauthorizedSignal() {
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_AUTH_UNAUTHORIZED_EVENT));
}

export function emitDriveRefreshSignal() {
  const value = String(Date.now());
  window.localStorage.setItem(CLOUDNEXT_DRIVE_REFRESH_KEY, value);
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_DRIVE_REFRESH_EVENT, { detail: value }));
  return value;
}

export function emitOpenUploadModalSignal() {
  const value = String(Date.now());
  window.dispatchEvent(new CustomEvent(CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT, { detail: value }));
  return value;
}
