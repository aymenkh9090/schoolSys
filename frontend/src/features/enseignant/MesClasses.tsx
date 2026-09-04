import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, GraduationCap, Users } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi } from '@/api/organisation.api'
import { cn } from '@/lib/utils'
import { WEEK_ORDER, sessionsOfDay, useMonPlanning } from './useMonPlanning'

/** Liste des élèves d'une classe, chargée seulement quand la carte est dépliée. */
function ElevesClasse({ classeId }: { classeId: number }) {
  const { data: eleves = [], isLoading } = useQuery({
    queryKey: ['eleves-classe', classeId],
    queryFn: () => organisationApi.eleves.byClass(classeId),
  })

  if (isLoading) return <div className="h-16 animate-pulse rounded-lg bg-brand-bgSecondary dark:bg-slate-800" />
  if (eleves.length === 0) {
    return <p className="text-sm text-brand-textMuted dark:text-slate-400">Aucun élève actif dans cette classe.</p>
  }

  return (
    <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
      {eleves.map((e, i) => (
        <div
          key={e.idEleve}
          className="flex items-center gap-2 rounded-lg border border-brand-border px-2.5 py-1.5 dark:border-slate-700"
        >
          <span className="w-5 shrink-0 text-right font-mono text-[11px] text-brand-textMuted dark:text-slate-500">{i + 1}</span>
          <span className="truncate text-sm text-brand-text dark:text-slate-200">{e.nom} {e.prenom}</span>
        </div>
      ))}
    </div>
  )
}

/** Les classes de l'enseignant connecté : matières enseignées, effectif, élèves. */
export default function MesClasses() {
  const [openClasse, setOpenClasse] = useState<number | null>(null)
  const { me, meLoading, meError, meErrorMessage, timetable } = useMonPlanning()

  const { data: affectations = [], isLoading: affectationsLoading } = useQuery({
    queryKey: ['teaching-assignments-me', me?.idEnseignant],
    queryFn: () => organisationApi.teachingAssignments.byTeacher(me!.idEnseignant),
    enabled: !!me,
  })

  if (meLoading) {
    return <div className="h-64 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
  }

  if (meError || !me) {
    return (
      <div className="space-y-6">
        <PageHeader title="Mes classes" subtitle="Classes et élèves dont j'ai la charge" />
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-6 text-sm text-yellow-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
          {meErrorMessage ??
            "Aucune fiche enseignant n'est liée à votre compte. Demandez à l'administration de créer votre fiche dans Gestion des enseignants."}
        </div>
      </div>
    )
  }

  const actives = affectations.filter((a) => a.isActive)

  // Regroupement par classe : une classe peut recevoir plusieurs matières/types de séance.
  const parClasse = new Map<number, { code: string; niveau: string; effectif: number; matieres: Set<string> }>()
  for (const a of actives) {
    const entry = parClasse.get(a.classGroupId) ?? {
      code: a.classGroupCode,
      niveau: a.levelNom,
      effectif: a.classGroupNbEleve,
      matieres: new Set<string>(),
    }
    entry.matieres.add(a.subjectLib)
    parClasse.set(a.classGroupId, entry)
  }
  const classes = Array.from(parClasse.entries()).sort((a, b) => a[1].code.localeCompare(b[1].code))

  // Nombre de séances hebdomadaires par classe, d'après l'emploi du temps publié.
  const seancesParClasse = new Map<string, number>()
  for (const day of WEEK_ORDER) {
    for (const s of sessionsOfDay(timetable, day)) {
      if (s.classCode) seancesParClasse.set(s.classCode, (seancesParClasse.get(s.classCode) ?? 0) + 1)
    }
  }

  const totalEleves = classes.reduce((sum, [, c]) => sum + c.effectif, 0)
  const totalMatieres = new Set(actives.map((a) => a.subjectLib)).size

  return (
    <div className="space-y-6">
      <PageHeader title="Mes classes" subtitle={`${me.nomComplet} — classes et élèves dont j'ai la charge`} />

      <div className="grid grid-cols-3 gap-3">
        <StatCard title="Classes" value={classes.length} icon={GraduationCap} color="blue" />
        <StatCard title="Élèves (effectif)" value={totalEleves} icon={Users} color="green" />
        <StatCard title="Matières enseignées" value={totalMatieres} icon={GraduationCap} color="purple" />
      </div>

      {affectationsLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
      ) : classes.length === 0 ? (
        <div className="rounded-xl border border-brand-border bg-white p-12 text-center dark:border-slate-700 dark:bg-slate-900">
          <GraduationCap size={32} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-400" />
          <p className="text-sm text-brand-textMuted dark:text-slate-400">
            Aucune classe ne vous est affectée pour le moment.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {classes.map(([classeId, c]) => {
            const ouvert = openClasse === classeId
            return (
              <div
                key={classeId}
                className="rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900"
              >
                <button
                  type="button"
                  onClick={() => setOpenClasse(ouvert ? null : classeId)}
                  className={cn(
                    'flex w-full items-center gap-3 p-4 text-left transition-colors',
                    'hover:bg-brand-bgSecondary dark:hover:bg-slate-800',
                    ouvert && 'rounded-b-none'
                  )}
                >
                  {ouvert ? <ChevronDown size={18} className="shrink-0 text-brand-textMuted" /> : <ChevronRight size={18} className="shrink-0 text-brand-textMuted" />}
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-semibold text-brand-text dark:text-slate-100">{c.code}</p>
                      <Badge variant="default">{c.niveau}</Badge>
                      {seancesParClasse.has(c.code) && (
                        <Badge variant="info">{seancesParClasse.get(c.code)} séance(s)/sem.</Badge>
                      )}
                    </div>
                    <p className="mt-1 truncate text-xs text-brand-textMuted dark:text-slate-400">
                      {Array.from(c.matieres).join(' · ')}
                    </p>
                  </div>
                  <span className="flex shrink-0 items-center gap-1.5 text-sm text-brand-textMuted dark:text-slate-400">
                    <Users size={14} /> {c.effectif}
                  </span>
                </button>

                {ouvert && (
                  <div className="border-t border-brand-border p-4 dark:border-slate-700">
                    <ElevesClasse classeId={classeId} />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
