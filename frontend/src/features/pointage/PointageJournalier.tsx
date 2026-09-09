import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  ChevronLeft, ChevronRight, Users, Clock, CheckCircle, XCircle,
  Sun, Sunset, Search, CheckCheck,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { DateInputFR } from '@/components/ui/DateInputFR'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { Pagination } from '@/components/ui/Pagination'
import {
  pointageApi,
  type PresencePersonnel,
  type StatutPresencePersonnel,
  type TypePersonnel,
  type Periode,
} from '@/api/pointage.api'
import { organisationApi } from '@/api/organisation.api'
import { cn } from '@/lib/utils'

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
  ENSEIGNANT: 'Enseignants',
  SURVEILLANT: 'Surveillants',
  ADMINISTRATIF: 'Administratifs',
}

const PERIODE_LABELS: Record<Periode, string> = {
  MATIN: 'Matin',
  APRES_MIDI: 'Après-midi',
}

const TAILLE_PAGE = 15

/** Une ligne du tableau : un membre du personnel, pointé ou non sur le créneau courant. */
interface LigneMembre {
  membreId: number
  nom: string
  typePersonnel: TypePersonnel
  pointage: PresencePersonnel | null
}

function periodeParDefaut(): Periode {
  return new Date().getHours() < 13 ? 'MATIN' : 'APRES_MIDI'
}

function formatDateInput(d: Date) {
  return d.toISOString().split('T')[0]
}

function offsetDate(d: Date, days: number) {
  const r = new Date(d)
  r.setDate(r.getDate() + days)
  return r
}

