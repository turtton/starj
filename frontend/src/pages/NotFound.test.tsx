import { expect, test } from 'vitest'
import { render } from 'vitest-browser-preact'
import { NotFound } from './NotFound'

test('renders the 404 message and a link home', async () => {
  const screen = render(<NotFound />)

  await expect.element(screen.getByText('404')).toBeVisible()
  await expect.element(screen.getByText('ページが見つかりません')).toBeVisible()

  const home = screen.getByRole('link', { name: 'ホームへ戻る' })
  await expect.element(home).toBeVisible()
  await expect.element(home).toHaveAttribute('href', '/')
})
