import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock,
  FileText,
  Search,
  ShieldAlert,
  XCircle,
} from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Select'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { absenceApi, type AppelReponse, type StatutPresence } from '@/api/absence.api'
import { AppelDetailModal } from './AppelDetailModal'
import { SoumettreJustificatifModal } from './SoumettreJustificatifModal'
import {
  STATUT_PRESENCE_LABELS,
  STATUT_PRESENCE_VARIANTS,
  formatJour,
  todayIso,
  useReferentielScolaire,
} from './absenceLabels'

/** Une absence du jour, à plat : la ligne d'appel replacée dans sa séance. */
interface LigneJour {
  ligneAppelId: number
  eleveId: number
  statut: StatutPresence
  estJustifie: boolean
  minutesRetard: number | null
  raisonExclusion: string | null
  appel: AppelReponse
}

function decalerJour(iso: string, jours: number): string {
  const d = new Date(iso)
  d.setDate(d.getDate() + jours)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

/**
 * Constat des absences d'une journée, toutes classes confondues. C'est l'écran
 * d'entrée de la vie scolaire : il rassemble ce que les enseignants ont saisi
 * séance par séance, et permet de justifier ou de corriger dans la foulée.
 */
export default function AbsencesDuJour() {
  const qc = useQueryClient()
  const ref = useReferentielScolaire()

  const [jour, setJour] = useState(todayIso())
  const [classeId, setClasseId] = useState('')
  const [filtreStatut, setFiltreStatut] = useState<StatutPresence | ''>('')
  const [recherche, setRecherche] = useState('')
  const [detail, setDetail] = useState<AppelReponse | null>(null)
  const [aJustifier, setAJustifier] = useState<{ ligneAppelId: number; eleveNom: string; contexte: string } | null>(null)

  const params = { date: jour, groupeClasseId: classeId ? Number(classeId) : undefined }

  const { data: appels = [], isLoading } = useQuery({
    queryKey: ['appel-sessions', params],
    queryFn: () => absenceApi.appel.lister(params),
  })

  const lignes = useMemo<LigneJour[]>(
    () =>
      appels.flatMap((appel) =>
        (appel.lignesAppel ?? [])
          .filter((l) => l.statut !== 'PRESENT')
          .map((l) => ({
            ligneAppelId: l.id,
            eleveId: l.eleveId,
            statut: l.statut,
            estJustifie: l.estJustifie,
            minutesRetard: l.minutesRetard ?? null,
            raisonExclusion: l.raisonExclusion || null,
            appel,
          }))
      ),
    [appels]
  )

  const lignesFiltrees = useMemo(() => {
    const q = recherche.trim().toLowerCase()
    return lignes.filter(
      (l) =>
        (!filtreStatut || l.statut === filtreStatut) &&
        (!q || ref.nomEleve(l.eleveId).toLowerCase().includes(q))
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lignes, filtreStatut, recherche, ref.eleves])

  const compte = (s: StatutPresence) => lignes.filter((l) => l.statut === s).length
  const nonJustifiees = lignes.filter((l) => l.statut === 'ABSENT' && !l.estJustifie).length
  const appelsOuverts = appels.filter((a) => !a.estVerrouille).length

  const columns: Column<LigneJour>[] = [
    {
      key: 'eleve',
      header: 'Élève',
      render: (l) => (
        <div>
          <p className="font-medium text-brand-text dark:text-slate-200">{ref.nomEleve(l.eleveId)}</p>
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Classe {ref.classeEleve(l.eleveId)}</p>
        </div>
      ),
    },
    {
      key: 'seance',
      header: 'Séance',
      render: (l) => (
        <div>
          <p className="text-sm text-brand-text dark:text-slate-200">
            {ref.nomClasse(l.appel.groupeClasseId)} · {ref.nomMatiere(l.appel.matiereId)}
          </p>
          <p className="text-xs text-brand-textMuted dark:text-slate-400">{ref.nomEnseignant(l.appel.enseignantId)}</p>
        </div>
      ),
    },
    {
      key: 'statut',
      header: 'Statut',
      render: (l) => (
        <div className="space-y-1">
          <Badge variant={STATUT_PRESENCE_VARIANTS[l.statut]}>{STATUT_PRESENCE_LABELS[l.statut]}</Badge>
          {l.statut === 'RETARD' && l.minutesRetard ? (
            <p className="text-xs text-brand-textMuted dark:text-slate-400">{l.minutesRetard} min</p>
          ) : null}
          {l.raisonExclusion && <p className="text-xs text-brand-textMuted dark:text-slate-400">{l.raisonExclusion}</p>}
        </div>
      ),
    },
    {
      key: 'justification',
      header: 'Justification',
      render: (l) =>
        l.estJustifie ? <Badge variant="success">Justifiée</Badge> : <Badge variant="danger">Non justifiée</Badge>,
    },
    {
      key: 'appel',
      header: 'Appel',
      render: (l) => (
        <Badge variant={l.appel.estVerrouille ? 'default' : 'warning'}>
          {l.appel.estVerrouille ? 'Clôturé' : 'Ouvert'}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: '',
      className: 'w-px whitespace-nowrap',
      render: (l) => (
        <div className="flex gap-1">
          {l.statut === 'ABSENT' && !l.estJustifie && (
            <Button
              size="sm"
              variant="outline"
              onClick={() =>
                setAJustifier({
                  ligneAppelId: l.ligneAppelId,
                  eleveNom: ref.nomEleve(l.eleveId),
                  contexte: `${formatJour(l.appel.dateSeance)} · ${ref.nomClasse(l.appel.groupeClasseId)} · ${ref.nomMatiere(l.appel.matiereId)}`,
                })
              }
            >
              <FileText size={13} /> Justifier
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={() => setDetail(l.appel)}>
            Voir la séance
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Absences du jour"
        subtitle="Toutes les absences, retards et exclusions saisis par les enseignants pour la journée"
      />

      <div className="flex flex-wrap items-end gap-3">
        <div className="flex items-center gap-3 rounded-xl border border-brand-border bg-white px-4 py-2.5 dark:border-slate-700 dark:bg-slate-900">
          <button
            type="button"
            onClick={() => setJour(decalerJour(jour, -1))}
            className="rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            aria-label="Jour précédent"
          >
            <ChevronLeft size={16} />
          </button>
          <input
            type="date"
            value={jour}
            max={todayIso()}
            onChange={(e) => setJour(e.target.value)}
            className="border-none bg-transparent text-sm font-medium text-brand-text outline-none dark:text-slate-200"
          />
          <button
            type="button"
            onClick={() => setJour(decalerJour(jour, 1))}
            disabled={jour >= todayIso()}
            className="rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text disabled:opacity-30 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            aria-label="Jour suivant"
          >
            <ChevronRight size={16} />
          </button>
        </div>

        <Button variant="outline" onClick={() => setJour(todayIso())}>
          <CalendarDays size={16} /> Aujourd'hui
        </Button>

        <div className="w-56">
          <Select
            label="Classe"
            placeholder="Toutes les classes"
            value={classeId}
            onChange={(e) => setClasseId(e.target.value)}
            options={ref.classes.map((c) => ({ value: c.idClasse, label: `${c.code} (${c.levelNom})` }))}
          />
        </div>

        <div className="w-44">
          <Select
            label="Statut"
            placeholder="Tous"
            value={filtreStatut}
            onChange={(e) => setFiltreStatut(e.target.value as StatutPresence | '')}
            options={[
              { value: 'ABSENT', label: 'Absents' },
              { value: 'RETARD', label: 'Retards' },
              { value: 'EXCLU', label: 'Exclusions' },
            ]}
          />
        </div>

        <div className="relative min-w-[200px] flex-1">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-400" />
          <input
            value={recherche}
            onChange={(e) => setRecherche(e.target.value)}
            placeholder="Rechercher un élève…"
            className="w-full rounded-lg border border-brand-border bg-white py-2.5 pl-9 pr-3 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <StatCard title="Séances appelées" value={appels.length} icon={ClipboardList} color="blue" />
        <StatCard title="Absents" value={compte('ABSENT')} icon={XCircle} color="red" />
        <StatCard title="Retards" value={compte('RETARD')} icon={Clock} color="yellow" />
        <StatCard title="Exclusions" value={compte('EXCLU')} icon={ShieldAlert} color="purple" />
        <StatCard title="Non justifiées" value={nonJustifiees} icon={FileText} color="amber" />
      </div>

      {appelsOuverts > 0 && (
        <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
          {appelsOuverts} séance{appelsOuverts > 1 ? 's' : ''} d'appel encore ouverte
          {appelsOuverts > 1 ? 's' : ''} : les statuts peuvent encore évoluer d'ici la clôture.
        </p>
      )}

      <DataTable
        columns={columns}
        data={lignesFiltrees}
        keyField="ligneAppelId"
        loading={isLoading}
        emptyMessage={
          appels.length === 0
            ? "Aucune séance d'appel pour ce jour."
            : 'Aucune absence pour ces filtres — toutes les présences sont à jour.'
        }
      />

      <SoumettreJustificatifModal cible={aJustifier} onClose={() => setAJustifier(null)} />

      {detail && (
        <AppelDetailModal
          appel={detail}
          onClose={() => setDetail(null)}
          onUpdate={(maj) => {
            setDetail(maj)
            qc.invalidateQueries({ queryKey: ['appel-sessions'] })
          }}
        />
      )}
    </div>
  )
}