function heureCourante() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export default function PointageJournalier() {
  const qc = useQueryClient()
  const [date, setDate] = useState(new Date())
  const [periode, setPeriode] = useState<Periode>(periodeParDefaut())
  const [typePersonnel, setTypePersonnel] = useState<TypePersonnel>('ENSEIGNANT')
  const [recherche, setRecherche] = useState('')
  const [page, setPage] = useState(0)
  const [retardCible, setRetardCible] = useState<LigneMembre | null>(null)
  const [minutesRetard, setMinutesRetard] = useState('')
  const [heureArrivee, setHeureArrivee] = useState(heureCourante())

  const dateStr = formatDateInput(date)
  const todayStr = formatDateInput(new Date())

  function trySetDate(next: Date) {
    if (formatDateInput(next) > todayStr) {
      toast.error("Le pointage n'est pas disponible pour une date future")
      return
    }
    setDate(next)
    setPage(0)
  }

  // ── Données ────────────────────────────────────────────────────────────────
  const { data: rapport, isLoading: loadingRapport } = useQuery({
    queryKey: ['pointage-rapport', dateStr],
    queryFn: () => pointageApi.rapport(dateStr),
  })

  const { data: enseignants = [], isLoading: loadingEnseignants } = useQuery({
    queryKey: ['pointage-enseignants-disponibles', dateStr, periode],
    queryFn: () => pointageApi.enseignantsDisponibles(dateStr, periode),
    enabled: typePersonnel === 'ENSEIGNANT',
  })

  const { data: surveillants = [], isLoading: loadingSurveillants } = useQuery({
    queryKey: ['pointage-users', 'SURVEILLANT'],
    queryFn: () => organisationApi.users.list('SURVEILLANT'),
    enabled: typePersonnel === 'SURVEILLANT',
  })

  const { data: administratifs = [], isLoading: loadingAdministratifs } = useQuery({
    queryKey: ['pointage-users', 'SCHOOL_ADMIN'],
    queryFn: () => organisationApi.users.list('SCHOOL_ADMIN'),
    enabled: typePersonnel === 'ADMINISTRATIF',
  })

  const loadingPersonnel =
    (typePersonnel === 'ENSEIGNANT' && loadingEnseignants) ||
    (typePersonnel === 'SURVEILLANT' && loadingSurveillants) ||
    (typePersonnel === 'ADMINISTRATIF' && loadingAdministratifs)

  // ── Fusion personnel × pointages du créneau ────────────────────────────────
  const lignes = useMemo<LigneMembre[]>(() => {
    const pointagesDuCreneau = (rapport?.enregistrements ?? []).filter(
      (p) => p.periode === periode && p.typePersonnel === typePersonnel
    )
    const parMembre = new Map(pointagesDuCreneau.map((p) => [p.membrePersonnelId, p]))

    const membres: Array<{ membreId: number; nom: string }> =
      typePersonnel === 'ENSEIGNANT'
        ? enseignants.map((t) => ({
            membreId: t.idEnseignant,
            nom: t.nomComplet || `${t.prenom} ${t.nom}`.trim(),
          }))
        : (typePersonnel === 'SURVEILLANT' ? surveillants : administratifs)
            .filter((u) => u.actif)
            .map((u) => ({ membreId: u.id, nom: u.nomComplet || u.email }))

    return membres.map((m) => ({
      ...m,
      typePersonnel,
      pointage: parMembre.get(m.membreId) ?? null,
    }))
  }, [rapport, periode, typePersonnel, enseignants, surveillants, administratifs])

  const lignesFiltrees = useMemo(() => {
    const q = recherche.trim().toLowerCase()
    return q ? lignes.filter((l) => l.nom.toLowerCase().includes(q)) : lignes
  }, [lignes, recherche])

  const lignesPage = lignesFiltrees.slice(page * TAILLE_PAGE, (page + 1) * TAILLE_PAGE)

  // Compteurs sur le créneau + type affichés, pour que les cartes suivent les filtres.
  const compte = (s: StatutPresencePersonnel) => lignes.filter((l) => l.pointage?.statut === s).length
  const nonPointes = lignes.filter((l) => !l.pointage).length

  // ── Mutations ──────────────────────────────────────────────────────────────
  function invalider() {
    qc.invalidateQueries({ queryKey: ['pointage-rapport', dateStr] })
  }

  function messageErreur(e: { response?: { data?: { message?: string } } }) {
    return e.response?.data?.message ?? 'Erreur'
  }

  const pointerMutation = useMutation({
    mutationFn: (params: {
      ligne: LigneMembre
      statut: StatutPresencePersonnel
      minutesRetard?: number
      heureArrivee?: string
    }) => {
      const corps = {
        membrePersonnelId: params.ligne.membreId,
        typePersonnel: params.ligne.typePersonnel,
        datePointage: dateStr,
        periode,
        statut: params.statut,
        minutesRetard: params.minutesRetard,
        heureArrivee: params.heureArrivee,
        // Le serveur remplace tous les champs à la modification : on réinjecte
        // la note existante pour ne pas la perdre en changeant juste le statut.
        note: params.ligne.pointage?.note,
      }
      return params.ligne.pointage
        ? pointageApi.modifier(params.ligne.pointage.id, corps)
        : pointageApi.pointer(corps)
    },
    onSuccess: (_, params) => {
      toast.success(`${params.ligne.nom} — ${STATUT_LABELS[params.statut].toLowerCase()}`)
      invalider()
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(messageErreur(e)),
  })

  const masseMutation = useMutation({
    mutationFn: (cibles: LigneMembre[]) =>
      pointageApi.pointerEnMasse(
        cibles.map((l) => ({
          membrePersonnelId: l.membreId,
          typePersonnel: l.typePersonnel,
          datePointage: dateStr,
          periode,
          statut: 'PRESENT' as StatutPresencePersonnel,
        }))
      ),
    onSuccess: (res) => {
      if (res.echoues > 0) toast.warning(`${res.reussis} pointés, ${res.echoues} en échec`)
      else toast.success(`${res.reussis} membres marqués présents`)
      invalider()
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(messageErreur(e)),
  })

  function pointer(ligne: LigneMembre, statut: StatutPresencePersonnel) {
    // Le serveur exige des minutes de retard > 0 : on les demande avant d'envoyer.
    if (statut === 'EN_RETARD') {
      setRetardCible(ligne)
      setMinutesRetard(String(ligne.pointage?.minutesRetard || ''))
      setHeureArrivee(ligne.pointage?.heureArrivee?.slice(0, 5) || heureCourante())
      return
    }
    pointerMutation.mutate({ ligne, statut })
  }

  function confirmerRetard() {
    const minutes = Number(minutesRetard)
    if (!retardCible || !minutes || minutes <= 0) {
      toast.error('Indiquez un nombre de minutes de retard supérieur à 0')
      return
    }
    pointerMutation.mutate(
      { ligne: retardCible, statut: 'EN_RETARD', minutesRetard: minutes, heureArrivee },
      { onSuccess: () => setRetardCible(null) }
    )
  }

  // ── Colonnes ───────────────────────────────────────────────────────────────
  const columns: Column<LigneMembre>[] = [
    {
      key: 'nom',
      header: 'Nom',
      render: (l) => (
        <div>
          <p className="font-medium text-brand-text dark:text-slate-200">{l.nom}</p>
          {l.pointage?.note && (
            <p className="text-xs text-brand-textMuted dark:text-slate-400">{l.pointage.note}</p>
          )}
        </div>
      ),
    },
    {
      key: 'statut',
      header: 'Statut',
      render: (l) =>
        l.pointage ? (
          <Badge variant={STATUT_VARIANTS[l.pointage.statut]}>{STATUT_LABELS[l.pointage.statut]}</Badge>
        ) : (
          <span className="text-xs text-brand-textMuted dark:text-slate-400">Non pointé</span>
        ),
    },
    {
      key: 'heureArrivee',
      header: 'Arrivée',
      render: (l) =>
        l.pointage?.heureArrivee ? (
          <span className="font-mono text-sm">{l.pointage.heureArrivee.slice(0, 5)}</span>
        ) : (
          '—'
        ),
    },
    {
      key: 'minutesRetard',
      header: 'Retard',
      render: (l) => (l.pointage?.minutesRetard ? `${l.pointage.minutesRetard} min` : '—'),
    },
    {
      key: 'actions',
      header: 'Pointer',
      className: 'w-px whitespace-nowrap',
      render: (l) => (
        <div className="flex items-center gap-1.5">
          {([
            ['PRESENT', 'Présent', 'success'],
            ['ABSENT', 'Absent', 'danger'],
            ['EN_RETARD', 'Retard', 'warning'],
          ] as const).map(([statut, label, ton]) => {
            const actif = l.pointage?.statut === statut
            return (
              <button
                key={statut}
                type="button"
                disabled={pointerMutation.isPending}
                onClick={() => pointer(l, statut)}
                className={cn(
                  'px-2.5 py-1 rounded-md text-xs font-medium border transition-colors disabled:opacity-50',
                  actif && ton === 'success' && 'bg-emerald-600 border-emerald-600 text-white',
                  actif && ton === 'danger' && 'bg-red-600 border-red-600 text-white',
                  actif && ton === 'warning' && 'bg-amber-500 border-amber-500 text-white',
                  !actif &&
                    'border-brand-border dark:border-slate-700 text-brand-textMuted dark:text-slate-400 hover:bg-brand-bgSecondary dark:hover:bg-slate-800'
                )}
              >
                {label}
              </button>
            )
          })}
          <select
            value={l.pointage?.statut ?? ''}
            onChange={(e) => e.target.value && pointer(l, e.target.value as StatutPresencePersonnel)}
            className="text-xs border border-brand-border dark:border-slate-700 rounded-md px-2 py-1 bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
            aria-label={`Autre statut pour ${l.nom}`}
          >
            <option value="">Autre…</option>
            {(['EN_CONGE', 'ABSENCE_JUSTIFIEE', 'ABSENCE_NON_JUSTIFIEE'] as const).map((s) => (
              <option key={s} value={s}>
                {STATUT_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHero
        title="Pointage du personnel"
        subtitle="Sélectionnez un créneau, puis pointez chaque membre du personnel"
        icon={Clock}
        actions={
          <Button
            variant="outline" className="border-white/30 bg-white/15 text-white backdrop-blur hover:bg-white/25"
            disabled={nonPointes === 0}
            loading={masseMutation.isPending}
            onClick={() => masseMutation.mutate(lignes.filter((l) => !l.pointage))}
          >
            <CheckCheck size={16} /> Tout marquer présent ({nonPointes})
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-3">
        {/* Date */}
        <div className="flex items-center gap-3 bg-white dark:bg-slate-900 border border-brand-border dark:border-slate-700 rounded-xl px-4 py-3">
          <button
            onClick={() => trySetDate(offsetDate(date, -1))}
            className="p-1 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200"
            aria-label="Jour précédent"
          >
            <ChevronLeft size={16} />
          </button>
          <DateInputFR
            value={dateStr}
            onChange={(v) => trySetDate(new Date(v))}
            className="w-28 text-sm font-medium text-brand-text dark:text-slate-200 border-none outline-none bg-transparent"
          />
          <button
            onClick={() => trySetDate(offsetDate(date, 1))}
            disabled={dateStr >= todayStr}
            className="p-1 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200 disabled:opacity-30"
            aria-label="Jour suivant"
          >
            <ChevronRight size={16} />
          </button>
        </div>

        {/* Créneau */}
        <div className="inline-flex rounded-lg border border-brand-border dark:border-slate-700 p-0.5 bg-brand-bgSecondary dark:bg-slate-800">
          {(['MATIN', 'APRES_MIDI'] as const).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => { setPeriode(p); setPage(0) }}
              className={cn(
                'px-4 py-2 text-sm font-medium rounded-md transition-colors flex items-center gap-1.5',
                periode === p
                  ? 'bg-white dark:bg-slate-900 text-brand-blue shadow-sm'
                  : 'text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200'
              )}
            >
              {p === 'MATIN' ? <Sun size={14} /> : <Sunset size={14} />}
              {PERIODE_LABELS[p]}
            </button>
          ))}
        </div>

        {/* Type de personnel */}
        <div className="inline-flex rounded-lg border border-brand-border dark:border-slate-700 p-0.5 bg-brand-bgSecondary dark:bg-slate-800">
          {(Object.keys(TYPE_LABELS) as TypePersonnel[]).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => { setTypePersonnel(t); setPage(0) }}
              className={cn(
                'px-3 py-2 text-sm font-medium rounded-md transition-colors',
                typePersonnel === t
                  ? 'bg-white dark:bg-slate-900 text-brand-blue shadow-sm'
                  : 'text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200'
              )}
            >
              {TYPE_LABELS[t]}
            </button>
          ))}
        </div>

        {/* Recherche */}
        <div className="relative flex-1 min-w-[200px]">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-400" />
          <input
            value={recherche}
            onChange={(e) => { setRecherche(e.target.value); setPage(0) }}
            placeholder="Rechercher un nom…"
            className="w-full pl-9 pr-3 py-2.5 text-sm border border-brand-border dark:border-slate-700 rounded-lg bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
          />
        </div>
      </div>

      {/* Stats du créneau courant */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard title="Présents" value={compte('PRESENT')} icon={CheckCircle} color="green" />
        <StatCard title="Absents" value={compte('ABSENT') + compte('ABSENCE_NON_JUSTIFIEE')} icon={XCircle} color="red" />
        <StatCard title="En retard" value={compte('EN_RETARD')} icon={Clock} color="yellow" />
        <StatCard title="Non pointés" value={nonPointes} icon={Users} color="blue" />
      </div>

      <DataTable
        columns={columns}
        data={lignesPage}
        keyField="membreId"
        loading={loadingRapport || loadingPersonnel}
        emptyMessage={
          recherche
            ? 'Aucun membre ne correspond à cette recherche'
            : typePersonnel === 'ENSEIGNANT'
              ? `Aucun enseignant n'a cours le ${dateStr} (${PERIODE_LABELS[periode].toLowerCase()}).`
              : `Aucun ${TYPE_LABELS[typePersonnel].toLowerCase().replace(/s$/, '')} actif.`
        }
      />

      <Pagination page={page} pageSize={TAILLE_PAGE} total={lignesFiltrees.length} onPageChange={setPage} />

      {/* Retard : minutes obligatoires côté serveur */}
      <Modal open={retardCible !== null} onClose={() => setRetardCible(null)} title={`Retard — ${retardCible?.nom ?? ''}`}>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Minutes de retard *"
              type="number"
              min={1}
              value={minutesRetard}
              onChange={(e) => setMinutesRetard(e.target.value)}
              autoFocus
            />
            <Input
              label="Heure d'arrivée"
              type="time"
              value={heureArrivee}
              onChange={(e) => setHeureArrivee(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2 border-t border-brand-border dark:border-slate-700">
            <Button variant="outline" onClick={() => setRetardCible(null)}>Annuler</Button>
            <Button onClick={confirmerRetard} loading={pointerMutation.isPending}>Enregistrer</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
