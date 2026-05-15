<template>
  <div ref="pageShellRef" class="page-shell">
    <section class="page-hero reveal-section reveal-section--hero">
      <div>
        <h2>生态地图</h2>
        <p>在地图上查看观测点分布，按生态系统筛选，并联动查看每条观测记录的详情信息和关联物种。</p>
      </div>
      <el-space wrap>
        <el-tag type="success" size="large">{{ pointCount }} 个位置点</el-tag>
        <el-tag size="large">{{ ecosystemCount }} 个生态系统</el-tag>
      </el-space>
    </section>

    <section class="map-story-band reveal-section reveal-section--story">
      <article class="map-story-band__feature">
        <div class="map-story-band__eyebrow">Marine Snapshot</div>
        <h3>{{ selectedEcosystemName }}</h3>
        <p>
          当前地图正在串联 {{ pointCount }} 个观测点，优先追踪 {{ highlightedLocation }} 一带的生态变化，
          让地图、时间和物种记录形成一条更容易阅读的观测叙事。
        </p>
      </article>

      <div class="map-story-band__stats">
        <article class="map-story-band__stat">
          <span>最新观测</span>
          <strong>{{ latestObservedAt }}</strong>
          <p>帮助我们快速把视线拉回最近一次现场记录。</p>
        </article>
        <article class="map-story-band__stat">
          <span>当前焦点</span>
          <strong>{{ highlightedLocation }}</strong>
          <p>自动把地图上下文锚定到当前最值得看的区域。</p>
        </article>
        <article class="map-story-band__stat">
          <span>关联物种</span>
          <strong>{{ highlightedSpeciesCount }}</strong>
          <p>打开详情后可继续追踪这一条记录里关联的物种数量。</p>
        </article>
      </div>
    </section>

    <div class="map-grid reveal-section reveal-section--grid">
      <el-card class="panel-card" shadow="never">
        <div class="toolbar">
          <el-select v-model="query.ecosystemId" placeholder="按生态系统筛选" clearable style="width: 240px">
            <el-option v-for="item in ecosystemOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="refreshMapData">刷新地图</el-button>
          <el-button plain @click="resetFilter">重置</el-button>
          <div class="spacer" />
          <RouterLink to="/observations">
            <el-button plain>进入观测录入</el-button>
          </RouterLink>
        </div>

        <div ref="mapRef" class="eco-map" />
      </el-card>

      <div class="map-side">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="side-header">
              <strong>观测点列表</strong>
              <span>{{ pointCount }} 条</span>
            </div>
          </template>

          <el-scrollbar height="320">
            <div v-if="observations.length" class="observation-list">
              <button
                v-for="item in observations"
                :key="item.id"
                type="button"
                class="observation-item"
                :class="{ 'is-active': selectedDetail?.id === item.id }"
                @click="focusObservation(item.id, true)"
              >
                <div class="observation-item__topline">
                  <span class="observation-item__badge">#{{ item.id }}</span>
                  <span class="observation-item__time">{{ formatObservedDate(item.observedAt) }}</span>
                </div>
                <strong>{{ item.locationName || item.ecosystemName }}</strong>
                <span>{{ item.ecosystemName }}</span>
                <div class="observation-item__footer">
                  <span>坐标</span>
                  <span>{{ formatCoordinate(item.locationLat) }}, {{ formatCoordinate(item.locationLng) }}</span>
                </div>
              </button>
            </div>
            <el-empty v-else description="当前筛选条件下没有观测点" />
          </el-scrollbar>
        </el-card>

        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="side-header">
              <strong>点位详情</strong>
              <span v-if="detailLoading">加载中...</span>
            </div>
          </template>

          <template v-if="selectedDetail">
            <div class="detail-hero">
              <div>
                <div class="detail-hero__eyebrow">Observation Detail</div>
                <h3>{{ selectedDetail.locationName || selectedDetail.ecosystemName }}</h3>
                <p>{{ selectedDetail.ecosystemName }} · {{ formatObservedDate(selectedDetail.observedAt) }}</p>
              </div>
              <div class="detail-hero__tags">
                <el-tag effect="plain">#{{ selectedDetail.id }}</el-tag>
                <el-tag effect="plain">{{ selectedDetail.observerName || '未指定观察者' }}</el-tag>
              </div>
            </div>

            <div class="detail-stat-grid">
              <div class="detail-stat-card">
                <span>纬度</span>
                <strong>{{ formatCoordinate(selectedDetail.locationLat) }}</strong>
              </div>
              <div class="detail-stat-card">
                <span>经度</span>
                <strong>{{ formatCoordinate(selectedDetail.locationLng) }}</strong>
              </div>
            </div>

            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="生态系统">{{ selectedDetail.ecosystemName }}</el-descriptions-item>
              <el-descriptions-item label="观察者">{{ selectedDetail.observerName }}</el-descriptions-item>
              <el-descriptions-item label="观测时间">{{ formatObservedDate(selectedDetail.observedAt) }}</el-descriptions-item>
              <el-descriptions-item label="地点说明">{{ selectedDetail.locationName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="坐标">
                {{ selectedDetail.locationLat }}, {{ selectedDetail.locationLng }}
              </el-descriptions-item>
              <el-descriptions-item label="环境参数">{{ selectedDetail.envJson || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备注">{{ selectedDetail.note || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider>关联物种</el-divider>

            <div class="detail-species-table">
              <el-table :data="selectedDetail.speciesItems" size="small" max-height="220">
                <el-table-column prop="scientificName" label="学名" min-width="160" />
                <el-table-column prop="chineseName" label="中文名" min-width="120" />
                <el-table-column prop="countEstimated" label="数量" min-width="80" />
              </el-table>
            </div>
          </template>
          <el-empty v-else description="点击地图点位或右侧列表查看详情" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import L from 'leaflet'
import 'leaflet.markercluster'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink } from 'vue-router'
import { fetchAllEcosystems } from '@/api/ecosystems'
import { fetchObservationDetail, fetchObservations } from '@/api/observations'
import { ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'
import { listenDataChanged } from '@/utils/dataSync'
import { addPreferredTileLayer, toMapDisplayPoint } from '@/utils/mapProvider'
import { buildMapPopupCard, createMapMarkerIcon } from '@/utils/mapMarkerTheme'
import type { Ecosystem, ObservationDetailView, ObservationView } from '@/types/gsmv'

const pageShellRef = ref<HTMLDivElement>()
const mapRef = ref<HTMLDivElement>()
const loading = ref(false)
const detailLoading = ref(false)
const ecosystemOptions = ref<Ecosystem[]>([])
const observations = ref<ObservationView[]>([])
const selectedDetail = ref<ObservationDetailView | null>(null)

const query = reactive({
  ecosystemId: undefined as number | undefined,
})

const pointCount = computed(() => observations.value.length)
const ecosystemCount = computed(() => ecosystemOptions.value.length)
const selectedEcosystemName = computed(() => {
  if (!query.ecosystemId) {
    return '全部生态系统'
  }
  return ecosystemOptions.value.find((item) => item.id === query.ecosystemId)?.name || '当前生态系统'
})
const latestObservedAt = computed(() => {
  if (!observations.value.length) {
    return '等待新观测'
  }
  const latest = [...observations.value]
    .map((item) => item.observedAt)
    .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0]
  return formatObservedDate(latest)
})
const highlightedLocation = computed(() => {
  return selectedDetail.value?.locationName || observations.value[0]?.locationName || '湛江近海'
})
const highlightedSpeciesCount = computed(() => selectedDetail.value?.speciesItems.length || 0)

let map: L.Map | null = null
let markerLayer: L.MarkerClusterGroup | null = null
let stopDataSyncListener: (() => void) | null = null
let resizeObserver: ResizeObserver | null = null
let revealObserver: IntersectionObserver | null = null
let invalidateTimer: number | null = null

const markerMap = new Map<number, L.Marker>()
const markerDataMap = new Map<number, ObservationView>()
const defaultCenter = ZHANJIANG_OFFSHORE_CENTER

function scheduleInvalidateMap(delay = 80) {
  if (!map) {
    return
  }
  if (invalidateTimer != null) {
    window.clearTimeout(invalidateTimer)
  }
  invalidateTimer = window.setTimeout(() => {
    map?.invalidateSize(false)
    invalidateTimer = null
  }, delay)
}

function createClusterIcon(cluster: L.MarkerCluster) {
  const count = cluster.getChildCount()
  const size = count >= 50 ? 54 : count >= 20 ? 48 : 42
  return L.divIcon({
    html: `<span>${count}</span>`,
    className: 'gsmv-marker-cluster',
    iconSize: L.point(size, size),
  })
}

function getMarkerTone(id: number) {
  const tones = ['aqua', 'emerald', 'violet'] as const
  return tones[Math.abs(id) % tones.length]
}

function formatObservedDate(value: string) {
  return value.includes('T') ? value.replace('T', ' ') : value
}

function formatCoordinate(value: number) {
  return Number(value).toFixed(3)
}

function updateMarkerVisualState(activeId?: number | null) {
  markerMap.forEach((marker, id) => {
    const item = markerDataMap.get(id)
    if (!item) {
      return
    }
    marker.setIcon(
      createMapMarkerIcon(item.locationName || item.ecosystemName || 'M', {
        active: id === activeId,
        tone: getMarkerTone(id),
      }),
    )
    marker.setZIndexOffset(id === activeId ? 1200 : 0)
  })
}

function ensureMap() {
  if (map || !mapRef.value) {
    return
  }

  map = L.map(mapRef.value, { zoomControl: true }).setView(toMapDisplayPoint(defaultCenter[0], defaultCenter[1]), 7)
  addPreferredTileLayer(map)
  markerLayer = L.markerClusterGroup({
    showCoverageOnHover: false,
    spiderfyOnMaxZoom: true,
    zoomToBoundsOnClick: true,
    disableClusteringAtZoom: 9,
    maxClusterRadius: 42,
    iconCreateFunction: createClusterIcon,
  }).addTo(map)
  scheduleInvalidateMap(120)

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      scheduleInvalidateMap(60)
    })
    resizeObserver.observe(mapRef.value)
  }
}

