import type { ObservationEnvironment } from '@/types/gsmv'

const NUMBER_KEYS = ['waterTemperature', 'salinity', 'ph', 'dissolvedOxygen', 'transparency', 'depthMeters'] as const

export function createEmptyObservationEnvironment(): ObservationEnvironment {
  return {
    waterTemperature: null,
    salinity: null,
    ph: null,
    dissolvedOxygen: null,
    transparency: null,
    depthMeters: null,
    weather: '',
    seaState: '',
  }
}

export function parseObservationEnvironment(envJson?: string): ObservationEnvironment {
  if (!envJson) {
    return createEmptyObservationEnvironment()
  }

  try {
    const parsed = JSON.parse(envJson) as Record<string, unknown>
    const next = createEmptyObservationEnvironment()

    NUMBER_KEYS.forEach((key) => {
      const raw = parsed[key]
      next[key] = typeof raw === 'number' ? raw : raw == null ? null : Number(raw)
      if (Number.isNaN(next[key])) {
        next[key] = null
      }
    })

    next.weather = typeof parsed.weather === 'string' ? parsed.weather : ''
    next.seaState = typeof parsed.seaState === 'string' ? parsed.seaState : ''
    return next
  } catch {
    return createEmptyObservationEnvironment()
  }
}

export function stringifyObservationEnvironment(env: ObservationEnvironment): string | undefined {
  const payload: Record<string, number | string> = {}

  NUMBER_KEYS.forEach((key) => {
    const value = env[key]
    if (typeof value === 'number' && Number.isFinite(value)) {
      payload[key] = value
    }
  })

  if (env.weather?.trim()) {
    payload.weather = env.weather.trim()
  }
  if (env.seaState?.trim()) {
    payload.seaState = env.seaState.trim()
  }

  return Object.keys(payload).length ? JSON.stringify(payload) : undefined
}

export function describeObservationEnvironment(envJson?: string): string {
  const env = parseObservationEnvironment(envJson)
  const parts: string[] = []

  if (typeof env.waterTemperature === 'number') {
    parts.push(`水温 ${env.waterTemperature}°C`)
  }
  if (typeof env.salinity === 'number') {
    parts.push(`盐度 ${env.salinity}‰`)
  }
  if (typeof env.ph === 'number') {
    parts.push(`pH ${env.ph}`)
  }
  if (typeof env.dissolvedOxygen === 'number') {
    parts.push(`溶解氧 ${env.dissolvedOxygen} mg/L`)
  }
  if (typeof env.transparency === 'number') {
    parts.push(`透明度 ${env.transparency} m`)
  }
  if (typeof env.depthMeters === 'number') {
    parts.push(`水深 ${env.depthMeters} m`)
  }
  if (env.weather?.trim()) {
    parts.push(`天气 ${env.weather.trim()}`)
  }
  if (env.seaState?.trim()) {
    parts.push(`海况 ${env.seaState.trim()}`)
  }

  return parts.length ? parts.join(' / ') : '-'
}

export function observationEnvironmentEntries(envJson?: string) {
  const env = parseObservationEnvironment(envJson)

  return [
    { label: '水温', value: typeof env.waterTemperature === 'number' ? `${env.waterTemperature} °C` : '-' },
    { label: '盐度', value: typeof env.salinity === 'number' ? `${env.salinity} ‰` : '-' },
    { label: 'pH', value: typeof env.ph === 'number' ? String(env.ph) : '-' },
    { label: '溶解氧', value: typeof env.dissolvedOxygen === 'number' ? `${env.dissolvedOxygen} mg/L` : '-' },
    { label: '透明度', value: typeof env.transparency === 'number' ? `${env.transparency} m` : '-' },
    { label: '水深', value: typeof env.depthMeters === 'number' ? `${env.depthMeters} m` : '-' },
    { label: '天气', value: env.weather?.trim() || '-' },
    { label: '海况', value: env.seaState?.trim() || '-' },
  ]
}
