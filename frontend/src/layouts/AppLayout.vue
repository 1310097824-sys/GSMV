<template>
  <div class="app-shell">
    <aside class="app-shell__aside">
      <section class="brand-card">
        <div class="brand-card__glow" />
        <div class="brand-card__glow brand-card__glow--soft" />
        <span class="brand-card__eyebrow">GSMV</span>
        <h1>海洋生物多样性管理台</h1>
        <p>围绕物种档案、生态系统、观测记录、智能分析与统计报表，构建一体化海洋观测工作区。</p>
        <div class="brand-card__meta">
          <span>生态建档</span>
          <span>智能识图</span>
          <span>科研问答</span>
        </div>
        <div class="brand-card__status">
          <div class="brand-card__status-item">
            <span>可用模块</span>
            <strong>{{ primaryMenus.length }}</strong>
          </div>
          <div class="brand-card__status-item">
            <span>当前身份</span>
            <strong>{{ authStore.profile?.roles[0] || '访客' }}</strong>
          </div>
        </div>
      </section>

      <nav class="nav-card">
        <RouterLink
          v-for="item in availableMenus"
          :key="item.path"
          :to="item.path"
          class="nav-card__item"
          :class="{ 'is-active': isMenuActive(item.path) }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <main class="app-shell__main">
      <header class="app-header">
        <div class="app-header__halo app-header__halo--one" />
        <div class="app-header__halo app-header__halo--two" />

        <div class="app-header__intro">
          <span class="app-header__eyebrow">{{ activeMenuLabel }}</span>
          <strong>欢迎回来！{{ welcomeName }}</strong>
        </div>

        <div class="app-header__actions">
          <RouterLink to="/profile" class="profile-chip">
            <el-avatar :size="40" :src="authStore.profile?.avatarUrl || '/default-avatar.jpg'">
              {{ authStore.profile?.displayName?.slice(0, 1) || 'U' }}
            </el-avatar>
            <span>个人中心</span>
          </RouterLink>
          <el-tag type="success" effect="dark">{{ authStore.profile?.roles.join(', ') || '访客' }}</el-tag>
          <el-button type="primary" plain @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <section class="app-toolbar">
        <nav class="app-toolbar__nav">
          <RouterLink
            v-for="item in primaryMenus"
            :key="item.path"
            :to="item.path"
            class="app-toolbar__link"
            :class="{ 'is-active': isMenuActive(item.path) }"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
      </section>

      <section class="app-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import {
  ChatDotRound,
  Connection,
  DataAnalysis,
  Document,
  Finished,
  Histogram,
  Location,
  MapLocation,
  Notebook,
  Setting,
  User,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const menus = [
  { path: '/dashboard', label: '仪表盘', icon: DataAnalysis, authority: 'REPORT_READ' },
  { path: '/species', label: '物种档案', icon: Document, authority: 'SPECIES_READ' },
  { path: '/ecosystems', label: '生态系统', icon: Location, authority: 'ECOSYSTEM_READ' },
  { path: '/eco-map', label: '生态地图', icon: MapLocation, authority: 'OBS_READ' },
  { path: '/observations', label: '观测记录', icon: Notebook, authority: 'OBS_READ' },
  { path: '/assistant', label: 'AI 助手', icon: ChatDotRound },
  { path: '/agent-runs', label: 'Agent 协作台', icon: Connection },
  { path: '/ai-reviews', label: 'AI 复核', icon: Finished, authority: 'AI_REVIEW_READ' },
  { path: '/ai-reports', label: 'AI 科研报告', icon: Document, authority: 'REPORT_READ' },
  { path: '/rag-knowledge', label: 'RAG 知识中台', icon: Notebook, authority: 'RAG_READ' },
  { path: '/reports', label: '统计报表', icon: Histogram, authority: 'REPORT_READ' },
  { path: '/audits', label: '审计日志', icon: Setting, authority: 'AUDIT_READ' },
  { path: '/users', label: '用户权限', icon: User, authority: 'USER_ADMIN' },
  { path: '/profile', label: '个人中心', icon: User },
]

const availableMenus = computed(() =>
  menus.filter((item) => !item.authority || authStore.authorities.includes(item.authority)),
)

const primaryMenus = computed(() =>
  availableMenus.value.filter((item) => item.path !== '/profile'),
)

const activeMenuLabel = computed(() =>
  availableMenus.value.find((item) => isMenuActive(item.path))?.label || '当前工作区',
)

const welcomeName = computed(() => authStore.profile?.displayName || authStore.profile?.username || '未登录用户')

function isMenuActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 328px 1fr;
  min-height: 100vh;
  gap: 24px;
  padding: 22px;
  max-width: 1720px;
  margin: 0 auto;
}

