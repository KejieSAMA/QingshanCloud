import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath } from 'node:url';

// Browser verification only. No request under /api is forwarded to a real service.
export default defineConfig({
  root: fileURLToPath(new URL('..', import.meta.url)),
  plugins: [vue(), {
    name: 'isolated-sso-recovery-api',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        if (!request.url.startsWith('/api/')) return next();
        response.setHeader('Content-Type', 'application/json; charset=utf-8');
        if (request.url === '/api/auth/status') {
          response.end(JSON.stringify({ provider: 'FORM', configured: true, ssoEnabled: true,
            formSsoStartUrl: 'https://oa.example.test/?module=home' }));
        } else if (request.url.startsWith('/api/auth/sso/start?')) {
          response.end(JSON.stringify({ isolatedTest: 'fresh-ticket-start', requestPath: request.url }));
        } else {
          response.statusCode = 503;
          response.end(JSON.stringify({ code: 'SSO_UNAVAILABLE', message: '隔离测试：不连接实际业务服务' }));
        }
      });
    },
  }],
  server: { host: '127.0.0.1', port: 4187, strictPort: true },
});
