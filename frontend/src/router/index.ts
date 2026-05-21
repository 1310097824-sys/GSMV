import { createRouter, createWebHistory } from 'vue-router'
import { ACCESS_TOKEN_KEY, PROFILE_KEY } from '@/constants/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    authority?: string
  }
}

const routes = [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { authority: 'REPORT_READ' } },
      { path: 'species', component: () => import('@/views/SpeciesView.vue'), meta: { authority: 'SPECIES_READ' } },
      { path: 'species/:id', component: () => import('@/views/SpeciesDetailView.vue'), meta: { authority: 'SPECIES_READ' } },
      { path: 'ecosystems', component: () => import('@/views/EcosystemView.vue'), meta: { authority: 'ECOSYSTEM_READ' } },
      { path: 'eco-map', component: () => import('@/views/EcoMapView.vue'), meta: { authority: 'OBS_READ' } },
      { path: 'observations', component: () => import('@/views/ObservationView.vue'), meta: { authority: 'OBS_READ' } },
      { path: 'assistant', component: () => import('@/views/AiAssistantView.vue') },
      { path: 'ai-reviews', component: () => import('@/views/AiReviewTicketsView.vue'), meta: { authority: 'AI_REVIEW_READ' } },
      { path: 'ai-reports', component: () => import('@/views/AiReportsView.vue'), meta: { authority: 'REPORT_READ' } },
      { path: 'rag-knowledge', component: () => import('@/views/RagKnowledgeView.vue'), meta: { authority: 'RAG_READ' } },
      { path: 'reports', component: () => import('@/views/ReportsView.vue'), meta: { authority: 'REPORT_READ' } },
      { path: 'audits', component: () => import('@/views/AuditView.vue'), meta: { authority: 'AUDIT_READ' } },
      { path: 'users', component: () => import('@/views/UsersView.vue'), meta: { authority: 'USER_ADMIN' } },
      { path: 'profile', component: () => import('@/views/ProfileView.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (to.meta.public) {
    if (to.path === '/login' && to.query.fresh === '1') {
      return true
    }
    if ((to.path === '/login' || to.path === '/register') && token) {
      return '/dashboard'
    }
    return true
  }
  if (!token) {
    return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  const raw = localStorage.getItem(PROFILE_KEY)
  const authorities: string[] = raw ? JSON.parse(raw).authorities ?? [] : []
  if (to.meta.authority && !authorities.includes(to.meta.authority)) {
    return '/dashboard'
  }
  return true
})

export default router
