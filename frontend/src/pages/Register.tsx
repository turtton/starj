import { useLocation } from 'preact-iso'
import { useEffect, useState } from 'preact/hooks'
import { register, user } from '../lib/auth'

export function Register() {
  const { route } = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const authed = user.value
  useEffect(() => {
    if (authed) route('/', true)
  }, [authed])

  async function onSubmit(e: Event) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await register(username, password)
      route('/', true)
    } catch (err) {
      setError(err instanceof Error ? err.message : '登録に失敗しました')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div class="mx-auto max-w-sm">
      <h1 class="mb-6 text-2xl font-bold">新規登録</h1>
      <form onSubmit={onSubmit} class="space-y-4">
        <div>
          <label for="register-username" class="mb-1 block text-sm font-medium text-slate-700">
            ユーザー名
          </label>
          <input
            id="register-username"
            type="text"
            value={username}
            onInput={(e) => setUsername((e.target as HTMLInputElement).value)}
            required
            autocomplete="username"
            class="w-full rounded-md border border-slate-300 px-3 py-2 focus:border-slate-500 focus:outline-none"
          />
        </div>
        <div>
          <label for="register-password" class="mb-1 block text-sm font-medium text-slate-700">
            パスワード
          </label>
          <input
            id="register-password"
            type="password"
            value={password}
            onInput={(e) => setPassword((e.target as HTMLInputElement).value)}
            required
            autocomplete="new-password"
            class="w-full rounded-md border border-slate-300 px-3 py-2 focus:border-slate-500 focus:outline-none"
          />
        </div>
        {error && <p class="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={busy}
          class="w-full rounded-md bg-slate-900 px-4 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {busy ? '送信中...' : '登録'}
        </button>
      </form>
      <p class="mt-4 text-center text-sm text-slate-600">
        すでにアカウントがある場合は{' '}
        <a href="/login" class="font-medium text-slate-900 underline">
          ログイン
        </a>
      </p>
    </div>
  )
}
