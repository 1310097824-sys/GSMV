import 'leaflet'

declare module 'leaflet' {
  interface MarkerCluster extends FeatureGroup {
    getAllChildMarkers(): Marker[]
    getChildCount(): number
  }

  interface MarkerClusterGroupOptions extends LayerOptions {
    showCoverageOnHover?: boolean
    spiderfyOnMaxZoom?: boolean
    zoomToBoundsOnClick?: boolean
    disableClusteringAtZoom?: number
    maxClusterRadius?: number
    iconCreateFunction?: (cluster: MarkerCluster) => DivIcon
  }

  interface MarkerClusterGroup extends FeatureGroup {
    clearLayers(): this
    addLayer(layer: Layer): this
    getBounds(): LatLngBounds
    zoomToShowLayer(layer: Layer, callback?: () => void): void
  }

  function markerClusterGroup(options?: MarkerClusterGroupOptions): MarkerClusterGroup
}
