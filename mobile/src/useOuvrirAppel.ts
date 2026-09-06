import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert } from 'react-native'

import { appelApi, organisationApi } from './api'
import type { SessionView } from './api/types'
import { todayIso } from './planning'

/**
 * Ouvre — ou récupère — l'appel d'une séance de l'emploi du temps.
 *
 * Partagé par l'accueil et par l'écran de choix : le geste est le même des deux
 * endroits, et il ne doit exister qu'une seule façon de composer la requête.
 * La séance du planning étant hebdomadaire, c'est le jour de cours qui
 * distingue l'appel d'aujourd'hui de celui de la semaine dernière.
 */
export function useOuvrirAppel(
  enseignantId: number | undefined,
  onOuvert: (appelId: number, titre: string, groupeClasseId: number) => void
) {
  const qc = useQueryClient()

  const { data: classes = [] } = useQuery({
    queryKey: ['classes'],
    queryFn: organisationApi.classes,
    enabled: !!enseignantId,
  })
  const { data: matieres = [] } = useQuery({
    queryKey: ['matieres'],
    queryFn: organisationApi.matieres,
    enabled: !!enseignantId,
  })
  const { data: annees = [] } = useQuery({
    queryKey: ['annees'],
    queryFn: organisationApi.annees,
    enabled: !!enseignantId,
  })

  const anneeCourante = annees.find((a) => a.estCourante) ?? annees[0]

  const mutation = useMutation({
    mutationFn: (session: SessionView) => {
      const classe = classes.find((c) => c.code === session.classCode)
      if (!classe) throw new Error(`Classe ${session.classCode} introuvable dans l'établissement`)
      if (!anneeCourante) throw new Error('Aucune année scolaire définie')
      const matiere = matieres.find((m) => m.codeMatiere === session.subjectCode)
      return appelApi.ouvrir({
        seancePlanningId: session.id,
        enseignantId: enseignantId!,
        groupeClasseId: classe.idClasse,
        anneeAcademique: anneeCourante.nom,
        matiereId: matiere?.idMatiere,
        dateSeance: todayIso(),
      })
    },
    onSuccess: (appel, session) => {
      void qc.invalidateQueries({ queryKey: ['appels-du-jour'] })
      // La classe voyage avec l'appel : c'est elle, et non l'enseignant, qui
      // porte les signalements que la feuille d'appel doit afficher.
      onOuvert(appel.id, `${session.classCode} · ${session.subjectName}`, appel.groupeClasseId)
    },
    onError: (e: Error) => Alert.alert('Ouverture impossible', e.message),
  })

  return { ouvrir: mutation, anneeCourante, classes }
}