function renderMarkers() {
  if (!map || !markerLayer) {
    return
  }

  const clusterLayer = markerLayer
  clusterLayer.clearLayers()
  markerMap.clear()
  markerDataMap.clear()

  if (!observations.value.length) {
    map.setView(toMapDisplayPoint(defaultCenter[0], defaultCenter[1]), 7)
    scheduleInvalidateMap()
    return
  }

  const bounds: [number, number][] = []

  observations.value.forEach((item) => {
    const point = toMapDisplayPoint(item.locationLat, item.locationLng)
    const popupHtml = buildMapPopupCard({
      eyebrow: 'Observation',
      title: item.locationName || 'Observation point',
      subtitle: item.ecosystemName,
      meta: formatObservedDate(item.observedAt),
      chips: [
        { label: 'Ecosystem', value: item.ecosystemName },
        { label: 'Record', value: `#${item.id}` },
      ],
      lines: [
        `Coordinates ${formatCoordinate(item.locationLat)}, ${formatCoordinate(item.locationLng)}`,
        item.observerName ? `Observer ${item.observerName}` : '',
      ],
    })
    const marker = L.marker(point, {
      icon: createMapMarkerIcon(item.locationName || item.ecosystemName || 'M', {
        active: selectedDetail.value?.id === item.id,
        tone: getMarkerTone(item.id),
      }),
    })

    marker.bindPopup(popupHtml, { className: 'gsmv-map-popup' })
    marker.on('click', () => {
      void focusObservation(item.id, false)
    })
    marker.addTo(clusterLayer)
    markerMap.set(item.id, marker)
    markerDataMap.set(item.id, item)
    bounds.push(point)
  })

  if (bounds.length === 1) {
    map.setView(bounds[0], 8, { animate: false })
  } else {
    map.fitBounds(bounds, { padding: [24, 24], maxZoom: 8 })
  }

  updateMarkerVisualState(selectedDetail.value?.id)
  scheduleInvalidateMap()
}

