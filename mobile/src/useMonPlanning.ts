import { useQuery } from '@tanstack/react-query'

import { organisationApi, planningApi } from './api'
import { dayCodeOf, sessionsOfDay } from './planning'

/**
 * Fiche de l'enseignant connecté et ses séances du jour, tirées du dernier
 * emploi du temps **publié**.
 *
 * `/teachers/me` est réservé au rôle TEACHER : un compte de vie scolaire
 * obtiendrait un 403. Le mobile ne s'adresse qu'aux enseignants, l'écran
 * traite donc l'erreur comme un « aucune fiche enseignant » et le dit.
 */
export function useMonPlanning() {
  const moiQuery = useQuery({
    queryKey: ['teacher-me'],
    queryFn: organisationApi.moi,
    retry: false,
  })
  const moi = moiQuery.data

  const { data: generes = [] } = useQuery({
    queryKey: ['timetables-generated'],
    queryFn: planningApi.generes,
    enabled: !!moi,
  })

  const publie = generes
    .filter((g) => g.status === 'PUBLISHED')
    .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))[0]

  const vueQuery = useQuery({
    queryKey: ['timetable-me', publie?.jobId, moi?.codeEnseignant],
    queryFn: () => planningApi.vueEnseignant(publie!.jobId, moi!.codeEnseignant),
    enabled: !!publie && !!moi,
  })

  return {
    moi,
    moiLoading: moiQuery.isLoading,
    moiError: moiQuery.error as Error | null,
    publie,
    /** Vue complète de la semaine — l'accueil en tire le total, le planning l'affiche. */
    vue: vueQuery.data,
    vueLoading: vueQuery.isLoading,
    seancesDuJour: sessionsOfDay(vueQuery.data, dayCodeOf()),
    refetch: () => {
      void moiQuery.refetch()
      void vueQuery.refetch()
    },
  }
}
