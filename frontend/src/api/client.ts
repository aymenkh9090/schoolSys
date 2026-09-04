import axios from 'axios'
import * as authStore from '@/auth/authStore'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
})

apiClient.interceptors.request.use(async (config) => {
  const { accessToken } = authStore.getState()
  if (accessToken) {
    try {
      await authStore.updateToken(30)
    } catch {
      window.location.href = '/login'
      return config
    }
    config.headers.Authorization = `Bearer ${authStore.getState().accessToken}`
  }

  const tenantId = authStore.getState().tokenParsed?.tenant_id
  if (tenantId) {
    config.headers['X-Tenant-ID'] = tenantId
  }

  return config
})

export default apiClient
