import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  CheckCircle,
  Clock,
  FileCheck,
  FileText,
  Inbox,
  Search,
  UserSearch,
  XCircle,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { Tabs } from '@/components/ui/Tabs'
import { DataTable, type Column } from '@/components/ui/DataTable'
import {
  absenceApi,
  type AbsenceEleveReponse,
  type JustificatifReponse,
  type StatutJustificatif,
} from '@/api/absence.api'
import type { Eleve } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'
import { SoumettreJustificatifModal } from './SoumettreJustificatifModal'
import {
  STATUT_JUSTIFICATIF_LABELS,
  STATUT_JUSTIFICATIF_VARIANTS,
  STATUT_PRESENCE_LABELS,
  STATUT_PRESENCE_VARIANTS,
  TYPE_JUSTIFICATIF_LABELS,
  formatJour,
  isoIlYA,
  libelleEleve,
  todayIso,
  useReferentielScolaire,
} from './absenceLabels'

type Onglet = 'file' | 'dossier'

/**
 * Justificatifs d'absence des élèves : la file d'attente que la vie scolaire
 * traite au quotidien, et le dossier d'un élève pour déposer un justificatif sur
 * une absence précise. Aucun identifiant technique n'est saisi : les absences et
 * les justificatifs sont choisis dans des listes.
 */
