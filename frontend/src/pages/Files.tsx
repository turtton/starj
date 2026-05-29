import { useEffect, useRef, useState } from 'preact/hooks'
import {
  downloadUrl,
  files,
  initialized,
  loadMore,
  loading,
  nextCursor,
  refresh,
  remove,
  upload,
  type StorageObject,
} from '../lib/storage'

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(1)} ${units[i]}`
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export function Files() {
  const fileInput = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (!initialized.value) {
      refresh().catch((err) => setError(err instanceof Error ? err.message : '読み込みに失敗しました'))
    }
  }, [])

  async function onUpload(e: Event) {
    const input = e.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    setError(null)
    setUploading(true)
    try {
      await upload(file)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'アップロードに失敗しました')
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  async function onDelete(item: StorageObject) {
    if (!confirm(`「${item.filename}」を削除しますか?`)) return
    setError(null)
    try {
      await remove(item.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : '削除に失敗しました')
    }
  }

  const items = files.value

  return (
    <div>
      <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-bold">ファイル</h1>
        <label class="cursor-pointer rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700">
          {uploading ? 'アップロード中...' : 'アップロード'}
          <input
            ref={fileInput}
            type="file"
            class="hidden"
            disabled={uploading}
            onChange={onUpload}
          />
        </label>
      </div>

      {error && <p class="mb-4 text-sm text-red-600">{error}</p>}

      {items.length === 0 && initialized.value && !loading.value ? (
        <p class="py-16 text-center text-slate-500">ファイルがありません</p>
      ) : (
        <ul class="divide-y divide-slate-200 overflow-hidden rounded-md border border-slate-200 bg-white">
          {items.map((item) => (
            <li key={item.id} class="flex items-center justify-between gap-4 px-4 py-3">
              <div class="min-w-0">
                <p class="truncate font-medium text-slate-900">{item.filename}</p>
                <p class="text-xs text-slate-500">
                  {formatBytes(item.size)} · {formatDate(item.createdAt)}
                </p>
              </div>
              <div class="flex shrink-0 items-center gap-2 text-sm">
                <a
                  href={downloadUrl(item.id)}
                  class="rounded-md border border-slate-300 px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100"
                >
                  ダウンロード
                </a>
                <button
                  type="button"
                  onClick={() => onDelete(item)}
                  class="rounded-md border border-red-300 px-3 py-1.5 font-medium text-red-600 hover:bg-red-50"
                >
                  削除
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {nextCursor.value && (
        <div class="mt-4 text-center">
          <button
            type="button"
            disabled={loading.value}
            onClick={() => loadMore().catch((err) => setError(err instanceof Error ? err.message : '読み込みに失敗しました'))}
            class="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
          >
            {loading.value ? '読み込み中...' : 'さらに読み込む'}
          </button>
        </div>
      )}
    </div>
  )
}
