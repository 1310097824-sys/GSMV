<template>
  <div class="page-shell dashboard-page">
    <section class="page-hero dashboard-hero">
      <div class="page-hero__content">
        <span class="page-hero__eyebrow">Mission Control</span>
        <h2>今天最值得关注的海域变化</h2>
        <p>
          仪表盘把系统概览、空间分布与近期活动压缩成一张可读的工作首屏。先快速判断当前数据规模和变化焦点，再进入观测记录、统计报表或
          AI 助手继续推进工作。
        </p>
        <div class="page-hero__actions">
          <RouterLink to="/reports">
            <el-button type="primary">查看完整报表</el-button>
          </RouterLink>
          <RouterLink to="/observations">
            <el-button type="primary" plain>进入观测记录</el-button>
          </RouterLink>
        </div>
      </div>

      <div class="dashboard-window">
        <div class="dashboard-window__header">
          <span>今日海域快照</span>
          <strong>{{ summary.recentObservationCount }} 条近 7 天新增动态</strong>
        </div>

        <div class="dashboard-window__metrics">
          <article class="dashboard-window__metric">
            <span>总观测</span>
            <strong>{{ summary.totalObservations }}</strong>
            <small>当前系统累计开工量</small>
          </article>
          <article class="dashboard-window__metric">
            <span>活跃生态系统</span>
            <strong>{{ topEcosystems[0]?.ecosystemName || '待补充' }}</strong>
            <small>{{ topEcosystems[0]?.observationCount || 0 }} 次观测</small>
          </article>
          <article class="dashboard-window__metric">
            <span>活跃观测员</span>
            <strong>{{ topObservers[0]?.name || '待补充' }}</strong>
            <small>{{ topObservers[0]?.value || 0 }} 条记录</small>
          </article>
        </div>

        <div class="dashboard-window__story">
          <div class="dashboard-window__column">
            <span>当前焦点生态系统</span>
            <ul>
              <li v-for="item in topEcosystems" :key="item.ecosystemName">
                <strong>{{ item.ecosystemName }}</strong>
                <small>{{ item.observationCount }} 次观测 / {{ item.speciesCount }} 个物种</small>
              </li>
            </ul>
          </div>
          <div class="dashboard-window__column">
            <span>近期活跃观测员</span>
            <ul>
              <li v-for="item in topObservers" :key="item.name">
                <strong>{{ item.name }}</strong>
                <small>{{ item.value }} 次观测活动</small>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <section class="dashboard-story-grid">
      <el-card class="panel-card dashboard-story-card" shadow="never">
        <template #header>
          <strong>此刻值得先看的三件事</strong>
        </template>
        <div class="dashboard-focus-list">
          <article v-for="item in focusCards" :key="item.label" class="dashboard-focus-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <p>{{ item.hint }}</p>
          </article>
        </div>
      </el-card>

      <el-card class="panel-card dashboard-story-card" shadow="never">
        <template #header>
          <strong>继续下一步工作</strong>
        </template>
        <div class="dashboard-route-list">
          <RouterLink v-for="item in dashboardActions" :key="item.path" :to="item.path" class="dashboard-route-card">
            <span>{{ item.eyebrow }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
            <em>{{ item.action }}</em>
          </RouterLink>
        </div>
      </el-card>
    </section>

    <div class="summary-grid">
      <StatCard eyebrow="物种总数" :value="summary.totalSpecies" hint="当前启用中的物种档案数量" />
      <StatCard eyebrow="观测次数" :value="summary.totalObservations" hint="系统累计观测记录总数" />
      <StatCard eyebrow="生态系统" :value="summary.totalEcosystems" hint="已纳入管理的生态系统数量" />
      <StatCard eyebrow="活跃用户" :value="summary.totalUsers" hint="当前处于启用状态的系统用户数" />
      <StatCard eyebrow="近 7 天观测" :value="summary.recentObservationCount" hint="用于判断近期观测活动热度" />
    </div>

    <el-row :gutter="18">
      <el-col :lg="15" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>近 30 天观测趋势</strong>
          </template>
          <ChartPanel :option="trendOption" />
        </el-card>
      </el-col>
      <el-col :lg="9" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>观测人员活跃度</strong>
          </template>
          <ChartPanel :option="observerActivityOption" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="18">
      <el-col :lg="8" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>保护等级占比</strong>
          </template>
          <ChartPanel :option="protectionOption" />
        </el-card>
      </el-col>
      <el-col :lg="16" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>生态系统统计</strong>
          </template>
          <ChartPanel :option="ecosystemOption" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="18">
      <el-col :lg="12" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <strong>物种分布地图</strong>
              <div class="panel-header__tools">
                <el-select v-model="selectedProtectionLevel" size="small" class="panel-filter">
                  <el-option
                    v-for="option in speciesProtectionOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
                <span>{{ speciesMapMarkers.length }} / {{ speciesDistributionPoints.length }} 个分布点</span>
              </div>
            </div>
          </template>
          <ReportMapPanel :points="speciesMapMarkers" empty-description="当前没有可展示的物种分布点" :height="320" />
        </el-card>
      </el-col>
      <el-col :lg="12" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <strong>观测地点地图</strong>
              <span>{{ observationMapMarkers.length }} 个观测点</span>
            </div>
          </template>
          <ReportMapPanel :points="observationMapMarkers" empty-description="当前没有可展示的观测点位" :height="320" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { RouterLink } from 'vue-router'
import {
  fetchDashboardSummary,
  fetchEcosystemAnalytics,
  fetchObservationActivity,
  fetchObservationMapPoints,
  fetchObservationTrend,
  fetchProtectionLevelDistribution,
  fetchSpeciesDistributionPoints,
} from '@/api/reports'
import ChartPanel from '@/components/ChartPanel.vue'
import ReportMapPanel from '@/components/ReportMapPanel.vue'
import StatCard from '@/components/StatCard.vue'
import { listenDataChanged } from '@/utils/dataSync'
import type {
  DashboardSummary,
  EcosystemAnalyticsPoint,
  NameValuePoint,
  ObservationMapPoint,
  SpeciesDistributionPoint,
} from '@/types/gsmv'

const ALL_PROTECTION_LEVEL = '__ALL__'
const EMPTY_PROTECTION_LEVEL = '__EMPTY__'

const summary = ref<DashboardSummary>({
  totalSpecies: 0,
  totalObservations: 0,
  totalEcosystems: 0,
  totalUsers: 0,
  recentObservationCount: 0,
})

const trendData = ref<NameValuePoint[]>([])
const observerActivity = ref<NameValuePoint[]>([])
const protectionData = ref<NameValuePoint[]>([])
const ecosystemAnalytics = ref<EcosystemAnalyticsPoint[]>([])
const speciesDistributionPoints = ref<SpeciesDistributionPoint[]>([])
const observationMapPoints = ref<ObservationMapPoint[]>([])
const selectedProtectionLevel = ref(ALL_PROTECTION_LEVEL)

let stopDataSync: (() => void) | undefined

const topEcosystems = computed(() =>
  [...ecosystemAnalytics.value].sort((left, right) => right.observationCount - left.observationCount).slice(0, 3),
)

const topObservers = computed(() =>
  [...observerActivity.value].sort((left, right) => right.value - left.value).slice(0, 3),
)

const focusCards = computed(() => [
  {
    label: '近 7 天新增观测',
    value: `${summary.value.recentObservationCount} 条`,
    hint: '用来快速判断近期海域是否正在进入更活跃的采样阶段。',
  },
  {
    label: '当前最热生态系统',
    value: topEcosystems.value[0]?.ecosystemName || '待补充',
    hint: topEcosystems.value[0]
      ? `目前累计 ${topEcosystems.value[0].observationCount} 次观测，最适合作为首要观察面。`
      : '还没有足够的生态系统活动数据。',
  },
  {
    label: '当前最活跃观测员',
    value: topObservers.value[0]?.name || '待补充',
    hint: topObservers.value[0]
      ? `最近统计里共有 ${topObservers.value[0].value} 次观测记录。`
      : '目前还没有观测员活跃度数据。',
  },
])

const dashboardActions = [
  {
    eyebrow: '记录现场',
    title: '进入观测记录',
    description: '继续录入地点、环境参数与物种关联，让现场信息尽快进入系统。',
    action: '去新增或维护观测',
    path: '/observations',
  },
  {
    eyebrow: '看清全局',
    title: '打开统计报表',
    description: '把物种分布、生态系统活跃度和时间趋势放在同一页里复核。',
    action: '去查看完整报表',
    path: '/reports',
  },
  {
    eyebrow: '发起分析',
    title: '进入 AI 助手',
    description: '用自然语言追问近期变化、重点物种与空间范围，快速得到摘要。',
    action: '去问一个问题',
    path: '/assistant',
  },
]

const speciesProtectionOptions = computed(() => {
  const optionMap = new Map<string, string>()

  speciesDistributionPoints.value.forEach((item) => {
    const value = normalizeProtectionLevel(item.protectionLevel)
    if (!optionMap.has(value)) {
      optionMap.set(value, item.protectionLevel?.trim() || '未填写保护等级')
    }
  })

  return [
    { label: '全部保护等级', value: ALL_PROTECTION_LEVEL },
    ...Array.from(optionMap.entries()).map(([value, label]) => ({ value, label })),
  ]
})

const filteredSpeciesDistributionPoints = computed(() =>
  speciesDistributionPoints.value.filter((item) => {
    if (selectedProtectionLevel.value === ALL_PROTECTION_LEVEL) {
      return true
    }
    return normalizeProtectionLevel(item.protectionLevel) === selectedProtectionLevel.value
  }),
)

function wrapAxisLabel(value: string, maxChars = 6) {
  if (!value) {
    return ''
  }

  const lines: string[] = []
  for (let index = 0; index < value.length; index += maxChars) {
    lines.push(value.slice(index, index + maxChars))
  }
  return lines.join('\n')
}

function createWrappedAxisLabel(maxChars = 6) {
  return {
    interval: 0,
    rotate: 24,
    margin: 18,
    width: 104,
    lineHeight: 16,
    overflow: 'break' as const,
    formatter: (value: string) => wrapAxisLabel(value, maxChars),
  }
}

const trendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: trendData.value.map((item) => item.name),
    boundaryGap: false,
  },
  yAxis: { type: 'value' },
  series: [
    {
      data: trendData.value.map((item) => item.value),
      type: 'line',
      smooth: true,
      areaStyle: { color: 'rgba(21, 122, 116, 0.16)' },
      lineStyle: { color: '#157a74', width: 3 },
      itemStyle: { color: '#157a74' },
    },
  ],
  grid: { left: 36, right: 20, top: 28, bottom: 28 },
}))

const observerActivityOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: observerActivity.value.map((item) => item.name),
    axisLabel: { interval: 0, rotate: 18 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      data: observerActivity.value.map((item) => item.value),
      type: 'bar',
      itemStyle: { color: '#ef8354', borderRadius: [8, 8, 0, 0] },
    },
  ],
  grid: { left: 36, right: 20, top: 28, bottom: 48 },
}))

const protectionOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '72%'],
      data: protectionData.value,
      itemStyle: { borderRadius: 10 },
    },
  ],
}))

const ecosystemOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: {
    data: ['观测次数', '发现物种数'],
    top: 6,
    textStyle: { color: '#d7eefb' },
  },
  xAxis: {
    type: 'category',
    data: ecosystemAnalytics.value.map((item) => item.ecosystemName),
    axisLabel: createWrappedAxisLabel(),
    axisTick: { alignWithLabel: true },
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '观测次数',
      type: 'bar',
      data: ecosystemAnalytics.value.map((item) => item.observationCount),
      itemStyle: { color: '#157a74', borderRadius: [8, 8, 0, 0] },
    },
    {
      name: '发现物种数',
      type: 'bar',
      data: ecosystemAnalytics.value.map((item) => item.speciesCount),
      itemStyle: { color: '#ff9f43', borderRadius: [8, 8, 0, 0] },
    },
  ],
  grid: { left: 36, right: 20, top: 78, bottom: 104, containLabel: true },
}))

