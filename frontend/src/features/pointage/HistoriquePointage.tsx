import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, History, Sun, Sunset } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { DateInputFR } from '@/components/ui/DateInputFR'
import { pointageApi, type PresencePersonnel, type StatutPresencePersonnel, type TypePersonnel, type Periode } from '@/api/pointage.api'
import { organisationApi } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'

const STATUT_LABELS: Record<StatutPresencePersonnel, string> = {
  PRESENT: 'Présent',
  ABSENT: 'Absent',
  EN_RETARD: 'En retard',
  EN_CONGE: 'En congé',
  ABSENCE_JUSTIFIEE: 'Abs. justifiée',
  ABSENCE_NON_JUSTIFIEE: 'Abs. non justifiée',
}

const STATUT_VARIANTS: Record<StatutPresencePersonnel, 'success' | 'danger' | 'warning' | 'info' | 'default'> = {
  PRESENT: 'success',
  ABSENT: 'danger',
  EN_RETARD: 'warning',
  EN_CONGE: 'info',
  ABSENCE_JUSTIFIEE: 'warning',
  ABSENCE_NON_JUSTIFIEE: 'danger',
}

const TYPE_LABELS: Record<TypePersonnel, string> = {
  ENSEIGNANT: 'Enseignant',
  SURVEILLANT: 'Surveillant',
  ADMINISTRATIF: 'Administratif',
}

const PERIODE_LABELS: Record<Periode, string> = {
  MATIN: 'Matin',
  APRES_MIDI: 'Après-midi',
}

function getMonthBounds() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  return {
    debut: `${y}-${m}-01`,
    fin: `${y}-${m}-${String(new Date(y, now.getMonth() + 1, 0).getDate()).padStart(2, '0')}`,
  }
}

