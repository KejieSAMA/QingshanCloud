// Dependency evaluation is ordered: install the shared/PDF polyfills in the
// worker realm before PDF.js evaluates its legacy worker bundle.
import './pdfRuntimePolyfills';
// Use the readable locked dependency so the build can guard ICC availability;
// the production Worker is minified after applying that compatibility hook.
import 'pdfjs-dist/legacy/build/pdf.worker.mjs';
