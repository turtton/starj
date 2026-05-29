import type { ComponentChildren } from 'preact'
import { useLocation } from 'preact-iso'
import { useEffect } from 'preact/hooks'
import { authReady, user } from '../lib/auth'

export function RequireAuth({ children }: { children: ComponentChildren }) {
  const { route } = useLocation()
  const ready = authReady.value
  const current = user.value

  useEffect(() => {
    if (ready && !current) route('/login', true)
  }, [ready, current])

  if (!ready) {
    return <div class="py-16 text-center text-slate-500">読み込み中...</div>
  }
  if (!current) return null
  return <>{children}</>
}