const speciesMapMarkers = computed(() =>
  filteredSpeciesDistributionPoints.value.map((item) => ({
    id: item.speciesId,
    lat: item.locationLat,
    lng: item.locationLng,
    title: item.chineseName || item.scientificName,
    subtitle: item.chineseName ? item.scientificName : '',
    lines: [
      `地理范围：${item.geoRangeText || '未填写'}`,
      `保护等级：${item.protectionLevel || '未填写'}`,
      `濒危状态：${item.iucnStatus || '未填写'}`,
    ],
  })),
)

const observationMapMarkers = computed(() =>
  observationMapPoints.value.map((item) => ({
    id: item.observationId,
    lat: item.locationLat,
    lng: item.locationLng,
    title: item.locationName || item.ecosystemName,
    subtitle: `${item.ecosystemName} / ${item.observerName}`,
    lines: [item.observedAt, `关联物种 ${item.speciesCount} 种`, item.note || '无备注'],
  })),
)

async function loadDashboard() {
  try {
    const [summaryData, trend, observers, protection, ecosystemStats, speciesPoints, observationPoints] = await Promise.all([
      fetchDashboardSummary(),
      fetchObservationTrend(30),
      fetchObservationActivity(30),
      fetchProtectionLevelDistribution(),
      fetchEcosystemAnalytics(),
      fetchSpeciesDistributionPoints(),
      fetchObservationMapPoints(),
    ])

    summary.value = summaryData
    trendData.value = trend
    observerActivity.value = observers
    protectionData.value = protection
    ecosystemAnalytics.value = ecosystemStats
    speciesDistributionPoints.value = speciesPoints
    observationMapPoints.value = observationPoints

    if (!speciesProtectionOptions.value.some((option) => option.value === selectedProtectionLevel.value)) {
      selectedProtectionLevel.value = ALL_PROTECTION_LEVEL
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '综合看板加载失败')
  }
}

