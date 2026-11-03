import { reactive } from 'vue';
import { embedded, channel, parentOrigin, protocol, protocolVersion, trustedParent, validEnvelope } from './context.js';
import { setFileTransport } from './transport.js';
import { getAuthStatus, getCurrentSession, fetchUnauthenticatedJson, fetchJson } from '../api/cloudnext.js';
import { getStoredToken, setStoredSession, clearStoredSession } from '../session.js';
import { withDriveContext } from '../driveContext.js';
import { sanitizeReturnPath } from '../authNavigation.js';
import { ssoRecoveryMessage } from '../ssoRecovery.js';
import { createEmbeddedNavigation, NAVIGATION_VERSION } from './navigation.js';

export const embedState = reactive({ phase: 'loading', error: '', visible: true });
const pending = new Map(), handlers = [];
let permitted = false, routerRef, authSequence = 0, fileSequence = 0, basePosition = 0;
let host = { platform: 'web', nativeFiles: false };
let authAbort;
let starting = false;
let bootId = '', routeRevision = 0, navigation;
let previewOpen = false, previewRevision = 0;
export function setEmbeddedPreviewOpen(open) {
  previewOpen = open === true;
  postHost('preview-state', { open: previewOpen, bootId, revision: ++previewRevision });
}
function visibleDialog() {
  return Array.from(document.querySelectorAll('[role="dialog"][aria-modal="true"]'))
    .filter(node => node.getClientRects().length && getComputedStyle(node).visibility !== 'hidden').pop();
}
export function postHost(type, payload = {}) {
  if (embedded && permitted) window.parent.postMessage({ protocol, version: protocolVersion, channel, type, ...payload }, parentOrigin);
}
export function registerEmbeddedBack(id, active, close) {
  const handler = { id, active, close }; handlers.push(handler);
  return () => { const at = handlers.indexOf(handler); if (at >= 0) handlers.splice(at, 1); };
}
export function requestEmbeddedAuth() {
  if (!embedded || embedState.phase === 'authenticating') return;
  navigation?.reset();
  embedState.phase = 'loading'; embedState.error = '';
  const target = new URL(routerRef.currentRoute.value.fullPath, window.location.origin);
  for (const key of ['embed','channel','parentOrigin']) target.searchParams.delete(key);
  postHost('auth-needed', { returnPath: target.pathname + target.search + target.hash });
}
export function retryEmbeddedAuth() {
  embedState.phase = 'loading'; embedState.error = '';
  if (!permitted) void startEmbedded(routerRef); else postHost('retry-auth');
}
function fail(error) {
  embedState.phase = 'error';
  embedState.error = error?.data?.code ? ssoRecoveryMessage(error.data.code) : (error?.message || '云盘连接失败，请重试。');
  postHost('auth-error', { message: embedState.error });
}
async function authenticate(data) {
  if (embedState.phase === 'authenticating' || typeof data.ticket !== 'string') return;
  navigation?.reset();
  const sequence = ++authSequence;
  authAbort?.abort(); authAbort = new AbortController();
  embedState.phase = 'authenticating'; embedState.error = '';
  try {
    const session = await fetchUnauthenticatedJson('/api/auth/sso/embedded', {
      method: 'POST', credentials: 'omit', signal: authAbort.signal, body: JSON.stringify({ ticket: data.ticket, returnPath: sanitizeReturnPath(data.returnPath) })
    });
    if (sequence !== authSequence) return;
    host = { platform: data.platform === 'android' ? 'android' : 'web', nativeFiles: data.nativeFiles === true };
    setFileTransport(host.platform === 'android' ? nativeFile : null);
    setStoredSession(session);
    await routerRef.replace(withDriveContext(sanitizeReturnPath(data.returnPath)));
    basePosition = routerRef.options.history.state.position || 0;
    if (sequence !== authSequence) return;
    embedState.phase = 'ready'; postHost('authenticated', { bootId, navigationVersion: NAVIGATION_VERSION });
    setEmbeddedPreviewOpen(previewOpen);
  } catch (error) { if (sequence === authSequence) fail(error); }
}
async function nativeFile(payload) {
  const token = getStoredToken();
  const grant = await fetchJson('/api/drive/native-files/ticket', { method: 'POST', body: JSON.stringify(payload) });
  if (token !== getStoredToken()) throw Object.assign(new Error('操作已取消'), { name: 'AbortError' });
  const requestId = `${channel}-${++fileSequence}`;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { pending.delete(requestId); reject(new Error('文件操作未完成，请重试。')); }, 30 * 60 * 1000);
    pending.set(requestId, { resolve, reject, timeout });
    postHost(host.nativeFiles ? 'native-file' : 'file-fallback', { requestId, grant });
  });
}
export async function startEmbedded(router) {
  if (!embedded || starting) return;
  starting = true;
  routerRef = router;
  try {
    const status = await getAuthStatus();
    if (!trustedParent(status.embeddedOrigins)) throw new Error('当前 OA 入口尚未获准连接云盘。');
    permitted = true;
    bootId = Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join('');
    navigation = createEmbeddedNavigation({ router,
      sessionKey: () => embedState.phase === 'ready' ? getStoredToken() : '',
      blocked: () => Boolean(visibleDialog()),
      result: (requestId, value) => postHost('navigation-result', { requestId, bootId, ...value }),
    });
    const removeRouteListener = router.afterEach((to, _from, failure) => {
      if (failure || to.meta.public) return;
      const target = new URL(to.fullPath, window.location.origin);
      for (const key of ['embed','channel','parentOrigin']) target.searchParams.delete(key);
      postHost('route', { returnPath: target.pathname + target.search + target.hash, bootId, revision: ++routeRevision });
    });
    const handler = async event => {
      if (!validEnvelope(event, window.parent, parentOrigin, channel)) return;
      const data = event.data;
      if (data.type === 'authenticate') void authenticate(data);
      else if (data.type === 'host-error') { authSequence++; authAbort?.abort(); navigation.reset(); embedState.phase = 'error'; embedState.error = ''; }
      else if (data.type === 'navigate-tab' && data.bootId === bootId && embedState.phase === 'ready') navigation.enqueue(data);
      else if (data.type === 'cancel-navigation') navigation.cancel(data.requestId);
      else if (data.type === 'visibility') {
        embedState.visible = data.visible === true;
        document.documentElement.dataset.hostVisible = String(embedState.visible);
        if (embedState.visible && getStoredToken()) void getCurrentSession().catch(() => {});
      } else if (data.type === 'dispose') {
        authSequence++; authAbort?.abort(); navigation.dispose(); removeRouteListener?.(); clearStoredSession(); setFileTransport(null);
        for (const p of pending.values()) { clearTimeout(p.timeout); p.reject(Object.assign(new Error('操作已取消'), { name: 'AbortError' })); }
        pending.clear(); permitted = false; window.removeEventListener('message', handler);
      } else if (data.type === 'file-result') {
        const task = pending.get(data.requestId); if (!task) return;
        clearTimeout(task.timeout); pending.delete(data.requestId);
        if (data.ok) task.resolve(data.result);
        else task.reject(Object.assign(new Error(data.message || '文件处理失败，请重试。'), { name: data.cancelled ? 'AbortError' : 'Error' }));
      } else if (data.type === 'back') {
        try {
          const top = visibleDialog();
          if (top) {
            const close = top.querySelector('button[aria-label*="关闭"],button[title="关闭"]')
              || Array.from(top.querySelectorAll('button')).find(button => ['取消', '关闭'].includes(button.textContent.trim()));
            if (close && !close.disabled) close.click();
            postHost('back-result', { requestId: data.requestId, handled: true }); return;
          }
          const handler = [...handlers].reverse().find(h => h.active());
          if (handler) { const result = await handler.close(); if (result?.directory) basePosition = router.options.history.state.position || 0; postHost('back-result', { requestId: data.requestId, handled: true }); }
          else if ((router.options.history.state.position || 0) > basePosition) {
            router.back(); postHost('back-result', { requestId: data.requestId, handled: true });
          } else if (router.currentRoute.value.path !== '/') {
            const route = router.currentRoute.value;
            const target = route.name === 'workspace-detail' ? '/workspaces' : '/';
            // A directory's query is its parent breadcrumb. The existing page owns its own back handler.
            await router.replace(withDriveContext(target)); postHost('back-result', { requestId: data.requestId, handled: true });
          } else postHost('back-result', { requestId: data.requestId, handled: false });
        } catch (error) { postHost('back-result', { requestId: data.requestId, handled: true, message: error.message }); }
      }
    };
    window.addEventListener('message', handler);
    // The platform is known after authentication; browser downloads keep their ordinary path.
    postHost('ready', { bootId, navigationVersion: NAVIGATION_VERSION });
  } catch (error) { fail(error); }
  finally { starting = false; }
}
export function embeddedNativeFiles() { return embedded && host.platform === 'android'; }