export default function HistoriquePointage() {
  const [typePersonnel, setTypePersonnel] = useState<TypePersonnel>('ENSEIGNANT')
  const [membreId, setMembreId] = useState<number | null>(null)
  const [debut, setDebut] = useState(getMonthBounds().debut)
  const [fin, setFin] = useState(getMonthBounds().fin)
  const [query, setQuery] = useState<{ membreId: number; debut: string; fin: string } | null>(null)

  const { data: teachers = [] } = useQuery({
    queryKey: ['teachers'],
    queryFn: organisationApi.teachers.list,
    enabled: typePersonnel === 'ENSEIGNANT',
  })

  const { data: surveillants = [] } = useQuery({
    queryKey: ['pointage-users', 'SURVEILLANT'],
    queryFn: () => organisationApi.users.list('SURVEILLANT'),
    enabled: typePersonnel === 'SURVEILLANT',
  })

  const { data: administratifs = [] } = useQuery({
    queryKey: ['pointage-users', 'SCHOOL_ADMIN'],
    queryFn: () => organisationApi.users.list('SCHOOL_ADMIN'),
    enabled: typePersonnel === 'ADMINISTRATIF',
  })

  // Un seul sélecteur par nom, quel que soit le type de personnel.
  const membresOptions =
    typePersonnel === 'ENSEIGNANT'
      ? teachers.map((t) => ({ value: t.idEnseignant, label: t.nomComplet || `${t.prenom} ${t.nom}`.trim() }))
      : (typePersonnel === 'SURVEILLANT' ? surveillants : administratifs)
          .filter((u) => u.actif)
          .map((u) => ({ value: u.id, label: u.nomComplet || u.email }))

  const { data: historique = [], isLoading } = useQuery({
    queryKey: ['pointage-historique', query],
    queryFn: () => pointageApi.historique(query!.membreId, query!.debut, query!.fin),
    enabled: query !== null,
  })

  function handleSearch() {
    if (membreId && membreId > 0 && debut && fin) setQuery({ membreId, debut, fin })
  }

  const presents = historique.filter((h) => h.statut === 'PRESENT').length
  const absents = historique.filter((h) => h.statut === 'ABSENT' || h.statut === 'ABSENCE_NON_JUSTIFIEE').length
  const retards = historique.filter((h) => h.statut === 'EN_RETARD').length

  const columns: Column<PresencePersonnel>[] = [
    { key: 'datePointage', header: 'Date', render: (p) => <span className="font-medium">{formatDate(p.datePointage)}</span> },
    { key: 'nomMembre', header: 'Membre', render: (p) => p.nomMembre ?? `#${p.membrePersonnelId}` },
    { key: 'typePersonnel', header: 'Type', render: (p) => <Badge variant="default">{TYPE_LABELS[p.typePersonnel]}</Badge> },
    {
      key: 'periode', header: 'Créneau',
      render: (p) => (
        <span className="inline-flex items-center gap-1.5 text-sm text-brand-text dark:text-slate-200">
          {p.periode === 'MATIN' ? <Sun size={13} className="text-amber-500" /> : <Sunset size={13} className="text-orange-500" />}
          {PERIODE_LABELS[p.periode]}
        </span>
      ),
    },
    { key: 'heureArrivee', header: 'Arrivée', render: (p) => p.heureArrivee ? <span className="font-mono text-sm">{p.heureArrivee.slice(0, 5)}</span> : '—' },
    { key: 'minutesRetard', header: 'Retard', render: (p) => p.minutesRetard ? `${p.minutesRetard} min` : '—' },
    { key: 'statut', header: 'Statut', render: (p) => <Badge variant={STATUT_VARIANTS[p.statut]}>{STATUT_LABELS[p.statut]}</Badge> },
    { key: 'note', header: 'Note', render: (p) => <span className="text-xs text-brand-textMuted dark:text-slate-400">{p.note || '—'}</span> },
    { key: 'saisiPar', header: 'Saisi par', render: (p) => <span className="text-xs text-brand-textMuted dark:text-slate-400">{p.saisiPar || '—'}</span> },
  ]

  return (
    <div className="space-y-6">
      <PageHero
        title="Historique du pointage"
        subtitle="Consultez l'historique d'un membre du personnel"
        icon={History}
      />

      {/* Filtres */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4">
        <div className="flex flex-wrap gap-4 items-end">
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Type de personnel *</label>
            <select
              value={typePersonnel}
              onChange={(e) => { setTypePersonnel(e.target.value as TypePersonnel); setMembreId(null) }}
              className="w-44 border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
            >
              {Object.entries(TYPE_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </div>
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">
              {TYPE_LABELS[typePersonnel]} *
            </label>
            <select
              value={membreId ?? ''}
              onChange={(e) => setMembreId(e.target.value ? Number(e.target.value) : null)}
              className="w-56 border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
            >
              <option value="">Sélectionner…</option>
              {membresOptions.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </div>
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Début</label>
            <DateInputFR
              value={debut}
              onChange={(v) => setDebut(v)}
              className="w-36"
            />
          </div>
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Fin</label>
            <DateInputFR
              value={fin}
              onChange={(v) => setFin(v)}
              className="w-36"
            />
          </div>
          <Button onClick={handleSearch} loading={isLoading}><Search size={16} /> Rechercher</Button>
        </div>
      </div>

      {/* Résumé */}
      {query && (
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 text-center">
            <p className="text-2xl font-bold text-green-600 dark:text-emerald-400">{presents}</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">Présences</p>
          </div>
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 text-center">
            <p className="text-2xl font-bold text-red-500">{absents}</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">Absences</p>
          </div>
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 text-center">
            <p className="text-2xl font-bold text-yellow-500">{retards}</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">Retards</p>
          </div>
        </div>
      )}

      {query === null ? (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-12 text-center">
          <History size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
          <p className="text-sm text-brand-textMuted dark:text-slate-400">Sélectionnez un membre du personnel et une plage de dates pour consulter son historique.</p>
        </div>
      ) : (
        <DataTable
          columns={columns}
          data={historique}
          keyField="id"
          loading={isLoading}
          emptyMessage="Aucun pointage trouvé pour cette période"
        />
      )}
    </div>
  )
}
