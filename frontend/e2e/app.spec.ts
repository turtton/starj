import { expect, test, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const PASSWORD = 'password123'

function uniqueUsername(prefix = 'e2e'): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`
}

async function registerAndLogin(page: Page, username: string): Promise<void> {
  await page.goto('/register')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(PASSWORD)
  await page.getByRole('button', { name: '登録' }).click()
  // Registration auto-logs-in and routes to the file list.
  await expect(page.getByRole('heading', { name: 'ファイル' })).toBeVisible()
}

test('register, upload, download, delete and logout against the real backend', async ({ page }) => {
  const username = uniqueUsername()
  await registerAndLogin(page, username)

  await expect(page.getByText(username)).toBeVisible()
  await expect(page.getByText('ファイルがありません')).toBeVisible()

  await page.locator('input[type="file"]').setInputFiles({
    name: 'hello.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('hello from e2e'),
  })

  const row = page.locator('li', { hasText: 'hello.txt' })
  await expect(row).toBeVisible()

  // Download round-trips the bytes back out of MinIO.
  const downloadPromise = page.waitForEvent('download')
  await row.getByRole('link', { name: 'ダウンロード' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('hello.txt')
  const downloadedPath = await download.path()
  expect(await readFile(downloadedPath, 'utf8')).toBe('hello from e2e')

  // Delete confirms via a native dialog, then the list is empty again.
  page.once('dialog', (dialog) => dialog.accept())
  await row.getByRole('button', { name: '削除' }).click()
  await expect(page.getByText('ファイルがありません')).toBeVisible()

  await page.getByRole('button', { name: 'ログアウト' }).click()
  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible()
})

test('paginates the file list with cursor pages', async ({ page }) => {
  const username = uniqueUsername('e2epage')
  await registerAndLogin(page, username)

  const input = page.locator('input[type="file"]')
  const TOTAL = 21 // PAGE_SIZE (20) + 1 forces a second page

  for (let i = 0; i < TOTAL; i++) {
    const name = `file-${String(i).padStart(2, '0')}.txt`
    await input.setInputFiles({ name, mimeType: 'text/plain', buffer: Buffer.from(name) })
    await expect(page.locator('li', { hasText: name })).toBeVisible()
  }

  // Uploads optimistically prepend client-side; reload to exercise the real
  // server-side cursor pagination instead.
  await page.reload()
  await expect(page.getByRole('heading', { name: 'ファイル' })).toBeVisible()

  await expect(page.locator('ul > li')).toHaveCount(20)
  const loadMore = page.getByRole('button', { name: 'さらに読み込む' })
  await expect(loadMore).toBeVisible()

  await loadMore.click()
  await expect(page.locator('ul > li')).toHaveCount(TOTAL)
  await expect(loadMore).toHaveCount(0)
})
