import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Play, CheckCircle, Landmark, Layers, BookOpen, Info } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi, type NationalPattern, type NationalPatternApplyResult } from '@/api/organisation.api'

export default function ProgrammeNational() {
  const qc = useQueryClient()
  const [applyTarget, setApplyTarget] = useState<NationalPattern | null>(null)
  const [schoolYearId, setSchoolYearId] = useState('')
  const [applyResult, setApplyResult] = useState<NationalPatternApplyResult | null>(null)

  const { data: patterns = [], isLoading } = useQuery({
    queryKey: ['national-patterns'],
    queryFn: () => organisationApi.nationalPatterns.list('TN'),
  })

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })

  const applyMutation = useMutation({
    mutationFn: ({ id, yearId }: { id: number; yearId?: number }) =>
      organisationApi.nationalPatterns.apply(id, yearId),
    onSuccess: (result) => {
      toast.success('Programme appliqué')
      setApplyResult(result)
      qc.invalidateQueries({ queryKey: ['teaching-assignments'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const activeCount = patterns.filter((p) => p.active).length
  const niveauxCouverts = new Set(patterns.map((p) => p.levelCode)).size

  return (
    <div className="space-y-6">
      <PageHero
        title="Programme national"
        subtitle="Les programmes officiels du Ministère de l'Éducation, à appliquer à vos niveaux"
        icon={Landmark}
      />

      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard title="Programmes disponibles" value={patterns.length} icon={Landmark} color="blue" />
        <StatCard title="Actifs" value={activeCount} icon={CheckCircle} color="green" />
        <StatCard title="Niveaux couverts" value={niveauxCouverts} icon={Layers} color="purple" />
      </div>

      {/* Un programme n'est pas une ligne de référentiel : c'est une action —
          l'appliquer à un niveau. La carte porte donc son bouton. */}
      <p className="flex items-start gap-1.5 text-sm text-brand-textMuted dark:text-slate-400">
        <Info size={14} className="mt-0.5 shrink-0" />
        Appliquer un programme copie ses matières et leurs heures hebdomadaires vers le niveau
        correspondant de votre établissement. Vous les ajustez ensuite depuis « Programme de l'école ».
      </p>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-40 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && patterns.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <Landmark size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">Aucun programme national trouvé</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            Le référentiel officiel n'est pas chargé pour ce pays. Le programme de l'école reste
            saisissable matière par matière.
          </p>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {patterns.map((p) => {
          const heures = p.details?.reduce((sum, d) => sum + (d.totalHoursPerWeek ?? 0), 0) ?? 0
          return (
            <div
              key={p.idNationalPattern}
              className={`relative flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
                p.active ? '' : 'opacity-70'
              }`}
            >
              <span className={`absolute inset-x-0 top-0 h-1 ${p.active ? 'bg-brand-teal' : 'bg-slate-300 dark:bg-slate-600'}`} />

              <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">{p.name}</h3>
                    <p className="mt-0.5 font-mono text-xs text-brand-textMuted dark:text-slate-400">{p.code}</p>
                  </div>
                  <Badge variant={p.active ? 'success' : 'default'}>{p.active ? 'Actif' : 'Inactif'}</Badge>
                </div>

                <div className="flex flex-wrap gap-1.5">
                  <Badge variant="info">{p.levelCode}</Badge>
                  {p.version && <Badge variant="default">v{p.version}</Badge>}
                  {p.academicYear && <Badge variant="default">{p.academicYear}</Badge>}
                </div>

                <div className="mt-auto flex items-center gap-4 text-xs text-brand-textMuted dark:text-slate-400">
                  <span className="inline-flex items-center gap-1.5">
                    <BookOpen size={13} />
                    <strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">{p.details?.length ?? 0}</strong>
                    matière{(p.details?.length ?? 0) > 1 ? 's' : ''}
                  </span>
                  {heures > 0 && (
                    <span className="tabular-nums">
                      <strong className="font-semibold text-brand-text dark:text-slate-200">{heures}h</strong> / semaine
                    </span>
                  )}
                </div>
              </div>

              <div className="border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                <Button
                  size="sm" variant="outline" className="w-full"
                  onClick={() => { setApplyTarget(p); setApplyResult(null) }}
                >
                  <Play size={13} /> Appliquer à mon établissement
                </Button>
              </div>
            </div>
          )
        })}
      </div>

      {/* Modal appliquer */}
      <Modal
        open={!!applyTarget}
        onClose={() => { setApplyTarget(null); setApplyResult(null) }}
        title="Appliquer un programme national"
        size="xl"
      >
        {!applyResult ? (
          <div className="space-y-5">
            {/* Quel programme, vers quel niveau, pour combien d'heures : les
                trois choses à vérifier avant de confirmer. */}
            <div className="flex items-center gap-3 rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-brand-teal text-white">
                <Landmark size={20} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-brand-text dark:text-slate-100">{applyTarget?.name}</p>
                <p className="truncate text-xs text-brand-textMuted dark:text-slate-400">
                  Niveau {applyTarget?.levelCode} · {applyTarget?.details?.length ?? 0} matière{(applyTarget?.details?.length ?? 0) > 1 ? 's' : ''}
                </p>
              </div>
              <div className="shrink-0 rounded-lg bg-teal-50 px-3 py-2 text-right dark:bg-teal-500/10">
                <p className="text-lg font-bold leading-none tabular-nums text-brand-teal dark:text-teal-400">
                  {applyTarget?.details?.reduce((sum, d) => sum + (d.totalHoursPerWeek ?? 0), 0) ?? 0}h
                </p>
                <p className="mt-1 text-[11px] font-medium text-brand-teal dark:text-teal-400">par semaine</p>
              </div>
            </div>

            <p className="flex items-start gap-1.5 rounded-lg bg-blue-50 p-3 text-xs text-blue-700 dark:bg-blue-500/10 dark:text-blue-400">
              <Info size={13} className="mt-px shrink-0" />
              Les matières déjà programmées pour ce niveau sont conservées telles quelles :
              l'opération n'écrase rien et ne crée pas de doublon. Elle peut être relancée sans risque.
            </p>

            {applyTarget?.details && applyTarget.details.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">
                  Matières incluses ({applyTarget.details.length})
                </p>
                <div className="max-h-56 overflow-y-auto rounded-lg border border-brand-border p-2 dark:border-slate-700">
                  <div className="grid gap-1.5 sm:grid-cols-2">
                    {applyTarget.details.map((d) => (
                      <div
                        key={d.idNationalPatternDetail}
                        className="flex items-center justify-between gap-2 rounded-md bg-brand-bgSecondary/60 px-2.5 py-1.5 text-sm dark:bg-slate-800/50"
                      >
                        <span className="truncate font-medium text-brand-text dark:text-slate-200">{d.subjectCode}</span>
                        <span className="shrink-0 tabular-nums text-xs text-brand-textMuted dark:text-slate-400">
                          {d.totalHoursPerWeek}h/sem
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            <div>
              <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Année scolaire</label>
              <select
                value={schoolYearId}
                onChange={(e) => setSchoolYearId(e.target.value)}
                className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              >
                <option value="">Toutes les années actives (par défaut)</option>
                {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
              </select>
            </div>

            <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
              <Button variant="outline" type="button" onClick={() => setApplyTarget(null)}>Annuler</Button>
              <Button
                onClick={() => applyTarget && applyMutation.mutate({ id: applyTarget.idNationalPattern, yearId: schoolYearId ? Number(schoolYearId) : undefined })}
                loading={applyMutation.isPending}
              >
                <Play size={15} /> Appliquer le programme
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-start gap-3 rounded-xl border border-green-200 bg-green-50 p-4 dark:border-emerald-500/20 dark:bg-emerald-500/10">
              <CheckCircle size={20} className="text-green-600 dark:text-emerald-400 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="text-sm font-semibold text-green-800 dark:text-emerald-300">
                  Programme appliqué pour le niveau {applyTarget?.levelCode}
                </p>
                <p className="text-xs text-green-700 dark:text-emerald-400">
                  {applyResult.created > 0
                    ? <>{applyResult.created} matière{applyResult.created > 1 ? 's' : ''} nouvellement programmée{applyResult.created > 1 ? 's' : ''} pour ce niveau.</>
                    : <>Aucune nouvelle matière créée — le programme était déjà appliqué pour ce niveau.</>}
                  {applyResult.skipped > 0 && <> {applyResult.skipped} matière{applyResult.skipped > 1 ? 's' : ''} déjà configurée{applyResult.skipped > 1 ? 's' : ''} ({applyResult.skipped > 1 ? 'ignorées' : 'ignorée'}, pas de doublon créé).</>}
                </p>
              </div>
            </div>
            <div className="flex justify-end border-t border-brand-border pt-4 dark:border-slate-700">
              <Button onClick={() => { setApplyTarget(null); setApplyResult(null) }}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
