<template>
  <div ref="mapRef" class="leaflet-picker" />
</template>

<script setup lang="ts">
import L from 'leaflet'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'
import { addPreferredTileLayer, toMapDisplayPoint, toStoredPoint } from '@/utils/mapProvider'
import { createMapMarkerIcon } from '@/utils/mapMarkerTheme'

const props = defineProps<{
  lat?: number | null
  lng?: number | null
}>()

const emit = defineEmits<{
  (event: 'update', payload: { lat: number; lng: number }): void
}>()

const mapRef = ref<HTMLDivElement>()
let map: L.Map | null = null
let marker: L.Marker | null = null
let resizeObserver: ResizeObserver | null = null
let invalidateTimer: number | null = null

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

function syncMarker(lat: number, lng: number) {
  if (!map) {
    return
  }
  const [displayLat, displayLng] = toMapDisplayPoint(lat, lng)
  const latLng = L.latLng(displayLat, displayLng)
  if (!marker) {
    marker = L.marker(latLng, {
      icon: createMapMarkerIcon('P', {
        active: true,
        compact: true,
        tone: 'emerald',
      }),
    }).addTo(map)
  } else {
    marker.setLatLng(latLng)
  }
  map.panTo(latLng, { animate: false })
}

onMounted(() => {
  if (!mapRef.value) {
    return
  }
  const initialCenterRaw: [number, number] =
    props.lat != null && props.lng != null ? [props.lat, props.lng] : ZHANJIANG_OFFSHORE_CENTER
  const initialCenter = toMapDisplayPoint(initialCenterRaw[0], initialCenterRaw[1])

  map = L.map(mapRef.value, { zoomControl: true }).setView(initialCenter, 7)
  addPreferredTileLayer(map)
  map.on('click', (event) => {
    const [storedLat, storedLng] = toStoredPoint(event.latlng.lat, event.latlng.lng)
    const payload = {
      lat: Number(storedLat.toFixed(6)),
      lng: Number(storedLng.toFixed(6)),
    }
    syncMarker(payload.lat, payload.lng)
    emit('update', payload)
  })
  if (props.lat != null && props.lng != null) {
    syncMarker(props.lat, props.lng)
  }

  nextTick(() => {
    window.setTimeout(() => {
      scheduleInvalidateMap(120)
    }, 250)
  })

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      scheduleInvalidateMap(60)
    })
    resizeObserver.observe(mapRef.value)
  }
})

watch(
  () => [props.lat, props.lng],
  ([lat, lng]) => {
    if (lat != null && lng != null) {
      syncMarker(lat, lng)
    }
  },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  if (invalidateTimer != null) {
    window.clearTimeout(invalidateTimer)
    invalidateTimer = null
  }
  map?.remove()
  map = null
  marker = null
})
</script>

<style scoped>
.leaflet-picker {
  width: 100%;
  min-height: 320px;
}
</style>