async function fetchAllObservationPoints(ecosystemId?: number) {
  const all: ObservationView[] = []
  let page = 1
  let total = 0
  const size = 100

  do {
    const pageData = await fetchObservations({ ecosystemId, page, size })
    all.push(...pageData.items)
    total = pageData.total
    page += 1
  } while (all.length < total)

  return all
}

async function loadOptions() {
  ecosystemOptions.value = await fetchAllEcosystems()
  if (query.ecosystemId && !ecosystemOptions.value.some((item) => item.id === query.ecosystemId)) {
    query.ecosystemId = undefined
  }
}

async function loadObservations() {
  loading.value = true
  try {
    observations.value = await fetchAllObservationPoints(query.ecosystemId)
    renderMarkers()
    if (observations.value.length) {
      const currentId = selectedDetail.value?.id ?? observations.value[0].id
      await focusObservation(currentId, false)
    } else {
      selectedDetail.value = null
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生态地图加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshMapData() {
  try {
    await loadOptions()
    await loadObservations()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生态地图刷新失败')
  }
}

async function focusObservation(id: number, openPopup: boolean) {
  detailLoading.value = true
  try {
    selectedDetail.value = await fetchObservationDetail(id)
    updateMarkerVisualState(id)
    const marker = markerMap.get(id)
    if (marker && map && markerLayer) {
      markerLayer.zoomToShowLayer(marker, () => {
        map?.setView(marker.getLatLng(), Math.max(map.getZoom(), 8), { animate: false })
        if (openPopup) {
          marker.openPopup()
        }
      })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '观测详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function resetFilter() {
  query.ecosystemId = undefined
  void refreshMapData()
}

function setupRevealObserver() {
  if (!pageShellRef.value || typeof IntersectionObserver === 'undefined') {
    pageShellRef.value?.querySelectorAll<HTMLElement>('.reveal-section').forEach((element) => {
      element.classList.add('is-visible')
    })
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver?.unobserve(entry.target)
        }
      })
    },
    {
      threshold: 0.18,
      rootMargin: '0px 0px -10% 0px',
    },
  )

  pageShellRef.value.querySelectorAll<HTMLElement>('.reveal-section').forEach((element) => {
    revealObserver?.observe(element)
  })
}

onMounted(async () => {
  try {
    stopDataSyncListener = listenDataChanged(() => {
      void refreshMapData()
    })
    await nextTick()
    setupRevealObserver()
    ensureMap()
    await refreshMapData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生态地图初始化失败')
  }
})

onBeforeUnmount(() => {
  stopDataSyncListener?.()
  stopDataSyncListener = null
  resizeObserver?.disconnect()
  resizeObserver = null
  revealObserver?.disconnect()
  revealObserver = null
  if (invalidateTimer != null) {
    window.clearTimeout(invalidateTimer)
    invalidateTimer = null
  }
  map?.remove()
  map = null
  markerLayer = null
  markerMap.clear()
  markerDataMap.clear()
})
</script>

<style scoped>
.reveal-section {
  opacity: 0;
  transform: translateY(24px) scale(0.985);
  filter: blur(10px);
  transition:
    opacity 0.72s ease,
    transform 0.72s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.72s ease;
}

.reveal-section.is-visible {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

.reveal-section--hero {
  transition-delay: 0.02s;
}

.reveal-section--story {
  transition-delay: 0.12s;
}

.reveal-section--grid {
  transition-delay: 0.2s;
}

.map-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.85fr);
  gap: 18px;
}

