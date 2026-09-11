import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext, type AuthUser } from './AuthContext'

/** JWT exp 를 읽어 만료 여부만 본다. 서명 검증은 서버 몫이다. */
function isExpired(token: string) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()
  } catch {
    // 형태가 깨진 Token 은 쓸 수 없으므로 만료와 같게 다룬다.
    return true
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    try {
      const saved = localStorage.getItem('auth')
      if (!saved) return null

      // 만료된 Token 은 공개 페이지 요청이 200 으로 통과해버려, 예약을 누를 때까지
      // 로그인된 것처럼 보인다. 시작할 때 한 번 걸러낸다.
      const parsed: AuthUser = JSON.parse(saved)
      return isExpired(parsed.token) ? null : parsed
    } catch {
      return null
    }
  })

  useEffect(() => {
    if (user) {
      localStorage.setItem('auth', JSON.stringify(user))
      localStorage.setItem('token', user.token)
    } else {
      localStorage.removeItem('auth')
      localStorage.removeItem('token')
    }
  }, [user])

  const login = (u: AuthUser) => setUser(u)
  const logout = () => setUser(null)
  const isRole = (...roles: string[]) => !!user && roles.includes(user.role)

  return (
    <AuthContext.Provider value={{ user, login, logout, isRole }}>
      {children}
    </AuthContext.Provider>
  )
}