function normalizeProtectionLevel(value?: string) {
  const trimmed = value?.trim()
  return trimmed || EMPTY_PROTECTION_LEVEL
}

onMounted(() => {
  stopDataSync = listenDataChanged((detail) => {
    if (['species', 'observation', 'ecosystem', 'user'].includes(detail.type)) {
      void loadDashboard()
    }
  })
  void loadDashboard()
})

onBeforeUnmount(() => {
  stopDataSync?.()
})
</script>

<style scoped>
.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 440px);
  align-items: stretch;
  gap: 18px;
}

.dashboard-window {
  position: relative;
  z-index: 1;
  padding: 18px;
  border-radius: 28px;
  border: 1px solid rgba(175, 246, 255, 0.18);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03)),
    rgba(5, 24, 57, 0.54);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.dashboard-window__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.dashboard-window__header span {
  color: var(--gsmv-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.dashboard-window__header strong {
  max-width: 220px;
  text-align: right;
  font-size: 16px;
  line-height: 1.5;
}

.dashboard-window__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-window__metric {
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(181, 244, 255, 0.14);
  background: rgba(255, 255, 255, 0.05);
}

.dashboard-window__metric span,
.dashboard-window__column span {
  color: var(--gsmv-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.dashboard-window__metric strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 24px;
  line-height: 1.15;
}

.dashboard-window__metric small,
.dashboard-window__column small {
  color: rgba(230, 247, 255, 0.78);
  line-height: 1.6;
}

.dashboard-window__story {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.dashboard-window__column {
  padding: 16px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(181, 244, 255, 0.12);
}

.dashboard-window__column ul {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-window__column li {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dashboard-window__column strong {
  font-size: 15px;
}

.dashboard-story-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
}

.dashboard-story-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-focus-list,
.dashboard-route-list {
  display: grid;
  gap: 14px;
}

.dashboard-focus-item,
.dashboard-route-card {
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid rgba(176, 244, 255, 0.14);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03)),
    rgba(6, 25, 60, 0.54);
}

.dashboard-focus-item span,
.dashboard-route-card span {
  color: var(--gsmv-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.dashboard-focus-item strong,
.dashboard-route-card strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 20px;
  line-height: 1.22;
}

.dashboard-focus-item p,
.dashboard-route-card p {
  margin: 0;
  color: rgba(232, 247, 255, 0.84);
  line-height: 1.72;
}

.dashboard-route-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dashboard-route-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.dashboard-route-card:hover {
  transform: translateY(-2px);
  border-color: rgba(189, 247, 255, 0.24);
  box-shadow: 0 18px 36px rgba(2, 15, 44, 0.18);
}

.dashboard-route-card em {
  margin-top: auto;
  color: var(--gsmv-primary);
  font-style: normal;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 18px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.panel-header strong {
  font-size: 16px;
  letter-spacing: 0.01em;
}

.panel-header span {
  color: var(--gsmv-muted);
  font-size: 13px;
}

.panel-header__tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.panel-filter {
  width: 180px;
}

@media (max-width: 1180px) {
  .dashboard-hero,
  .dashboard-story-grid,
  .dashboard-route-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 840px) {
  .dashboard-window__metrics,
  .dashboard-window__story {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .panel-header__tools {
    width: 100%;
    justify-content: space-between;
  }

  .panel-filter {
    width: 100%;
    max-width: 220px;
  }
}
</style>
