import { Navigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

export function RedirectByRole() {
  const { authenticated, isSuperAdmin, initialized } = useAuth()

  if (!initialized) return null
  if (!authenticated) return <Navigate to="/etablissement/login" replace />

  if (isSuperAdmin) return <Navigate to="/super-admin" replace />
  return <Navigate to="/ecole" replace />
}
