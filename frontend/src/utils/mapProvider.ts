import L from 'leaflet'

const EARTH_RADIUS = 6378245.0
const EE = 0.00669342162296594323
const PI = Math.PI

type LatLngTuple = [number, number]

function transformLat(x: number, y: number) {
  let ret =
    -100.0 +
    2.0 * x +
    3.0 * y +
    0.2 * y * y +
    0.1 * x * y +
    0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(x: number, y: number) {
  let ret =
    300.0 +
    x +
    2.0 * y +
    0.1 * x * x +
    0.1 * x * y +
    0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

function isInChina(lat: number, lng: number) {
  return lng >= 72.004 && lng <= 137.8347 && lat >= 0.8293 && lat <= 55.8271
}

function wgs84ToGcj02(lat: number, lng: number): LatLngTuple {
  if (!isInChina(lat, lng)) {
    return [lat, lng]
  }

  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((EARTH_RADIUS * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / ((EARTH_RADIUS / sqrtMagic) * Math.cos(radLat) * PI)
  return [lat + dLat, lng + dLng]
}

function gcj02ToWgs84(lat: number, lng: number): LatLngTuple {
  if (!isInChina(lat, lng)) {
    return [lat, lng]
  }

  const [mgLat, mgLng] = wgs84ToGcj02(lat, lng)
  return [lat * 2 - mgLat, lng * 2 - mgLng]
}

export function toMapDisplayPoint(lat: number, lng: number): LatLngTuple {
  return wgs84ToGcj02(lat, lng)
}

export function toStoredPoint(lat: number, lng: number): LatLngTuple {
  return gcj02ToWgs84(lat, lng)
}

function addGlobalImageryLayer(map: L.Map) {
  L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    attribution: '&copy; Esri',
    maxNativeZoom: 19,
    maxZoom: 19,
  }).addTo(map)

  L.tileLayer(
    'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
    {
      attribution: '&copy; Esri',
      maxNativeZoom: 19,
      maxZoom: 19,
      opacity: 0.82,
      pane: 'overlayPane',
    },
  ).addTo(map)
}

function createAmapVectorLayer() {
  return L.tileLayer('https://wprd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scl=1&style=7&x={x}&y={y}&z={z}', {
    attribution: '&copy; Gaode Maps',
    subdomains: ['1', '2', '3', '4'],
    maxNativeZoom: 18,
    maxZoom: 18,
  })
}

function shouldUseAmapChinaLayer(map: L.Map) {
  const center = map.getCenter()
  return map.getZoom() >= 5 && isInChina(center.lat, center.lng)
}

function addHybridChinaGlobalLayer(map: L.Map) {
  addGlobalImageryLayer(map)

  const chinaLayer = createAmapVectorLayer()
  const syncChinaLayer = () => {
    const shouldShow = shouldUseAmapChinaLayer(map)
    const isShown = map.hasLayer(chinaLayer)

    if (shouldShow && !isShown) {
      chinaLayer.addTo(map)
      return
    }

    if (!shouldShow && isShown) {
      map.removeLayer(chinaLayer)
    }
  }

  map.on('moveend zoomend', syncChinaLayer)
  syncChinaLayer()
}

export function addPreferredTileLayer(map: L.Map) {
  const preferredProvider = String(import.meta.env.VITE_MAP_PROVIDER || 'amap').toLowerCase()
  const tiandituKey = String(import.meta.env.VITE_TIANDITU_KEY || '').trim()

  if (preferredProvider === 'tianditu' && tiandituKey) {
    L.tileLayer(
      `https://t0.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${tiandituKey}`,
      {
        attribution: '&copy; Tianditu',
        maxZoom: 18,
      },
    ).addTo(map)

    L.tileLayer(
      `https://t0.tianditu.gov.cn/cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${tiandituKey}`,
      {
        attribution: '&copy; Tianditu',
        maxZoom: 18,
        pane: 'overlayPane',
      },
    ).addTo(map)

    return
  }

  if (preferredProvider === 'esri' || preferredProvider === 'global') {
    addGlobalImageryLayer(map)
    return
  }

  if (preferredProvider === 'amap-vector') {
    createAmapVectorLayer().addTo(map)
    return
  }

  addHybridChinaGlobalLayer(map)
}
