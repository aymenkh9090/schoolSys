import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Lock, LockOpen, ShieldAlert, BookOpen } from 'lucide-react'

import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { absenceApi, type AppelReponse, type LigneAppelReponse, type StatutPresence } from '@/api/absence.api'
import { organisationApi, type Eleve, type SchoolClass } from '@/api/organisation.api'
import { formatDate, cn } from '@/lib/utils'
import { CahierContenu } from './CahierContenu'

function eleveNom(eleves: Eleve[], id: number): string {
  const e = eleves.find((x) => x.idEleve === id)
  return e ? `${e.nom} ${e.prenom}` : `Élève #${id}`
}

function classeNom(classes: SchoolClass[], id: number): string {
  const c = classes.find((x) => x.idClasse === id)
  return c ? c.code : `Classe #${id}`
}

// ─── Statut badge ─────────────────────────────────────────────────────────────

const STATUT_LABELS: Record<StatutPresence, string> = {
  PRESENT: 'Présent',
  ABSENT: 'Absent',
  RETARD: 'En retard',
  EXCLU: 'Exclusion',
}

const STATUT_VARIANTS: Record<StatutPresence, 'success' | 'danger' | 'warning' | 'default'> = {
  PRESENT: 'success',
  ABSENT: 'danger',
  RETARD: 'warning',
  EXCLU: 'danger',
}

const STATUT_LABELS_COURT: Record<StatutPresence, string> = {
  PRESENT: 'Présent',
  ABSENT: 'Absent',
  RETARD: 'Retard',
  EXCLU: 'Exclu',
}

const STATUT_ACTIVE_CLASSES: Record<StatutPresence, string> = {
  PRESENT: 'bg-emerald-600 text-white border-emerald-600',
  ABSENT: 'bg-red-600 text-white border-red-600',
  RETARD: 'bg-amber-500 text-white border-amber-500',
  EXCLU: 'bg-violet-600 text-white border-violet-600',
}

const STATUT_INACTIVE_CLASSES: Record<StatutPresence, string> = {
  PRESENT: 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/30 dark:hover:bg-emerald-500/20',
  ABSENT: 'bg-red-50 text-red-700 border-red-200 hover:bg-red-100 dark:bg-red-500/10 dark:text-red-400 dark:border-red-500/30 dark:hover:bg-red-500/20',
  RETARD: 'bg-amber-50 text-amber-700 border-amber-200 hover:bg-amber-100 dark:bg-amber-500/10 dark:text-amber-400 dark:border-amber-500/30 dark:hover:bg-amber-500/20',
  EXCLU: 'bg-violet-50 text-violet-700 border-violet-200 hover:bg-violet-100 dark:bg-violet-500/10 dark:text-violet-400 dark:border-violet-500/30 dark:hover:bg-violet-500/20',
}

function initiales(nom: string): string {
  const parts = nom.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?'
}

// ─── Détail session ────────────────────────────────────────────────────────────

