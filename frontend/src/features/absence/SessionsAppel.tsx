import { useState, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Info, Lock, LockOpen, CalendarDays, ClipboardCheck, Users } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { absenceApi, type AppelReponse } from '@/api/absence.api'
import { organisationApi, type Teacher, type SchoolClass } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'
import { SeancesDuJour } from '@/features/enseignant/SeancesDuJour'
import { useMonPlanning } from '@/features/enseignant/useMonPlanning'
import { AppelDetailModal } from './AppelDetailModal'

function enseignantNom(teachers: Teacher[], id: number): string {
  const t = teachers.find((x) => x.idEnseignant === id)
  return t ? t.nomComplet : `Enseignant #${id}`
}

function classeNom(classes: SchoolClass[], id: number): string {
  const c = classes.find((x) => x.idClasse === id)
  return c ? c.code : `Classe #${id}`
}

// ─── Schema (ouverture manuelle) ──────────────────────────────────────────────

const schema = z.object({
  seancePlanningId: z.coerce.number().min(1, 'Obligatoire'),
  enseignantId: z.coerce.number().min(1, 'Obligatoire'),
  groupeClasseId: z.coerce.number().min(1, 'Obligatoire'),
  anneeAcademique: z.string().min(1, 'Obligatoire'),
  matiereId: z.coerce.number().optional(),
  dateSeance: z.string().min(1, 'Obligatoire'),
})

type FormData = z.infer<typeof schema>

// ─── Main ─────────────────────────────────────────────────────────────────────

