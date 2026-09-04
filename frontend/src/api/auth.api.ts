import apiClient from './client'

export const authApi = {
  completeFirstLogin: (newPassword: string) =>
    apiClient.post<void>('/api/auth/first-login/complete', { newPassword }).then((r) => r.data),
}