.map-story-band {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(420px, 0.9fr);
  gap: 18px;
  margin-bottom: 18px;
}

.map-story-band__feature,
.map-story-band__stat {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(150, 232, 255, 0.14);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 18px 36px rgba(2, 15, 44, 0.14);
  transition:
    transform 0.28s ease,
    border-color 0.28s ease,
    box-shadow 0.28s ease;
}

.map-story-band__feature {
  padding: 24px 26px;
  border-radius: 28px;
  background:
    radial-gradient(circle at 12% 0%, rgba(90, 232, 255, 0.18), transparent 34%),
    linear-gradient(145deg, rgba(10, 41, 94, 0.94), rgba(5, 21, 58, 0.98));
}

.map-story-band__feature::after,
.map-story-band__stat::after {
  content: "";
  position: absolute;
  inset: auto -20% -38% auto;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(103, 246, 211, 0.12), transparent 70%);
  pointer-events: none;
  animation: map-card-drift 14s ease-in-out infinite;
}

.map-story-band__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #7feaff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.map-story-band__feature h3 {
  margin: 14px 0 8px;
  color: #f7fdff;
  font-size: 32px;
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.map-story-band__feature p {
  max-width: 560px;
  margin: 0;
  color: rgba(223, 246, 255, 0.82);
  line-height: 1.8;
}