.app-shell__aside {
  position: sticky;
  top: 22px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.brand-card,
.nav-card,
.app-header,
.app-toolbar {
  position: relative;
  border: 1px solid var(--gsmv-border);
  border-radius: 30px;
  background: var(--gsmv-surface);
  box-shadow:
    0 24px 80px rgba(0, 8, 28, 0.34),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(20px);
  overflow: hidden;
}

.brand-card {
  isolation: isolate;
  padding: 30px;
  background:
    radial-gradient(circle at 14% 10%, rgba(170, 246, 255, 0.24), transparent 28%),
    radial-gradient(circle at 88% 82%, rgba(104, 243, 255, 0.18), transparent 24%),
    linear-gradient(155deg, rgba(202, 247, 255, 0.2), rgba(4, 28, 74, 0.84)),
    linear-gradient(180deg, rgba(8, 36, 86, 0.92), rgba(4, 18, 52, 0.98));
}

.brand-card::before {
  content: '';
  position: absolute;
  inset: 14px;
  border-radius: 24px;
  border: 1px solid rgba(200, 247, 255, 0.08);
  pointer-events: none;
}

.brand-card__glow {
  position: absolute;
  inset: auto -20% -26% auto;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(103, 234, 255, 0.22), transparent 72%);
  filter: blur(10px);
}

.brand-card__glow--soft {
  inset: -16% auto auto -12%;
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(163, 244, 255, 0.16), transparent 72%);
}

.brand-card__eyebrow {
  position: relative;
  z-index: 1;
  display: inline-block;
  margin-bottom: 12px;
  color: var(--gsmv-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.brand-card h1,
.brand-card p,
.brand-card__meta,
.brand-card__status {
  position: relative;
  z-index: 1;
}

.brand-card h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.02;
  letter-spacing: -0.04em;
  color: transparent;
  background: linear-gradient(180deg, #f8feff 0%, rgba(208, 244, 255, 0.82) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  text-shadow: 0 12px 32px rgba(112, 236, 255, 0.12);
}

.brand-card p {
  margin: 0;
  color: var(--gsmv-muted);
  line-height: 1.78;
}

.brand-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.brand-card__meta span {
  padding: 9px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(186, 244, 255, 0.18);
  color: #e6fbff;
  font-size: 13px;
}

.brand-card__status {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 22px;
}

.brand-card__status-item {
  padding: 14px 16px;
  border-radius: 20px;
  border: 1px solid rgba(178, 244, 255, 0.16);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.03)),
    rgba(4, 19, 52, 0.16);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 18px 30px rgba(0, 0, 0, 0.14);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.brand-card__status-item:hover {
  transform: translateY(-2px);
  border-color: rgba(185, 246, 255, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 24px 42px rgba(0, 9, 30, 0.2);
}

.brand-card__status-item span {
  display: block;
  margin-bottom: 8px;
  color: rgba(223, 248, 255, 0.72);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.brand-card__status-item strong {
  font-size: 22px;
  color: #f3fdff;
}

.nav-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  background:
    radial-gradient(circle at 100% 0%, rgba(132, 243, 255, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.01)),
    linear-gradient(180deg, rgba(10, 34, 80, 0.92), rgba(4, 17, 48, 0.96)),
    rgba(8, 30, 72, 0.68);
}

.nav-card::before {
  content: '';
  position: absolute;
  inset: 14px;
  border-radius: 22px;
  border: 1px solid rgba(196, 246, 255, 0.06);
  pointer-events: none;
}

.nav-card__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px 14px 20px;
  border-radius: 20px;
  color: var(--gsmv-muted);
  border: 1px solid transparent;
  transition:
    transform 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
  overflow: hidden;
}

.nav-card__item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, #8af6ff 0%, #3aa9ff 100%);
  opacity: 0;
  transform: translateX(-8px);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.nav-card__item::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 18px;
  background: linear-gradient(120deg, rgba(255, 255, 255, 0.1), transparent 48%);
  opacity: 0;
  transition: opacity 0.18s ease;
  pointer-events: none;
}

.nav-card__item .el-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(171, 242, 255, 0.08);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.nav-card__item span {
  position: relative;
  z-index: 1;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.015em;
}

.nav-card__item:hover,
.nav-card__item.is-active {
  color: var(--gsmv-text);
  background:
    linear-gradient(135deg, rgba(94, 238, 255, 0.2), rgba(19, 116, 208, 0.18)),
    rgba(255, 255, 255, 0.04);
  border-color: rgba(171, 242, 255, 0.2);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 18px 32px rgba(0, 11, 38, 0.22);
  transform: translateX(4px);
}

.nav-card__item:hover::before,
.nav-card__item.is-active::before {
  opacity: 1;
  transform: translateX(0);
}

.nav-card__item:hover::after,
.nav-card__item.is-active::after {
  opacity: 1;
}

.nav-card__item:hover .el-icon,
.nav-card__item.is-active .el-icon {
  background: linear-gradient(135deg, rgba(108, 242, 255, 0.2), rgba(26, 117, 212, 0.16));
  border-color: rgba(171, 242, 255, 0.18);
  transform: translateY(-1px) scale(1.02);
}

