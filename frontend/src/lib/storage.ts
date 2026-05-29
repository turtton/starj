import { signal } from '@preact/signals'
import { api } from './api'

export interface StorageObject {
  id: string
  filename: string
  contentType: string
  size: number
  ownerId?: number
  createdAt: string
}

interface StorageListResponse {
  items: StorageObject[]
  nextCursor: string | null
}

const PAGE_SIZE = 20

export const files = signal<StorageObject[]>([])
export const nextCursor = signal<string | null>(null)
export const loading = signal(false)
export const initialized = signal(false)

export function downloadUrl(id: string): string {
  return `/api/storage/${encodeURIComponent(id)}/content`
}

export async function loadMore(): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const params = new URLSearchParams({ size: String(PAGE_SIZE) })
    if (nextCursor.value) params.set('cursor', nextCursor.value)
    const page = await api.get<StorageListResponse>(`/storage?${params.toString()}`)
    files.value = [...files.value, ...page.items]
    nextCursor.value = page.nextCursor
  } finally {
    loading.value = false
  }
}

export async function refresh(): Promise<void> {
  files.value = []
  nextCursor.value = null
  initialized.value = true
  await loadMore()
}

export async function upload(file: File): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  const created = await api.postForm<StorageObject>('/storage', form)
  files.value = [created, ...files.value]
}

export async function remove(id: string): Promise<void> {
  await api.delete(`/storage/${encodeURIComponent(id)}`)
  files.value = files.value.filter((f) => f.id !== id)
}
