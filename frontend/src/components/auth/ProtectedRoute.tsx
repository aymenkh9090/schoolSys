import { Navigate } from 'react-router-dom'
import { useAuth, type AppRole } from '@/hooks/useAuth'

interface Props {
  children: React.ReactNode
  roles?: AppRole[]
  /** Réservé à la page de changement de mot de passe elle-même, pour éviter une boucle de redirection. */
  allowPasswordPending?: boolean
}

export function ProtectedRoute({ children, roles, allowPasswordPending = false }: Props) {
  const { authenticated, hasRole, initialized, mustChangePassword } = useAuth()

  if (!initialized) return null

  if (!authenticated) return <Navigate to="/etablissement/login" replace />

  if (roles && !roles.some((r) => hasRole(r))) {
    return <Navigate to="/non-autorise" replace />
  }

  if (mustChangePassword && !allowPasswordPending) {
    return <Navigate to="/premiere-connexion/mot-de-passe" replace />
  }

  return <>{children}</>
}
