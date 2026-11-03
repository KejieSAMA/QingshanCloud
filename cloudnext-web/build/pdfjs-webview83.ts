import type { Plugin } from 'vite';

/** PDF.js' ICC path needs real GC finalization, which cannot be polyfilled.
 * Leave JPEG/JPEG2000 WebAssembly enabled, but use PDF.js' DeviceRGB/CMYK
 * fallback when finalization is missing. Fail closed if the dependency changes.
 * This build hook never edits node_modules. */
export function lowerPdfIccRuntime(source: string) {
  const availability = 'if (this.#useWasm) {';
  const allocation = 'IccColorSpace.#finalizer ||= new FinalizationRegistry(';
  if (source.split(availability).length !== 2 || source.split(allocation).length !== 2) {
    throw new Error('PDF.js ICC compatibility hook no longer matches the locked dependency.');
  }
  return source.replace(availability, 'if (this.#useWasm && typeof FinalizationRegistry === "function") {')
    .replace(allocation, 'IccColorSpace.#finalizer ||= oaCreatePdfIccFinalizer(')
    + `\nfunction oaCreatePdfIccFinalizer(callback) {
      if (typeof FinalizationRegistry === "function") return new FinalizationRegistry(callback);
      throw new Error("PDF_ICC_FINALIZATION_UNAVAILABLE");
    }\n`;
}

export function pdfJsWebView83Plugin(): Plugin {
  return {
    name: 'pdfjs-webview83-icc-fallback',
    enforce: 'pre',
    transform(source, id) {
      if (!id.replaceAll('\\', '/').split('?')[0].endsWith('/pdfjs-dist/legacy/build/pdf.worker.mjs')) return;
      return { code: lowerPdfIccRuntime(source), map: null };
    },
  };
}