/** Feuille d'appel d'une séance : statuts des élèves, cahier de séance, clôture. */
export function AppelDetailModal({
  appel,
  onClose,
  onUpdate,
}: {
  appel: AppelReponse
  onClose: () => void
  onUpdate: (updated: AppelReponse) => void
}) {
  const [confirmCloture, setConfirmCloture] = useState(false)
  const [detailRequete, setDetailRequete] = useState<{
    ligneId: number
    eleveNom: string
    statut: 'RETARD' | 'EXCLU'
  } | null>(null)
  const [arriveeAt, setArriveeAt] = useState('')
  const [raisonExclusion, setRaisonExclusion] = useState('')

  const { data: eleves = [] } = useQuery({ queryKey: ['eleves-all'], queryFn: organisationApi.eleves.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes-all'], queryFn: organisationApi.classes.list })

  const { data: cahier, isLoading: cahierLoading, isError: cahierIntrouvable } = useQuery({
    queryKey: ['cahier', appel.id],
    queryFn: () => absenceApi.cahier.recuperer(appel.id),
    retry: false,
  })

  const estFermee = appel.estVerrouille

  const modifierMutation = useMutation({
    mutationFn: ({ ligneId, statut, arriveeAt: arrivee, raisonExclusion: raison }: {
      ligneId: number
      statut: StatutPresence
      arriveeAt?: string
      raisonExclusion?: string
    }) => absenceApi.appel.modifierStatut(ligneId, { statut, arriveeAt: arrivee, raisonExclusion: raison }),
    onSuccess: (ligneMaj) => {
      toast.success('Statut mis à jour')
      onUpdate({
        ...appel,
        lignesAppel: appel.lignesAppel.map((l) => (l.id === ligneMaj.id ? ligneMaj : l)),
      })
      setDetailRequete(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur')
    },
  })

  const ouvrirDetailRequete = (ligneId: number, nom: string, statut: 'RETARD' | 'EXCLU') => {
    setDetailRequete({ ligneId, eleveNom: nom, statut })
    setArriveeAt(new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16))
    setRaisonExclusion('')
  }

  const cloturerMutation = useMutation({
    mutationFn: () => absenceApi.appel.verrouiller(appel.id),
    onSuccess: async () => {
      setConfirmCloture(false)
      toast.success('Session clôturée — les statuts sont désormais figés')
      const fraiche = await absenceApi.appel.get(appel.id)
      onUpdate(fraiche)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur lors de la clôture')
    },
  })

  const compteurs = (appel.lignesAppel ?? []).reduce<Record<StatutPresence, number>>(
    (acc, l) => { acc[l.statut] = (acc[l.statut] ?? 0) + 1; return acc },
    { PRESENT: 0, ABSENT: 0, RETARD: 0, EXCLU: 0 }
  )

  return (
    <Modal open onClose={onClose} title={`Séance d'appel — Classe ${classeNom(classes, appel.groupeClasseId)}`} size="full">
      <div className="flex flex-col h-full">
        {/* En-tête récap */}
        <div className="flex flex-wrap items-center justify-between gap-4 mb-4 pb-4 border-b border-brand-border dark:border-slate-700">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-1 text-sm text-brand-textMuted">
            <p>Année : <span className="font-medium text-brand-text dark:text-slate-200">{appel.anneeAcademique}</span></p>
            <p>Ouverture : <span className="font-medium text-brand-text dark:text-slate-200">{formatDate(appel.ouvertureAt)}</span></p>
            <p className="flex items-center gap-2">
              Statut :
              {estFermee
                ? <Badge variant="default"><Lock size={12} className="inline me-1" />Clôturée</Badge>
                : <Badge variant="success"><LockOpen size={12} className="inline me-1" />Ouverte</Badge>}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {(Object.keys(STATUT_LABELS) as StatutPresence[]).map((s) => (
              <Badge key={s} variant={STATUT_VARIANTS[s]}>
                {compteurs[s]} {STATUT_LABELS[s]}
              </Badge>
            ))}
          </div>

          {!estFermee && (
            <Button variant="danger" onClick={() => setConfirmCloture(true)} loading={cloturerMutation.isPending}>
              <Lock size={16} /> Clôturer la session
            </Button>
          )}
        </div>

        {estFermee && (
          <div className="flex items-start gap-3 p-3 mb-4 bg-amber-50 border border-amber-200 rounded-xl dark:bg-amber-500/10 dark:border-amber-500/30">
            <ShieldAlert size={18} className="text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
            <p className="text-sm text-amber-800 dark:text-amber-300">
              Cette session est clôturée : les statuts de présence ne peuvent plus être modifiés ici.
              Pour régulariser un cas particulier, utilisez le circuit de justificatif depuis « Suivi des absences ».
            </p>
          </div>
        )}

        {/* Cahier de séance */}
        <div className="mb-4 p-4 rounded-xl border border-brand-border dark:border-slate-700 bg-white dark:bg-slate-900">
          <div className="flex items-center gap-2 mb-3">
            <BookOpen size={16} className="text-brand-blue" />
            <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Cahier de séance</h3>
            {cahier && (
              <Badge variant={cahier.estVerrouille ? 'default' : 'success'} className="ms-auto">
                {cahier.estVerrouille ? 'Verrouillé' : 'Modifiable'}
              </Badge>
            )}
          </div>

          {cahierLoading ? (
            <p className="text-sm text-brand-textMuted">Chargement...</p>
          ) : cahierIntrouvable || !cahier ? (
            <p className="text-sm text-brand-textMuted">Cahier non encore rempli par l'enseignant pour cette séance.</p>
          ) : (
            <CahierContenu cahier={cahier} />
          )}
        </div>

        {/* Liste des élèves */}
        <div className="flex-1 overflow-y-auto -mx-1 px-1">
          {appel.lignesAppel?.length ? (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
              {appel.lignesAppel.map((ligne: LigneAppelReponse) => {
                const nom = eleveNom(eleves, ligne.eleveId)
                return (
                  <div
                    key={ligne.id}
                    className="flex items-center justify-between gap-3 p-3.5 rounded-xl border border-brand-border bg-white dark:bg-slate-900 dark:border-slate-700 hover:border-brand-blue/40 transition-colors"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-10 h-10 shrink-0 rounded-full bg-brand-blue/10 text-brand-blue dark:bg-brand-blue/20 flex items-center justify-center text-sm font-semibold">
                        {initiales(nom)}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-brand-text dark:text-slate-100 truncate">{nom}</p>
                        {ligne.estJustifie && <Badge variant="info" className="mt-1">Justifié</Badge>}
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {(Object.keys(STATUT_LABELS) as StatutPresence[]).map((s) => {
                        const actif = ligne.statut === s
                        return (
                          <button
                            key={s}
                            type="button"
                            disabled={estFermee || modifierMutation.isPending}
                            onClick={() => {
                              if (actif) return
                              if (s === 'RETARD' || s === 'EXCLU') ouvrirDetailRequete(ligne.id, nom, s)
                              else modifierMutation.mutate({ ligneId: ligne.id, statut: s })
                            }}
                            className={cn(
                              'px-2.5 py-1.5 rounded-lg border text-xs font-semibold transition-colors',
                              actif ? STATUT_ACTIVE_CLASSES[s] : STATUT_INACTIVE_CLASSES[s],
                              (estFermee || modifierMutation.isPending) && !actif && 'opacity-40 cursor-not-allowed'
                            )}
                          >
                            {STATUT_LABELS_COURT[s]}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="text-center text-sm text-brand-textMuted py-6">Aucune ligne d'appel</p>
          )}
        </div>

        <div className="flex justify-end mt-4 pt-4 border-t border-brand-border dark:border-slate-700">
          <Button variant="outline" onClick={onClose}>Fermer</Button>
        </div>
      </div>

      {/* Précisions requises pour Retard / Exclusion */}
      <Modal
        open={!!detailRequete}
        onClose={() => setDetailRequete(null)}
        title={detailRequete?.statut === 'RETARD' ? "Heure d'arrivée" : "Raison de l'exclusion"}
        size="sm"
      >
        {detailRequete && (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              modifierMutation.mutate(
                detailRequete.statut === 'RETARD'
                  ? { ligneId: detailRequete.ligneId, statut: 'RETARD', arriveeAt }
                  : { ligneId: detailRequete.ligneId, statut: 'EXCLU', raisonExclusion }
              )
            }}
            className="space-y-4"
          >
            <p className="text-sm text-brand-textMuted">{detailRequete.eleveNom}</p>

            {detailRequete.statut === 'RETARD' ? (
              <Input
                label="Heure d'arrivée *"
                type="datetime-local"
                value={arriveeAt}
                onChange={(e) => setArriveeAt(e.target.value)}
                required
              />
            ) : (
              <Input
                label="Raison de l'exclusion *"
                value={raisonExclusion}
                onChange={(e) => setRaisonExclusion(e.target.value)}
                placeholder="Ex: perturbation du cours"
                required
              />
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" type="button" onClick={() => setDetailRequete(null)}>Annuler</Button>
              <Button
                type="submit"
                loading={modifierMutation.isPending}
                disabled={detailRequete.statut === 'RETARD' ? !arriveeAt : !raisonExclusion.trim()}
              >
                Confirmer
              </Button>
            </div>
          </form>
        )}
      </Modal>

      <ConfirmDialog
        open={confirmCloture}
        onClose={() => setConfirmCloture(false)}
        onConfirm={() => cloturerMutation.mutate()}
        loading={cloturerMutation.isPending}
        title="Clôturer la séance ?"
        message="Les présences et absences saisies seront figées : plus aucune modification ne sera possible sur cette séance."
        confirmLabel="Clôturer"
      />
    </Modal>
  )
}

