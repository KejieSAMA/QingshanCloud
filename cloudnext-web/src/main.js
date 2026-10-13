import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';
import routes from './router';
import './styles/base.css';
import './mobile/styles/legacy.css';
import './mobile/styles/mobile.css';
import './next/styles/demo.css';
import './next/styles/experience.css';
import { getCurrentSession } from './api/cloudnext';
import { CLOUDNEXT_AUTH_UNAUTHORIZED_EVENT, CLOUDNEXT_SESSION_CHANGED_EVENT, CLOUDNEXT_TOKEN_KEY,
  clearStoredSession, getStoredToken, getStoredSession, setStoredSession } from './session';
import { loginRouteFor, sanitizeReturnPath, normalizeEntryLocation } from './authNavigation';
import { syncSession, busyUploads } from './next/state';

normalizeEntryLocation(window.location, window.history);
const router = createRouter({ history: createWebHistory(), routes });
let checkingToken = '', sessionRequest;
async function checkSession() {
  const token = getStoredToken();
  if (!token) return false;
  if (checkingToken !== token || !sessionRequest) {
    checkingToken = token;
    sessionRequest = getCurrentSession();
  }
  try {
    const session = await sessionRequest;
    if (token !== getStoredToken()) return false;
    setStoredSession(session); syncSession(session); return true;
  } finally { if (checkingToken === token) sessionRequest = null; }
}
router.beforeEach(async to => {
  const ssoCallback = to.name === 'login' && ['complete', 'error'].includes(to.query.sso);
  if (ssoCallback || (to.meta.public && to.name !== 'login')) return true;
  if (!getStoredToken()) return to.meta.public ? true : loginRouteFor(to.fullPath);
  try {
    if (!await checkSession()) return to.meta.public ? true : loginRouteFor(to.fullPath);
    return to.name === 'login' ? sanitizeReturnPath(to.query.returnPath) : true;
  } catch (error) {
    if (error.status === 401 || error.name === 'AbortError') return to.meta.public ? true : loginRouteFor(to.fullPath);
    throw error;
  }
});
const toLogin = () => {
  syncSession(null);
  if (!router.currentRoute.value.meta.public) void router.replace(loginRouteFor(router.currentRoute.value.fullPath));
};
window.addEventListener(CLOUDNEXT_SESSION_CHANGED_EVENT, () => syncSession(getStoredSession()));
window.addEventListener(CLOUDNEXT_AUTH_UNAUTHORIZED_EVENT, toLogin);
window.addEventListener('storage', async event => {
  if (event.key !== CLOUDNEXT_TOKEN_KEY && event.key !== null) return;
  syncSession(null);
  if (!getStoredToken()) { toLogin(); return; }
  try {
    if (!await checkSession()) return;
    const current = router.currentRoute.value;
    if (current.name === 'login' && !['complete', 'error'].includes(current.query.sso)) {
      await router.replace(sanitizeReturnPath(current.query.returnPath));
    }
  } catch (error) { if (error.status === 401) { clearStoredSession(); toLogin(); } }
});
window.addEventListener('beforeunload', event => {
  if (busyUploads()) { event.preventDefault(); event.returnValue = ''; }
});
router.onError(error => {
  const node = document.getElementById('boot-error');
  if (node) { node.hidden = false; node.textContent = '页面加载失败，请刷新重试。' + (error.message || ''); }
});
const app = createApp(App).use(router);
router.isReady().then(() => app.mount('#app')).catch(() => {});
