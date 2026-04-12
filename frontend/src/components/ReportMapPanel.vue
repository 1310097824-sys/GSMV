<template>
  <div class="report-map-panel">
    <div class="report-map-panel__glow report-map-panel__glow--one" />
    <div class="report-map-panel__glow report-map-panel__glow--two" />
    <div ref="mapRef" class="report-map-panel__map" :style="{ minHeight: `${height}px` }" />
    <div v-if="!points.length" class="report-map-panel__empty">
      <el-empty :description="emptyDescription" />
    </div>
  </div>
</template>

<script setup lang="ts">
import L from 'leaflet'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'

interface ReportMapMarker {
  id: string | number
  lat: number
  lng: number
  title: string
  subtitle?: string
  lines?: string[]
}

const props = withDefaults(
  defineProps<{
    points: ReportMapMarker[]
    emptyDescription?: string
    height?: number
  }>(),
  {
    emptyDescription: '当前没有可展示的地图点位',
    height: 340,
  },
)

const mapRef = ref<HTMLDivElement>()

let map: L.Map | null = null
let markerLayer: L.LayerGroup | null = null
let resizeObserver: ResizeObserver | null = null

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function buildPopup(point: ReportMapMarker) {
  const subtitle = point.subtitle ? `<div>${escapeHtml(point.subtitle)}</div>` : ''
  const lines = (point.lines || [])
    .filter(Boolean)
    .map((line) => `<div>${escapeHtml(line)}</div>`)
    .join('')

  return `
    <div style="min-width: 200px">
      <strong>${escapeHtml(point.title)}</strong>
      ${subtitle}
      ${lines}
    </div>
  `
}

function ensureMap() {
  if (map || !mapRef.value) {
    return
  }

  map = L.map(mapRef.value, { zoomControl: true }).setView(ZHANJIANG_OFFSHORE_CENTER, 3)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(map)
  markerLayer = L.layerGroup().addTo(map)
}

function renderMarkers() {
  ensureMap()
  if (!map || !markerLayer) {
    return
  }

  markerLayer.clearLayers()

  if (!props.points.length) {
    map.setView(ZHANJIANG_OFFSHORE_CENTER, 3)
    window.setTimeout(() => map?.invalidateSize(), 60)
    return
  }

  const bounds: [number, number][] = []
  props.points.forEach((point) => {
    const marker = L.marker([point.lat, point.lng])
    marker.bindPopup(buildPopup(point))
    marker.addTo(markerLayer!)
    bounds.push([point.lat, point.lng])
  })

  map.fitBounds(bounds, { padding: [24, 24], maxZoom: 8 })
  window.setTimeout(() => map?.invalidateSize(), 60)
}

onMounted(async () => {
  await nextTick()
  ensureMap()
  renderMarkers()
  if (mapRef.value) {
    resizeObserver = new ResizeObserver(() => {
      map?.invalidateSize()
    })
    resizeObserver.observe(mapRef.value)
  }
})

watch(
  () => props.points,
  () => {
    renderMarkers()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  markerLayer = null
  map?.remove()
  map = null
})
</script>

<style scoped>
.report-map-panel {
  position: relative;
  border-radius: 22px;
  overflow: hidden;
}

.report-map-panel__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
}

.report-map-panel__glow--one {
  top: -42px;
  right: -16px;
  width: 140px;
  height: 140px;
  background: radial-gradient(circle, rgba(97, 228, 255, 0.16), transparent 72%);
}

.report-map-panel__glow--two {
  bottom: -54px;
  left: -18px;
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(124, 245, 220, 0.1), transparent 74%);
}

.report-map-panel__map {
  position: relative;
  z-index: 1;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(174, 244, 255, 0.14);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 18px 40px rgba(3, 15, 42, 0.18);
}

.report-map-panel__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(10, 31, 72, 0.58), rgba(6, 21, 52, 0.84)),
    radial-gradient(circle at 50% 10%, rgba(135, 239, 255, 0.14), transparent 36%);
  border-radius: 20px;
}
</style>
