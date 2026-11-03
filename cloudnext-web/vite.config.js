import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { pdfJsWebView83Plugin } from './build/pdfjs-webview83';
import { pdfJsWasmAssetsPlugin } from './build/pdfjs-wasm-assets';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [vue(), pdfJsWebView83Plugin(), pdfJsWasmAssetsPlugin({ scriptTarget: 'chrome83' })],
    worker: { format: 'es', plugins: () => [pdfJsWebView83Plugin()] },
    build: { target: 'chrome83', rollupOptions: { input: { main: 'index.html', next: 'next.html' } } },
    server: {
      host: '0.0.0.0',
      port: 5174,
      proxy: {
        '/api': {
          target: env.CLOUDNEXT_API_TARGET || 'http://localhost:8088',
          changeOrigin: true
        }
      }
    },
    test: {
      globals: true
    }
  };
});
