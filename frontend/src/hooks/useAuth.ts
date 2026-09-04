import { useAuthState } from '@/auth/AuthProvider'
import * as authStore from '@/auth/authStore'
import type { AppRole } from '@/lib/roles'

export type { AppRole }

export function useAuth() {
  const { initialized, accessToken, tokenParsed } = useAuthState()

  const roles: AppRole[] =
    (tokenParsed?.realm_access?.roles as AppRole[]) ?? []

  const hasRole = (role: AppRole) => roles.includes(role)
  const isSuperAdmin = hasRole('PLATFORM_SUPER_ADMIN')
  const isSchoolAdmin = hasRole('SCHOOL_ADMIN')
  const isTeacher = hasRole('TEACHER')
  const isSurveillant = hasRole('SURVEILLANT')

  const tenantId: string | undefined = tokenParsed?.tenant_id
  const mustChangePassword = tokenParsed?.must_change_password === 'true'

  return {
    initialized,
    authenticated: accessToken != null,
    user: tokenParsed,
    roles,
    hasRole,
    isSuperAdmin,
    isSchoolAdmin,
    isTeacher,
    isSurveillant,
    tenantId,
    mustChangePassword,
    token: accessToken ?? undefined,
    login: authStore.login,
    logout: () => {
      void authStore.logout()
    },
  }
}
