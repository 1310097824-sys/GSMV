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
import 'leaflet.markercluster'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'
import { addPreferredTileLayer, toMapDisplayPoint } from '@/utils/mapProvider'
import { buildMapPopupCard, createMapMarkerIcon } from '@/utils/mapMarkerTheme'

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
let markerLayer: L.MarkerClusterGroup | null = null
let resizeObserver: ResizeObserver | null = null
let invalidateTimer: number | null = null

function createClusterIcon(cluster: L.MarkerCluster) {
  const count = cluster.getChildCount()
  const size = count >= 30 ? 52 : count >= 10 ? 46 : 40
  return L.divIcon({
    html: `<span>${count}</span>`,
    className: 'gsmv-marker-cluster',
    iconSize: L.point(size, size),
  })
}

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

function pointsSignature() {
  return props.points
    .map((point) => {
      const lines = point.lines?.join('|') || ''
      return `${point.id}:${point.lat}:${point.lng}:${point.title}:${point.subtitle || ''}:${lines}`
    })
    .join(';')
}

function getMarkerTone(index: number) {
  const tones = ['aqua', 'emerald', 'violet'] as const
  return tones[index % tones.length]
}

function ensureMap() {
  if (map || !mapRef.value) {
    return
  }

  map = L.map(mapRef.value, { zoomControl: true }).setView(
    toMapDisplayPoint(ZHANJIANG_OFFSHORE_CENTER[0], ZHANJIANG_OFFSHORE_CENTER[1]),
    3,
  )
  addPreferredTileLayer(map)
  markerLayer = L.markerClusterGroup({
    showCoverageOnHover: false,
    spiderfyOnMaxZoom: true,
    zoomToBoundsOnClick: true,
    disableClusteringAtZoom: 8,
    maxClusterRadius: 42,
    iconCreateFunction: createClusterIcon,
  }).addTo(map)
  scheduleInvalidateMap(120)
}

function renderMarkers() {
  ensureMap()
  if (!map || !markerLayer) {
    return
  }

  const clusterLayer = markerLayer
  clusterLayer.clearLayers()

  if (!props.points.length) {
    map.setView(toMapDisplayPoint(ZHANJIANG_OFFSHORE_CENTER[0], ZHANJIANG_OFFSHORE_CENTER[1]), 3)
    scheduleInvalidateMap()
    return
  }

  const bounds: [number, number][] = []
  props.points.forEach((point, index) => {
    const displayPoint = toMapDisplayPoint(point.lat, point.lng)
    const marker = L.marker(displayPoint, {
      icon: createMapMarkerIcon(point.title || 'M', {
        compact: true,
        tone: getMarkerTone(index),
      }),
    })
    marker.bindPopup(
      buildMapPopupCard({
        eyebrow: 'Report Map',
        title: point.title,
        subtitle: point.subtitle,
        lines: point.lines || [],
      }),
      { className: 'gsmv-map-popup' },
    )
    marker.addTo(clusterLayer)
    bounds.push(displayPoint)
  })

  if (bounds.length === 1) {
    map.setView(bounds[0], 7, { animate: false })
  } else {
    map.fitBounds(bounds, { padding: [24, 24], maxZoom: 7 })
  }
  scheduleInvalidateMap()
}

onMounted(async () => {
  await nextTick()
  ensureMap()
  renderMarkers()
  if (mapRef.value) {
    resizeObserver = new ResizeObserver(() => {
      scheduleInvalidateMap(60)
    })
    resizeObserver.observe(mapRef.value)
  }
})

watch(
  () => pointsSignature(),
  () => {
    renderMarkers()
  },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  if (invalidateTimer != null) {
    window.clearTimeout(invalidateTimer)
    invalidateTimer = null
  }
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
