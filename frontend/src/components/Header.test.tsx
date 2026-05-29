import type { ComponentChildren } from 'preact'
import { LocationProvider } from 'preact-iso'
import { afterEach, expect, test } from 'vitest'
import { render } from 'vitest-browser-preact'
import { user } from '../lib/auth'
import { Header } from './Header'

function Wrapper({ children }: { children: ComponentChildren }) {
  return <LocationProvider>{children}</LocationProvider>
}

afterEach(() => {
  user.value = null
})

test('shows only the brand when signed out', async () => {
  const screen = render(<Header />, { wrapper: Wrapper })

  await expect.element(screen.getByText('starj')).toBeVisible()
  expect(screen.getByRole('button', { name: 'ログアウト' }).query()).toBeNull()
})

test('shows the username and logout button when the user signal is set', async () => {
  user.value = { id: 1, username: 'alice' }
  const screen = render(<Header />, { wrapper: Wrapper })

  await expect.element(screen.getByText('alice')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'ログアウト' })).toBeVisible()
})
