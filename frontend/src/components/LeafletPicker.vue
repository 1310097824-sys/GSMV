<template>
  <div ref="mapRef" class="leaflet-picker" />
</template>

<script setup lang="ts">
import L from 'leaflet'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ZHANJIANG_OFFSHORE_CENTER } from '@/constants/ecosystem'

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

function refreshMap() {
  if (!map) {
    return
  }

  map.invalidateSize(false)

  if (marker) {
    map.panTo(marker.getLatLng(), { animate: false })
    return
  }

  const fallbackCenter: [number, number] =
    props.lat != null && props.lng != null ? [props.lat, props.lng] : ZHANJIANG_OFFSHORE_CENTER
  map.setView(fallbackCenter, map.getZoom(), { animate: false })
}

function syncMarker(lat: number, lng: number) {
  if (!map) {
    return
  }
  const latLng = L.latLng(lat, lng)
  if (!marker) {
    marker = L.marker(latLng).addTo(map)
  } else {
    marker.setLatLng(latLng)
  }
  map.panTo(latLng)
}

onMounted(() => {
  if (!mapRef.value) {
    return
  }
  const initialCenter: [number, number] =
    props.lat != null && props.lng != null ? [props.lat, props.lng] : ZHANJIANG_OFFSHORE_CENTER

  map = L.map(mapRef.value, { zoomControl: true }).setView(initialCenter, 7)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(map)
  map.on('click', (event) => {
    const payload = {
      lat: Number(event.latlng.lat.toFixed(6)),
      lng: Number(event.latlng.lng.toFixed(6)),
    }
    syncMarker(payload.lat, payload.lng)
    emit('update', payload)
  })
  if (props.lat != null && props.lng != null) {
    syncMarker(props.lat, props.lng)
  }

  nextTick(() => {
    window.setTimeout(() => {
      refreshMap()
    }, 250)
  })

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      refreshMap()
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
