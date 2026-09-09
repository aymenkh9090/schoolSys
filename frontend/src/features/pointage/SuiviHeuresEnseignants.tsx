import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Search, Plus, Clock, TrendingUp, AlertTriangle } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { pointageApi, type SuiviHeuresEnseignant, type MiseAJourHeuresRequete } from '@/api/pointage.api'
import { organisationApi } from '@/api/organisation.api'

const schema = z.object({
  enseignantId: z.coerce.number().min(1, 'Obligatoire'),
  numeroSemaine: z.coerce.number().min(1).max(53, 'Semaine invalide'),
  anneeAcademique: z.string().min(1, 'Obligatoire'),
  heuresPrevues: z.coerce.number().min(0),
  heuresRealisees: z.coerce.number().min(0),
  notes: z.string().optional(),
})

type FormData = z.infer<typeof schema>

function getCurrentWeek() {
  const now = new Date()
  const start = new Date(now.getFullYear(), 0, 1)
  return Math.ceil(((now.getTime() - start.getTime()) / 86400000 + start.getDay() + 1) / 7)
}

export default function SuiviHeuresEnseignants() {
  const qc = useQueryClient()
  const [enseignantId, setEnseignantId] = useState<number | null>(null)
  const [anneeAcademique, setAnneeAcademique] = useState('2024-2025')
  const [query, setQuery] = useState<{ id: number; annee: string } | null>(null)
  const [open, setOpen] = useState(false)

  const { data: teachers = [] } = useQuery({ queryKey: ['teachers'], queryFn: organisationApi.teachers.list })

  const { data: suivi = [], isLoading } = useQuery({
    queryKey: ['suivi-heures', query],
    queryFn: () => pointageApi.suiviHeures.annuel(query!.id, query!.annee),
    enabled: query !== null,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      anneeAcademique: '2024-2025',
      numeroSemaine: getCurrentWeek(),
      heuresPrevues: 18,
      heuresRealisees: 0,
    },
  })

  const muttreAJour = useMutation({
    mutationFn: (dto: MiseAJourHeuresRequete) => pointageApi.suiviHeures.mettreAJour(dto),
    onSuccess: () => {
      toast.success('Suivi mis à jour')
      qc.invalidateQueries({ queryKey: ['suivi-heures', query] })
      setOpen(false)
      reset()
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  function handleSearch() {
    if (enseignantId) setQuery({ id: enseignantId, annee: anneeAcademique })
  }

  const totalPrevues = suivi.reduce((s, w) => s + (w.heuresPrevues ?? 0), 0)
  const totalRealisees = suivi.reduce((s, w) => s + (w.heuresRealisees ?? 0), 0)
  const tauxMoyen = suivi.length > 0 ? suivi.reduce((s, w) => s + (w.tauxPresence ?? 0), 0) / suivi.length : 0

  const columns: Column<SuiviHeuresEnseignant>[] = [
    { key: 'numeroSemaine', header: 'Semaine', render: (s) => <span className="font-semibold">S{s.numeroSemaine}</span> },
    { key: 'heuresPrevues', header: 'Prévues', render: (s) => `${s.heuresPrevues}h` },
    { key: 'heuresRealisees', header: 'Réalisées', render: (s) => <span className={s.heuresRealisees < s.heuresPrevues ? 'text-red-500 font-medium' : 'text-green-600 font-medium'}>{s.heuresRealisees}h</span> },
    { key: 'heuresManquees', header: 'Manquées', render: (s) => s.heuresManquees > 0 ? <span className="text-red-500">{s.heuresManquees}h</span> : <span className="text-green-600">—</span> },
    {
      key: 'tauxPresence', header: 'Taux présence',
      render: (s) => (
        <div className="flex items-center gap-2">
          <div className="w-24 h-1.5 rounded-full bg-brand-bgSecondary">
            <div
              className={`h-1.5 rounded-full ${s.tauxPresence >= 80 ? 'bg-green-500' : s.tauxPresence >= 60 ? 'bg-yellow-400' : 'bg-red-500'}`}
              style={{ width: `${Math.min(100, s.tauxPresence)}%` }}
            />
          </div>
          <span className="text-sm font-medium">{s.tauxPresence.toFixed(0)}%</span>
        </div>
      ),
    },
    { key: 'notes', header: 'Notes', render: (s) => <span className="text-xs text-brand-textMuted dark:text-slate-400">{s.notes || '—'}</span> },
  ]

  return (
    <div className="space-y-6">
      <PageHero
        title="Suivi des heures enseignants"
        subtitle="Heures prévues vs réalisées par semaine"
        icon={TrendingUp}
        actions={
          <Button variant="outline" className="border-white/30 bg-white/15 text-white backdrop-blur hover:bg-white/25" onClick={() => setOpen(true)}>
            <Plus size={16} /> Mettre à jour
          </Button>
        }
      />

      {/* Filtres */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4">
        <div className="flex flex-wrap gap-4 items-end">
          <div className="w-56">
            <Select
              label="Enseignant *"
              placeholder="Sélectionner un enseignant"
              value={enseignantId ?? ''}
              onChange={(e) => setEnseignantId(e.target.value ? Number(e.target.value) : null)}
              options={teachers.map((t) => ({ value: t.idEnseignant, label: t.nomComplet || `${t.prenom} ${t.nom}` }))}
            />
          </div>
          <div className="w-36">
            <Input
              label="Année académique"
              value={anneeAcademique}
              onChange={(e) => setAnneeAcademique(e.target.value)}
              placeholder="2024-2025"
            />
          </div>
          <Button onClick={handleSearch} loading={isLoading}><Search size={16} /> Afficher</Button>
        </div>
      </div>

      {/* Résumé */}
      {query && (
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center">
              <Clock size={18} className="text-brand-blue" />
            </div>
            <div>
              <p className="text-xs text-brand-textMuted dark:text-slate-400">Heures prévues</p>
              <p className="text-xl font-bold text-brand-text dark:text-slate-100">{totalPrevues}h</p>
            </div>
          </div>
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-green-50 dark:bg-emerald-500/10 flex items-center justify-center">
              <TrendingUp size={18} className="text-green-600 dark:text-emerald-400" />
            </div>
            <div>
              <p className="text-xs text-brand-textMuted dark:text-slate-400">Heures réalisées</p>
              <p className="text-xl font-bold text-brand-text dark:text-slate-100">{totalRealisees}h</p>
            </div>
          </div>
          <div className={`bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 flex items-center gap-3`}>
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${tauxMoyen >= 80 ? 'bg-green-50 dark:bg-emerald-500/10' : 'bg-red-50 dark:bg-red-500/10'}`}>
              <AlertTriangle size={18} className={tauxMoyen >= 80 ? 'text-green-600 dark:text-emerald-400' : 'text-red-500 dark:text-red-400'} />
            </div>
            <div>
              <p className="text-xs text-brand-textMuted dark:text-slate-400">Taux moyen</p>
              <p className="text-xl font-bold text-brand-text dark:text-slate-100">{tauxMoyen.toFixed(1)}%</p>
            </div>
          </div>
        </div>
      )}

      {query === null ? (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-12 text-center">
          <Clock size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
          <p className="text-sm text-brand-textMuted dark:text-slate-400">Sélectionnez un enseignant pour afficher son suivi d'heures annuel.</p>
        </div>
      ) : (
        <DataTable columns={columns} data={suivi} keyField="id" loading={isLoading} emptyMessage="Aucun suivi d'heures trouvé" />
      )}

      {/* Modal MAJ */}
      <Modal open={open} onClose={() => { setOpen(false); reset() }} title="Mettre à jour le suivi" size="md">
        <form onSubmit={handleSubmit((d) => muttreAJour.mutate(d as MiseAJourHeuresRequete))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Select
              label="Enseignant *"
              placeholder="— Choisir —"
              options={teachers.map((t) => ({ value: t.idEnseignant, label: t.nomComplet || `${t.prenom} ${t.nom}` }))}
              error={errors.enseignantId?.message}
              {...register('enseignantId')}
            />
            <Input label="Numéro semaine *" type="number" {...register('numeroSemaine')} error={errors.numeroSemaine?.message} />
          </div>
          <Input label="Année académique *" placeholder="2024-2025" {...register('anneeAcademique')} error={errors.anneeAcademique?.message} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Heures prévues" type="number" {...register('heuresPrevues')} />
            <Input label="Heures réalisées" type="number" {...register('heuresRealisees')} />
          </div>
          <Input label="Notes" {...register('notes')} />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => { setOpen(false); reset() }}>Annuler</Button>
            <Button type="submit" loading={muttreAJour.isPending}>Enregistrer</Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
