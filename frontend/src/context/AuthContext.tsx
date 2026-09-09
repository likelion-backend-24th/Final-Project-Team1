import { createContext, useContext } from 'react'

export interface AuthUser {
  id: number
  name: string
  role: string
  token: string
}

export interface AuthContextType {
  user: AuthUser | null
  login: (user: AuthUser) => void
  logout: () => void
  isRole: (...roles: string[]) => boolean
}

export const AuthContext = createContext<AuthContextType | null>(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}
