import apiClient from './client'

// ── Types alignés sur le backend (tenant-business) ───────────────────────────

export type EtablissementType = 'PRIMAIRE' | 'COLLEGE' | 'SECONDAIRE'
export type TenantPlan = 'FREE' | 'STANDARD' | 'PREMIUM'
export type TenantStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED'

export interface Tenant {
  id: number
  code: string
  name: string
  type: EtablissementType
  address: string
  phone: string
  logo: string | null
  active: boolean
  status: TenantStatus
  plan: TenantPlan
  /** Présents uniquement dans la réponse de création (affichés une seule fois). */
  adminEmail?: string
  adminUsername?: string
  adminTempPassword?: string
}

export interface CreateTenantDto {
  code: string
  name: string
  type: EtablissementType
  plan: TenantPlan
  address: string
  phone: string
  logo?: string
  emailAdmin: string
  nomCompletAdmin: string
}

export interface UpdateTenantDto {
  name: string
  type: EtablissementType
  plan: TenantPlan
  address: string
  phone: string
  logo?: string
}

export interface PublicTenant {
  id: number
  code: string
  name: string
  type: EtablissementType
  logo: string | null
}

/** Réponse paginée Spring Data. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const tenantApi = {
  /** Résolution publique (sans auth) d'un établissement par son code — page de login. */
  publicByCode: (code: string) =>
    apiClient
      .get<PublicTenant>(`/api/public/tenants/by-code/${encodeURIComponent(code)}`)
      .then((r) => r.data),

  /** Établissement de l'utilisateur connecté (nom, logo) — branding du dashboard. */
  me: () => apiClient.get<PublicTenant>('/api/tenants/me').then((r) => r.data),

  list: (page = 0, size = 50) =>
    apiClient
      .get<Page<Tenant>>('/api/tenants', { params: { page, size } })
      .then((r) => r.data.content),
  get: (id: number) =>
    apiClient.get<Tenant>(`/api/tenants/${id}`).then((r) => r.data),
  create: (dto: CreateTenantDto) =>
    apiClient.post<Tenant>('/api/tenants', dto).then((r) => r.data),
  update: (id: number, dto: UpdateTenantDto) =>
    apiClient.put<Tenant>(`/api/tenants/${id}`, dto).then((r) => r.data),
  delete: (id: number) => apiClient.delete(`/api/tenants/${id}`),
  activate: (id: number) =>
    apiClient.patch<Tenant>(`/api/tenants/${id}/activate`).then((r) => r.data),
  suspend: (id: number) =>
    apiClient.patch<Tenant>(`/api/tenants/${id}/suspend`).then((r) => r.data),
  stats: () =>
    apiClient
      .get<{ total: number; active: number; inactive: number }>('/api/tenants/stats')
      .then((r) => r.data),
}
