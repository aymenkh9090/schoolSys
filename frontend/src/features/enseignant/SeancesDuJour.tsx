import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CalendarDays, ClipboardList, Clock, DoorOpen, Users } from 'lucide-react'

import { SectionTitle } from '@/components/ui/SectionTitle'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { absenceApi, type AppelReponse } from '@/api/absence.api'
import { organisationApi } from '@/api/organisation.api'
import type { SessionView } from '@/api/planning.api'
import { AppelDetailModal } from '@/features/absence/AppelDetailModal'
import { cn } from '@/lib/utils'
import {
  DAY_LABELS,
  SESSION_TYPE_LABELS,
  dayCodeOf,
  hhmm,
  isOngoing,
  nowMinutes,
  todayIso,
  toMinutes,
  useMonPlanning,
} from './useMonPlanning'

interface Props {
  /** Titre du bloc. */
  title?: string
  /** Masque le bloc quand l'enseignant n'a aucun cours aujourd'hui. */
  hideWhenEmpty?: boolean
}

/**
 * Cours du jour de l'enseignant connecté, avec ouverture de l'appel directement
 * depuis le web : la séance de l'emploi du temps publié fournit l'identifiant de
 * planning, la classe et la matière — aucune saisie manuelle n'est nécessaire.
 */
export function SeancesDuJour({ title = "Mes cours d'aujourd'hui", hideWhenEmpty = false }: Props) {
  const qc = useQueryClient()
  const [appel, setAppel] = useState<AppelReponse | null>(null)
  const { me, published, timetableLoading, todaySessions } = useMonPlanning()

  const { data: classes = [] } = useQuery({ queryKey: ['classes-all'], queryFn: organisationApi.classes.list, enabled: !!me })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects-all'], queryFn: organisationApi.subjects.list, enabled: !!me })
  const { data: years = [] } = useQuery({ queryKey: ['school-years-all'], queryFn: organisationApi.schoolYears.list, enabled: !!me })

  const anneeCourante = years.find((y) => y.estCourante) ?? years[0]

  const ouvrirMutation = useMutation({
    mutationFn: (session: SessionView) => {
      const classe = classes.find((c) => c.code === session.classCode)
      if (!classe) throw new Error(`Classe ${session.classCode} introuvable dans l'établissement`)
      if (!anneeCourante) throw new Error('Aucune année scolaire définie')
      const matiere = subjects.find((s) => s.codeMatiere === session.subjectCode)
      return absenceApi.appel.ouvrir({
        seancePlanningId: session.id,
        enseignantId: me!.idEnseignant,
        groupeClasseId: classe.idClasse,
        anneeAcademique: anneeCourante.nom,
        matiereId: matiere?.idMatiere,
        // La séance du planning est hebdomadaire : le jour de cours distingue
        // l'appel de cette semaine de celui des semaines précédentes.
        dateSeance: todayIso(),
      })
    },
    onSuccess: (data) => {
      setAppel(data)
      qc.invalidateQueries({ queryKey: ['appel-sessions'] })
    },
    onError: (e: Error & { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? e.message ?? "Impossible d'ouvrir l'appel"),
  })

  if (!me) return null
  if (hideWhenEmpty && todaySessions.length === 0 && !timetableLoading) return null

  const now = nowMinutes()
  const prochaine = todaySessions.find((s) => toMinutes(s.startTime) > now)

  return (
    <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
      <SectionTitle
        title={title}
        icon={CalendarDays}
        accent="teal"
        action={
          <span className="text-xs text-brand-textMuted dark:text-slate-400">
            {DAY_LABELS[dayCodeOf()]} · {todaySessions.length} séance(s)
          </span>
        }
      />

      {!published ? (
        <p className="py-8 text-center text-sm text-brand-textMuted dark:text-slate-400">
          Aucun emploi du temps publié pour le moment — vos cours apparaîtront ici dès la publication.
        </p>
      ) : timetableLoading ? (
        <div className="h-32 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
      ) : todaySessions.length === 0 ? (
        <p className="py-8 text-center text-sm text-brand-textMuted dark:text-slate-400">
          Aucun cours prévu aujourd'hui ({DAY_LABELS[dayCodeOf()]}).
        </p>
      ) : (
        <div className="space-y-2">
          {todaySessions.map((s) => {
            const enCours = isOngoing(s)
            const passee = toMinutes(s.endTime) <= now
            return (
              <div
                key={s.id}
                className={cn(
                  'flex flex-wrap items-center gap-3 rounded-xl border p-3 transition-colors',
                  enCours
                    ? 'border-teal-300 bg-teal-50/60 dark:border-teal-500/40 dark:bg-teal-500/10'
                    : 'border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900',
                  passee && !enCours && 'opacity-60'
                )}
              >
                <div className="flex w-20 shrink-0 flex-col items-center rounded-lg bg-brand-bgSecondary px-2 py-1.5 dark:bg-slate-800">
                  <span className="font-mono text-sm font-bold text-brand-text dark:text-slate-100">{hhmm(s.startTime)}</span>
                  <span className="font-mono text-[10px] text-brand-textMuted dark:text-slate-400">{hhmm(s.endTime)}</span>
                </div>

                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate text-sm font-semibold text-brand-text dark:text-slate-100">{s.subjectName}</p>
                    {enCours && <Badge variant="success"><Clock size={11} className="me-1 inline" />En cours</Badge>}
                    {!enCours && prochaine?.id === s.id && <Badge variant="info">Prochaine</Badge>}
                  </div>
                  <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-brand-textMuted dark:text-slate-400">
                    <span className="flex items-center gap-1"><Users size={12} />{s.classCode}</span>
                    {s.roomCode && <span className="flex items-center gap-1"><DoorOpen size={12} />{s.roomCode}</span>}
                    <span>{SESSION_TYPE_LABELS[s.sessionType] ?? s.sessionType}</span>
                    {s.groupLabel && <span className="rounded bg-brand-bgSecondary px-1.5 dark:bg-slate-800">{s.groupLabel}</span>}
                  </p>
                </div>

                <Button
                  size="sm"
                  variant={enCours ? 'primary' : 'outline'}
                  loading={ouvrirMutation.isPending && ouvrirMutation.variables?.id === s.id}
                  onClick={() => ouvrirMutation.mutate(s)}
                >
                  <ClipboardList size={14} /> Faire l'appel
                </Button>
              </div>
            )
          })}
        </div>
      )}

      {appel && (
        <AppelDetailModal
          appel={appel}
          onClose={() => setAppel(null)}
          onUpdate={(updated) => {
            setAppel(updated)
            qc.invalidateQueries({ queryKey: ['appel-sessions'] })
          }}
        />
      )}
    </div>
  )
}
