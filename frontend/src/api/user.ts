import { api } from './client'
import type { ApiResponse } from '../types'

export interface MyProfileResponse {
  id: number
  email: string
  name: string
  role: string
}

export interface NameAvailabilityResponse {
  available: boolean
}

export const userApi = {
  // GET /api/v1/users/me
  getMe: () => api.get<ApiResponse<MyProfileResponse>>('/users/me'),
  // GET /api/v1/users/me/name-availability?name=
  checkNameAvailability: (name: string) =>
    api.get<ApiResponse<NameAvailabilityResponse>>(`/users/me/name-availability?name=${encodeURIComponent(name)}`),
  // PATCH /api/v1/users/me/name
  changeName: (name: string) => api.patch<ApiResponse<MyProfileResponse>>('/users/me/name', { name }),
  // PATCH /api/v1/users/me/password
  changePassword: (currentPassword: string, newPassword: string) =>
    api.patch<ApiResponse<null>>('/users/me/password', { currentPassword, newPassword }),
}
