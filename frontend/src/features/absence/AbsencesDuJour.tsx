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
  X,
  GraduationCap,
  CheckCircle2,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Select'
import { StatCard } from '@/components/ui/StatCard'
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

  /**
   * Groupées par classe : la vie scolaire traite un cas après l'autre, mais
   * elle appelle une famille, un professeur principal, une classe — jamais une
   * ligne isolée. Le tableau à plat mélangeait la 7A et la terminale.
   */
  const parClasse = useMemo(() => {
    const groupes = new Map<number, LigneJour[]>()
    for (const l of lignesFiltrees) {
      const cle = l.appel.groupeClasseId
      groupes.set(cle, [...(groupes.get(cle) ?? []), l])
    }
    return [...groupes.entries()]
      .map(([classeId, items]) => ({ classeId, nom: ref.nomClasse(classeId), items }))
      .sort((a, b) => a.nom.localeCompare(b.nom))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lignesFiltrees, ref.classes])

  return (
    <div className="space-y-6">
      <PageHero
        title="Absences du jour"
        subtitle="Ce que les enseignants ont saisi séance par séance, rassemblé pour la vie scolaire"
        icon={ClipboardList}
      />

      <div className="space-y-4 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex items-center gap-3 rounded-lg border border-brand-border px-3 py-2 dark:border-slate-700">
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

          <div className="relative min-w-[200px] flex-1">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500" />
            <input
              value={recherche}
              onChange={(e) => setRecherche(e.target.value)}
              placeholder="Rechercher un élève…"
              className="w-full rounded-lg border border-brand-border bg-white py-2.5 pl-9 pr-9 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            />
            {recherche && (
              <button
                onClick={() => setRecherche('')}
                title="Effacer la recherche"
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text dark:hover:bg-slate-800"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>

        {/* Le statut se filtre en pastilles portant leur compte : on voit
            combien de retards existent avant de cliquer dessus. */}
        <div className="flex flex-wrap gap-2">
          {([
            ['', 'Tous', lignes.length],
            ['ABSENT', 'Absents', compte('ABSENT')],
            ['RETARD', 'Retards', compte('RETARD')],
            ['EXCLU', 'Exclusions', compte('EXCLU')],
          ] as const).map(([valeur, libelle, nb]) => {
            const actif = filtreStatut === valeur
            return (
              <button
                key={libelle}
                onClick={() => setFiltreStatut(valeur as StatutPresence | '')}
                aria-pressed={actif}
                className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                  actif
                    ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                    : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                }`}
              >
                {libelle}
                <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                  actif ? 'bg-white/20 text-white' : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                }`}>
                  {nb}
                </span>
              </button>
            )
          })}
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

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-36 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && lignesFiltrees.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400">
            <CheckCircle2 size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {appels.length === 0 ? "Aucune séance d'appel pour ce jour" : 'Aucune absence pour ces filtres'}
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            {appels.length === 0
              ? "Les enseignants n'ont encore rien saisi, ou la journée n'est pas travaillée."
              : 'Toutes les présences sont à jour pour cette sélection.'}
          </p>
        </div>
      )}

      {/* Un bloc par classe. */}
      <div className="space-y-6">
        {parClasse.map((g) => (
          <section key={g.classeId}>
            <div className="mb-3 flex items-center gap-3">
              <h2 className="flex items-center gap-1.5 text-sm font-semibold text-brand-text dark:text-slate-100">
                <GraduationCap size={14} /> {g.nom}
              </h2>
              <span className="rounded-full bg-brand-bgSecondary px-2 py-0.5 text-xs font-medium tabular-nums text-brand-textMuted dark:bg-slate-800 dark:text-slate-400">
                {g.items.length} signalement{g.items.length > 1 ? 's' : ''}
              </span>
              <span className="h-px flex-1 bg-brand-border dark:bg-slate-700" />
            </div>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {g.items.map((l) => (
                <div
                  key={l.ligneAppelId}
                  className="flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900"
                >
                  <div className="flex flex-1 flex-col gap-2.5 p-4">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">
                          {ref.nomEleve(l.eleveId)}
                        </h3>
                        <p className="mt-0.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                          {ref.nomMatiere(l.appel.matiereId)} · {ref.nomEnseignant(l.appel.enseignantId)}
                        </p>
                      </div>
                      <Badge variant={STATUT_PRESENCE_VARIANTS[l.statut]}>
                        {STATUT_PRESENCE_LABELS[l.statut]}
                      </Badge>
                    </div>

                    {l.statut === 'RETARD' && l.minutesRetard ? (
                      <p className="text-xs text-brand-textMuted dark:text-slate-400">
                        Arrivé avec {l.minutesRetard} min de retard
                      </p>
                    ) : null}
                    {l.raisonExclusion && (
                      <p className="rounded-lg bg-brand-bgSecondary/60 p-2 text-xs text-brand-textMuted dark:bg-slate-800/50 dark:text-slate-400">
                        {l.raisonExclusion}
                      </p>
                    )}

                    <div className="mt-auto flex flex-wrap gap-1.5">
                      {l.estJustifie
                        ? <Badge variant="success">Justifiée</Badge>
                        : <Badge variant="danger">Non justifiée</Badge>}
                      {!l.appel.estVerrouille && <Badge variant="warning">Appel ouvert</Badge>}
                    </div>
                  </div>

                  <div className="flex items-center justify-end gap-2 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
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
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>

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