.map-story-band__feature:hover,
.map-story-band__stat:hover {
  transform: translateY(-3px);
  border-color: rgba(162, 239, 255, 0.22);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 24px 40px rgba(2, 15, 44, 0.18);
}

.map-story-band__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.map-story-band__stat {
  position: relative;
  padding: 18px 18px 20px;
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02)),
    rgba(5, 24, 60, 0.78);
}

.map-story-band__stat span {
  display: block;
  color: rgba(197, 232, 246, 0.72);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.map-story-band__stat strong {
  display: block;
  margin-top: 22px;
  max-width: calc(100% - 72px);
  color: #f4fdff;
  font-size: 26px;
  line-height: 1.08;
  letter-spacing: -0.04em;
  text-wrap: balance;
}

.map-story-band__stat p {
  margin: 10px 0 0;
  color: rgba(214, 241, 252, 0.74);
  font-size: 12px;
  line-height: 1.7;
}

.map-story-band__stat::before {
  content: "";
  position: absolute;
  top: 16px;
  right: 16px;
  width: 38px;
  height: 38px;
  border-radius: 16px;
  border: 1px solid rgba(176, 246, 255, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 12px 24px rgba(2, 15, 42, 0.16);
}

.map-story-band__stat::after {
  content: "";
  position: absolute;
  top: 26px;
  right: 26px;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  z-index: 1;
}

.map-story-band__stat:nth-child(1)::before {
  background:
    linear-gradient(180deg, rgba(112, 241, 255, 0.18), rgba(9, 49, 102, 0.82)),
    rgba(8, 26, 58, 0.9);
}

.map-story-band__stat:nth-child(1)::after {
  background:
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.96) 0 22%, transparent 24%),
    conic-gradient(from 90deg, rgba(89, 235, 255, 0.98), rgba(20, 125, 219, 0.84));
}

.map-story-band__stat:nth-child(2)::before {
  background:
    linear-gradient(180deg, rgba(118, 245, 226, 0.2), rgba(7, 74, 96, 0.82)),
    rgba(8, 26, 58, 0.9);
}

.map-story-band__stat:nth-child(2)::after {
  background:
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.96) 0 18%, transparent 20%),
    radial-gradient(circle at 50% 50%, rgba(118, 245, 226, 0.98) 0 56%, transparent 58%);
  box-shadow: 0 0 0 5px rgba(118, 245, 226, 0.14);
}

.map-story-band__stat:nth-child(3)::before {
  background:
    linear-gradient(180deg, rgba(132, 208, 255, 0.2), rgba(29, 78, 162, 0.82)),
    rgba(8, 26, 58, 0.9);
}

.map-story-band__stat:nth-child(3)::after {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(196, 235, 255, 0.94));
  clip-path: polygon(50% 0%, 100% 38%, 80% 100%, 20% 100%, 0% 38%);
}

@keyframes map-card-drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(-10px, -8px, 0) scale(1.04);
  }
}

.eco-map {
  min-height: 620px;
  border-radius: 22px;
  overflow: hidden;
}

