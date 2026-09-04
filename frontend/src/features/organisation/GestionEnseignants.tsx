import { useState, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, PowerOff, Power, Upload, Download,
  Search, Eye, BarChart2, X, CheckCircle, AlertCircle, UserPlus, UserCheck, KeyRound
} from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { cn } from '@/lib/utils'
import { organisationApi, type Teacher, type TeacherRequest, type TeacherImportResult, type SchoolUser } from '@/api/organisation.api'

// ─── Constants ────────────────────────────────────────────────────────────────

const SPECIALITES = [
  'MATHEMATIQUES', 'LANGUE_ARABE', 'LANGUE_FRANCAISE', 'LANGUE_ANGLAISE',
  'SCIENCES_PHYSIQUES', 'SCIENCES_NATURELLES', 'HISTOIRE_GEOGRAPHIE',
  'EDUCATION_PHYSIQUE', 'INFORMATIQUE', 'EDUCATION_ISLAMIQUE',
  'EDUCATION_CIVIQUE', 'PHILOSOPHIE', 'AUTRE',
]

// ─── Validation schema ────────────────────────────────────────────────────────

const teacherSchema = z.object({
  codeEnseignant: z.string().min(1, 'Obligatoire').max(15),
  numIdentite: z.string().min(1, 'Obligatoire').max(15),
  nom: z.string().min(1, 'Obligatoire').max(64),
  prenom: z.string().min(1, 'Obligatoire').max(64),
  email: z.string().email('Email invalide').max(100).optional().or(z.literal('')),
  telephone: z.string().max(20).optional().or(z.literal('')),
  specialite: z.string().optional().or(z.literal('')),
  maxHeuresSemaine: z.coerce.number().min(1).max(40).optional(),
  maxHeuresJour: z.coerce.number().min(1).max(10).optional(),
  minHeuresJour: z.coerce.number().min(0).max(10).optional(),
  estEnPoste: z.boolean().optional(),
}).refine(
  (d) => {
    if (d.minHeuresJour != null && d.maxHeuresJour != null) {
      return d.minHeuresJour <= d.maxHeuresJour
    }
    return true
  },
  { message: 'Min heures/jour ≤ Max heures/jour', path: ['minHeuresJour'] }
)

type FormData = z.infer<typeof teacherSchema>

// ─── Helpers ──────────────────────────────────────────────────────────────────

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function StatusBadge({ actif }: { actif: boolean }) {
  return (
    <Badge variant={actif ? 'success' : 'danger'}>
      {actif ? 'En poste' : 'Muté'}
    </Badge>
  )
}

// ─── Import Modal ─────────────────────────────────────────────────────────────

function ImportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [dragging, setDragging] = useState(false)
  const [result, setResult] = useState<TeacherImportResult | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const importMutation = useMutation({
    mutationFn: organisationApi.teachers.importFile,
    onSuccess: (data) => {
      setResult(data)
      qc.invalidateQueries({ queryKey: ['teachers'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur lors de l\'import')
    },
  })

  const handleFile = (file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!['csv', 'xlsx', 'xls'].includes(ext ?? '')) {
      toast.error('Format non supporté. Utilisez .csv, .xlsx ou .xls')
      return
    }
    importMutation.mutate(file)
  }

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }, [])

  const handleClose = () => { setResult(null); onClose() }

  const downloadCsv = async () => {
    try {
      const blob = await organisationApi.teachers.downloadTemplateCsv()
      downloadBlob(blob, 'template_enseignants.csv')
    } catch { toast.error('Erreur téléchargement CSV') }
  }

  const downloadExcel = async () => {
    try {
      const blob = await organisationApi.teachers.downloadTemplateExcel()
      downloadBlob(blob, 'template_enseignants.xlsx')
    } catch { toast.error('Erreur téléchargement Excel') }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Importer des enseignants" size="md">
      {result ? (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <CheckCircle className="text-emerald-500 flex-shrink-0" size={32} />
            <div>
              <p className="font-semibold text-brand-text dark:text-slate-100">Import terminé</p>
              <p className="text-sm text-brand-textMuted dark:text-slate-400">{result.totalLignes} lignes traitées</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="bg-emerald-50 dark:bg-emerald-500/10 rounded-lg p-3 text-center">
              <p className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">{result.importes}</p>
              <p className="text-xs text-emerald-700 dark:text-emerald-400">Importés</p>
            </div>
            <div className="bg-amber-50 dark:bg-amber-500/10 rounded-lg p-3 text-center">
              <p className="text-2xl font-bold text-amber-600 dark:text-amber-400">{result.ignores}</p>
              <p className="text-xs text-amber-700 dark:text-amber-400">Ignorés</p>
            </div>
            <div className="bg-red-50 dark:bg-red-500/10 rounded-lg p-3 text-center">
              <p className="text-2xl font-bold text-red-600 dark:text-red-400">{result.erreurs?.length ?? 0}</p>
              <p className="text-xs text-red-700 dark:text-red-400">Erreurs</p>
            </div>
          </div>

          {result.erreurs?.length > 0 && (
            <div className="border border-red-200 dark:border-red-500/20 rounded-lg overflow-hidden">
              <div className="bg-red-50 dark:bg-red-500/10 px-3 py-2 text-xs font-medium text-red-700 dark:text-red-400">
                Lignes en erreur
              </div>
              <div className="max-h-40 overflow-y-auto divide-y divide-red-100 dark:divide-red-500/20">
                {result.erreurs.map((err, i) => (
                  <div key={i} className="px-3 py-2 text-xs flex gap-2">
                    <span className="font-mono text-brand-textMuted dark:text-slate-400">Ligne {err.ligne}</span>
                    <span className="text-red-600 dark:text-red-400">{err.message}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <Button onClick={() => setResult(null)} variant="secondary">Importer un autre fichier</Button>
            <Button onClick={handleClose}>Fermer</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-5">
          {/* Download templates */}
          <div>
            <p className="text-sm font-medium text-brand-text dark:text-slate-200 mb-2">Étape 1 — Téléchargez le modèle</p>
            <div className="flex gap-3">
              <Button variant="secondary" size="sm" onClick={downloadCsv}>
                <Download size={14} /> Modèle CSV
              </Button>
              <Button variant="secondary" size="sm" onClick={downloadExcel}>
                <Download size={14} /> Modèle Excel
              </Button>
            </div>
            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-2">
              Colonnes : codeEnseignant, numIdentite, nom, prenom, email, telephone, maxHeuresSemaine, specialite
            </p>
          </div>

          {/* Drop zone */}
          <div>
            <p className="text-sm font-medium text-brand-text dark:text-slate-200 mb-2">Étape 2 — Importez votre fichier</p>
            <div
              onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              onClick={() => inputRef.current?.click()}
              className={cn(
                'border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors',
                dragging
                  ? 'border-brand-blue bg-blue-50 dark:bg-blue-500/10'
                  : 'border-brand-border dark:border-slate-700 hover:border-brand-blue hover:bg-brand-bgSecondary dark:hover:bg-slate-800'
              )}
            >
              {importMutation.isPending ? (
                <div className="flex flex-col items-center gap-2">
                  <div className="w-8 h-8 border-2 border-brand-blue border-t-transparent rounded-full animate-spin" />
                  <p className="text-sm text-brand-textMuted dark:text-slate-400">Import en cours…</p>
                </div>
              ) : (
                <>
                  <Upload size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
                  <p className="text-sm font-medium text-brand-text dark:text-slate-200">Glissez votre fichier ici</p>
                  <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">ou cliquez pour parcourir</p>
                  <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-2">Formats acceptés : .csv · .xlsx · .xls</p>
                </>
              )}
            </div>
            <input
              ref={inputRef}
              type="file"
              accept=".csv,.xlsx,.xls"
              className="hidden"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f) }}
            />
          </div>

          <div className="flex justify-end">
            <Button variant="secondary" onClick={handleClose}>Annuler</Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

// ─── Detail Modal ─────────────────────────────────────────────────────────────

function DetailModal({ teacher, open, onClose }: { teacher: Teacher | null; open: boolean; onClose: () => void }) {
  const { data: detail, isLoading } = useQuery({
    queryKey: ['teacher-detail', teacher?.idEnseignant],
    queryFn: () => organisationApi.teachers.getWithAssignments(teacher!.idEnseignant),
    enabled: !!teacher && open,
  })

  const t = detail ?? teacher

  return (
    <Modal open={open} onClose={onClose} title="Fiche Enseignant" size="lg">
      {isLoading || !t ? (
        <div className="flex justify-center py-8">
          <div className="w-8 h-8 border-2 border-brand-blue border-t-transparent rounded-full animate-spin" />
        </div>
      ) : (
        <div className="space-y-6">
          {/* Header */}
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-full bg-brand-navy text-white flex items-center justify-center text-xl font-bold flex-shrink-0">
              {t.prenom?.[0]}{t.nom?.[0]}
            </div>
            <div>
              <h3 className="text-lg font-bold text-brand-text dark:text-slate-100">{t.nomComplet || `${t.prenom} ${t.nom}`}</h3>
              <p className="text-sm text-brand-textMuted dark:text-slate-400">{t.codeEnseignant} · {t.specialite || '—'}</p>
              <div className="mt-1"><StatusBadge actif={t.estEnPoste} /></div>
            </div>
          </div>

          {/* Info grid */}
          <div className="grid grid-cols-2 gap-4">
            {[
              ['N° Identité', t.numIdentite],
              ['Email', t.email || '—'],
              ['Téléphone', t.telephone || '—'],
              ['Max H/semaine', t.maxHeuresSemaine ? `${t.maxHeuresSemaine}h` : '—'],
              ['Max H/jour', t.maxHeuresJour ? `${t.maxHeuresJour}h` : '—'],
              ['Min H/jour', t.minHeuresJour != null ? `${t.minHeuresJour}h` : '—'],
            ].map(([label, value]) => (
              <div key={label}>
                <p className="text-xs text-brand-textMuted dark:text-slate-400">{label}</p>
                <p className="text-sm font-medium text-brand-text dark:text-slate-200">{value}</p>
              </div>
            ))}
          </div>

          {/* Charge horaire */}
          {t.maxHeuresSemaine && t.totalHeures != null && (
            <div>
              <p className="text-sm font-medium text-brand-text dark:text-slate-200 mb-2">Charge horaire</p>
              <div className="flex items-center gap-3">
                <div className="flex-1 h-2 bg-brand-bgSecondary dark:bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full bg-brand-blue transition-all"
                    style={{ width: `${Math.min(100, (t.totalHeures / t.maxHeuresSemaine) * 100)}%` }}
                  />
                </div>
                <span className="text-sm font-medium text-brand-text dark:text-slate-200 whitespace-nowrap">
                  {t.totalHeures}h / {t.maxHeuresSemaine}h
                </span>
              </div>
            </div>
          )}

          {/* Affectations */}
          <div>
            <p className="text-sm font-medium text-brand-text dark:text-slate-200 mb-2">
              Affectations ({t.affectations?.length ?? t.nombreAffectations ?? 0})
            </p>
            {!t.affectations || t.affectations.length === 0 ? (
              <p className="text-sm text-brand-textMuted dark:text-slate-400 italic">Aucune affectation</p>
            ) : (
              <div className="border border-brand-border dark:border-slate-700 rounded-lg overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-brand-bgSecondary dark:bg-slate-800">
                    <tr>
                      {['Classe', 'Matière', 'Type séance', 'Statut'].map(h => (
                        <th key={h} className="px-3 py-2 text-start text-xs font-medium text-brand-textMuted dark:text-slate-400">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-brand-border dark:divide-slate-700 text-brand-text dark:text-slate-200">
                    {t.affectations.map((a) => (
                      <tr key={a.idTeachingAssignment}>
                        <td className="px-3 py-2 font-medium">{a.nomClasse}</td>
                        <td className="px-3 py-2">{a.nomMatiere}</td>
                        <td className="px-3 py-2"><Badge variant="info">{a.typeSeance}</Badge></td>
                        <td className="px-3 py-2"><Badge variant={a.isActive ? 'success' : 'default'}>{a.isActive ? 'Actif' : 'Inactif'}</Badge></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}

// ─── Workload Modal ───────────────────────────────────────────────────────────

function WorkloadModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { data: workload = [], isLoading } = useQuery({
    queryKey: ['teachers-workload'],
    queryFn: organisationApi.teachers.getWorkload,
    enabled: open,
  })

  return (
    <Modal open={open} onClose={onClose} title="Charge Horaire — Vue Globale" size="xl">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-brand-bgSecondary dark:bg-slate-800">
              {['Enseignant', 'Spécialité', 'Affectations', 'Total H', 'Max H/sem', 'Charge', 'Statut'].map(h => (
                <th key={h} className="px-4 py-3 text-start text-xs font-medium text-brand-textMuted dark:text-slate-400">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-border dark:divide-slate-700">
            {isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr key={i}>
                  {Array.from({ length: 7 }).map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded animate-pulse" />
                    </td>
                  ))}
                </tr>
              ))
            ) : workload.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-brand-textMuted dark:text-slate-400">Aucun enseignant</td></tr>
            ) : (
              workload.map((t: Teacher) => {
                const pct = t.maxHeuresSemaine && t.totalHeures != null
                  ? Math.min(100, Math.round((t.totalHeures / t.maxHeuresSemaine) * 100))
                  : null
                const overloaded = pct != null && pct > 100
                const underloaded = pct != null && pct < 60
                return (
                  <tr key={t.idEnseignant} className="hover:bg-brand-bgSecondary/50 dark:hover:bg-slate-800/50 text-brand-text dark:text-slate-200">
                    <td className="px-4 py-3 font-medium">{t.nomComplet || `${t.prenom} ${t.nom}`}</td>
                    <td className="px-4 py-3 text-brand-textMuted dark:text-slate-400">{t.specialite || '—'}</td>
                    <td className="px-4 py-3 text-center">{t.nombreAffectations ?? 0}</td>
                    <td className="px-4 py-3">{t.totalHeures != null ? `${t.totalHeures}h` : '—'}</td>
                    <td className="px-4 py-3">{t.maxHeuresSemaine ? `${t.maxHeuresSemaine}h` : '—'}</td>
                    <td className="px-4 py-3 min-w-[120px]">
                      {pct != null ? (
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-1.5 bg-brand-bgSecondary dark:bg-slate-800 rounded-full overflow-hidden">
                            <div
                              className={cn('h-full rounded-full', overloaded ? 'bg-red-500' : underloaded ? 'bg-amber-400' : 'bg-emerald-500')}
                              style={{ width: `${Math.min(100, pct)}%` }}
                            />
                          </div>
                          <span className={cn('text-xs font-medium', overloaded ? 'text-red-600 dark:text-red-400' : underloaded ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400')}>
                            {pct}%
                          </span>
                        </div>
                      ) : '—'}
                    </td>
                    <td className="px-4 py-3">
                      {pct == null ? <Badge variant="default">Non planifié</Badge>
                        : overloaded ? <Badge variant="danger">Surchargé</Badge>
                        : underloaded ? <Badge variant="warning">Sous-chargé</Badge>
                        : <Badge variant="success">OK</Badge>}
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </Modal>
  )
}

// ─── Teacher Form Modal ────────────────────────────────────────────────────────

function TeacherFormModal({
  open, onClose, editing
}: {
  open: boolean
  onClose: () => void
  editing: Teacher | null
}) {
  const qc = useQueryClient()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof teacherSchema>, unknown, FormData>({
    resolver: zodResolver(teacherSchema),
  })

  // Sync editing → form
  const prevEditing = useRef<Teacher | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    if (editing) {
      reset({
        codeEnseignant: editing.codeEnseignant,
        numIdentite: editing.numIdentite,
        nom: editing.nom,
        prenom: editing.prenom,
        email: editing.email ?? '',
        telephone: editing.telephone ?? '',
        specialite: editing.specialite ?? '',
        maxHeuresSemaine: editing.maxHeuresSemaine,
        maxHeuresJour: editing.maxHeuresJour,
        minHeuresJour: editing.minHeuresJour,
        estEnPoste: editing.estEnPoste,
      })
    } else {
      reset({ estEnPoste: true })
    }
  }

  const saveMutation = useMutation({
    mutationFn: (data: FormData) => {
      const payload: TeacherRequest = {
        ...data,
        email: data.email || undefined,
        telephone: data.telephone || undefined,
        specialite: data.specialite || undefined,
      }
      return editing
        ? organisationApi.teachers.update(editing.idEnseignant, payload)
        : organisationApi.teachers.create(payload)
    },
    onSuccess: () => {
      toast.success(editing ? 'Enseignant mis à jour' : 'Enseignant créé')
      qc.invalidateQueries({ queryKey: ['teachers'] })
      onClose()
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur lors de l\'enregistrement')
    },
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Modifier — ${editing.nomComplet || `${editing.prenom} ${editing.nom}`}` : 'Nouvel Enseignant'}
      size="lg"
    >
      <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
        {/* Identité */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Identité</p>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Code enseignant" required placeholder="T001" error={errors.codeEnseignant?.message} {...register('codeEnseignant')} />
            <Input label="N° d'identité" required placeholder="12345678" error={errors.numIdentite?.message} {...register('numIdentite')} />
            <Input label="Nom" required placeholder="Ben Ali" error={errors.nom?.message} {...register('nom')} />
            <Input label="Prénom" required placeholder="Mohamed" error={errors.prenom?.message} {...register('prenom')} />
          </div>
        </div>

        {/* Contact */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Contact</p>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Email" type="email" placeholder="m.benali@ecole.tn" error={errors.email?.message} {...register('email')} />
            <Input label="Téléphone" placeholder="+216 20 000 000" error={errors.telephone?.message} {...register('telephone')} />
          </div>
        </div>

        {/* Pédagogie */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Pédagogie</p>
          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Spécialité"
              placeholder="— Choisir —"
              options={SPECIALITES.map(s => ({ value: s, label: s.replace(/_/g, ' ') }))}
              error={errors.specialite?.message}
              {...register('specialite')}
            />
            <div className="flex items-end gap-2">
              <div className="flex items-center gap-2 mt-6">
                <input type="checkbox" id="estEnPoste" {...register('estEnPoste')} className="w-4 h-4 rounded accent-brand-navy" />
                <label htmlFor="estEnPoste" className="text-sm font-medium text-brand-text dark:text-slate-200 cursor-pointer">En poste</label>
              </div>
            </div>
          </div>
        </div>

        {/* Contraintes horaires */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Contraintes horaires</p>
          <div className="grid grid-cols-3 gap-4">
            <Input label="Max H/semaine" type="number" min={1} max={40} placeholder="20" error={errors.maxHeuresSemaine?.message} {...register('maxHeuresSemaine')} />
            <Input label="Max H/jour" type="number" min={1} max={10} placeholder="6" error={errors.maxHeuresJour?.message} {...register('maxHeuresJour')} />
            <Input label="Min H/jour" type="number" min={0} max={10} placeholder="2" error={errors.minHeuresJour?.message} {...register('minHeuresJour')} />
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-2 border-t border-brand-border dark:border-slate-700">
          <Button type="button" variant="secondary" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={saveMutation.isPending}>
            {editing ? 'Mettre à jour' : 'Créer l\'enseignant'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────

type FilterStatus = 'all' | 'actif' | 'mute'

export function GestionEnseignants() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState<FilterStatus>('all')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Teacher | null>(null)
  const [detailTeacher, setDetailTeacher] = useState<Teacher | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Teacher | null>(null)
  const [statusTarget, setStatusTarget] = useState<{ teacher: Teacher; action: 'deactivate' | 'reactivate' } | null>(null)
  const [showImport, setShowImport] = useState(false)
  const [showWorkload, setShowWorkload] = useState(false)
  const [accountTarget, setAccountTarget] = useState<Teacher | null>(null)
  const [createdAccount, setCreatedAccount] = useState<SchoolUser | null>(null)

  const { data: teachers = [], isLoading } = useQuery({
    queryKey: ['teachers'],
    queryFn: organisationApi.teachers.list,
  })

  // Comptes enseignants existants → pour savoir quelles fiches ont déjà un accès
  const { data: teacherAccounts = [] } = useQuery({
    queryKey: ['school-users', 'TEACHER'],
    queryFn: () => organisationApi.users.list('TEACHER'),
  })
  const hasAccount = (t: Teacher) =>
    teacherAccounts.some((u) => u.enseignantId === t.idEnseignant || (!!t.email && u.email === t.email))

  const createAccountMutation = useMutation({
    mutationFn: (t: Teacher) => organisationApi.users.create({ role: 'TEACHER', teacherId: t.idEnseignant }),
    onSuccess: (data) => {
      toast.success('Compte créé')
      qc.invalidateQueries({ queryKey: ['school-users'] })
      setAccountTarget(null)
      if (data.tempPassword) setCreatedAccount(data)
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Erreur lors de la création du compte'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.teachers.delete(id),
    onSuccess: () => { toast.success('Enseignant supprimé'); qc.invalidateQueries({ queryKey: ['teachers'] }); setDeleteTarget(null) },
    onError: () => toast.error('Erreur lors de la suppression'),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'deactivate' | 'reactivate' }) =>
      action === 'deactivate'
        ? organisationApi.teachers.deactivate(id)
        : organisationApi.teachers.reactivate(id),
    onSuccess: (_, vars) => {
      toast.success(vars.action === 'deactivate' ? 'Enseignant désactivé (muté)' : 'Enseignant réactivé')
      qc.invalidateQueries({ queryKey: ['teachers'] })
      setStatusTarget(null)
    },
    onError: () => toast.error('Erreur lors du changement de statut'),
  })

  // Stats
  const total = teachers.length
  const enPoste = teachers.filter((t: Teacher) => t.estEnPoste).length
  const mutes = total - enPoste

  // Filter + search
  const filtered = teachers.filter((t: Teacher) => {
    const matchSearch = !search ||
      `${t.nom} ${t.prenom} ${t.codeEnseignant} ${t.specialite ?? ''}`.toLowerCase().includes(search.toLowerCase())
    const matchStatus =
      filterStatus === 'all' ||
      (filterStatus === 'actif' && t.estEnPoste) ||
      (filterStatus === 'mute' && !t.estEnPoste)
    return matchSearch && matchStatus
  })

  const openCreate = () => { setEditing(null); setShowForm(true) }
  const openEdit = (t: Teacher) => { setEditing(t); setShowForm(true) }

  const columns: Column<Teacher>[] = [
    {
      key: 'codeEnseignant',
      header: 'Code',
      render: (t) => <span className="font-mono text-brand-textMuted dark:text-slate-400 text-xs">{t.codeEnseignant}</span>,
    },
    {
      key: 'nomComplet',
      header: 'Nom complet',
      render: (t) => (
        <div>
          <p className="font-medium text-brand-text dark:text-slate-200">{t.nomComplet || `${t.prenom} ${t.nom}`}</p>
          <p className="text-xs text-brand-textMuted dark:text-slate-400">{t.email || ''}</p>
        </div>
      ),
    },
    {
      key: 'specialite',
      header: 'Spécialité',
      render: (t) => t.specialite
        ? <Badge variant="info">{t.specialite.replace(/_/g, ' ')}</Badge>
        : <span className="text-brand-textMuted dark:text-slate-400">—</span>,
    },
    {
      key: 'telephone',
      header: 'Téléphone',
      render: (t) => <span className="text-brand-textMuted dark:text-slate-400">{t.telephone || '—'}</span>,
    },
    {
      key: 'maxHeuresSemaine',
      header: 'Max H/sem',
      render: (t) => t.maxHeuresSemaine ? `${t.maxHeuresSemaine}h` : '—',
    },
    {
      key: 'estEnPoste',
      header: 'Statut',
      render: (t) => <StatusBadge actif={t.estEnPoste} />,
    },
    {
      key: 'actions',
      header: '',
      className: 'w-px',
      render: (t) => (
        <div className="flex items-center gap-1 justify-end">
          {hasAccount(t) ? (
            <span title="Compte d'accès actif" className="p-1.5 text-emerald-600 dark:text-emerald-400">
              <UserCheck size={15} />
            </span>
          ) : (
            <button
              title={t.email ? 'Créer un compte d\'accès' : 'Ajoutez un email à la fiche pour créer un compte'}
              disabled={!t.email}
              onClick={() => setAccountTarget(t)}
              className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-navy dark:hover:text-slate-200 transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
            >
              <UserPlus size={15} />
            </button>
          )}
          <button
            title="Voir détail"
            onClick={() => setDetailTeacher(t)}
            className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-navy dark:hover:text-slate-200 transition-colors"
          >
            <Eye size={15} />
          </button>
          <button
            title="Modifier"
            onClick={() => openEdit(t)}
            className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-navy dark:hover:text-slate-200 transition-colors"
          >
            <Pencil size={15} />
          </button>
          {t.estEnPoste ? (
            <button
              title="Désactiver (muté)"
              onClick={() => setStatusTarget({ teacher: t, action: 'deactivate' })}
              className="p-1.5 rounded-md hover:bg-amber-50 dark:hover:bg-amber-500/10 text-brand-textMuted dark:text-slate-400 hover:text-amber-600 dark:hover:text-amber-400 transition-colors"
            >
              <PowerOff size={15} />
            </button>
          ) : (
            <button
              title="Réactiver"
              onClick={() => setStatusTarget({ teacher: t, action: 'reactivate' })}
              className="p-1.5 rounded-md hover:bg-emerald-50 dark:hover:bg-emerald-500/10 text-brand-textMuted dark:text-slate-400 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors"
            >
              <Power size={15} />
            </button>
          )}
          <button
            title="Supprimer"
            onClick={() => setDeleteTarget(t)}
            className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-500/10 text-brand-textMuted dark:text-slate-400 hover:text-danger transition-colors"
          >
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Enseignants"
        subtitle="Corps enseignant de l'établissement"
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => setShowWorkload(true)}>
              <BarChart2 size={15} /> Charge horaire
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setShowImport(true)}>
              <Upload size={15} /> Importer
            </Button>
            <Button size="sm" onClick={openCreate}>
              <Plus size={15} /> Nouvel enseignant
            </Button>
          </div>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <StatCard title="Total enseignants" value={total} icon={AlertCircle} color="blue" />
        <StatCard title="En poste" value={enPoste} icon={CheckCircle} color="green" />
        <StatCard title="Mutés" value={mutes} icon={PowerOff} color="amber" />
      </div>

      {/* Filters */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 mb-4 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px] max-w-xs">
          <Search size={15} className="absolute start-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Nom, code, spécialité…"
            className="w-full ps-9 pe-3 py-2 text-sm border border-brand-border dark:border-slate-700 rounded-lg bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/20 focus:border-brand-blue"
          />
          {search && (
            <button onClick={() => setSearch('')} className="absolute end-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
              <X size={14} />
            </button>
          )}
        </div>

        <div className="flex gap-1 border border-brand-border dark:border-slate-700 rounded-lg overflow-hidden">
          {([['all', 'Tous'], ['actif', 'En poste'], ['mute', 'Mutés']] as const).map(([val, label]) => (
            <button
              key={val}
              onClick={() => setFilterStatus(val)}
              className={cn(
                'px-3 py-1.5 text-sm transition-colors',
                filterStatus === val
                  ? 'bg-brand-navy text-white'
                  : 'text-brand-textMuted dark:text-slate-400 hover:bg-brand-bgSecondary dark:hover:bg-slate-800'
              )}
            >
              {label}
            </button>
          ))}
        </div>

        <span className="text-sm text-brand-textMuted dark:text-slate-400 ms-auto">
          {filtered.length} résultat{filtered.length !== 1 ? 's' : ''}
        </span>
      </div>

      {/* Table */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700">
        <DataTable
          columns={columns}
          data={filtered}
          keyField="idEnseignant"
          loading={isLoading}
          emptyMessage="Aucun enseignant trouvé"
        />
      </div>

      {/* Modals */}
      <TeacherFormModal open={showForm} onClose={() => setShowForm(false)} editing={editing} />

      <DetailModal
        teacher={detailTeacher}
        open={!!detailTeacher}
        onClose={() => setDetailTeacher(null)}
      />

      <ImportModal open={showImport} onClose={() => setShowImport(false)} />

      <WorkloadModal open={showWorkload} onClose={() => setShowWorkload(false)} />

      <ConfirmDialog
        open={!!statusTarget}
        title={statusTarget?.action === 'deactivate' ? 'Désactiver l\'enseignant ?' : 'Réactiver l\'enseignant ?'}
        message={
          statusTarget?.action === 'deactivate'
            ? `"${statusTarget.teacher.nomComplet || `${statusTarget.teacher.prenom} ${statusTarget.teacher.nom}`}" sera marqué comme muté. Il ne sera plus disponible pour les affectations.`
            : `"${statusTarget?.teacher.nomComplet || `${statusTarget?.teacher.prenom} ${statusTarget?.teacher.nom}`}" sera remis en poste.`
        }
        confirmLabel={statusTarget?.action === 'deactivate' ? 'Désactiver' : 'Réactiver'}
        variant={statusTarget?.action === 'deactivate' ? 'danger' : 'primary'}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (!statusTarget) return
          statusMutation.mutate({ id: statusTarget.teacher.idEnseignant, action: statusTarget.action })
        }}
        onClose={() => setStatusTarget(null)}
      />

      <ConfirmDialog
        open={!!accountTarget}
        title="Créer un compte d'accès ?"
        message={`Un compte de connexion (rôle Enseignant) sera créé pour "${accountTarget?.nomComplet || `${accountTarget?.prenom} ${accountTarget?.nom}`}" avec l'email ${accountTarget?.email}. Un mot de passe temporaire vous sera affiché.`}
        confirmLabel="Créer le compte"
        variant="primary"
        loading={createAccountMutation.isPending}
        onConfirm={() => accountTarget && createAccountMutation.mutate(accountTarget)}
        onClose={() => setAccountTarget(null)}
      />

      {/* Mot de passe temporaire du compte créé */}
      <Modal open={!!createdAccount} onClose={() => setCreatedAccount(null)} title="Compte créé" size="sm">
        {createdAccount && (
          <div className="space-y-4">
            <div className="p-4 rounded-lg bg-green-50 dark:bg-emerald-500/10 border border-green-200 dark:border-emerald-500/20">
              <p className="text-sm font-semibold text-green-800 dark:text-emerald-300 mb-1">{createdAccount.nomComplet}</p>
              <p className="text-xs text-green-700 dark:text-emerald-400">{createdAccount.email}</p>
              <p className="text-xs text-green-700 dark:text-emerald-400 mt-1">Un email avec ces informations vient de lui être envoyé.</p>
            </div>
            <div className="p-4 rounded-lg bg-yellow-50 dark:bg-amber-500/10 border border-yellow-200 dark:border-amber-500/20">
              <div className="flex items-center gap-2 mb-1">
                <KeyRound size={14} className="text-yellow-700 dark:text-amber-400" />
                <p className="text-sm font-semibold text-yellow-800 dark:text-amber-300">Mot de passe temporaire</p>
              </div>
              <p className="font-mono font-bold text-yellow-900 dark:text-amber-200 text-lg">{createdAccount.tempPassword}</p>
              <p className="text-xs text-yellow-700 dark:text-amber-400 mt-1">
                En cas de non-réception de l'email, communiquez-lui ce mot de passe. Il devra le changer à la première connexion.
              </p>
            </div>
            <div className="flex justify-end">
              <Button onClick={() => setCreatedAccount(null)}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Supprimer l'enseignant ?"
        message={`Cette action est irréversible. Supprimer définitivement "${deleteTarget?.nomComplet || `${deleteTarget?.prenom} ${deleteTarget?.nom}`}" ?`}
        confirmLabel="Supprimer"
        variant="danger"
        loading={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idEnseignant)}
        onClose={() => setDeleteTarget(null)}
      />
    </div>
  )
}

export default GestionEnseignants
