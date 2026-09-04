import apiClient from './client'

export interface SchoolUser {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
  enabled: boolean
}

export interface CreateUserDto {
  email: string
  firstName: string
  lastName: string
  role: string
  password?: string
}

export const schoolUserApi = {
  list: () =>
    apiClient.get<SchoolUser[]>('/api/users').then((r) => r.data),
  create: (dto: CreateUserDto) =>
    apiClient.post<SchoolUser>('/api/users', dto).then((r) => r.data),
  update: (id: string, dto: Partial<CreateUserDto>) =>
    apiClient.put<SchoolUser>(`/api/users/${id}`, dto).then((r) => r.data),
  delete: (id: string) => apiClient.delete(`/api/users/${id}`),
  resetPassword: (id: string, password: string) =>
    apiClient
      .post(`/api/users/${id}/reset-password`, { password })
      .then((r) => r.data),
}
