import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [vue()],
    build: { rollupOptions: { input: { main: 'index.html', next: 'next.html' } } },
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
