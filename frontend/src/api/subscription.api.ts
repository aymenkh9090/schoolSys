import apiClient from './client'
import type { Page, TenantPlan } from './tenant.api'

// ── Types alignés sur le backend (tenant-business : Subscription) ───────────

export type BillingCycle = 'MONTHLY' | 'YEARLY'
export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED'

export interface Subscription {
  id: number
  tenantId: number
  plan: TenantPlan
  billingCycle: BillingCycle
  status: SubscriptionStatus
  startDate: string
  endDate: string
  price: number | null
  notes: string | null
}

/** Une ligne de la table "Abonnements" : établissement + son abonnement courant (s'il existe). */
export interface SubscriptionOverview {
  tenantId: number
  tenantName: string
  tenantCode: string
  subscriptionId: number | null
  plan: TenantPlan | null
  billingCycle: BillingCycle | null
  status: SubscriptionStatus | null
  startDate: string | null
  endDate: string | null
  daysRemaining: number | null
}

export interface CreateSubscriptionDto {
  tenantId: number
  plan: TenantPlan
  billingCycle: BillingCycle
  startDate?: string
  price?: number
  notes?: string
}

export interface RenewSubscriptionDto {
  billingCycle: BillingCycle
  price?: number
}

export interface ChangePlanDto {
  plan: TenantPlan
}

export const subscriptionApi = {
  overview: (page = 0, size = 50) =>
    apiClient
      .get<Page<SubscriptionOverview>>('/api/super-admin/subscriptions', { params: { page, size } })
      .then((r) => r.data.content),
  current: (tenantId: number) =>
    apiClient
      .get<Subscription>(`/api/super-admin/subscriptions/tenant/${tenantId}`)
      .then((r) => r.data),
  history: (tenantId: number, page = 0, size = 20) =>
    apiClient
      .get<Page<Subscription>>(`/api/super-admin/subscriptions/tenant/${tenantId}/history`, {
        params: { page, size },
      })
      .then((r) => r.data.content),
  create: (dto: CreateSubscriptionDto) =>
    apiClient.post<Subscription>('/api/super-admin/subscriptions', dto).then((r) => r.data),
  renew: (tenantId: number, dto: RenewSubscriptionDto) =>
    apiClient
      .patch<Subscription>(`/api/super-admin/subscriptions/tenant/${tenantId}/renew`, dto)
      .then((r) => r.data),
  changePlan: (tenantId: number, dto: ChangePlanDto) =>
    apiClient
      .patch<Subscription>(`/api/super-admin/subscriptions/tenant/${tenantId}/plan`, dto)
      .then((r) => r.data),
  cancel: (tenantId: number) =>
    apiClient
      .patch<Subscription>(`/api/super-admin/subscriptions/tenant/${tenantId}/cancel`)
      .then((r) => r.data),
}
