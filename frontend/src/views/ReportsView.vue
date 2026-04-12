<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>统计报表</h2>
        <p>集中查看物种分布地图、观测地点地图、分类与保护等级统计、生态系统统计和观测活动分析，并支持导出为 Excel 或 PDF。</p>
      </div>
      <el-space wrap>
        <el-select v-model="days" class="day-filter">
          <el-option v-for="item in dayOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="loadReports">刷新报表</el-button>
        <el-button :loading="exportingExcel" @click="handleExport('excel')">导出 Excel</el-button>
        <el-button :loading="exportingPdf" @click="handleExport('pdf')">导出 PDF</el-button>
      </el-space>
    </section>

    <div class="summary-grid">
      <StatCard eyebrow="物种总数" :value="summary.totalSpecies" hint="用于判断物种档案覆盖规模" />
      <StatCard eyebrow="观测次数" :value="summary.totalObservations" hint="用于判断累计观测工作量" />
      <StatCard eyebrow="生态系统" :value="summary.totalEcosystems" hint="用于判断生态系统采样覆盖面" />
      <StatCard eyebrow="活跃用户" :value="summary.totalUsers" hint="当前启用中的系统用户数量" />
    </div>

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
          <ReportMapPanel :points="speciesMapMarkers" empty-description="暂无物种分布点" :height="360" />
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
          <ReportMapPanel :points="observationMapMarkers" empty-description="暂无观测地点点位" :height="360" />
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
      <el-col :lg="8" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>濒危状态占比</strong>
          </template>
          <ChartPanel :option="iucnOption" />
        </el-card>
      </el-col>
      <el-col :lg="8" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>物种纲级占比</strong>
          </template>
          <ChartPanel :option="classOption" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="18">
      <el-col :lg="10" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>物种门级占比</strong>
          </template>
          <ChartPanel :option="phylumOption" />
        </el-card>
      </el-col>
      <el-col :lg="14" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>生态系统统计</strong>
          </template>
          <ChartPanel :option="ecosystemOption" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="18">
      <el-col :lg="14" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>近 {{ days }} 天观测趋势</strong>
          </template>
          <ChartPanel :option="trendOption" />
        </el-card>
      </el-col>
      <el-col :lg="10" :xs="24">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <strong>按人员统计观测次数</strong>
          </template>
          <ChartPanel :option="observerOption" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import {
  downloadReportExport,
  fetchDashboardSummary,
  fetchEcosystemAnalytics,
  fetchIucnStatusDistribution,
  fetchObservationActivity,
  fetchObservationMapPoints,
  fetchObservationTrend,
  fetchProtectionLevelDistribution,
  fetchSpeciesClassDistribution,
  fetchSpeciesDistributionPoints,
  fetchSpeciesPhylumDistribution,
} from '@/api/reports'
import ChartPanel from '@/components/ChartPanel.vue'
import ReportMapPanel from '@/components/ReportMapPanel.vue'
import StatCard from '@/components/StatCard.vue'
import { listenDataChanged } from '@/utils/dataSync'
import { triggerBlobDownload } from '@/utils/download'
import type {
  DashboardSummary,
  EcosystemAnalyticsPoint,
  NameValuePoint,
  ObservationMapPoint,
  SpeciesDistributionPoint,
} from '@/types/gsmv'

const ALL_PROTECTION_LEVEL = '__ALL__'
const EMPTY_PROTECTION_LEVEL = '__EMPTY__'

const dayOptions = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

const days = ref(30)
const loading = ref(false)
const exportingExcel = ref(false)
const exportingPdf = ref(false)
const selectedProtectionLevel = ref(ALL_PROTECTION_LEVEL)

const summary = ref<DashboardSummary>({
  totalSpecies: 0,
  totalObservations: 0,
  totalEcosystems: 0,
  totalUsers: 0,
  recentObservationCount: 0,
})
const protectionPoints = ref<NameValuePoint[]>([])
const iucnPoints = ref<NameValuePoint[]>([])
const phylumPoints = ref<NameValuePoint[]>([])
const classPoints = ref<NameValuePoint[]>([])
const ecosystemPoints = ref<EcosystemAnalyticsPoint[]>([])
const trendPoints = ref<NameValuePoint[]>([])
const observerPoints = ref<NameValuePoint[]>([])
const speciesDistributionPoints = ref<SpeciesDistributionPoint[]>([])
const observationMapPoints = ref<ObservationMapPoint[]>([])

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

const protectionOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '72%'],
      data: protectionPoints.value,
      itemStyle: { borderRadius: 10 },
    },
  ],
}))

const iucnOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '72%'],
      data: iucnPoints.value,
      itemStyle: { borderRadius: 10 },
    },
  ],
}))

const phylumOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: phylumPoints.value.map((item) => item.name),
    axisLabel: { interval: 0, rotate: 18 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'bar',
      data: phylumPoints.value.map((item) => item.value),
      itemStyle: { color: '#4f7cff', borderRadius: [8, 8, 0, 0] },
    },
  ],
  grid: { left: 36, right: 20, top: 28, bottom: 48 },
}))

const classOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: classPoints.value.map((item) => item.name),
    axisLabel: { interval: 0, rotate: 18 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'bar',
      data: classPoints.value.map((item) => item.value),
      itemStyle: { color: '#6a9c89', borderRadius: [8, 8, 0, 0] },
    },
  ],
  grid: { left: 36, right: 20, top: 28, bottom: 48 },
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
    data: ecosystemPoints.value.map((item) => item.ecosystemName),
    axisLabel: createWrappedAxisLabel(),
    axisTick: { alignWithLabel: true },
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '观测次数',
      type: 'bar',
      data: ecosystemPoints.value.map((item) => item.observationCount),
      itemStyle: { color: '#157a74', borderRadius: [8, 8, 0, 0] },
    },
    {
      name: '发现物种数',
      type: 'bar',
      data: ecosystemPoints.value.map((item) => item.speciesCount),
      itemStyle: { color: '#ff9f43', borderRadius: [8, 8, 0, 0] },
    },
  ],
  grid: { left: 36, right: 20, top: 78, bottom: 104, containLabel: true },
}))

const trendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: trendPoints.value.map((item) => item.name),
    boundaryGap: false,
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'line',
      smooth: true,
      data: trendPoints.value.map((item) => item.value),
      lineStyle: { color: '#157a74', width: 3 },
      areaStyle: { color: 'rgba(21, 122, 116, 0.18)' },
    },
  ],
  grid: { left: 36, right: 20, top: 28, bottom: 28 },
}))

const observerOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: observerPoints.value.map((item) => item.name),
    axisLabel: { interval: 0, rotate: 18 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'bar',
      data: observerPoints.value.map((item) => item.value),
      itemStyle: { color: '#ef8354', borderRadius: [8, 8, 0, 0] },
    },
  ],
  grid: { left: 36, right: 20, top: 28, bottom: 48 },
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

async function loadReports() {
  loading.value = true
  try {
    const [
      summaryData,
      protection,
      iucn,
      phylum,
      classes,
      ecosystem,
      trend,
      observers,
      speciesPoints,
      observationPoints,
    ] = await Promise.all([
      fetchDashboardSummary(),
      fetchProtectionLevelDistribution(),
      fetchIucnStatusDistribution(),
      fetchSpeciesPhylumDistribution(),
      fetchSpeciesClassDistribution(),
      fetchEcosystemAnalytics(),
      fetchObservationTrend(days.value),
      fetchObservationActivity(days.value),
      fetchSpeciesDistributionPoints(),
      fetchObservationMapPoints(),
    ])

    summary.value = summaryData
    protectionPoints.value = protection
    iucnPoints.value = iucn
    phylumPoints.value = phylum
    classPoints.value = classes
    ecosystemPoints.value = ecosystem
    trendPoints.value = trend
    observerPoints.value = observers
    speciesDistributionPoints.value = speciesPoints
    observationMapPoints.value = observationPoints

    if (!speciesProtectionOptions.value.some((option) => option.value === selectedProtectionLevel.value)) {
      selectedProtectionLevel.value = ALL_PROTECTION_LEVEL
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报表加载失败')
  } finally {
    loading.value = false
  }
}

async function handleExport(format: 'excel' | 'pdf') {
  const loadingRef = format === 'excel' ? exportingExcel : exportingPdf
  loadingRef.value = true

  try {
    const { blob, fileName } = await downloadReportExport(format, days.value)
    triggerBlobDownload(blob, fileName)
    ElMessage.success(format === 'excel' ? 'Excel 报表已开始下载' : 'PDF 报表已开始下载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    loadingRef.value = false
  }
}

function normalizeProtectionLevel(value?: string) {
  const trimmed = value?.trim()
  return trimmed || EMPTY_PROTECTION_LEVEL
}

onMounted(() => {
  stopDataSync = listenDataChanged((detail) => {
    if (['species', 'observation', 'ecosystem', 'user'].includes(detail.type)) {
      void loadReports()
    }
  })
  void loadReports()
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

.day-filter {
  width: 140px;
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

  .panel-filter,
  .day-filter {
    width: 100%;
    max-width: 220px;
  }
}
</style>
