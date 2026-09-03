import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

interface AuthUser {
  id: number
  name: string
  role: string
  token: string
}

interface AuthContextType {
  user: AuthUser | null
  login: (user: AuthUser) => void
  logout: () => void
  isRole: (...roles: string[]) => boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    try {
      const saved = localStorage.getItem('auth')
      return saved ? JSON.parse(saved) : null
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

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}
