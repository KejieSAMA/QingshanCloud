export const CLOUDNEXT_LOGIN_PREFERENCE_KEY = 'cloudnext.login.preference';

function resolveStorage(storage) {
  if (storage) return storage;
  return typeof window !== 'undefined' ? window.localStorage : null;
}

export function loadLoginPreference(storage) {
  const target = resolveStorage(storage);
  if (!target) return { rememberPassword: false, credential: '' };
  try {
    const value = JSON.parse(target.getItem(CLOUDNEXT_LOGIN_PREFERENCE_KEY) || '{}');
    return {
      rememberPassword: value?.rememberPassword === true,
      credential: typeof value?.credential === 'string' ? value.credential.slice(0, 240) : ''
    };
  } catch {
    target.removeItem(CLOUDNEXT_LOGIN_PREFERENCE_KEY);
    return { rememberPassword: false, credential: '' };
  }
}

export function saveLoginPreference(preference, storage) {
  const target = resolveStorage(storage);
  if (!target) return;
  if (!preference?.rememberPassword) {
    target.removeItem(CLOUDNEXT_LOGIN_PREFERENCE_KEY);
    return;
  }
  target.setItem(CLOUDNEXT_LOGIN_PREFERENCE_KEY, JSON.stringify({
    rememberPassword: true,
    credential: String(preference.credential || '').trim().slice(0, 240)
  }));
}

export function browserPasswordStorageAvailable() {
  return typeof window !== 'undefined'
    && typeof navigator !== 'undefined'
    && typeof window.PasswordCredential === 'function'
    && Boolean(navigator.credentials?.store);
}

export async function storeBrowserPassword(credential, password) {
  if (!browserPasswordStorageAvailable()) return false;
  const value = new window.PasswordCredential({
    id: String(credential || '').trim(),
    name: String(credential || '').trim(),
    password: String(password || '')
  });
  await navigator.credentials.store(value);
  return true;
}

export async function loadBrowserPassword(expectedCredential = '') {
  if (typeof navigator === 'undefined' || !navigator.credentials?.get) return null;
  if (typeof window === 'undefined' || typeof window.PasswordCredential !== 'function') return null;
  try {
    const value = await navigator.credentials.get({ password: true, mediation: 'optional' });
    if (!value || value.type !== 'password') return null;
    if (expectedCredential && value.id !== expectedCredential) return null;
    return { credential: value.id || '', password: value.password || '' };
  } catch {
    return null;
  }
}

export async function preventSilentPasswordAccess() {
  if (typeof navigator === 'undefined' || !navigator.credentials?.preventSilentAccess) return;
  try {
    await navigator.credentials.preventSilentAccess();
  } catch {
    // Browser privacy settings may reject this request; the application still clears its own preference.
  }
}
