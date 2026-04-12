export type DataChangeType = 'ecosystem' | 'observation' | 'user' | 'species' | 'aiReview'

export interface DataChangeDetail {
  type: DataChangeType
  at: number
}

const DATA_SYNC_EVENT = 'gsmv:data-changed'
const DATA_SYNC_STORAGE_KEY = 'gsmv:data-sync'

export function notifyDataChanged(type: DataChangeType) {
  const detail: DataChangeDetail = {
    type,
    at: Date.now(),
  }

  window.dispatchEvent(new CustomEvent<DataChangeDetail>(DATA_SYNC_EVENT, { detail }))
  localStorage.setItem(DATA_SYNC_STORAGE_KEY, JSON.stringify(detail))
}

export function listenDataChanged(listener: (detail: DataChangeDetail) => void) {
  const handleCustomEvent = (event: Event) => {
    listener((event as CustomEvent<DataChangeDetail>).detail)
  }

  const handleStorage = (event: StorageEvent) => {
    if (event.key !== DATA_SYNC_STORAGE_KEY || !event.newValue) {
      return
    }

    try {
      listener(JSON.parse(event.newValue) as DataChangeDetail)
    } catch {
      return
    }
  }

  window.addEventListener(DATA_SYNC_EVENT, handleCustomEvent as EventListener)
  window.addEventListener('storage', handleStorage)

  return () => {
    window.removeEventListener(DATA_SYNC_EVENT, handleCustomEvent as EventListener)
    window.removeEventListener('storage', handleStorage)
  }
}
