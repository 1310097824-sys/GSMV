import L from 'leaflet'

type MarkerTone = 'aqua' | 'emerald' | 'violet'

interface MarkerIconOptions {
  active?: boolean
  compact?: boolean
  tone?: MarkerTone
}

interface PopupChip {
  label: string
  value: string
}

interface PopupCardOptions {
  eyebrow?: string
  title: string
  subtitle?: string
  meta?: string
  chips?: PopupChip[]
  lines?: string[]
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function getMarkerInitial(label: string) {
  const first = Array.from(label.trim())[0] ?? '海'
  return escapeHtml(first.toUpperCase())
}

export function createMapMarkerIcon(label: string, options: MarkerIconOptions = {}) {
  const classNames = ['gsmv-map-marker']
  if (options.active) {
    classNames.push('gsmv-map-marker--active')
  }
  if (options.compact) {
    classNames.push('gsmv-map-marker--compact')
  }
  classNames.push(`gsmv-map-marker--${options.tone ?? 'aqua'}`)

  const width = options.compact ? 38 : 44
  const height = options.compact ? 50 : 58
  const anchorX = Math.round(width / 2)
  const anchorY = height - 4

  return L.divIcon({
    className: 'gsmv-map-marker-icon',
    html: `
      <div class="${classNames.join(' ')}">
        <span class="gsmv-map-marker__halo"></span>
        <span class="gsmv-map-marker__label">${getMarkerInitial(label)}</span>
        <span class="gsmv-map-marker__body">
          <span class="gsmv-map-marker__core"></span>
        </span>
        <span class="gsmv-map-marker__tail"></span>
      </div>
    `,
    iconSize: L.point(width, height),
    iconAnchor: L.point(anchorX, anchorY),
    popupAnchor: L.point(0, -anchorY + 10),
  })
}

function renderPopupSection(lines: string[]) {
  if (!lines.length) {
    return ''
  }

  return `
    <div class="gsmv-map-popup-card__lines">
      ${lines.map((line) => `<div class="gsmv-map-popup-card__line">${escapeHtml(line)}</div>`).join('')}
    </div>
  `
}

function renderPopupChips(chips: PopupChip[]) {
  if (!chips.length) {
    return ''
  }

  return `
    <div class="gsmv-map-popup-card__chips">
      ${chips
        .map(
          (chip) => `
            <div class="gsmv-map-popup-card__chip">
              <span class="gsmv-map-popup-card__chip-label">${escapeHtml(chip.label)}</span>
              <strong class="gsmv-map-popup-card__chip-value">${escapeHtml(chip.value)}</strong>
            </div>
          `,
        )
        .join('')}
    </div>
  `
}

export function buildMapPopupCard(options: PopupCardOptions) {
  const chips = (options.chips || []).filter((chip) => chip.label && chip.value)
  const lines = (options.lines || []).filter(Boolean)

  return `
    <div class="gsmv-map-popup-card">
      ${options.eyebrow ? `<div class="gsmv-map-popup-card__eyebrow">${escapeHtml(options.eyebrow)}</div>` : ''}
      <div class="gsmv-map-popup-card__title">${escapeHtml(options.title)}</div>
      ${options.subtitle ? `<div class="gsmv-map-popup-card__subtitle">${escapeHtml(options.subtitle)}</div>` : ''}
      ${options.meta ? `<div class="gsmv-map-popup-card__meta">${escapeHtml(options.meta)}</div>` : ''}
      ${renderPopupChips(chips)}
      ${renderPopupSection(lines)}
    </div>
  `
}
