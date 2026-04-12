<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <h2>生态地图</h2>
        <p>在地图上查看观测点分布，按生态系统筛选，并联动查看每条观测记录的详细信息和关联物种。</p>
      </div>
      <el-space wrap>
        <el-tag type="success" size="large">{{ pointCount }} 个位置点</el-tag>
        <el-tag size="large">{{ ecosystemCount }} 个生态系统</el-tag>
      </el-space>
    </section>

    <div class="map-grid">
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
                <strong>{{ item.locationName || item.ecosystemName }}</strong>
                <span>{{ item.ecosystemName }}</span>
                <span>{{ item.observedAt }}</span>
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
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="生态系统">{{ selectedDetail.ecosystemName }}</el-descriptions-item>
              <el-descriptions-item label="观察者">{{ selectedDetail.observerName }}</el-descriptions-item>
              <el-descriptions-item label="观测时间">{{ selectedDetail.observedAt }}</el-descriptions-item>
              <el-descriptions-item label="地点说明">{{ selectedDetail.locationName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="坐标">
                {{ selectedDetail.locationLat }}, {{ selectedDetail.locationLng }}
              </el-descriptions-item>
              <el-descriptions-item label="环境参数">{{ selectedDetail.envJson || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备注">{{ selectedDetail.note || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider>关联物种</el-divider>

            <el-table :data="selectedDetail.speciesItems" size="small" max-height="220">
              <el-table-column prop="scientificName" label="学名" min-width="160" />
              <el-table-column prop="chineseName" label="中文名" min-width="120" />
              <el-table-column prop="countEstimated" label="数量" min-width="80" />
            </el-table>
          </template>
          <el-empty v-else description="点击地图点位或右侧列表查看详情" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import L from 'leaflet'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink } from 'vue-router'
import { fetchAllEcosystems } from '@/api/ecosystems'
import { fetchObservationDetail, fetchObservations } from '@/api/observations'
import { ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'
import { listenDataChanged } from '@/utils/dataSync'
import type { Ecosystem, ObservationDetailView, ObservationView } from '@/types/gsmv'

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

let map: L.Map | null = null
let markerLayer: L.LayerGroup | null = null
let stopDataSyncListener: (() => void) | null = null
const markerMap = new Map<number, L.Marker>()
const defaultCenter = ZHANJIANG_OFFSHORE_CENTER

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function ensureMap() {
  if (map || !mapRef.value) {
    return
  }
  map = L.map(mapRef.value, { zoomControl: true }).setView(defaultCenter, 7)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(map)
  markerLayer = L.layerGroup().addTo(map)
  renderMarkers()
}

function renderMarkers() {
  if (!map || !markerLayer) {
    return
  }

  markerLayer.clearLayers()
  markerMap.clear()

  if (!observations.value.length) {
    map.setView(defaultCenter, 7)
    return
  }

  const bounds: [number, number][] = []

  observations.value.forEach((item) => {
    const point: [number, number] = [item.locationLat, item.locationLng]
    const popupHtml = `
      <div style="min-width: 180px">
        <strong>${escapeHtml(item.ecosystemName)}</strong><br/>
        <span>${escapeHtml(item.locationName || '未命名点位')}</span><br/>
        <small>${escapeHtml(item.observedAt)}</small>
      </div>
    `
    const marker = L.marker(point)
    marker.bindPopup(popupHtml)
    marker.on('click', () => {
      void focusObservation(item.id, false)
    })
    marker.addTo(markerLayer!)
    markerMap.set(item.id, marker)
    bounds.push(point)
  })

  map.fitBounds(bounds, { padding: [24, 24], maxZoom: 10 })
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
    const marker = markerMap.get(id)
    if (marker && map) {
      map.setView(marker.getLatLng(), Math.max(map.getZoom(), 9))
      if (openPopup) {
        marker.openPopup()
      }
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

function handlePageFocus() {
  void refreshMapData()
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void refreshMapData()
  }
}

onMounted(async () => {
  try {
    stopDataSyncListener = listenDataChanged(() => {
      void refreshMapData()
    })
    window.addEventListener('focus', handlePageFocus)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    await nextTick()
    ensureMap()
    await refreshMapData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生态地图初始化失败')
  }
})

onBeforeUnmount(() => {
  stopDataSyncListener?.()
  stopDataSyncListener = null
  window.removeEventListener('focus', handlePageFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  map?.remove()
  map = null
  markerLayer = null
  markerMap.clear()
})
</script>

<style scoped>
.map-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.85fr);
  gap: 18px;
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

.observation-item strong {
  color: #f1fcff;
  font-size: 18px;
}

.observation-item span {
  color: rgba(203, 234, 247, 0.8);
  font-size: 13px;
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
  .map-grid {
    grid-template-columns: 1fr;
  }

  .eco-map {
    min-height: 460px;
  }
}
</style>
