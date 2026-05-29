import { useLocation } from 'preact-iso'
import { logout, user } from '../lib/auth'

export function Header() {
  const { route } = useLocation()
  const current = user.value

  async function onLogout() {
    await logout()
    route('/login', true)
  }

  return (
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex w-full max-w-3xl items-center justify-between px-4 py-3">
        <a href="/" class="text-lg font-semibold text-slate-900">
          starj
        </a>
        {current && (
          <div class="flex items-center gap-3 text-sm">
            <span class="text-slate-600">{current.username}</span>
            <button
              type="button"
              onClick={onLogout}
              class="rounded-md border border-slate-300 px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100"
            >
              ログアウト
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
