<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>综合数据看板</h2>
        <p>把系统概览、物种分布、观测地点和关键统计图表集中展示，便于快速判断当前数据规模和近期监测态势。</p>
      </div>
      <RouterLink to="/reports">
        <el-button type="primary" plain>查看详细报表</el-button>
      </RouterLink>
    </section>

    <div class="summary-grid">
      <StatCard eyebrow="物种总数" :value="summary.totalSpecies" hint="当前启用中的物种档案数量" />
      <StatCard eyebrow="观测次数" :value="summary.totalObservations" hint="系统累计观测记录总数" />
      <StatCard eyebrow="生态系统" :value="summary.totalEcosystems" hint="已纳入管理的生态系统数量" />
      <StatCard eyebrow="活跃用户" :value="summary.totalUsers" hint="当前处于启用状态的系统用户数" />
      <StatCard eyebrow="近 7 天观测" :value="summary.recentObservationCount" hint="用于判断近期观测活动活跃度" />
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
