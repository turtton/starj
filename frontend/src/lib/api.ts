const BASE = '/api'

export class ApiError extends Error {
  status: number
  detail?: string

  constructor(status: number, message: string, detail?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1') + '=([^;]*)'),
  )
  return match ? decodeURIComponent(match[1]) : null
}

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
}

async function toError(res: Response): Promise<ApiError> {
  let title = res.statusText || 'Request failed'
  let detail: string | undefined
  try {
    const body = (await res.json()) as ProblemDetail
    if (body.title) title = body.title
    if (body.detail) detail = body.detail
  } catch {
    // non-JSON body; keep defaults
  }
  return new ApiError(res.status, detail ?? title, detail)
}

async function request(method: string, path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  if (MUTATING.has(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', token)
  }
  const res = await fetch(BASE + path, {
    ...init,
    method,
    headers,
    credentials: 'include',
  })
  return res
}

export const api = {
  async get<T>(path: string): Promise<T> {
    const res = await request('GET', path)
    if (!res.ok) throw await toError(res)
    return (await res.json()) as T
  },

  async postJson<T>(path: string, body: unknown): Promise<T> {
    const res = await request('POST', path, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw await toError(res)
    if (res.status === 204) return undefined as T
    return (await res.json()) as T
  },

  async postForm<T>(path: string, form: FormData): Promise<T> {
    const res = await request('POST', path, { body: form })
    if (!res.ok) throw await toError(res)
    if (res.status === 204) return undefined as T
    return (await res.json()) as T
  },

  async delete(path: string): Promise<void> {
    const res = await request('DELETE', path)
    if (!res.ok && res.status !== 404) throw await toError(res)
  },

  /** Primes the XSRF-TOKEN cookie so subsequent mutating requests carry the header. */
  async ensureCsrf(): Promise<void> {
    await request('GET', '/auth/csrf')
  },
}
