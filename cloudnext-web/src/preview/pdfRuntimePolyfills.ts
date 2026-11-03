import './runtimePolyfills';

type Indexable = { length: number; [index: number]: unknown };

export const PDFJS_WEBVIEW87_RUNTIME_MARKER = 'pdfjs-webview87-v1';

function normalizeRelativeIndex(value: number) {
  const number = Number(value);
  if (Number.isNaN(number) || number === 0) return 0;
  if (!Number.isFinite(number)) return number;
  return number < 0 ? Math.ceil(number) : Math.floor(number);
}

function indexedAt(this: Indexable, index: number) {
  if (this == null) throw new TypeError('Array.prototype.at called on null or undefined');
  const target = Object(this) as Indexable;
  const length = Math.min(Math.max(Number(target.length) || 0, 0), Number.MAX_SAFE_INTEGER);
  const relativeIndex = normalizeRelativeIndex(index);
  const resolvedIndex = relativeIndex >= 0 ? relativeIndex : length + relativeIndex;
  return resolvedIndex < 0 || resolvedIndex >= length ? undefined : target[resolvedIndex];
}

function stringAt(this: string, index: number) {
  if (this == null) throw new TypeError('String.prototype.at called on null or undefined');
  const target = String(this);
  const relativeIndex = normalizeRelativeIndex(index);
  const resolvedIndex = relativeIndex >= 0 ? relativeIndex : target.length + relativeIndex;
  return resolvedIndex < 0 || resolvedIndex >= target.length ? undefined : target.charAt(resolvedIndex);
}

function defineAt(prototype: object, implementation: (this: never, index: number) => unknown) {
  if (typeof (prototype as { at?: unknown }).at === 'function') return;
  Object.defineProperty(prototype, 'at', {
    configurable: true,
    enumerable: false,
    writable: true,
    value: implementation,
  });
}

function installPromiseWithResolvers() {
  const constructor = Promise as PromiseConstructor & {
    withResolvers?: <T>() => {
      promise: Promise<T>;
      resolve: (value: T | PromiseLike<T>) => void;
      reject: (reason?: unknown) => void;
    };
  };
  if (typeof constructor.withResolvers === 'function') return;
  Object.defineProperty(constructor, 'withResolvers', {
    configurable: true,
    writable: true,
    value: function withResolvers<T>() {
      let resolve!: (value: T | PromiseLike<T>) => void;
      let reject!: (reason?: unknown) => void;
      const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
      });
      return { promise, resolve, reject };
    },
  });
}

function installUrlParse() {
  const constructor = URL as typeof URL & {
    parse?: (url: string | URL, base?: string | URL) => URL | null;
  };
  if (typeof constructor.parse === 'function') return;
  Object.defineProperty(constructor, 'parse', {
    configurable: true,
    writable: true,
    value: (url: string | URL, base?: string | URL) => {
      try {
        return new URL(String(url), base === undefined ? undefined : String(base));
      } catch {
        return null;
      }
    },
  });
}

function installAbortSignalAny() {
  const constructor = AbortSignal as typeof AbortSignal & {
    any?: (signals: Iterable<AbortSignal>) => AbortSignal;
  };
  if (typeof constructor.any === 'function') return;
  Object.defineProperty(constructor, 'any', {
    configurable: true,
    writable: true,
    value: (signals: Iterable<AbortSignal>) => {
      const controller = new AbortController();
      const entries = Array.from(signals);
      const listeners: Array<{ signal: AbortSignal; listener: () => void }> = [];
      const abortFrom = (signal: AbortSignal) => {
        listeners.forEach((entry) => entry.signal.removeEventListener('abort', entry.listener));
        const reason = 'reason' in signal ? signal.reason : undefined;
        controller.abort(reason);
      };
      for (const signal of entries) {
        if (signal.aborted) {
          abortFrom(signal);
          return controller.signal;
        }
      }
      entries.forEach((signal) => {
        const listener = () => abortFrom(signal);
        listeners.push({ signal, listener });
        signal.addEventListener('abort', listener, { once: true });
      });
      return controller.signal;
    },
  });
}

export function installPdfJsLegacyRuntimePolyfills() {
  defineAt(Array.prototype, indexedAt);
  defineAt(String.prototype, stringAt);
  const typedArrayPrototype = Object.getPrototypeOf(Uint8Array.prototype) as object | null;
  if (typedArrayPrototype) defineAt(typedArrayPrototype, indexedAt);
  installPromiseWithResolvers();
  installUrlParse();
  installAbortSignalAny();
  Object.defineProperty(globalThis, '__PDFJS_WEBVIEW87_RUNTIME__', {
    configurable: true,
    value: PDFJS_WEBVIEW87_RUNTIME_MARKER,
    writable: false,
  });
}

installPdfJsLegacyRuntimePolyfills();
