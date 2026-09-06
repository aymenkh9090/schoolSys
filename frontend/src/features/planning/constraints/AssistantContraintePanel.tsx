import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  AlertTriangle,
  BookMarked,
  Check,
  CircleSlash,
  ClipboardList,
  Loader2,
  MessageSquare,
  Pencil,
  Send,
  ShieldCheck,
  Sparkles,
  Target,
  XCircle,
} from 'lucide-react'

import { Button } from '@/components/ui/Button'
import {
  planningAssistantApi,
  type ConsigneSource,
  type ConstraintProposal,
} from '@/api/aiAssistant.api'
import { planningApi } from '@/api/planning.api'
import { cn } from '@/lib/utils'
import { slugifyCode } from './dslLabels'
import {
  NIVEAUX,
  effetsConcrets,
  reformuler,
  resumerContrainte,
  type Effet,
} from './resumeContrainte'

interface Props {
  profileId: number
  schoolYearId?: number
  /** Ouvre l'éditeur détaillé sur la règle proposée, pour l'ajuster à la main. */
  onEditManually: (proposal: ConstraintProposal, name: string) => void
  /**
   * Demande injectée depuis l'extérieur — le texte d'un article de la
   * circulaire, par exemple. L'analyse se lance alors d'elle-même :
   * l'utilisateur a déjà choisi la règle en cliquant, lui demander de cliquer
   * une seconde fois n'ajouterait aucune décision.
   */
  demandeExterne?: { texte: string; nom: string; jeton: number } | null
}

const EXEMPLES = [
  'Pas de sport le vendredi après-midi.',
  'Un enseignant ne doit pas avoir plus de 4 heures de cours d’affilée.',
  'De préférence, les travaux pratiques de physique le matin.',
]

/** Conseils de reformulation, montrés quand la compréhension est refusée. */
const CONSEILS = [
  'Nommez la matière : « sport », « mathématiques »…',
  'Dites le jour ou le moment : « le vendredi », « après 15h », « le matin ».',
  'Donnez le nombre s’il y en a un : « pas plus de 4 heures ».',
  'Une seule règle à la fois : écrivez-en une deuxième ensuite.',
]

/**
 * L'assistant qui transforme une phrase en contrainte, à côté de la liste.
 *
 * Il occupe la moitié droite de l'écran des contraintes, et ce n'est pas une
 * commodité de mise en page : un directeur arrive avec une règle en tête
 * (« pas de sport le vendredi »), pas avec l'envie de choisir une portée et un
 * opérateur. La liste dit ce qui s'applique déjà, le panneau sert à ajouter ce
 * qui manque — les deux à la fois, sans changer d'écran.
 *
 * La réponse est rendue en trois blocs, et cet ordre est celui d'une
 * explication orale : ce que j'ai compris de votre phrase, ce que la règle dit
 * exactement, ce que ça change concrètement. Puis une question fermée — cette
 * compréhension vous convient-elle ? — parce qu'un accord doit être un geste,
 * pas l'absence de désaccord.
 *
 * La forme technique de la règle n'est jamais montrée. Elle existe, elle est
 * validée par le serveur, elle reste consultable dans l'éditeur détaillé pour
 * qui le demande ; l'afficher ici ferait passer un écran de décision pour un
 * écran de développeur, et personne n'ose valider ce qu'il ne comprend pas.
 *
 * Le parcours reste en deux temps, et c'est le cœur du contrat : l'assistant
 * PROPOSE (rien n'est écrit), l'utilisateur ACCEPTE (le serveur revalide et
 * enregistre).
 */
