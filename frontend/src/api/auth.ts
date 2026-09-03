import { api } from './client'
import type { ApiResponse, HostRequest } from '../types'

export interface LoginResponse {
  accessToken: string
  expiresAt: string
  userId: number
  name: string
  role: string
}

export const authApi = {
  signup: (data: { name: string; email: string; password: string }) =>
    api.post<ApiResponse<{ id: number }>>('/auth/signup', data),

  login: (data: { email: string; password: string }) =>
    api.post<ApiResponse<LoginResponse>>('/auth/login', data),

  logout: () => api.post<ApiResponse<null>>('/auth/logout', {}),

  requestHost: (data: {
    orgName: string
    contactName: string
    contactPhone: string
    purpose?: string
  }) => api.post<ApiResponse<{ id: number }>>('/auth/host-requests', data),

  listHostRequests: () =>
    api.get<ApiResponse<HostRequest[]>>('/admin/host-requests'),

  approveHostRequest: (id: number) =>
    api.patch<ApiResponse<null>>(`/admin/host-requests/${id}/approve`),

  createOrganizerAccount: (data: {
    name: string
    email: string
    password: string
  }) => api.post<ApiResponse<{ id: number }>>('/admin/accounts', data),
}
