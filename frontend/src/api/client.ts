const BASE = '/api/v1'

function getToken() {
  return localStorage.getItem('token')
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(`${BASE}${path}`, { ...options, headers })
  const body = await res.json().catch(() => null)

  if (!res.ok) {
    const code = body?.data?.code ?? body?.message ?? `HTTP ${res.status}`

    // 로그인해서 받은 Token이 아니라, 브라우저에 남아있던 Token이 만료·무효화된 경우다.
    // (로그인 자체이 틀린 경우는 INVALID_CREDENTIALS로 별도 코드가 내려온다.)
    if (res.status === 401 && code === 'UNAUTHENTICATED' && token) {
      localStorage.removeItem('token')
      localStorage.removeItem('auth')
      window.location.href = '/auth'
    }

    throw Object.assign(new Error(code), { status: res.status, body })
  }
  return body
}

export const api = {
  get: <T>(path: string) => apiFetch<T>(path),
  post: <T>(path: string, data: unknown) =>
    apiFetch<T>(path, { method: 'POST', body: JSON.stringify(data) }),
  patch: <T>(path: string, data?: unknown) =>
    apiFetch<T>(path, { method: 'PATCH', body: data ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string) => apiFetch<T>(path, { method: 'DELETE' }),
}