.app-shell__main {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.app-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  min-height: 132px;
  padding: 26px 30px;
  background:
    radial-gradient(circle at 18% 0%, rgba(162, 244, 255, 0.18), transparent 30%),
    radial-gradient(circle at 84% 18%, rgba(108, 188, 255, 0.18), transparent 26%),
    linear-gradient(135deg, rgba(164, 242, 255, 0.14), rgba(8, 35, 85, 0.82)),
    rgba(8, 25, 60, 0.84);
}

.app-header::before {
  content: '';
  position: absolute;
  inset: 12px;
  border-radius: 24px;
  border: 1px solid rgba(198, 246, 255, 0.08);
  pointer-events: none;
}

.app-header__intro {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 58%;
}

.app-header__eyebrow {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(175, 246, 255, 0.18);
  background: rgba(255, 255, 255, 0.06);
  color: var(--gsmv-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.app-header strong {
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.06;
  letter-spacing: -0.04em;
  color: #f7fdff;
  text-shadow: 0 14px 34px rgba(106, 226, 255, 0.14);
}

.app-header__halo {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.app-header__halo--one {
  top: -48px;
  right: 130px;
  width: 150px;
  height: 150px;
  background: radial-gradient(circle, rgba(110, 242, 255, 0.14), transparent 72%);
}

.app-header__halo--two {
  bottom: -56px;
  left: 22%;
  width: 190px;
  height: 190px;
  background: radial-gradient(circle, rgba(87, 146, 255, 0.14), transparent 72%);
}

.app-header__actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
}

.profile-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px 8px 8px;
  border-radius: 999px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.12), rgba(102, 224, 255, 0.08)),
    rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(165, 242, 255, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.profile-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(180, 246, 255, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 18px 34px rgba(0, 10, 34, 0.18);
}

.profile-chip :deep(.el-avatar) {
  border: 1px solid rgba(191, 246, 255, 0.26);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.18);
}

.profile-chip span {
  color: var(--gsmv-text);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.app-toolbar {
  padding: 14px 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03)),
    rgba(8, 27, 63, 0.74);
}

.app-toolbar::before {
  content: '';
  position: absolute;
  inset: 12px;
  border-radius: 20px;
  border: 1px solid rgba(198, 246, 255, 0.06);
  pointer-events: none;
}

.app-toolbar__nav {
  display: flex;
  gap: 10px;
  align-items: center;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: thin;
}

.app-toolbar__nav::-webkit-scrollbar {
  height: 6px;
}

.app-toolbar__nav::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(175, 244, 255, 0.2);
}

.app-toolbar__link {
  position: relative;
  flex: 0 0 auto;
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid transparent;
  color: var(--gsmv-muted);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.01em;
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.app-toolbar__link::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 6px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(143, 245, 255, 0), rgba(143, 245, 255, 0.95), rgba(62, 165, 255, 0));
  opacity: 0;
  transform: scaleX(0.5);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.app-toolbar__link:hover,
.app-toolbar__link.is-active {
  color: var(--gsmv-text);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.12), rgba(101, 234, 255, 0.12)),
    rgba(255, 255, 255, 0.04);
  border-color: rgba(178, 245, 255, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 16px 28px rgba(0, 10, 34, 0.14);
  transform: translateY(-1px);
}

.app-toolbar__link:hover::after,
.app-toolbar__link.is-active::after {
  opacity: 1;
  transform: scaleX(1);
}

.app-content {
  flex: 1;
  padding-bottom: 18px;
}

.app-header__actions :deep(.el-tag) {
  height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(186, 246, 255, 0.16);
  background:
    linear-gradient(135deg, rgba(133, 242, 255, 0.18), rgba(76, 118, 255, 0.12)),
    rgba(255, 255, 255, 0.05);
  color: #effdff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.app-header__actions :deep(.el-button) {
  height: 42px;
  padding-inline: 18px;
  border-radius: 999px;
  border-color: rgba(186, 246, 255, 0.22);
  background:
    linear-gradient(135deg, rgba(116, 240, 255, 0.18), rgba(53, 127, 255, 0.16)),
    rgba(255, 255, 255, 0.05);
  color: #f4fdff;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 18px 32px rgba(0, 10, 34, 0.16);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.app-header__actions :deep(.el-button:hover) {
  transform: translateY(-1px);
  border-color: rgba(204, 248, 255, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 22px 38px rgba(0, 10, 34, 0.22);
}

@media (prefers-reduced-motion: reduce) {
  .brand-card__status-item,
  .nav-card__item,
  .nav-card__item::before,
  .nav-card__item::after,
  .nav-card__item .el-icon,
  .profile-chip,
  .app-toolbar__link,
  .app-toolbar__link::after,
  .app-header__actions :deep(.el-button) {
    transition: none;
  }
}

@media (max-width: 1100px) {
  .app-shell {
    grid-template-columns: 1fr;
    padding: 16px;
  }

  .app-shell__aside {
    position: static;
  }

  .app-header__intro {
    max-width: none;
  }
}

@media (max-width: 720px) {
  .brand-card__status {
    grid-template-columns: 1fr;
  }

  .app-header {
    padding: 18px 20px;
    min-height: auto;
    flex-direction: column;
  }

  .app-header strong {
    font-size: 24px;
  }

  .app-header__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
