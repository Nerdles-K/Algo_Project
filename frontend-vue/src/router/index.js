import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/app/recommend' },
    { path: '/login', component: () => import('../views/LoginPage.vue') },
    { path: '/register', component: () => import('../views/RegisterPage.vue') },
    {
      path: '/app',
      component: () => import('../views/AppShell.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/app/recommend' },
        { path: 'recommend', component: () => import('../views/RecommendTab.vue') },
        { path: 'friends', component: () => import('../views/FriendsTab.vue') },
        { path: 'overview', component: () => import('../views/OverviewTab.vue') },
        { path: 'lcc', component: () => import('../views/LccTab.vue') },
        { path: 'pagerank', component: () => import('../views/PageRankTab.vue') },
        { path: 'watch-history', component: () => import('../views/WatchHistoryTab.vue') },
      ],
    },
  ],
})

router.beforeEach(to => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/login'
  }
  if ((to.path === '/login' || to.path === '/register') && auth.isLoggedIn) {
    return '/app/recommend'
  }
})

export default router
