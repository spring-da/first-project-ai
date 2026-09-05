import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/share/markdown/:token', name: 'public-markdown-share', component: () => import('../views/PublicMarkdownShareView.vue') },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guestOnly: true } },
    { path: '/change-password', name: 'change-password', component: () => import('../views/ChangePasswordView.vue'), meta: { requiresAuth: true } },
    { path: '/admin/accounts/:userId/workspace', name: 'admin-member-workspace', redirect: (to) => ({ path: '/', query: { workspace: to.params.userId } }), meta: { requiresAuth: true, adminOnly: true } },
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { keepAlive: true } },
        { path: 'projects', name: 'projects', component: () => import('../views/ProjectsView.vue'), meta: { keepAlive: true } },
        { path: 'knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { keepAlive: true } },
        { path: 'tools', name: 'tools', component: () => import('../views/ToolsView.vue'), meta: { keepAlive: true } },
        { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { keepAlive: true } },
        { path: 'admin/accounts', name: 'admin-accounts', component: () => import('../views/AdminAccountsView.vue'), meta: { adminOnly: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
