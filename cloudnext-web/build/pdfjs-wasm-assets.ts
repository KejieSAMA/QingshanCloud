import { createReadStream, readFileSync, readdirSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, extname, join } from 'node:path';
import type { Plugin } from 'vite';
import { transformSync } from 'esbuild';

const require = createRequire(import.meta.url);
const pdfJsPackagePath = require.resolve('pdfjs-dist/package.json');
const pdfJsPackage = JSON.parse(readFileSync(pdfJsPackagePath, 'utf8')) as { version?: string };
const pdfJsWasmDirectory = join(dirname(pdfJsPackagePath), 'wasm');
const pdfJsCMapDirectory = join(dirname(pdfJsPackagePath), 'cmaps');

export const PDFJS_VERSION = String(pdfJsPackage.version ?? '').trim();
if (!PDFJS_VERSION) throw new Error('Unable to resolve the installed pdfjs-dist version.');

export const PDFJS_OPTIMIZED_DEPENDENCIES = [
  'pdfjs-dist/legacy/build/pdf.mjs',
] as const;
export const PDFJS_WASM_PUBLIC_PATH = `vendor/pdfjs/${PDFJS_VERSION}/wasm/`;
export const PDFJS_WASM_ASSET_NAMES = readdirSync(pdfJsWasmDirectory)
  .filter((name) => statSync(join(pdfJsWasmDirectory, name)).isFile())
  .sort();
export const PDFJS_CMAP_PUBLIC_PATH = `vendor/pdfjs/${PDFJS_VERSION}/cmaps/`;
export const PDFJS_CMAP_ASSET_NAMES = readdirSync(pdfJsCMapDirectory)
  .filter((name) => statSync(join(pdfJsCMapDirectory, name)).isFile())
  .sort();

const assetGroups = [
  { directory: pdfJsWasmDirectory, publicPath: PDFJS_WASM_PUBLIC_PATH, names: PDFJS_WASM_ASSET_NAMES },
  { directory: pdfJsCMapDirectory, publicPath: PDFJS_CMAP_PUBLIC_PATH, names: PDFJS_CMAP_ASSET_NAMES },
].map((group) => ({ ...group, nameSet: new Set(group.names) }));

export function pdfJsWasmAssetsPlugin({ scriptTarget }: { scriptTarget?: string } = {}): Plugin {
  return {
    name: 'pdfjs-wasm-assets',
    configureServer(server) {
      assetGroups.forEach((group) => {
        server.middlewares.use(`/${group.publicPath}`, (request, response, next) => {
          const requestedName = decodeURIComponent(String(request.url ?? '').split('?', 1)[0]).replace(/^\/+/, '');
          if (!group.nameSet.has(requestedName)) {
            next();
            return;
          }
          const sourcePath = join(group.directory, requestedName);
          response.statusCode = 200;
          response.setHeader('Content-Type', pdfJsAssetContentType(requestedName));
          response.setHeader('Content-Length', String(statSync(sourcePath).size));
          response.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
          if (request.method === 'HEAD') {
            response.end();
            return;
          }
          createReadStream(sourcePath).pipe(response);
        });
      });
    },
    generateBundle() {
      assetGroups.forEach((group) => {
        group.names.forEach((name) => {
          const original = readFileSync(join(group.directory, name));
          // Decoder fallback scripts are emitted as assets, outside Vite's JS
          // graph. They need the same lowering as Window and Worker modules.
          const source = scriptTarget && extname(name) === '.js'
            ? transformSync(original.toString('utf8'), { target: scriptTarget, minify: true, sourcefile: name }).code
            : original;
          this.emitFile({
            type: 'asset',
            fileName: `${group.publicPath}${name}`,
            source,
          });
        });
      });
    },
  };
}

function pdfJsAssetContentType(name: string) {
  if (extname(name) === '.wasm') return 'application/wasm';
  if (extname(name) === '.js') return 'text/javascript; charset=utf-8';
  if (extname(name) === '.bcmap') return 'application/octet-stream';
  return 'text/plain; charset=utf-8';
}