.map-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.side-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.observation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.observation-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid rgba(132, 230, 255, 0.14);
  border-radius: 20px;
  background:
    linear-gradient(160deg, rgba(91, 233, 255, 0.08), rgba(6, 26, 62, 0.78)),
    rgba(7, 28, 64, 0.82);
  color: var(--gsmv-text);
  text-align: left;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.observation-item__topline,
.observation-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.observation-item__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid rgba(156, 241, 255, 0.18);
  background: rgba(90, 232, 255, 0.12);
  color: #8befff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.observation-item__time {
  color: rgba(206, 235, 247, 0.76);
  font-size: 12px;
}

.observation-item strong {
  color: #f1fcff;
  font-size: 18px;
  line-height: 1.25;
}

.observation-item span {
  color: rgba(203, 234, 247, 0.8);
  font-size: 13px;
}

.observation-item__footer {
  margin-top: 2px;
  padding-top: 8px;
  border-top: 1px solid rgba(150, 232, 255, 0.08);
}

.detail-hero {
  position: relative;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid rgba(152, 234, 255, 0.16);
  border-radius: 22px;
  background:
    radial-gradient(circle at 14% 0%, rgba(90, 232, 255, 0.14), transparent 34%),
    linear-gradient(145deg, rgba(10, 41, 94, 0.94), rgba(5, 21, 58, 0.98));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 18px 34px rgba(2, 15, 42, 0.18);
  overflow: hidden;
}

.detail-hero::after {
  content: "";
  position: absolute;
  right: -26px;
  top: -28px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(112, 241, 255, 0.16), transparent 68%);
  pointer-events: none;
}

.detail-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(90, 232, 255, 0.1);
  border: 1px solid rgba(156, 241, 255, 0.18);
  color: #82ecff;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.detail-hero h3 {
  margin: 12px 0 6px;
  color: #f6feff;
  font-size: 24px;
  line-height: 1.15;
}

.detail-hero p {
  margin: 0;
  color: rgba(221, 244, 255, 0.78);
  line-height: 1.7;
}

.detail-hero__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-content: flex-start;
  gap: 8px;
}

.detail-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.detail-stat-card {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(150, 232, 255, 0.12);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.03)),
    rgba(5, 24, 60, 0.76);
}

.detail-stat-card span {
  display: block;
  color: rgba(197, 232, 246, 0.72);
  font-size: 12px;
}

.detail-stat-card strong {
  display: block;
  margin-top: 8px;
  color: #f2fdff;
  font-size: 22px;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.detail-species-table {
  padding: 10px;
  border-radius: 20px;
  border: 1px solid rgba(150, 232, 255, 0.1);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02)),
    rgba(4, 20, 52, 0.58);
}

.observation-item:hover,
.observation-item.is-active {
  transform: translateY(-2px);
  border-color: rgba(126, 237, 255, 0.28);
  background:
    linear-gradient(145deg, rgba(108, 244, 255, 0.18), rgba(14, 70, 149, 0.34)),
    rgba(11, 36, 82, 0.9);
  box-shadow:
    0 14px 28px rgba(0, 10, 34, 0.2),
    0 0 0 1px rgba(115, 238, 255, 0.08) inset;
}

.observation-item.is-active {
  border-color: rgba(155, 244, 255, 0.42);
  box-shadow:
    0 18px 34px rgba(0, 10, 34, 0.24),
    0 0 0 1px rgba(155, 244, 255, 0.14) inset,
    0 0 26px rgba(79, 216, 255, 0.12);
}

.observation-item.is-active strong {
  color: #ffffff;
}

.observation-item.is-active span {
  color: rgba(227, 249, 255, 0.88);
}

@media (max-width: 1080px) {
  .map-story-band,
  .map-grid {
    grid-template-columns: 1fr;
  }

  .eco-map {
    min-height: 460px;
  }

  .detail-hero,
  .detail-stat-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .map-story-band__stats {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .reveal-section,
  .reveal-section.is-visible,
  .map-story-band__feature,
  .map-story-band__stat,
  .map-story-band__feature::after,
  .map-story-band__stat::after {
    animation: none !important;
    transition: none !important;
    transform: none !important;
    filter: none !important;
    opacity: 1 !important;
  }
}
</style>
