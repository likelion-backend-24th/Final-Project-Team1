const BASE = '/api/v1'

function getToken() {
  return localStorage.getItem('token')
}

/** authRedirect=false 면 401 을 받아도 Token 을 지우거나 화면을 옮기지 않고 그대로 던진다. */
export type ApiOptions = RequestInit & { authRedirect?: boolean }

export function clearSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('auth')
}

export async function apiFetch<T>(
  path: string,
  options: ApiOptions = {}
): Promise<T> {
  const { authRedirect = true, ...init } = options
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(`${BASE}${path}`, { ...init, headers })
  const body = await res.json().catch(() => null)

  if (!res.ok) {
    const code = body?.data?.code ?? body?.message ?? `HTTP ${res.status}`

    // 로그인해서 받은 Token 이 아니라, 브라우저에 남아있던 Token 이 만료·무효화된 경우다.
    // (로그인 자체가 틀린 경우는 INVALID_CREDENTIALS 로 별도 코드가 내려온다.)
    // 결제 확정처럼 화면이 사라지면 사용자가 결과를 못 보는 호출은 authRedirect=false 로 제외한다.
    if (authRedirect && res.status === 401 && code === 'UNAUTHENTICATED' && token) {
      clearSession()
      window.location.href = '/auth'
    }

    throw Object.assign(new Error(code), { status: res.status, body })
  }
  return body
}

export const api = {
  get: <T>(path: string, options?: ApiOptions) => apiFetch<T>(path, options),
  post: <T>(path: string, data: unknown, options?: ApiOptions) =>
    apiFetch<T>(path, { ...options, method: 'POST', body: JSON.stringify(data) }),
  patch: <T>(path: string, data?: unknown, options?: ApiOptions) =>
    apiFetch<T>(path, { ...options, method: 'PATCH', body: data ? JSON.stringify(data) : undefined }),
  delete: <T>(path: string, options?: ApiOptions) => apiFetch<T>(path, { ...options, method: 'DELETE' }),
}
