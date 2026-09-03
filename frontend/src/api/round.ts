import { api } from './client'
import type { ApiResponse, Round } from '../types'

export const roundApi = {
  listByExpo: (expoId: number) =>
    api.get<ApiResponse<Round[]>>(`/expos/${expoId}/rounds`),

  createRound: (expoId: number, data: {
    startAt: string
    endAt: string
    capacity: number
    price?: number
  }) => api.post<ApiResponse<Round>>(`/expos/${expoId}/rounds`, data),

  deleteRound: (expoId: number, roundId: number) =>
    api.delete<ApiResponse<null>>(`/expos/${expoId}/rounds/${roundId}`),
}