export function AssistantContraintePanel({
  profileId,
  schoolYearId,
  onEditManually,
  demandeExterne,
}: Props) {
  const qc = useQueryClient()

  const [saisie, setSaisie] = useState('')
  /** La demande réellement soumise : elle survit à ce qu'on tape ensuite. */
  const [demande, setDemande] = useState('')
  const [proposal, setProposal] = useState<ConstraintProposal | null>(null)
  const [nom, setNom] = useState('')
  const [refuse, setRefuse] = useState(false)

  const filDiscussion = useRef<HTMLDivElement>(null)
  /** Dernier jeton traité : une demande externe ne se rejoue pas au re-rendu. */
  const jetonTraite = useRef<number | null>(null)

  // Le catalogue des champs sert à nommer les conditions en français
  // (« Jour », « Matière ») plutôt qu'en identifiants. Partagé avec l'éditeur
  // détaillé, donc déjà en cache la plupart du temps.
  const { data: schema } = useQuery({
    queryKey: ['dsl-schema'],
    queryFn: planningApi.constraints.custom.schema,
    staleTime: Infinity,
  })

  const proposeMutation = useMutation({
    mutationFn: (texte: string) =>
      planningAssistantApi.propose(texte, { schoolYearId, profileId }),
    onSuccess: (resultat, texte) => {
      setProposal(resultat)
      // Mise à jour fonctionnelle : un nom déjà posé par l'appelant (« Circulaire
      // § II.2 ») ne doit pas être écrasé par l'intitulé dérivé de la phrase, et
      // la closure de ce callback porte la valeur d'AVANT la demande.
      if (resultat.valid) setNom((actuel) => actuel || nomParDefaut(texte))
    },
    onError: () =>
      toast.error('L’assistant est injoignable. Vérifiez que le service IA est démarré.'),
  })

  const confirmMutation = useMutation({
    mutationFn: () =>
      planningAssistantApi.confirm({
        constraint_profile_id: profileId,
        code: slugifyCode(nom),
        name: nom,
        dsl: proposal!.dsl!,
        natural_language_request: demande,
      }),
    onSuccess: () => {
      toast.success('Contrainte créée — elle sera respectée à la prochaine génération')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
      reinitialiser()
    },
    onError: (e: { response?: { data?: { detail?: string } } }) =>
      toast.error(e.response?.data?.detail ?? 'Enregistrement impossible'),
  })

  const lancer = (texte: string, nomPropose?: string) => {
    const propre = texte.trim()
    if (!propre || proposeMutation.isPending) return
    setDemande(propre)
    setProposal(null)
    setRefuse(false)
    setNom(nomPropose ?? '')
    setSaisie('')
    proposeMutation.mutate(propre)
  }

  const reinitialiser = () => {
    setDemande('')
    setProposal(null)
    setNom('')
    setRefuse(false)
    setSaisie('')
  }

  /** « Modifier ma demande » : la phrase revient dans le champ, telle quelle. */
  const reprendre = () => {
    setSaisie(demande)
    setProposal(null)
    setRefuse(false)
  }

  /** « Non, ce n'est pas ça » : on jette la proposition et on aide à réécrire. */
  const refuser = () => {
    setSaisie(demande)
    setProposal(null)
    setRefuse(true)
  }

  useEffect(() => {
    if (!demandeExterne || jetonTraite.current === demandeExterne.jeton) return
    jetonTraite.current = demandeExterne.jeton
    lancer(demandeExterne.texte, demandeExterne.nom)
    // `lancer` est stable pour la durée de vie du panneau.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [demandeExterne])

  // Le fil suit la conversation : une réponse qui arrive hors du champ de
  // vision passe pour une absence de réponse.
  useEffect(() => {
    filDiscussion.current?.scrollTo({ top: filDiscussion.current.scrollHeight, behavior: 'smooth' })
  }, [demande, proposal, proposeMutation.isPending])

  const acceptable = Boolean(proposal?.valid && proposal.dsl && nom.trim())

  return (
    <section className="flex h-full flex-col overflow-hidden rounded-2xl border border-brand-border bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <header className="flex items-center gap-3 bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-3.5 text-white">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white/20">
          <Sparkles size={17} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-semibold">Assistant — création de contrainte</h2>
          <p className="truncate text-xs text-white/80">
            Dites ce que vous voulez, il s’occupe du reste
          </p>
        </div>
        {demande && (
          <button
            type="button"
            onClick={reinitialiser}
            className="rounded-lg px-2 py-1 text-xs text-white/85 transition-colors hover:bg-white/15 hover:text-white"
          >
            Recommencer
          </button>
        )}
      </header>

      <div ref={filDiscussion} className="min-h-[20rem] flex-1 space-y-4 overflow-y-auto p-4">
        {!demande && !proposeMutation.isPending && (
          <EtatVide onChoisir={(exemple) => setSaisie(exemple)} />
        )}

        {demande && (
          <div className="flex justify-end">
            <p className="max-w-[85%] rounded-2xl rounded-br-sm bg-indigo-50 px-3.5 py-2.5 text-sm leading-relaxed text-brand-text dark:bg-indigo-500/10 dark:text-slate-200">
              {demande}
            </p>
          </div>
        )}

        {refuse && (
          <div className="rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-3.5 dark:border-slate-700 dark:bg-slate-800/40">
            <p className="text-sm font-medium text-brand-text dark:text-slate-100">
              D’accord — réécrivons-la ensemble
            </p>
            <ul className="mt-2 space-y-1.5">
              {CONSEILS.map((conseil) => (
                <li
                  key={conseil}
                  className="flex items-start gap-2 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400"
                >
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-400" />
                  {conseil}
                </li>
              ))}
            </ul>
            <p className="mt-2.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-500">
              Votre phrase est restée dans le champ ci-dessous : corrigez-la et renvoyez-la.
            </p>
          </div>
        )}

        {proposeMutation.isPending && (
          <div className="flex items-center gap-2.5 rounded-xl border border-brand-border px-3.5 py-3 text-sm text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
            <Loader2 size={15} className="animate-spin text-indigo-600 dark:text-indigo-400" />
            <span>
              Je lis votre demande et je vérifie ce qu’elle donnerait sur l’année…
              <span className="mt-0.5 block text-xs">
                Cela prend de quelques secondes à une minute.
              </span>
            </span>
          </div>
        )}

        {proposal && !proposal.valid && (
          <div className="rounded-xl border border-red-200 bg-red-50/60 p-3.5 dark:border-red-500/25 dark:bg-red-500/5">
            <p className="flex items-center gap-2 text-sm font-semibold text-brand-text dark:text-slate-100">
              <XCircle size={16} className="shrink-0 text-danger" />
              Je n’ai pas bien compris votre phrase
            </p>
            <p className="mt-1.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
              {proposal.message || 'Essayez de la dire autrement.'}
            </p>
            <ul className="mt-2 space-y-1.5">
              {CONSEILS.slice(0, 3).map((conseil) => (
                <li
                  key={conseil}
                  className="flex items-start gap-2 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400"
                >
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-red-400" />
                  {conseil}
                </li>
              ))}
            </ul>
            <Button size="sm" variant="outline" className="mt-3" onClick={reprendre}>
              <Pencil size={13} /> Réécrire ma demande
            </Button>
          </div>
        )}

        {proposal?.valid && proposal.dsl && (
          <div className="space-y-3">
            <p className="flex items-center gap-2 text-sm font-semibold text-brand-text dark:text-slate-100">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md bg-indigo-600 text-[11px] font-bold text-white">
                ✓
              </span>
              L’assistant a compris votre demande
            </p>

            {/* 1 — Ce que j'ai compris. Si cette phrase est fausse, rien de ce
                qui suit ne mérite d'être lu : elle vient donc en premier. */}
            <Bloc
              icone={MessageSquare}
              titre="Reformulation"
              teinte="violet"
            >
              <p className="text-xs leading-relaxed text-brand-text dark:text-slate-200">
                {reformuler(proposal.dsl, proposal.summary, schema)}
              </p>
            </Bloc>

            {/* 2 — Ce que la règle dit exactement, en quatre lignes. */}
            <Bloc icone={ClipboardList} titre="Résumé de la contrainte" teinte="emeraude">
              <ul className="space-y-1.5">
                {resumerContrainte(proposal.dsl, schema).map((ligne) => (
                  <li key={ligne.etiquette} className="text-xs leading-relaxed">
                    <span className="font-semibold text-brand-text dark:text-slate-200">
                      {ligne.etiquette} :
                    </span>{' '}
                    <span className="text-brand-textMuted dark:text-slate-400">{ligne.valeur}</span>
                  </li>
                ))}
                <li className="flex items-center gap-1.5 text-xs">
                  <span className="font-semibold text-brand-text dark:text-slate-200">Niveau :</span>
                  <span
                    className={cn('h-2 w-2 shrink-0 rounded-full', NIVEAUX[proposal.dsl.severity].couleur)}
                  />
                  <span className="text-brand-textMuted dark:text-slate-400">
                    {NIVEAUX[proposal.dsl.severity].label}
                  </span>
                </li>
              </ul>
            </Bloc>

            {/* 3 — Ce que ça change, mesuré sur les cours réels de l'année. */}
            <Bloc icone={Target} titre="Effets concrets" teinte="bleu">
              <ul className="space-y-1.5">
                {effetsConcrets(proposal, proposal.dsl).map((effet, i) => (
                  <LigneEffet key={i} effet={effet} />
                ))}
              </ul>
            </Bloc>

            <Sources sources={proposal.sources} />

            <label className="block">
              <span className="text-xs font-medium text-brand-text dark:text-slate-200">
                Nom de cette contrainte, pour la retrouver dans la liste
              </span>
              <input
                value={nom}
                onChange={(e) => setNom(e.target.value)}
                placeholder="Pas de sport le vendredi après-midi"
                className="mt-1 w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text placeholder:text-brand-textMuted focus:border-transparent focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
              />
            </label>

            {/* Une question fermée, et trois issues nettes. L'accord est un
                geste : on ne le déduit pas de l'absence de refus. */}
            <div className="rounded-xl border border-emerald-200 bg-emerald-50/60 p-3 dark:border-emerald-500/25 dark:bg-emerald-500/5">
              <p className="text-xs font-semibold text-brand-text dark:text-slate-100">
                Cette compréhension vous convient-elle ?
              </p>
              <div className="mt-2.5 space-y-2">
                <Button
                  onClick={() => confirmMutation.mutate()}
                  loading={confirmMutation.isPending}
                  disabled={!acceptable}
                  className="w-full bg-indigo-600 hover:bg-indigo-700 focus-visible:ring-indigo-500"
                >
                  <Check size={15} /> Oui, créer cette contrainte
                </Button>
                <div className="flex gap-2">
                  <Button variant="outline" onClick={reprendre} className="flex-1">
                    <Pencil size={14} /> Modifier ma demande
                  </Button>
                  <Button variant="outline" onClick={refuser} className="flex-1">
                    <CircleSlash size={14} /> Ce n’est pas ça
                  </Button>
                </div>
              </div>
            </div>

            <ProchainesEtapes />

            {/* La porte de sortie de qui veut régler les détails lui-même.
                Discrète, et sans jargon : ce n'est pas le chemin normal. */}
            <button
              type="button"
              onClick={() => onEditManually(proposal, nom)}
              className="w-full rounded-lg px-2 py-1 text-center text-xs text-brand-textMuted underline-offset-2 transition-colors hover:text-brand-text hover:underline dark:text-slate-400 dark:hover:text-slate-200"
            >
              Ajuster les détails moi-même avant de créer
            </button>
          </div>
        )}
      </div>

      <footer className="border-t border-brand-border p-3 dark:border-slate-700">
        <div className="flex items-end gap-2">
          <textarea
            value={saisie}
            onChange={(e) => setSaisie(e.target.value)}
            onKeyDown={(e) => {
              // Entrée envoie, Maj+Entrée passe à la ligne : la convention que
              // l'utilisateur a déjà dans toutes ses messageries.
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                lancer(saisie)
              }
            }}
            rows={2}
            maxLength={500}
            disabled={proposeMutation.isPending}
            placeholder="Ex. : pas de mathématiques après 15h le vendredi."
            className="min-h-[2.75rem] flex-1 resize-none rounded-xl border border-brand-border bg-white px-3 py-2 text-sm text-brand-text placeholder:text-brand-textMuted focus:border-transparent focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-60 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:placeholder:text-slate-500"
          />
          <button
            type="button"
            onClick={() => lancer(saisie)}
            disabled={!saisie.trim() || proposeMutation.isPending}
            aria-label="Envoyer la demande à l’assistant"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {proposeMutation.isPending ? (
              <Loader2 size={17} className="animate-spin" />
            ) : (
              <Send size={17} />
            )}
          </button>
        </div>

        <p className="mt-2 flex items-start gap-1.5 text-[11px] leading-relaxed text-brand-textMuted dark:text-slate-500">
          <ShieldCheck size={12} className="mt-0.5 shrink-0" />
          Rien n’est enregistré tant que vous n’avez pas répondu « Oui ».
        </p>
      </footer>
    </section>
  )
}

// ─────────────────────────────────────────────────────────────────────────────

const TEINTES = {
  violet: {
    fond: 'bg-violet-50/70 dark:bg-violet-500/5',
    pastille: 'bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300',
  },
  emeraude: {
    fond: 'bg-emerald-50/70 dark:bg-emerald-500/5',
    pastille: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
  },
  bleu: {
    fond: 'bg-blue-50/70 dark:bg-blue-500/5',
    pastille: 'bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  },
}

/** Un des trois blocs de la réponse : une pastille, un titre, un contenu. */
function Bloc({
  icone: Icone,
  titre,
  teinte,
  children,
}: {
  icone: React.ElementType
  titre: string
  teinte: keyof typeof TEINTES
  children: React.ReactNode
}) {
  const { fond, pastille } = TEINTES[teinte]
  return (
    <section className={cn('flex gap-3 rounded-xl p-3', fond)}>
      <span className={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', pastille)}>
        <Icone size={15} />
      </span>
      <div className="min-w-0 flex-1">
        <h3 className="mb-1.5 text-xs font-semibold text-brand-text dark:text-slate-100">{titre}</h3>
        {children}
      </div>
    </section>
  )
}

function LigneEffet({ effet }: { effet: Effet }) {
  const alerte = effet.ton === 'alerte'
  return (
    <li className="flex items-start gap-2 text-xs leading-relaxed">
      {alerte ? (
        <AlertTriangle size={12} className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />
      ) : (
        <Check size={12} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" />
      )}
      <span
        className={cn(
          alerte
            ? 'text-amber-800 dark:text-amber-300'
            : 'text-brand-textMuted dark:text-slate-400'
        )}
      >
        {effet.texte}
      </span>
    </li>
  )
}

/**
 * Ce qui se passe après le « Oui ».
 *
 * Trois phrases, aucune technique. Un administrateur hésite moins à valider
 * quand il sait que la règle restera visible et désactivable — la peur de
 * l'irréversible est ce qui bloque le plus souvent la main sur le bouton.
 */
function ProchainesEtapes() {
  const etapes = [
    'Elle est enregistrée dans les contraintes de votre établissement.',
    'Elle apparaît dans la liste, à gauche : vous pouvez la désactiver à tout moment.',
    'Elle est appliquée à la prochaine génération de l’emploi du temps.',
  ]

  return (
    <div className="rounded-xl border border-brand-border p-3 dark:border-slate-700">
      <p className="text-xs font-semibold text-brand-text dark:text-slate-100">
        Ce qui se passera ensuite
      </p>
      <ol className="mt-2 space-y-1.5">
        {etapes.map((etape, i) => (
          <li
            key={etape}
            className="flex items-start gap-2 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400"
          >
            <span className="mt-px flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-brand-bgSecondary text-[10px] font-semibold text-brand-text dark:bg-slate-800 dark:text-slate-300">
              {i + 1}
            </span>
            {etape}
          </li>
        ))}
      </ol>
    </div>
  )
}

function EtatVide({ onChoisir }: { onChoisir: (exemple: string) => void }) {
  return (
    <div className="py-2">
      <span className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-50 dark:bg-indigo-500/10">
        <Sparkles size={20} className="text-indigo-600 dark:text-indigo-400" />
      </span>
      <p className="text-center text-sm font-medium text-brand-text dark:text-slate-200">
        Quelle règle voulez-vous ajouter ?
      </p>
      <p className="mx-auto mt-1 max-w-xs text-center text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
        Écrivez-la comme vous la diriez à un collègue. Je vous montrerai ce que j’ai compris et ce
        que ça changerait, avant que vous ne décidiez.
      </p>

      {/* Montrer la forme attendue vaut mieux que la décrire : une demande
          bien formée change du tout au tout la qualité de la réponse, et
          l'utilisateur n'a aucun moyen de le deviner. */}
      <div className="mt-4 space-y-1.5">
        {EXEMPLES.map((exemple) => (
          <button
            key={exemple}
            type="button"
            onClick={() => onChoisir(exemple)}
            className="flex w-full items-start gap-2 rounded-xl border border-brand-border px-3 py-2 text-left text-xs leading-relaxed text-brand-textMuted transition-colors hover:border-indigo-300 hover:bg-indigo-50/50 hover:text-brand-text dark:border-slate-700 dark:text-slate-400 dark:hover:border-indigo-500/40 dark:hover:bg-indigo-500/5 dark:hover:text-slate-200"
          >
            <Sparkles size={12} className="mt-0.5 shrink-0 text-indigo-500" />
            {exemple}
          </button>
        ))}
      </div>
    </div>
  )
}

/**
 * Le fondement réglementaire, quand il existe.
 *
 * Seuls les articles CONCORDANTS sont cités : ce sont ceux dont la portée est
 * bien celle que la règle applique. Les autres ont été remontés par la
 * recherche sans que la règle les applique — les afficher comme correspondants
 * serait une citation décorative, c'est-à-dire une apparence de preuve.
 */
function Sources({ sources }: { sources: ConsigneSource[] }) {
  const concordants = sources.filter((s) => s.concordance)
  if (concordants.length === 0) return null

  return (
    <div className="rounded-xl border border-brand-border p-2.5 dark:border-slate-700">
      <p className="flex items-center gap-1.5 text-xs font-semibold text-brand-text dark:text-slate-200">
        <BookMarked size={12} className="text-indigo-600 dark:text-indigo-400" />
        Prévu par la circulaire officielle
      </p>
      <ul className="mt-1.5 space-y-1">
        {concordants.map((source) => (
          <li
            key={source.id}
            className="text-xs leading-relaxed text-brand-textMuted dark:text-slate-400"
          >
            <span className="font-medium text-brand-text dark:text-slate-200">§ {source.id}</span> —{' '}
            {source.citation}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Intitulé court dérivé de la demande, tronqué sur un mot entier. */
function nomParDefaut(demande: string): string {
  const propre = demande.trim().replace(/[.!?]+$/, '')
  if (propre.length <= 60) return propre
  const coupe = propre.slice(0, 60)
  return coupe.slice(0, coupe.lastIndexOf(' ')) || coupe
}
