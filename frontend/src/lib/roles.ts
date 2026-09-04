/** Rôles applicatifs portés par le token, du plus élevé au plus restreint. */
export type AppRole =
  | 'PLATFORM_SUPER_ADMIN'
  | 'SCHOOL_ADMIN'
  | 'TEACHER'
  | 'SURVEILLANT'
  | 'STUDENT'
  | 'PARENT'

/** Rôles qu'un admin école peut attribuer depuis la gestion des comptes. */
export type UserRole = Extract<AppRole, 'SCHOOL_ADMIN' | 'TEACHER' | 'SURVEILLANT'>

export const ASSIGNABLE_ROLES: UserRole[] = ['SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT']

export const ROLE_LABELS: Record<AppRole, string> = {
  PLATFORM_SUPER_ADMIN: 'Super-administrateur',
  SCHOOL_ADMIN: 'Administrateur école',
  TEACHER: 'Enseignant',
  SURVEILLANT: 'Surveillant',
  STUDENT: 'Élève',
  PARENT: 'Parent',
}

export const ROLE_VARIANTS: Record<AppRole, 'danger' | 'info' | 'default'> = {
  PLATFORM_SUPER_ADMIN: 'danger',
  SCHOOL_ADMIN: 'danger',
  TEACHER: 'info',
  SURVEILLANT: 'default',
  STUDENT: 'default',
  PARENT: 'default',
}

/** Ordre de priorité d'affichage quand un compte porte plusieurs rôles. */
const ROLE_PRIORITY: AppRole[] = [
  'PLATFORM_SUPER_ADMIN',
  'SCHOOL_ADMIN',
  'SURVEILLANT',
  'TEACHER',
  'PARENT',
  'STUDENT',
]

/** Rôle métier principal d'un compte (les rôles techniques du SSO sont ignorés). */
export function mainRole(roles: string[]): AppRole | null {
  return ROLE_PRIORITY.find((r) => roles.includes(r)) ?? null
}

/** Libellé du rôle métier principal, ou `null` si le compte n'en porte aucun. */
export function roleLabel(roles: string[]): string | null {
  const role = mainRole(roles)
  return role ? ROLE_LABELS[role] : null
}
