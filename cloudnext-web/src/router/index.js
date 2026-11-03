import AdaptivePage from './AdaptivePage.vue';
export default [
  { path: '/oa-embed', name: 'oa-embed', component: () => import('../embedded/EmbeddedEntry.vue'), meta: { public: true, standalone: true } },
  { path: '/file-action', name: 'file-action', component: () => import('../embedded/FileActionPage.vue'), meta: { public: true, standalone: true } },
  { path: '/share/:token', name: 'public-link', component: () => import('../pages/PublicLinkPage.vue'), meta: { public: true, standalone: true } },
  { path: '/login', name: 'login', component: () => import('../auth/LoginRoute.vue'), meta: { public: true } },
  { path: '/preview/file', name: 'preview-file', component: () => import('../pages/FilePreviewPage.vue'), meta: { public: true, standalone: true } },
  { path: '/preview/office', name: 'preview-office', component: () => import('../pages/FilePreviewPage.vue'), meta: { public: true, standalone: true } },
  { path: '/edit/office', name: 'edit-office', component: () => import('../pages/FilePreviewPage.vue'), meta: { public: true, standalone: true } },
  ...[
    ['/', 'home'], ['/my-files', 'my-files'], ['/shared', 'shared'],
    ['/workspaces', 'workspaces'], ['/workspaces/:id', 'workspace-detail'],
    ['/links', 'links'], ['/trash', 'trash'], ['/admin', 'admin'],
    ['/search', 'search'], ['/recent', 'recent'], ['/favorites', 'favorites']
  ].map(([path, name]) => ({ path, name, component: AdaptivePage })),
  { path: '/:pathMatch(.*)*', redirect: '/' }
];
