/**
 * Reactive state machine for `LinksPage.vue`.
 *
 * Extracted into a standalone composable so the loading / empty / error /
 * success branches, the clipboard fallback, the revoke confirmation guard,
 * and the "refresh by server projection" behavior can be unit-tested
 * without `@vue/test-utils` or a jsdom environment — matching the pattern
 * established by `workspacePermissions.js` / `useDriveDirectoryState.js`
 * in iteration 1.
 *
 * The composable owns:
 *   - `links`, `loading`, `errorText`, `successText`
 *   - the in-flight `busyLinkIds` set
 *   - `refresh()`, `onCopy()`, `onRevoke()`, `onLinkUpdated()`,
 *     `onLinkCreated()`
 *   - the success-text auto-clear timer (defaults to `window.setTimeout`,
 *     overridable via deps for tests)
 *
 * The SFC keeps the dialog open/close flags and the template, and delegates
 * everything else here.
 *
 * Validates: Requirements 9.2, 9.4, 9.5, 9.6, 9.7
 */

import { computed, markRaw, ref } from 'vue';
import {
  listMyLinks as defaultListMyLinks,
  revokeLink as defaultRevokeLink
} from '../../api/cloudnext';

const DEFAULT_SUCCESS_TTL_MS = 2000;

/**
 * Resolve the clipboard handle, preferring the injected one (tests) and
 * falling back to the real `navigator.clipboard` if present.
 */
function resolveClipboard(deps) {
  if (deps && Object.prototype.hasOwnProperty.call(deps, 'clipboard')) {
    return deps.clipboard || null;
  }
  if (typeof navigator !== 'undefined' && navigator && navigator.clipboard) {
    return navigator.clipboard;
  }
  return null;
}

/**
 * Resolve the `confirm` callback the same way. Defaults to `window.confirm`
 * when running in a browser. Tests pass an explicit stub.
 */
function resolveConfirm(deps) {
  if (deps && typeof deps.confirm === 'function') {
    return deps.confirm;
  }
  if (typeof window !== 'undefined' && typeof window.confirm === 'function') {
    return window.confirm.bind(window);
  }
  return () => true;
}

/**
 * Run the legacy `<input>` + `document.execCommand('copy')` fallback.
 * Returns `true` on success. The composable accepts an injectable
 * implementation so the test environment (which has no DOM) can decide
 * whether the fallback succeeds without invoking real DOM APIs.
 */
function defaultExecCommandFallback(text) {
  if (typeof document === 'undefined' || !document?.body) return false;
  let input;
  try {
    input = document.createElement('input');
    input.setAttribute('readonly', 'readonly');
    input.style.position = 'fixed';
    input.style.top = '-1000px';
    input.style.opacity = '0';
    input.value = text;
    document.body.appendChild(input);
    if (typeof input.select === 'function') {
      input.select();
    }
    let ok = false;
    if (document.execCommand) {
      try {
        ok = document.execCommand('copy');
      } catch (error) {
        ok = false;
      }
    }
    return !!ok;
  } finally {
    if (input && input.parentNode) {
      input.parentNode.removeChild(input);
    }
  }
}

/**
 * Build the composable. All side-effect dependencies are injectable so the
 * unit tests can drive the state machine deterministically:
 *
 *   useLinksPageState({
 *     listMyLinks, revokeLink,                // api stubs
 *     clipboard,                              // { writeText: vi.fn() } | null
 *     confirm,                                // (msg) => boolean
 *     execCommandFallback,                    // (text) => boolean
 *     setTimeoutFn, clearTimeoutFn,           // timer hooks
 *     successTtlMs                            // override 2000ms default
 *   })
 *
 * All deps are optional. In production the SFC simply calls
 * `useLinksPageState()` and the defaults wire up the real `cloudnext.js`
 * API + `navigator.clipboard` + `window.setTimeout`.
 */
