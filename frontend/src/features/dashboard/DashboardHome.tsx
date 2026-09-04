import { useAuth } from '@/hooks/useAuth'
import DashboardEcole from './DashboardEcole'
import DashboardEnseignant from '@/features/enseignant/DashboardEnseignant'
import DashboardSurveillant from '@/features/surveillance/DashboardSurveillant'

/**
 * `/ecole` : tableau de bord de l'établissement pour l'administration ; journée
 * de vie scolaire pour un surveillant ; tableau de bord personnel pour un
 * enseignant. Chaque rôle n'appelle que les API qui lui sont ouvertes — un
 * surveillant n'a par exemple pas accès à la génération du planning.
 */
export default function DashboardHome() {
  const { isTeacher, isSchoolAdmin, isSurveillant } = useAuth()
  if (isSchoolAdmin) return <DashboardEcole />
  if (isSurveillant) return <DashboardSurveillant />
  return isTeacher ? <DashboardEnseignant /> : <DashboardEcole />
}