export default function JustificatifsAbsences() {
  const qc = useQueryClient()
  const ref = useReferentielScolaire()

  const [onglet, setOnglet] = useState<Onglet>('file')
  const [filtreStatut, setFiltreStatut] = useState<StatutJustificatif | ''>('EN_ATTENTE')

  // Dossier élève
  const [saisieEleve, setSaisieEleve] = useState('')
  const [eleve, setEleve] = useState<Eleve | null>(null)
  const [debut, setDebut] = useState(isoIlYA(60))
  const [fin, setFin] = useState(todayIso())

  const [aJustifier, setAJustifier] = useState<{ ligneAppelId: number; eleveNom: string; contexte: string } | null>(null)
  const [decision, setDecision] = useState<{ justif: JustificatifReponse; approuver: boolean } | null>(null)
  const [notes, setNotes] = useState('')

  // ── Données ────────────────────────────────────────────────────────────────
  const { data: file = [], isLoading: fileLoading } = useQuery({
    queryKey: ['justificatifs', 'file', filtreStatut],
    queryFn: () => absenceApi.justificatifs.list(filtreStatut ? { statut: filtreStatut } : undefined),
  })

  const { data: justificatifsEleve = [], isLoading: justifsEleveLoading } = useQuery({
    queryKey: ['justificatifs', 'eleve', eleve?.idEleve],
    queryFn: () => absenceApi.justificatifs.list({ eleveId: eleve!.idEleve }),
    enabled: eleve !== null,
  })

  const { data: absences = [], isLoading: absencesLoading } = useQuery({
    queryKey: ['absences-eleve', eleve?.idEleve, debut, fin],
    queryFn: () => absenceApi.appel.absencesEleve(eleve!.idEleve, { debut, fin }),
    enabled: eleve !== null,
  })

  // ── Traitement ─────────────────────────────────────────────────────────────
  const traiter = useMutation({
    mutationFn: ({ justif, approuver }: { justif: JustificatifReponse; approuver: boolean }) =>
      approuver
        ? absenceApi.justificatifs.approuver(justif.id, notes.trim() || undefined)
        : absenceApi.justificatifs.refuser(justif.id, notes.trim() || undefined),
    onSuccess: (_, { approuver }) => {
      toast.success(approuver ? 'Justificatif validé — absence justifiée' : 'Justificatif refusé')
      qc.invalidateQueries({ queryKey: ['justificatifs'] })
      qc.invalidateQueries({ queryKey: ['absences-eleve'] })
      setDecision(null)
      setNotes('')
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Le traitement a échoué'),
  })

  function rechercherEleve() {
    const trouve = ref.eleves.find((e) => libelleEleve(e) === saisieEleve.trim())
    if (!trouve) {
      toast.error('Élève introuvable — choisissez un nom dans la liste proposée')
      return
    }
    setEleve(trouve)
  }

  const compte = (s: StatutJustificatif) => file.filter((j) => j.statut === s).length

  /**
   * Colonnes du dossier élève. La file d'attente, elle, est passée en cartes :
   * on y prend une décision, on ne parcourt pas des lignes. Le dossier reste un
   * tableau parce qu'il se lit comme un relevé — un élève, ses dates en ordre.
   *
   * Le paramètre `avecEleve` a disparu avec la file : ici l'élève est déjà en
   * tête de page, sa colonne ne répétait qu'une seule et même valeur.
   */
  const colonnesJustificatifs = (): Column<JustificatifReponse>[] => {
    const colonnes: Column<JustificatifReponse>[] = []

    colonnes.push(
      {
        key: 'seance',
        header: 'Séance',
        render: (j) => (
          <div>
            <p className="text-sm text-brand-text dark:text-slate-200">{formatJour(j.dateSeance)}</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400">
              {ref.nomClasse(j.groupeClasseId)} · {ref.nomMatiere(j.matiereId)}
            </p>
          </div>
        ),
      },
      {
        key: 'typeDocument',
        header: 'Document',
        render: (j) => (
          <div>
            <p className="text-sm text-brand-text dark:text-slate-200">{TYPE_JUSTIFICATIF_LABELS[j.typeDocument]}</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400">{j.referenceDocument || 'Sans référence'}</p>
          </div>
        ),
      },
      {
        key: 'soumisAt',
        header: 'Déposé le',
        render: (j) => <span className="text-sm text-brand-textMuted dark:text-slate-400">{formatDate(j.soumisAt)}</span>,
      },
      {
        key: 'statut',
        header: 'Statut',
        render: (j) => (
          <div className="space-y-1">
            <Badge variant={STATUT_JUSTIFICATIF_VARIANTS[j.statut]}>{STATUT_JUSTIFICATIF_LABELS[j.statut]}</Badge>
            {j.notesAdmin && <p className="text-xs text-brand-textMuted dark:text-slate-400">{j.notesAdmin}</p>}
          </div>
        ),
      },
      {
        key: 'actions',
        header: '',
        className: 'w-px whitespace-nowrap',
        render: (j) =>
          j.statut === 'EN_ATTENTE' ? (
            <div className="flex gap-1">
              <Button size="sm" variant="outline" onClick={() => { setDecision({ justif: j, approuver: true }); setNotes('') }}>
                <CheckCircle size={13} /> Valider
              </Button>
              <Button size="sm" variant="outline" onClick={() => { setDecision({ justif: j, approuver: false }); setNotes('') }}>
                <XCircle size={13} /> Refuser
              </Button>
            </div>
          ) : (
            <span className="text-xs text-brand-textMuted dark:text-slate-400">
              {j.traiteAt ? `Traité le ${formatDate(j.traiteAt)}` : '—'}
            </span>
          ),
      }
    )

    return colonnes
  }

  // ── Colonnes : absences de l'élève ─────────────────────────────────────────
  const colonnesAbsences: Column<AbsenceEleveReponse>[] = [
    {
      key: 'dateSeance',
      header: 'Jour',
      render: (a) => <span className="text-sm font-medium text-brand-text dark:text-slate-200">{formatJour(a.dateSeance)}</span>,
    },
    {
      key: 'contexte',
      header: 'Séance',
      render: (a) => (
        <div>
          <p className="text-sm text-brand-text dark:text-slate-200">
            {ref.nomClasse(a.groupeClasseId)} · {ref.nomMatiere(a.matiereId)}
          </p>
          <p className="text-xs text-brand-textMuted dark:text-slate-400">{ref.nomEnseignant(a.enseignantId)}</p>
        </div>
      ),
    },
    {
      key: 'statut',
      header: 'Statut',
      render: (a) => (
        <div className="space-y-1">
          <Badge variant={STATUT_PRESENCE_VARIANTS[a.statut]}>{STATUT_PRESENCE_LABELS[a.statut]}</Badge>
          {a.statut === 'RETARD' && a.minutesRetard ? (
            <p className="text-xs text-brand-textMuted dark:text-slate-400">{a.minutesRetard} min</p>
          ) : null}
          {a.raisonExclusion && <p className="text-xs text-brand-textMuted dark:text-slate-400">{a.raisonExclusion}</p>}
        </div>
      ),
    },
    {
      key: 'justification',
      header: 'Justification',
      render: (a) =>
        a.estJustifie ? (
          <Badge variant="success">Justifiée</Badge>
        ) : a.statutJustificatif ? (
          <Badge variant={STATUT_JUSTIFICATIF_VARIANTS[a.statutJustificatif]}>
            {STATUT_JUSTIFICATIF_LABELS[a.statutJustificatif]}
          </Badge>
        ) : (
          <Badge variant="danger">Non justifiée</Badge>
        ),
    },
    {
      key: 'actions',
      header: '',
      className: 'w-px whitespace-nowrap',
      render: (a) =>
        // Le serveur n'accepte un justificatif que sur une absence : un retard se
        // régularise en corrigeant l'appel, pas par un dépôt de document.
        a.statut !== 'ABSENT' ? (
          <span className="text-xs text-brand-textMuted dark:text-slate-400">—</span>
        ) : a.statutJustificatif === 'EN_ATTENTE' ? (
          <span className="text-xs text-brand-textMuted dark:text-slate-400">Dépôt en cours</span>
        ) : (
          <Button
            size="sm"
            variant="outline"
            onClick={() =>
              setAJustifier({
                ligneAppelId: a.ligneAppelId,
                eleveNom: ref.nomEleve(a.eleveId),
                contexte: `${formatJour(a.dateSeance)} · ${ref.nomClasse(a.groupeClasseId)} · ${ref.nomMatiere(a.matiereId)}`,
              })
            }
          >
            <FileText size={13} /> Justifier
          </Button>
        ),
    },
  ]

  const statsEleve = useMemo(() => {
    const absencesSeules = absences.filter((a) => a.statut === 'ABSENT')
    return {
      total: absencesSeules.length,
      nonJustifiees: absencesSeules.filter((a) => !a.estJustifie).length,
      retards: absences.filter((a) => a.statut === 'RETARD').length,
      enAttente: justificatifsEleve.filter((j) => j.statut === 'EN_ATTENTE').length,
    }
  }, [absences, justificatifsEleve])

  return (
    <div className="space-y-6">
      <PageHero
        title="Justificatifs d'absence"
        subtitle="Traitez ce qui est déposé, et régularisez les absences élève par élève"
        icon={FileCheck}
      />

      <Tabs
        value={onglet}
        onChange={setOnglet}
        tabs={[
          { key: 'file', label: "File d'attente", icon: Inbox },
          { key: 'dossier', label: 'Dossier élève', icon: UserSearch },
        ]}
      />

      {onglet === 'file' ? (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <StatCard title="En attente" value={compte('EN_ATTENTE')} icon={Clock} color="yellow" />
            <StatCard title="Validés" value={compte('VALIDE')} icon={CheckCircle} color="green" />
            <StatCard title="Refusés" value={compte('REFUSE')} icon={XCircle} color="red" />
          </div>

          {/* Le statut se filtre en pastilles portant leur compte. */}
          <div className="rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
            <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Statut</p>
            <div className="flex flex-wrap gap-2">
              {([
                ['EN_ATTENTE', 'En attente', compte('EN_ATTENTE')],
                ['VALIDE', 'Validés', compte('VALIDE')],
                ['REFUSE', 'Refusés', compte('REFUSE')],
                ['', 'Tous', null],
              ] as const).map(([valeur, libelle, nb]) => {
                const actif = filtreStatut === valeur
                return (
                  <button
                    key={libelle}
                    onClick={() => setFiltreStatut(valeur as StatutJustificatif | '')}
                    aria-pressed={actif}
                    className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                      actif
                        ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                        : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                    }`}
                  >
                    {libelle}
                    {nb !== null && (
                      <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                        actif ? 'bg-white/20 text-white' : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                      }`}>
                        {nb}
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          </div>

          {fileLoading && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="h-44 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
              ))}
            </div>
          )}

          {!fileLoading && file.length === 0 && (
            <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
              <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400">
                <CheckCircle size={22} />
              </span>
              <p className="text-sm font-medium text-brand-text dark:text-slate-200">
                {filtreStatut === 'EN_ATTENTE' ? 'Rien en attente — tout est traité' : 'Aucun justificatif pour ce filtre'}
              </p>
            </div>
          )}

          {/* Une carte par justificatif : c'est une décision à prendre, pas une
              ligne à parcourir. Le motif et les notes sont du texte libre qui ne
              tenait pas dans une cellule. */}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {file.map((j) => (
              <div
                key={j.id}
                className="flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900"
              >
                <div className="flex flex-1 flex-col gap-2.5 p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">
                        {ref.nomEleve(j.eleveId)}
                      </h3>
                      <p className="mt-0.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                        Classe {ref.classeEleve(j.eleveId)}
                      </p>
                    </div>
                    <Badge variant={STATUT_JUSTIFICATIF_VARIANTS[j.statut]}>
                      {STATUT_JUSTIFICATIF_LABELS[j.statut]}
                    </Badge>
                  </div>

                  <div className="rounded-lg bg-brand-bgSecondary/60 p-2.5 dark:bg-slate-800/50">
                    <p className="text-xs font-medium text-brand-text dark:text-slate-200">
                      {formatJour(j.dateSeance)}
                    </p>
                    <p className="mt-0.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                      {ref.nomClasse(j.groupeClasseId)} · {ref.nomMatiere(j.matiereId)}
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-1.5">
                    <Badge variant="info">{TYPE_JUSTIFICATIF_LABELS[j.typeDocument]}</Badge>
                    {j.referenceDocument && (
                      <span className="truncate font-mono text-[11px] text-brand-textMuted dark:text-slate-400">
                        {j.referenceDocument}
                      </span>
                    )}
                  </div>

                  {j.notesAdmin && (
                    <p className="rounded-lg bg-brand-bgSecondary/60 p-2 text-xs text-brand-textMuted dark:bg-slate-800/50 dark:text-slate-400">
                      {j.notesAdmin}
                    </p>
                  )}

                  <p className="mt-auto text-xs text-brand-textMuted dark:text-slate-400">
                    Déposé le {formatDate(j.soumisAt)}
                  </p>
                </div>

                <div className="flex items-center justify-end gap-2 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                  {j.statut === 'EN_ATTENTE' ? (
                    <>
                      <Button size="sm" variant="outline" onClick={() => { setDecision({ justif: j, approuver: false }); setNotes('') }}>
                        <XCircle size={13} /> Refuser
                      </Button>
                      <Button size="sm" onClick={() => { setDecision({ justif: j, approuver: true }); setNotes('') }}>
                        <CheckCircle size={13} /> Valider
                      </Button>
                    </>
                  ) : (
                    <span className="text-xs text-brand-textMuted dark:text-slate-400">
                      {j.traiteAt ? `Traité le ${formatDate(j.traiteAt)}` : '—'}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </>
      ) : (
        <>
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-80">
              <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Élève</label>
              <input
                list="eleves-justificatifs"
                value={saisieEleve}
                onChange={(e) => setSaisieEleve(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && rechercherEleve()}
                placeholder="Tapez un nom, un prénom ou un code…"
                className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              />
              <datalist id="eleves-justificatifs">
                {ref.eleves.map((e) => (
                  <option key={e.idEleve} value={libelleEleve(e)} />
                ))}
              </datalist>
            </div>
            <div className="w-40">
              <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Du</label>
              <input
                type="date"
                value={debut}
                max={fin}
                onChange={(e) => setDebut(e.target.value)}
                className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              />
            </div>
            <div className="w-40">
              <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Au</label>
              <input
                type="date"
                value={fin}
                min={debut}
                onChange={(e) => setFin(e.target.value)}
                className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              />
            </div>
            <Button onClick={rechercherEleve}>
              <Search size={16} /> Ouvrir le dossier
            </Button>
          </div>

          {eleve === null ? (
            <div className="rounded-xl border border-brand-border bg-white p-12 text-center dark:border-slate-700 dark:bg-slate-900">
              <UserSearch size={32} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-400" />
              <p className="text-sm text-brand-textMuted dark:text-slate-400">
                Recherchez un élève pour voir ses absences et déposer un justificatif.
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              <p className="text-sm text-brand-textMuted dark:text-slate-400">
                Dossier de{' '}
                <span className="font-medium text-brand-text dark:text-slate-200">
                  {eleve.nom} {eleve.prenom}
                </span>{' '}
                — classe {eleve.classeCode}
              </p>

              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <StatCard title="Absences" value={statsEleve.total} icon={XCircle} color="red" />
                <StatCard title="Non justifiées" value={statsEleve.nonJustifiees} icon={FileText} color="amber" />
                <StatCard title="Retards" value={statsEleve.retards} icon={Clock} color="yellow" />
                <StatCard title="Dépôts en attente" value={statsEleve.enAttente} icon={Inbox} color="blue" />
              </div>

              <section className="space-y-3">
                <h2 className="text-sm font-semibold text-brand-text dark:text-slate-100">Absences sur la période</h2>
                <DataTable
                  columns={colonnesAbsences}
                  data={absences}
                  keyField="ligneAppelId"
                  loading={absencesLoading}
                  emptyMessage="Aucune absence sur cette période."
                />
              </section>

              <section className="space-y-3">
                <h2 className="text-sm font-semibold text-brand-text dark:text-slate-100">
                  Justificatifs de cet élève
                </h2>
                <DataTable
                  columns={colonnesJustificatifs()}
                  data={justificatifsEleve}
                  keyField="id"
                  loading={justifsEleveLoading}
                  emptyMessage="Aucun justificatif déposé pour cet élève."
                />
              </section>
            </div>
          )}
        </>
      )}

      <SoumettreJustificatifModal cible={aJustifier} onClose={() => setAJustifier(null)} />

      <Modal
        open={decision !== null}
        onClose={() => { setDecision(null); setNotes('') }}
        title={decision?.approuver ? 'Valider le justificatif' : 'Refuser le justificatif'}
        size="lg"
      >
        {decision && (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              traiter.mutate(decision)
            }}
            className="space-y-4"
          >
            <div className="rounded-lg border border-brand-border bg-brand-bgSecondary p-3 text-sm dark:border-slate-700 dark:bg-slate-800">
              <p className="font-medium text-brand-text dark:text-slate-100">{ref.nomEleve(decision.justif.eleveId)}</p>
              <p className="text-brand-textMuted dark:text-slate-400">
                {formatJour(decision.justif.dateSeance)} · {TYPE_JUSTIFICATIF_LABELS[decision.justif.typeDocument]}
                {decision.justif.referenceDocument ? ` · ${decision.justif.referenceDocument}` : ''}
              </p>
            </div>

            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              {decision.approuver
                ? "La validation marque l'absence comme justifiée dans la feuille d'appel."
                : "Le refus laisse l'absence non justifiée ; indiquez le motif pour la famille."}
            </p>

            <Input
              label={decision.approuver ? 'Note (optionnelle)' : 'Motif du refus'}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              autoFocus
            />

            <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
              <Button variant="outline" type="button" onClick={() => { setDecision(null); setNotes('') }}>
                Annuler
              </Button>
              <Button type="submit" variant={decision.approuver ? 'primary' : 'danger'} loading={traiter.isPending}>
                {decision.approuver ? 'Valider' : 'Refuser'}
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
