import { createContext, useContext } from 'react'

import type { AppRole } from '@/lib/roles'

export type { AppRole }

export interface MockAuthValue {
  initialized: boolean
  authenticated: boolean
  roles: AppRole[]
  token: string
  tenantId: string
}

const DevAuthContext = createContext<MockAuthValue>({
  initialized: true,
  authenticated: true,
  roles: ['SCHOOL_ADMIN'],
  token: 'dev-bypass-token',
  tenantId: 'dev-tenant',
})

export function DevAuthProvider({ children }: { children: React.ReactNode }) {
  const value: MockAuthValue = {
    initialized: true,
    authenticated: true,
    roles: ['SCHOOL_ADMIN'],
    token: 'dev-bypass-token',
    tenantId: 'dev-tenant',
  }
  return <DevAuthContext.Provider value={value}>{children}</DevAuthContext.Provider>
}

export function useDevAuth() {
  return useContext(DevAuthContext)
}
