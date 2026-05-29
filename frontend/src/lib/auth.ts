import { signal } from '@preact/signals'
import { api, ApiError } from './api'

export interface User {
  id: number
  username: string
}

export const user = signal<User | null>(null)
export const authReady = signal(false)

export async function bootstrap(): Promise<void> {
  await api.ensureCsrf()
  try {
    user.value = await api.get<User>('/auth/me')
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      user.value = null
    } else {
      throw err
    }
  } finally {
    authReady.value = true
  }
}

export async function login(username: string, password: string): Promise<void> {
  user.value = await api.postJson<User>('/auth/login', { username, password })
}

export async function register(username: string, password: string): Promise<void> {
  await api.postJson<{ id: number; username: string }>('/auth/register', {
    username,
    password,
  })
  await login(username, password)
}

export async function logout(): Promise<void> {
  await api.postJson<void>('/auth/logout', {})
  user.value = null
}
