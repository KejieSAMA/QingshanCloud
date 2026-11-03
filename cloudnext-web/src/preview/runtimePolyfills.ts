// This module must evaluate before the application graph and in the PDF worker realm.
// Pure implementations never replace a working native implementation during import.
import replaceAll from 'core-js-pure/actual/string/replace-all';
import hasOwn from 'core-js-pure/actual/object/has-own';
import clone from 'core-js-pure/actual/structured-clone';

function install(target: object, name: string, value: unknown) {
  if (typeof (target as Record<string, unknown>)[name] === 'function') return;
  Object.defineProperty(target, name, { configurable: true, enumerable: false, writable: true, value });
}

export function installWebView83RuntimePolyfills() {
  install(String.prototype, 'replaceAll', function (this: string, search: unknown, replacement: unknown) {
    return replaceAll(this, search, replacement);
  });
  install(Object, 'hasOwn', hasOwn);
  install(globalThis, 'structuredClone', clone);
}

installWebView83RuntimePolyfills();

export const WEBVIEW83_RUNTIME_MARKER = 'oa-webview83-runtime-v1';
Object.defineProperty(globalThis, '__OA_WEBVIEW83_RUNTIME__', {
  configurable: true,
  value: WEBVIEW83_RUNTIME_MARKER,
});
