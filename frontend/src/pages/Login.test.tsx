import type { ComponentChildren } from 'preact'
import { LocationProvider } from 'preact-iso'
import { afterEach, expect, test } from 'vitest'
import { render } from 'vitest-browser-preact'
import { user } from '../lib/auth'
import { Login } from './Login'

function Wrapper({ children }: { children: ComponentChildren }) {
  return <LocationProvider>{children}</LocationProvider>
}

afterEach(() => {
  user.value = null
})

test('renders the login form with a register link', async () => {
  const screen = render(<Login />, { wrapper: Wrapper })

  await expect.element(screen.getByRole('heading', { name: 'ログイン' })).toBeVisible()
  await expect.element(screen.getByLabelText('ユーザー名')).toBeVisible()
  await expect.element(screen.getByLabelText('パスワード')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'ログイン' })).toBeVisible()
  await expect
    .element(screen.getByRole('link', { name: '新規登録' }))
    .toHaveAttribute('href', '/register')
})

test('updates the username field as the user types', async () => {
  const screen = render(<Login />, { wrapper: Wrapper })

  const username = screen.getByLabelText('ユーザー名')
  await username.fill('alice')

  await expect.element(username).toHaveValue('alice')
})
