import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:8080'

// Set E2E_NO_SERVER=1 when the app is already running (e.g. you started it
// yourself); otherwise Playwright boots the packaged jar and waits for /health.
const webServer = process.env.E2E_NO_SERVER
  ? undefined
  : {
      command: 'java -jar ../build/libs/starj-0.0.1-SNAPSHOT.jar',
      url: `${baseURL}/health`,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe' as const,
      stderr: 'pipe' as const,
    }

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? 'line' : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer,
})
