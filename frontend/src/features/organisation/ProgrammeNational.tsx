import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Play, CheckCircle } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { DataTable, type Column } from '@/components/ui/DataTable'
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

  const columns: Column<NationalPattern>[] = [
    { key: 'code', header: 'Code', render: (p) => <span className="font-mono font-semibold text-sm">{p.code}</span> },
    { key: 'name', header: 'Nom', render: (p) => <span className="font-medium">{p.name}</span> },
    { key: 'levelCode', header: 'Niveau', render: (p) => <Badge variant="default">{p.levelCode}</Badge> },
    { key: 'version', header: 'Version', render: (p) => p.version ?? '—' },
    { key: 'academicYear', header: 'Année', render: (p) => p.academicYear ?? '—' },
    { key: 'active', header: 'Statut', render: (p) => <Badge variant={p.active ? 'success' : 'default'}>{p.active ? 'Actif' : 'Inactif'}</Badge> },
    {
      key: 'details', header: 'Matières',
      render: (p) => <span className="text-sm text-brand-textMuted">{p.details?.length ?? 0} matière(s)</span>,
    },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (p) => (
        <Button size="sm" variant="outline" onClick={() => { setApplyTarget(p); setApplyResult(null) }}>
          <Play size={12} /> Appliquer
        </Button>
      ),
    },
  ]

  const activeCount = patterns.filter((p) => p.active).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Programme national"
        subtitle="Programmes officiels du Ministère de l'Éducation (Tunisie)"
      />

      <div className="grid grid-cols-3 gap-3">
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4">
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Total programmes</p>
          <p className="text-2xl font-bold text-brand-text dark:text-slate-100">{patterns.length}</p>
        </div>
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4">
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Actifs</p>
          <p className="text-2xl font-bold text-green-600 dark:text-emerald-400">{activeCount}</p>
        </div>
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4">
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Niveaux couverts</p>
          <p className="text-2xl font-bold text-brand-text dark:text-slate-100">{new Set(patterns.map((p) => p.levelCode)).size}</p>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={patterns}
        keyField="idNationalPattern"
        loading={isLoading}
        emptyMessage="Aucun programme national trouvé"
      />

      {/* Modal appliquer */}
      <Modal
        open={!!applyTarget}
        onClose={() => { setApplyTarget(null); setApplyResult(null) }}
        title={`Appliquer le programme — ${applyTarget?.name}`}
        size="md"
      >
        {!applyResult ? (
          <div className="space-y-4">
            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              Ce programme définira les matières et heures hebdomadaires pour le niveau <strong>{applyTarget?.levelCode}</strong>.
              Les affectations existantes pour les mêmes classes seront conservées (pas de duplication).
            </p>

            {applyTarget?.details && applyTarget.details.length > 0 && (
              <div className="bg-brand-bgSecondary dark:bg-slate-800 rounded-xl p-3 max-h-48 overflow-y-auto">
                <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 mb-2">Matières incluses :</p>
                <div className="grid grid-cols-2 gap-1.5">
                  {applyTarget.details.map((d) => (
                    <div key={d.idNationalPatternDetail} className="flex justify-between text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 rounded px-2 py-1">
                      <span className="font-medium">{d.subjectCode}</span>
                      <span className="text-brand-textMuted dark:text-slate-400">{d.totalHoursPerWeek}h/sem</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Année scolaire (optionnel)</label>
              <select
                value={schoolYearId}
                onChange={(e) => setSchoolYearId(e.target.value)}
                className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
              >
                <option value="">Toutes les années actives</option>
                {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
              </select>
            </div>

            <div className="flex justify-end gap-2 pt-2">
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
            <div className="flex items-start gap-3 p-4 bg-green-50 dark:bg-emerald-500/10 border border-green-200 dark:border-emerald-500/20 rounded-xl">
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
            <div className="flex justify-end">
              <Button onClick={() => { setApplyTarget(null); setApplyResult(null) }}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