export function useLinksPageState(deps = {}) {
  const listMyLinksFn = deps.listMyLinks || defaultListMyLinks;
  const revokeLinkFn = deps.revokeLink || defaultRevokeLink;
  const setTimeoutFn =
    typeof deps.setTimeoutFn === 'function'
      ? deps.setTimeoutFn
      : (cb, ms) => (typeof window !== 'undefined' ? window.setTimeout(cb, ms) : setTimeout(cb, ms));
  const clearTimeoutFn =
    typeof deps.clearTimeoutFn === 'function'
      ? deps.clearTimeoutFn
      : (handle) => {
          if (typeof window !== 'undefined') window.clearTimeout(handle);
          else clearTimeout(handle);
        };
  const successTtlMs = Number.isFinite(deps.successTtlMs)
    ? deps.successTtlMs
    : DEFAULT_SUCCESS_TTL_MS;
  const execCommandFallback =
    typeof deps.execCommandFallback === 'function'
      ? deps.execCommandFallback
      : defaultExecCommandFallback;

  const links = ref([]);
  const loading = ref(false);
  const errorText = ref('');
  const successText = ref('');
  const busyLinkIds = ref(new Set());

  const summaryText = computed(() => `共 ${links.value.length} 条外链`);

  let successTimer = null;

  const showSuccess = (message) => {
    successText.value = message;
    if (successTimer) {
      clearTimeoutFn(successTimer);
    }
    successTimer = setTimeoutFn(() => {
      successText.value = '';
      successTimer = null;
    }, successTtlMs);
  };

  const cancelSuccessTimer = () => {
    if (successTimer) {
      clearTimeoutFn(successTimer);
      successTimer = null;
    }
  };

  const refresh = async () => {
    loading.value = true;
    try {
      const list = await listMyLinksFn();
      links.value = Array.isArray(list) ? list.map((link) => markRaw(link)) : [];
      errorText.value = '';
    } catch (error) {
      // keep prior data intact on error (Requirement 9.7)
      errorText.value = error?.message || '加载外链失败';
    } finally {
      loading.value = false;
    }
  };

  const isLinkBusy = (link) =>
    !!(link && link.id && busyLinkIds.value.has(link.id));

  const setLinkBusy = (id, busy) => {
    const next = new Set(busyLinkIds.value);
    if (busy) {
      next.add(id);
    } else {
      next.delete(id);
    }
    busyLinkIds.value = next;
  };

  const replaceLinkInPlace = (updated) => {
    if (!updated || !updated.id) return;
    const idx = links.value.findIndex((l) => l.id === updated.id);
    if (idx >= 0) {
      links.value.splice(idx, 1, markRaw(updated));
    }
  };

  const onCopy = async (link) => {
    const url = link?.shareUrl || '';
    if (!url) {
      errorText.value = '该外链尚无可复制的地址。';
      return;
    }
    errorText.value = '';
    const clipboard = resolveClipboard(deps);
    try {
      if (clipboard && typeof clipboard.writeText === 'function') {
        await clipboard.writeText(url);
        showSuccess('外链已复制到剪贴板');
        return;
      }
      if (execCommandFallback(url)) {
        showSuccess('外链已复制到剪贴板');
        return;
      }
      errorText.value = '复制失败，请手动复制';
    } catch (error) {
      // clipboard.writeText rejected — try the legacy fallback before giving up
      if (execCommandFallback(url)) {
        showSuccess('外链已复制到剪贴板');
        return;
      }
      errorText.value = '复制失败，请手动复制';
    }
  };

  const onLinkCreated = (link) => {
    if (link && link.id) {
      links.value = [
        markRaw(link),
        ...links.value.filter((existing) => existing.id !== link.id)
      ];
    }
    showSuccess('外链已创建');
  };

  const onLinkUpdated = (updated) => {
    replaceLinkInPlace(updated);
    errorText.value = '';
    showSuccess('外链已更新');
  };

  const onRevoke = async (link) => {
    if (!link?.id || link.revoked || isLinkBusy(link)) return;
    const confirmFn = resolveConfirm(deps);
    const confirmed = confirmFn(
      `失效后该外链将立即不可访问，是否继续？\n文件：${link.fileName || '(已删除)'}`
    );
    if (!confirmed) return;
    setLinkBusy(link.id, true);
    try {
      const updated = await revokeLinkFn(link.id);
      if (updated && updated.id) {
        replaceLinkInPlace(updated);
      } else {
        // server returned no body — fall back to flipping revoked locally
        const idx = links.value.findIndex((l) => l.id === link.id);
        if (idx >= 0) {
          links.value.splice(idx, 1, { ...link, revoked: true });
        }
      }
      errorText.value = '';
      showSuccess('外链已失效');
    } catch (error) {
      // Requirement 9.7: 错误时不回显成功 — never write to successText here.
      errorText.value = error?.message || '外链失效失败';
    } finally {
      setLinkBusy(link.id, false);
    }
  };

  return {
    // state
    links,
    loading,
    errorText,
    successText,
    busyLinkIds,
    summaryText,
    // queries
    isLinkBusy,
    // actions
    refresh,
    onCopy,
    onLinkCreated,
    onLinkUpdated,
    onRevoke,
    // lifecycle helpers (the SFC calls cancelSuccessTimer in onUnmounted)
    cancelSuccessTimer
  };
}
