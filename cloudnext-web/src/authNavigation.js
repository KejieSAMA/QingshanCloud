// All login returns use the same application, never another document.
export function sanitizeReturnPath(value, fallback = '/') {
  if (typeof value !== 'string') return fallback;
  let path = value.trim();
  if (/^\/next\.html(?:[?#]|$)/.test(path)) {
    const hash = path.indexOf('#');
    path = hash < 0 ? '/' : path.slice(hash + 1);
  }
  if (!path.startsWith('/') || path.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(path)) return fallback;
  try {
    const url = new URL(path, 'https://cloudnext.invalid');
    const decoded = decodeURIComponent(url.pathname);
    if (url.origin !== 'https://cloudnext.invalid' || /[\\\u0000-\u001f\u007f]/.test(decoded)
      || decoded.startsWith('//') || /^\/(?:login|next\.html)(?:\/|$)/i.test(decoded)) return fallback;
    return url.pathname + url.search + url.hash;
  } catch { return fallback; }
}
export function normalizeEntryLocation(location, history) {
  if (location.pathname !== '/next.html') return;
  history.replaceState(history.state, '', sanitizeReturnPath('/next.html' + location.search + location.hash));
}
export function loginRouteFor(returnPath) {
  const path = sanitizeReturnPath(returnPath);
  return { name: 'login', query: path === '/' ? {} : { returnPath: path } };
}
export function navigateAfterLogin(router, value) {
  return router.replace(sanitizeReturnPath(value));
}
