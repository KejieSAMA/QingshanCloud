import AdaptivePage from './AdaptivePage.vue';
export default [
  { path: '/login', name: 'login', component: () => import('../auth/LoginRoute.vue'), meta: { public: true } },
  { path: '/preview/office', name: 'preview-office', component: () => import('../pages/OnlyofficePreviewPage.vue'), meta: { public: true, standalone: true } },
  { path: '/edit/office', name: 'edit-office', component: () => import('../pages/OnlyofficePreviewPage.vue'), meta: { public: true, standalone: true } },
  ...[
    ['/', 'home'], ['/my-files', 'my-files'], ['/shared', 'shared'],
    ['/workspaces', 'workspaces'], ['/workspaces/:id', 'workspace-detail'],
    ['/links', 'links'], ['/trash', 'trash'], ['/admin', 'admin'],
    ['/search', 'search'], ['/recent', 'recent'], ['/favorites', 'favorites']
  ].map(([path, name]) => ({ path, name, component: AdaptivePage })),
  { path: '/:pathMatch(.*)*', redirect: '/' }
];