function todayIso(): string {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export default function SessionsAppel() {
  const qc = useQueryClient()
  const [openModal, setOpenModal] = useState(false)
  const [detailAppel, setDetailAppel] = useState<AppelReponse | null>(null)
  const [filterDate, setFilterDate] = useState(todayIso())
  const [filterClasse, setFilterClasse] = useState('')
  const [filterVerrouille, setFilterVerrouille] = useState('')

  // Si le compte connecté a une fiche enseignant, l'appel se fait directement
  // depuis ses séances du jour — sans saisir d'identifiant de planning.
  const { me } = useMonPlanning()

  const { data: teachers = [] } = useQuery({ queryKey: ['teachers-all'], queryFn: organisationApi.teachers.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes-all'], queryFn: organisationApi.classes.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects-all'], queryFn: organisationApi.subjects.list })
  const { data: schoolYears = [] } = useQuery({ queryKey: ['school-years-all'], queryFn: organisationApi.schoolYears.list })
  const anneeCourante = schoolYears.find((a) => a.estCourante) ?? schoolYears[0]

  const filterParams = {
    date: filterDate || undefined,
    groupeClasseId: filterClasse ? Number(filterClasse) : undefined,
    estVerrouille: filterVerrouille ? filterVerrouille === 'true' : undefined,
  }

  const { data: seances = [], isLoading: seancesLoading } = useQuery({
    queryKey: ['appel-sessions', filterParams],
    queryFn: () => absenceApi.appel.lister(filterParams),
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { anneeAcademique: '', dateSeance: todayIso() },
  })

  const prevOpen = useRef(false)
  if (!openModal && prevOpen.current) { prevOpen.current = false; reset() }
  if (openModal && !prevOpen.current) {
    prevOpen.current = true
    reset({ anneeAcademique: anneeCourante?.nom ?? '', dateSeance: todayIso() })
  }

  const ouvrirMutation = useMutation({
    mutationFn: (d: FormData) => absenceApi.appel.ouvrir({
      seancePlanningId: d.seancePlanningId,
      enseignantId: d.enseignantId,
      groupeClasseId: d.groupeClasseId,
      anneeAcademique: d.anneeAcademique,
      matiereId: d.matiereId,
      dateSeance: d.dateSeance,
    }),
    onSuccess: (data) => {
      toast.success('Session ouverte / récupérée')
      setFilterDate(data.dateSeance)
      qc.invalidateQueries({ queryKey: ['appel-sessions'] })
      setOpenModal(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur')
    },
  })

  return (
    <div className="space-y-6">
      <PageHero
        title="Sessions d'appel"
        subtitle="Les appels ouverts par les enseignants, consultables par jour et par classe"
        icon={ClipboardCheck}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => setOpenModal(true)}
          >
            <Plus size={16} /> Ouvrir une session
          </Button>
        }
      />

      {/* Appel web : séances du jour de l'enseignant connecté */}
      {me && <SeancesDuJour title="Mes cours d'aujourd'hui — appel en un clic" />}

      {/* Info banner */}
      <div className="flex items-start gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-500/20 dark:bg-blue-500/10">
        <Info size={18} className="mt-0.5 shrink-0 text-brand-blue" />
        <p className="text-sm text-blue-800 dark:text-blue-300">
          {me
            ? "L'appel se fait depuis le web comme depuis le mobile : sélectionnez la séance en cours ci-dessus, la session s'ouvre avec la classe et les élèves déjà chargés. Les sessions déjà ouvertes sont consultables plus bas."
            : "Les sessions d'appel sont ouvertes par les enseignants depuis le web ou l'application mobile. Parcourez-les ci-dessous par jour et par classe, ou ouvrez-en une manuellement."}
        </p>
      </div>

      {/* Filtres de recherche */}
      <div className="flex flex-wrap items-end gap-4 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="w-44">
          <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Jour</label>
          <input
            type="date"
            value={filterDate}
            onChange={(e) => setFilterDate(e.target.value)}
            className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          />
        </div>
        <div className="w-56">
          <Select
            label="Classe"
            placeholder="Toutes les classes"
            value={filterClasse}
            onChange={(e) => setFilterClasse(e.target.value)}
            options={classes.map((c) => ({ value: c.idClasse, label: `${c.code} (${c.levelNom})` }))}
          />
        </div>
        <div className="w-44">
          <Select
            label="Statut"
            placeholder="Toutes"
            value={filterVerrouille}
            onChange={(e) => setFilterVerrouille(e.target.value)}
            options={[
              { value: 'false', label: 'Ouvertes' },
              { value: 'true', label: 'Clôturées' },
            ]}
          />
        </div>
        <Button variant="outline" onClick={() => setFilterDate(todayIso())}>
          <CalendarDays size={16} /> Aujourd'hui
        </Button>
      </div>

      {/* Liste des sessions du jour/filtre */}
      {seancesLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-32 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!seancesLoading && seances.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <LockOpen size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            Aucune séance d'appel pour ces filtres
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            Les enseignants n'ont rien ouvert ce jour-là, ou la journée n'est pas travaillée.
          </p>
        </div>
      )}

      {/* Une carte par session, cliquable en entier. */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {seances.map((appel) => (
          <button
            key={appel.id}
            type="button"
            onClick={() => setDetailAppel(appel)}
            className="flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white text-left transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900"
          >
            <span className={`h-1 w-full ${appel.estVerrouille ? 'bg-slate-300 dark:bg-slate-600' : 'bg-brand-teal'}`} />

            <span className="flex flex-1 flex-col gap-2.5 p-4">
              <span className="flex items-start justify-between gap-2">
                <span className="min-w-0">
                  <span className="block truncate font-semibold text-brand-text dark:text-slate-100">
                    Classe {classeNom(classes, appel.groupeClasseId)}
                  </span>
                  <span className="mt-0.5 block truncate text-xs text-brand-textMuted dark:text-slate-400">
                    {enseignantNom(teachers, appel.enseignantId)}
                  </span>
                </span>
                <Badge variant={appel.estVerrouille ? 'default' : 'success'}>
                  {appel.estVerrouille
                    ? <><Lock size={11} className="me-1 inline" />Clôturée</>
                    : <><LockOpen size={11} className="me-1 inline" />Ouverte</>}
                </Badge>
              </span>

              <span className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-brand-textMuted dark:text-slate-400">
                <span className="inline-flex items-center gap-1.5">
                  <CalendarDays size={13} /> {formatDate(appel.ouvertureAt)}
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <Users size={13} />
                  <strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">
                    {appel.lignesAppel?.length ?? 0}
                  </strong>
                  élève{(appel.lignesAppel?.length ?? 0) > 1 ? 's' : ''}
                </span>
              </span>
            </span>

            <span className="flex justify-end border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 text-xs font-medium text-brand-blue dark:border-slate-700 dark:bg-slate-800/40">
              Voir le détail
            </span>
          </button>
        ))}
      </div>

      {/* Modal ouvrir session manuellement (secours : séance hors emploi du temps publié) */}
      <Modal open={openModal} onClose={() => setOpenModal(false)} title="Ouvrir une session d'appel" size="xl">
        <form onSubmit={handleSubmit((d) => ouvrirMutation.mutate(d))} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Input
                label="ID séance planning *"
                type="number"
                {...register('seancePlanningId')}
                error={errors.seancePlanningId?.message}
              />
              <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">Identifiant de la séance dans l'emploi du temps publié.</p>
            </div>
            <div>
              <Input
                label="Jour de cours *"
                type="date"
                {...register('dateSeance')}
                error={errors.dateSeance?.message}
              />
              <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">La séance étant hebdomadaire, le jour distingue chaque appel.</p>
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Enseignant *"
              placeholder="Sélectionner un enseignant"
              options={teachers.map((t) => ({ value: t.idEnseignant, label: t.nomComplet }))}
              {...register('enseignantId')}
              error={errors.enseignantId?.message}
            />
            <Select
              label="Classe *"
              placeholder="Sélectionner une classe"
              options={classes.map((c) => ({ value: c.idClasse, label: `${c.code} (${c.levelNom})` }))}
              {...register('groupeClasseId')}
              error={errors.groupeClasseId?.message}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Année académique *" placeholder={anneeCourante?.nom ?? '2025-2026'} {...register('anneeAcademique')} error={errors.anneeAcademique?.message} />
            <Select
              label="Matière (optionnel)"
              placeholder="Sélectionner une matière"
              options={subjects.map((s) => ({ value: s.idMatiere, label: s.libMatiere }))}
              {...register('matiereId')}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setOpenModal(false)}>Annuler</Button>
            <Button type="submit" loading={ouvrirMutation.isPending}>Ouvrir / Récupérer</Button>
          </div>
        </form>
      </Modal>

      {/* Modal détail */}
      {detailAppel && (
        <AppelDetailModal
          appel={detailAppel}
          onClose={() => setDetailAppel(null)}
          onUpdate={(updated) => {
            setDetailAppel(updated)
            qc.invalidateQueries({ queryKey: ['appel-sessions'] })
          }}
        />
      )}
    </div>
  )
}
