import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  BookMarked,
  CheckCircle2,
  ChevronDown,
  DoorOpen,
  GraduationCap,
  Heart,
  Languages,
  Library,
  Lightbulb,
  Loader2,
  Pencil,
  Plus,
  Scale,
  ScrollText,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  Trash2,
  Users,
  BookOpen,
} from 'lucide-react'

import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { PageHero } from '@/components/ui/PageHero'
import { organisationApi } from '@/api/organisation.api'
import {
  planningApi,
  type ConstraintCategory,
  type ConstraintDsl,
  type ConstraintProfile,
  type ConstraintSuggestion,
  type ConstraintType,
  type CustomConstraint,
  type ImportanceLevel,
} from '@/api/planning.api'
import type { ConsigneArticle, ConstraintProposal } from '@/api/aiAssistant.api'
import { cn } from '@/lib/utils'
import { AssistantContraintePanel } from './constraints/AssistantContraintePanel'
import { ContraintesOfficielles } from './constraints/ContraintesOfficielles'
import { CustomConstraintModal } from './constraints/CustomConstraintModal'
import { SuggestionsPanel } from './constraints/SuggestionsPanel'
import { slugifyCode } from './constraints/dslLabels'
import {
  CATEGORY_LABELS,
  CATEGORY_LABELS_AR,
  IMPORTANCE_LABELS,
  TYPE_EXPLANATIONS,
  TYPE_LABELS,
  TYPE_LABELS_AR,
  libelleCatalogue,
  type LibelleBilingue,
} from './constraints/catalogueLabels'

/**
 * Les contraintes de génération, sur un seul écran.
 *
 * L'écran est coupé en deux, et cette coupe EST la conception : à gauche ce
 * qui s'applique déjà — le catalogue livré avec le produit, plus les règles
 * propres à l'établissement —, à droite l'assistant pour ajouter ce qui
 * manque. Un directeur arrive avec une règle en tête, pas avec l'envie de
 * choisir une portée et un opérateur ; il la dicte à droite et la voit
 * apparaître à gauche, sans changer de page.
 *
 * Le catalogue est affiché en français ET en arabe. Le backend ne publie que
 * des intitulés anglais issus du seed (« Maximum student hours per day ») :
 * illisibles pour l'administration d'un collège tunisien, qui travaille en
 * arabe et lit une circulaire en arabe. Les deux langues côte à côte évitent
 * de traduire de tête avant de décider.
 *
 * La circulaire officielle et les suggestions statistiques restent
 * accessibles, mais en second plan : ce sont des sources d'inspiration, pas
 * l'état courant de la configuration.
 */

const CATEGORY_ICONS: Record<ConstraintCategory, typeof Users> = {
  STUDENT: Users,
  TEACHER: GraduationCap,
  SUBJECT: BookOpen,
  ROOM: DoorOpen,
  PEDAGOGICAL: Sparkles,
}

/** Une identité visuelle par niveau d'exigence, tenue sur tout l'écran. */
const TYPE_META: Record<ConstraintType, {
  icon: typeof ShieldCheck
  accent: string
  ring: string
  dot: string
  variant: 'danger' | 'warning' | 'info'
}> = {
  HARD: {
    icon: ShieldCheck,
    accent: 'text-red-600 dark:text-red-400',
    ring: 'bg-red-50 dark:bg-red-500/10',
    dot: 'bg-red-500',
    variant: 'danger',
  },
  MEDIUM: {
    icon: Scale,
    accent: 'text-amber-600 dark:text-amber-400',
    ring: 'bg-amber-50 dark:bg-amber-500/10',
    dot: 'bg-amber-500',
    variant: 'warning',
  },
  SOFT: {
    icon: Heart,
    accent: 'text-blue-600 dark:text-blue-400',
    ring: 'bg-blue-50 dark:bg-blue-500/10',
    dot: 'bg-blue-500',
    variant: 'info',
  },
}

const IMPORTANCE_OPTIONS: ImportanceLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

type Filtre = 'toutes' | 'actives' | 'inactives'

const FILTRES: { key: Filtre; label: string }[] = [
  { key: 'toutes', label: 'Toutes' },
  { key: 'actives', label: 'Actives' },
  { key: 'inactives', label: 'Inactives' },
]

/** Une ligne de la liste, quelle que soit son origine. */
type Item =
  | {
      kind: 'catalogue'
      cle: string
      settingId: number
      type: ConstraintType
      category: ConstraintCategory
      enabled: boolean
      importance: ImportanceLevel
      libelle: LibelleBilingue
    }
  | {
      kind: 'custom'
      cle: string
      regle: CustomConstraint
      type: ConstraintType
      enabled: boolean
    }

export default function ConfigurationContraintes() {
  const qc = useQueryClient()

  const [selectedProfile, setSelectedProfile] = useState<ConstraintProfile | null>(null)
  const [createProfileOpen, setCreateProfileOpen] = useState(false)
  const [profileName, setProfileName] = useState('')
  const [deleteProfileTarget, setDeleteProfileTarget] = useState<ConstraintProfile | null>(null)

  const [filtre, setFiltre] = useState<Filtre>('toutes')
  const [recherche, setRecherche] = useState('')
  const [arabeVisible, setArabeVisible] = useState(true)
  const [catalogueOuvert, setCatalogueOuvert] = useState(false)

  const [circulaireOuverte, setCirculaireOuverte] = useState(false)
  const [suggestionsOuvertes, setSuggestionsOuvertes] = useState(false)

  /** Demande poussée vers l'assistant (article de la circulaire, reformulation). */
  const [demandeAssistant, setDemandeAssistant] = useState<{
    texte: string
    nom: string
    jeton: number
  } | null>(null)

  // Éditeur champ par champ : porte de sortie de l'assistant, et mode expert.
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState<CustomConstraint | null>(null)
  const [seed, setSeed] = useState<{ dsl: ConstraintDsl; nom: string } | null>(null)
  const [deleteRuleTarget, setDeleteRuleTarget] = useState<CustomConstraint | null>(null)

  const { data: profiles = [], isLoading: loadingProfiles } = useQuery({
    queryKey: ['constraint-profiles'],
    queryFn: planningApi.constraints.profiles.list,
  })

  const { data: definitions = [] } = useQuery({
    queryKey: ['constraint-definitions'],
    queryFn: planningApi.constraints.definitions,
  })

  // Un profil sans année scolaire n'est jamais retenu par le solveur, qui cherche
  // d'abord le profil actif de l'année générée. L'écran ne demande pas l'année :
  // on rattache donc le nouveau profil à l'année courante.
  const { data: schoolYears = [] } = useQuery({
    queryKey: ['school-years'],
    queryFn: organisationApi.schoolYears.list,
  })
  const currentYear = schoolYears.find((y) => y.estCourante) ?? schoolYears[0]

  const profile = selectedProfile

  const { data: customRules = [], isLoading: loadingRules } = useQuery({
    queryKey: ['custom-constraints', profile?.idConstraintProfile],
    queryFn: () => planningApi.constraints.custom.list(profile!.idConstraintProfile),
    enabled: !!profile,
  })

  /**
   * Ouvrir sur le profil actif plutôt que sur une liste déroulante vide.
   *
   * C'est celui que le solveur utilisera : le montrer d'emblée évite de faire
   * croire qu'aucune contrainte n'est configurée, ce que l'écran vide laissait
   * entendre à chaque visite.
   */
  useEffect(() => {
    if (selectedProfile || profiles.length === 0) return
    const cible = profiles.find((p) => p.active) ?? profiles[0]
    planningApi.constraints.profiles.get(cible.idConstraintProfile).then(setSelectedProfile)
  }, [profiles, selectedProfile])

  /** Recharge le profil courant après une modification de ses réglages. */
  const rafraichirProfil = () => {
    qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
    if (profile) {
      planningApi.constraints.profiles.get(profile.idConstraintProfile).then(setSelectedProfile)
    }
  }

  const createProfileMutation = useMutation({
    mutationFn: () =>
      planningApi.constraints.profiles.createDefault({
        name: profileName,
        schoolYearId: currentYear?.idAnnee,
      }),
    onSuccess: (nouveau) => {
      toast.success('Profil créé avec les contraintes par défaut')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      setSelectedProfile(nouveau)
      setCreateProfileOpen(false)
      setProfileName('')
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const activateProfileMutation = useMutation({
    mutationFn: (id: number) => planningApi.constraints.profiles.activate(id),
    onSuccess: (actif) => {
      toast.success(`« ${actif.name} » est désormais le profil utilisé pour la génération`)
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      setSelectedProfile(actif)
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteProfileMutation = useMutation({
    mutationFn: (id: number) => planningApi.constraints.profiles.delete(id),
    onSuccess: () => {
      toast.success('Profil supprimé')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      setSelectedProfile(null)
      setDeleteProfileTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ settingId, enabled }: { settingId: number; enabled: boolean }) =>
      planningApi.constraints.profiles.updateSetting(settingId, { enabled }),
    onSuccess: (_, { enabled }) => {
      toast.success(enabled ? 'Contrainte activée' : 'Contrainte désactivée')
      rafraichirProfil()
    },
    onError: () => toast.error('Mise à jour impossible'),
  })

  const updateImportanceMutation = useMutation({
    mutationFn: ({ settingId, importance }: { settingId: number; importance: ImportanceLevel }) =>
      planningApi.constraints.profiles.updateSetting(settingId, { importance }),
    onSuccess: () => {
      toast.success('Priorité mise à jour')
      rafraichirProfil()
    },
    onError: () => toast.error('Mise à jour impossible'),
  })

  const addSettingMutation = useMutation({
    mutationFn: ({ profileId, definitionId }: { profileId: number; definitionId: number }) =>
      planningApi.constraints.profiles.addSetting(profileId, {
        constraintDefinitionId: definitionId,
        enabled: true,
        importance: 'MEDIUM',
      }),
    onSuccess: () => {
      toast.success('Contrainte ajoutée au profil')
      rafraichirProfil()
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleRuleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      planningApi.constraints.custom.setEnabled(id, enabled),
    onSuccess: (_, { enabled }) => {
      toast.success(enabled ? 'Règle activée' : 'Règle désactivée')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
    },
    onError: () => toast.error('Mise à jour impossible'),
  })

  const deleteRuleMutation = useMutation({
    mutationFn: (id: number) => planningApi.constraints.custom.delete(id),
    onSuccess: () => {
      toast.success('Règle supprimée')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
      setDeleteRuleTarget(null)
    },
    onError: () => toast.error('Suppression impossible'),
  })

  // Mémorisé sur le profil : sans cela, la liste fabriquée plus bas se
  // reconstruit à chaque rendu, y compris pendant la frappe dans la recherche.
  const settings = useMemo(() => profile?.settings ?? [], [profile])

  const items: Item[] = useMemo(() => {
    const catalogue: Item[] = settings.map((s) => ({
      kind: 'catalogue',
      cle: `c-${s.idConstraintSetting}`,
      settingId: s.idConstraintSetting,
      type: s.type,
      category: s.category,
      enabled: s.enabled,
      importance: s.importance,
      libelle: libelleCatalogue(s.constraintCode, s.constraintName, s.description),
    }))

    const perso: Item[] = customRules.map((r) => ({
      kind: 'custom',
      cle: `p-${r.idCustomConstraint}`,
      regle: r,
      type: r.severity,
      enabled: r.enabled,
    }))

    // Les règles de l'établissement d'abord : ce sont celles que l'utilisateur
    // vient de dicter, et celles qu'il cherche à retrouver.
    return [...perso, ...catalogue]
  }, [settings, customRules])

  const visibles = useMemo(() => {
    const terme = recherche.trim().toLowerCase()
    return items.filter((item) => {
      if (filtre === 'actives' && !item.enabled) return false
      if (filtre === 'inactives' && item.enabled) return false
      if (!terme) return true
      const texte =
        item.kind === 'catalogue'
          ? `${item.libelle.nom} ${item.libelle.nomAr} ${item.libelle.description} ${item.libelle.descriptionAr}`
          : `${item.regle.name} ${item.regle.summary ?? ''} ${item.regle.naturalLanguageRequest ?? ''}`
      return texte.toLowerCase().includes(terme)
    })
  }, [items, filtre, recherche])

  const actives = items.filter((i) => i.enabled).length
  const parType = (t: ConstraintType) => items.filter((i) => i.type === t).length

  const availableDefinitions = definitions.filter(
    (d) => !settings.some((s) => s.definitionId === d.idConstraintDefinition)
  )

  /** Une phrase de la circulaire, ou une règle reformulée, envoyée à l'assistant. */
  const envoyerAssistant = (texte: string, nom: string) => {
    setCirculaireOuverte(false)
    setDemandeAssistant({ texte, nom, jeton: Date.now() })
  }

  const ouvrirEditeur = (regle: CustomConstraint | null, graine?: { dsl: ConstraintDsl; nom: string }) => {
    setEditing(regle)
    setSeed(graine ?? null)
    setEditorOpen(true)
  }

  return (
    <div className="space-y-5">
      {/* ── En-tête ─────────────────────────────────────────────────────── */}
      <PageHero
        title="Contraintes"
        subtitle="Les règles que le générateur doit respecter. Activez celles qui vous conviennent, ou dictez la vôtre à l’assistant."
        icon={Scale}
        actions={
          <>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => setCirculaireOuverte(true)}
            >
              <BookMarked size={16} /> Circulaire officielle
            </Button>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => setSuggestionsOuvertes(true)}
            >
              <Lightbulb size={16} /> Suggestions
            </Button>
          </>
        }
      />

      {/* ── Profil de contraintes ───────────────────────────────────────── */}
      <BandeauProfil
        profiles={profiles}
        profile={profile}
        loading={loadingProfiles}
        onSelect={(id) =>
          id
            ? planningApi.constraints.profiles.get(id).then(setSelectedProfile)
            : setSelectedProfile(null)
        }
        onActivate={() => profile && activateProfileMutation.mutate(profile.idConstraintProfile)}
        activating={activateProfileMutation.isPending}
        onCreate={() => setCreateProfileOpen(true)}
        onDelete={() => profile && setDeleteProfileTarget(profile)}
      />

      {!profile && !loadingProfiles && (
        <div className="rounded-2xl border border-dashed border-brand-border bg-white p-12 text-center dark:border-slate-700 dark:bg-slate-900">
          <Settings size={30} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-500" />
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            Aucun profil de contraintes
          </p>
          <p className="mx-auto mt-1 max-w-md text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
            Un profil rassemble les règles utilisées par une génération. Créez-en un : il arrive
            avec les contraintes standard, prêtes à être ajustées.
          </p>
          <Button className="mt-4" onClick={() => setCreateProfileOpen(true)}>
            <Plus size={16} /> Créer un profil
          </Button>
        </div>
      )}

      {profile && (
        <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_26rem] 2xl:grid-cols-[minmax(0,1fr)_29rem]">
          {/* ── Volet gauche : ce qui s'applique ──────────────────────── */}
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-3">
              {(['HARD', 'MEDIUM', 'SOFT'] as ConstraintType[]).map((t) => {
                const { icon: TypeIcon, accent, ring } = TYPE_META[t]
                return (
                  <div
                    key={t}
                    className="rounded-2xl border border-brand-border bg-white p-3.5 dark:border-slate-700 dark:bg-slate-900"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-xl', ring)}>
                        <TypeIcon size={17} className={accent} />
                      </span>
                      <div className="min-w-0">
                        <p className="text-lg font-bold leading-none text-brand-text dark:text-slate-100">
                          {parType(t)}
                        </p>
                        <p className="truncate text-xs font-medium text-brand-text dark:text-slate-300">
                          {TYPE_LABELS[t]}
                        </p>
                      </div>
                    </div>
                    <p className="mt-2 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
                      {TYPE_EXPLANATIONS[t]}
                    </p>
                  </div>
                )
              })}
            </div>

            {/* Barre d'outils : chercher, filtrer, basculer l'arabe. */}
            <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-brand-border bg-white p-2.5 dark:border-slate-700 dark:bg-slate-900">
              <div className="relative min-w-[12rem] flex-1">
                <Search
                  size={15}
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500"
                />
                <input
                  value={recherche}
                  onChange={(e) => setRecherche(e.target.value)}
                  placeholder="Rechercher une contrainte…"
                  className="w-full rounded-xl border border-brand-border bg-white py-2 pl-9 pr-3 text-sm text-brand-text placeholder:text-brand-textMuted focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
                />
              </div>

              <div className="flex gap-1 rounded-xl bg-brand-bgSecondary p-1 dark:bg-slate-800/70">
                {FILTRES.map((f) => (
                  <button
                    key={f.key}
                    type="button"
                    onClick={() => setFiltre(f.key)}
                    className={cn(
                      'rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
                      filtre === f.key
                        ? 'bg-white text-brand-text shadow-sm dark:bg-slate-900 dark:text-slate-100'
                        : 'text-brand-textMuted hover:text-brand-text dark:text-slate-400'
                    )}
                  >
                    {f.label}
                  </button>
                ))}
              </div>

              {/* L'arabe se coupe, il ne se perd pas : certains directeurs
                  travaillent en français seul et une carte à quatre lignes
                  leur coûte de la lisibilité. */}
              <button
                type="button"
                onClick={() => setArabeVisible((v) => !v)}
                aria-pressed={arabeVisible}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-medium transition-colors',
                  arabeVisible
                    ? 'border-brand-blue/40 bg-brand-blue/10 text-brand-blue dark:border-brand-blue/40 dark:text-blue-300'
                    : 'border-brand-border text-brand-textMuted hover:text-brand-text dark:border-slate-700 dark:text-slate-400'
                )}
              >
                <Languages size={14} /> العربية
              </button>
            </div>

            <p className="px-1 text-xs text-brand-textMuted dark:text-slate-400">
              <span className="font-semibold text-emerald-600 dark:text-emerald-400">{actives}</span>{' '}
              contrainte{actives > 1 ? 's' : ''} active{actives > 1 ? 's' : ''} sur {items.length}
              {visibles.length !== items.length && ` — ${visibles.length} affichée${visibles.length > 1 ? 's' : ''}`}
            </p>

            {loadingRules && items.length === 0 ? (
              <div className="flex items-center justify-center gap-2 rounded-2xl border border-brand-border bg-white py-12 text-sm text-brand-textMuted dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
                <Loader2 size={16} className="animate-spin" /> Chargement des contraintes…
              </div>
            ) : visibles.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-brand-border bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900">
                <Search size={26} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-500" />
                <p className="text-sm text-brand-textMuted dark:text-slate-400">
                  Aucune contrainte ne correspond à cette recherche.
                </p>
              </div>
            ) : (
              <ul className="space-y-3">
                {visibles.map((item) => (
                  <li key={item.cle}>
                    <CarteContrainte
                      item={item}
                      arabeVisible={arabeVisible}
                      onToggle={() =>
                        item.kind === 'catalogue'
                          ? toggleMutation.mutate({
                              settingId: item.settingId,
                              enabled: !item.enabled,
                            })
                          : toggleRuleMutation.mutate({
                              id: item.regle.idCustomConstraint,
                              enabled: !item.enabled,
                            })
                      }
                      onImportance={(importance) =>
                        item.kind === 'catalogue' &&
                        updateImportanceMutation.mutate({ settingId: item.settingId, importance })
                      }
                      onEdit={() => item.kind === 'custom' && ouvrirEditeur(item.regle)}
                      onDelete={() => item.kind === 'custom' && setDeleteRuleTarget(item.regle)}
                    />
                  </li>
                ))}
              </ul>
            )}

            {/* Le reste du catalogue, replié : ce sont des contraintes que ce
                profil n'utilise pas. Les mêler aux actives ferait croire
                qu'elles s'appliquent. */}
            {availableDefinitions.length > 0 && (
              <div className="overflow-hidden rounded-2xl border border-dashed border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900">
                <button
                  type="button"
                  onClick={() => setCatalogueOuvert((v) => !v)}
                  aria-expanded={catalogueOuvert}
                  className="flex w-full items-center gap-2.5 px-4 py-3 text-left transition-colors hover:bg-brand-bgSecondary/60 dark:hover:bg-slate-800/40"
                >
                  <Library size={16} className="shrink-0 text-brand-textMuted dark:text-slate-400" />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-brand-text dark:text-slate-100">
                      Ajouter une contrainte du catalogue
                    </span>
                    <span className="block text-xs text-brand-textMuted dark:text-slate-400">
                      {availableDefinitions.length} contrainte
                      {availableDefinitions.length > 1 ? 's' : ''} disponible
                      {availableDefinitions.length > 1 ? 's' : ''}, non utilisée
                      {availableDefinitions.length > 1 ? 's' : ''} par ce profil
                    </span>
                  </span>
                  <ChevronDown
                    size={16}
                    className={cn(
                      'shrink-0 text-brand-textMuted transition-transform dark:text-slate-400',
                      catalogueOuvert && 'rotate-180'
                    )}
                  />
                </button>

                {catalogueOuvert && (
                  <ul className="divide-y divide-brand-border border-t border-brand-border dark:divide-slate-700 dark:border-slate-700">
                    {availableDefinitions.map((d) => {
                      const libelle = libelleCatalogue(d.code, d.name, d.description)
                      const CatIcon = CATEGORY_ICONS[d.category]
                      return (
                        <li key={d.idConstraintDefinition} className="flex items-start gap-3 px-4 py-3">
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-medium text-brand-text dark:text-slate-100">
                                {libelle.nom}
                              </span>
                              <Badge variant={TYPE_META[d.type].variant}>{TYPE_LABELS[d.type]}</Badge>
                            </div>
                            {arabeVisible && libelle.nomAr && (
                              <p dir="rtl" lang="ar" className="mt-0.5 text-sm text-brand-textMuted dark:text-slate-400">
                                {libelle.nomAr}
                              </p>
                            )}
                            <p className="mt-0.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
                              {libelle.description}
                            </p>
                            <p className="mt-1.5 flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-500">
                              <CatIcon size={12} className="shrink-0" />
                              {CATEGORY_LABELS[d.category]}
                            </p>
                          </div>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() =>
                              addSettingMutation.mutate({
                                profileId: profile.idConstraintProfile,
                                definitionId: d.idConstraintDefinition,
                              })
                            }
                            loading={addSettingMutation.isPending}
                          >
                            <Plus size={12} /> Ajouter
                          </Button>
                        </li>
                      )
                    })}
                  </ul>
                )}
              </div>
            )}
          </div>

          {/* ── Volet droit : l'assistant ─────────────────────────────── */}
          {/* Le panneau reste sous les yeux pendant qu'on parcourt la liste, et
              borne sa hauteur à celle de l'écran : c'est son fil de discussion
              qui défile, pas la page entière. */}
          <div className="xl:sticky xl:top-4 xl:flex xl:max-h-[calc(100vh-2rem)] xl:flex-col">
            <AssistantContraintePanel
              profileId={profile.idConstraintProfile}
              schoolYearId={profile.academicYearId}
              demandeExterne={demandeAssistant}
              onEditManually={(proposal: ConstraintProposal, nom: string) => {
                if (proposal.dsl) ouvrirEditeur(null, { dsl: proposal.dsl, nom })
              }}
            />

            <button
              type="button"
              onClick={() => ouvrirEditeur(null)}
              className="mt-2 flex w-full shrink-0 items-center justify-center gap-1.5 rounded-xl px-2 py-1.5 text-xs text-brand-textMuted underline-offset-2 transition-colors hover:text-brand-text hover:underline dark:text-slate-400 dark:hover:text-slate-200"
            >
              <ScrollText size={13} /> Créer une contrainte moi-même, sans l’assistant
            </button>
          </div>
        </div>
      )}

      {/* ── Circulaire officielle ───────────────────────────────────────── */}
      <Modal
        open={circulaireOuverte}
        onClose={() => setCirculaireOuverte(false)}
        title="Circulaire officielle du ministère"
        size="full"
      >
        <ContraintesOfficielles
          onActivate={(article: ConsigneArticle) =>
            envoyerAssistant(article.texte, `Circulaire § ${article.id}`)
          }
        />
      </Modal>

      {/* ── Suggestions ─────────────────────────────────────────────────── */}
      <Modal
        open={suggestionsOuvertes}
        onClose={() => setSuggestionsOuvertes(false)}
        title="Suggestions issues du dernier planning"
        size="full"
      >
        <SuggestionsPanel
          schoolYearId={profile?.academicYearId}
          onAdopt={(suggestion: ConstraintSuggestion, dsl: ConstraintDsl) => {
            setSuggestionsOuvertes(false)
            ouvrirEditeur(null, { dsl, nom: suggestion.title })
          }}
        />
      </Modal>

      {/* ── Éditeur champ par champ ─────────────────────────────────────── */}
      {profile && (
        <CustomConstraintModal
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          profileId={profile.idConstraintProfile}
          schoolYearId={profile.academicYearId}
          editing={editing}
          initialRule={seed?.dsl ?? null}
          initialName={seed?.nom}
          initialCode={seed ? slugifyCode(seed.nom) : undefined}
        />
      )}

      {/* ── Nouveau profil ──────────────────────────────────────────────── */}
      <Modal
        open={createProfileOpen}
        onClose={() => setCreateProfileOpen(false)}
        title="Nouveau profil de contraintes"
        size="sm"
      >
        <div className="space-y-4">
          <Input
            label="Nom du profil *"
            value={profileName}
            onChange={(e) => setProfileName((e.target as HTMLInputElement).value)}
            placeholder="Profil standard 2024-2025"
          />
          <p className="text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
            Le profil arrive avec toutes les contraintes standard activées. Vous les ajusterez
            ensuite une par une.
          </p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setCreateProfileOpen(false)}>
              Annuler
            </Button>
            <Button
              onClick={() => createProfileMutation.mutate()}
              loading={createProfileMutation.isPending}
              disabled={!profileName}
            >
              Créer
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteProfileTarget}
        onClose={() => setDeleteProfileTarget(null)}
        onConfirm={() =>
          deleteProfileTarget &&
          deleteProfileMutation.mutate(deleteProfileTarget.idConstraintProfile)
        }
        loading={deleteProfileMutation.isPending}
        title="Supprimer le profil"
        message={`Supprimer le profil « ${deleteProfileTarget?.name} », toutes ses contraintes configurées et ses règles personnalisées ? Cette action est irréversible.`}
        variant="danger"
        confirmLabel="Supprimer"
      />

      <ConfirmDialog
        open={!!deleteRuleTarget}
        onClose={() => setDeleteRuleTarget(null)}
        onConfirm={() =>
          deleteRuleTarget && deleteRuleMutation.mutate(deleteRuleTarget.idCustomConstraint)
        }
        loading={deleteRuleMutation.isPending}
        title="Supprimer la règle"
        message={`Supprimer définitivement « ${deleteRuleTarget?.name} » ? Les prochaines générations n’en tiendront plus compte.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Le profil courant, et son état vis-à-vis de la génération.
 *
 * « Utilisé pour la génération » plutôt que « actif » : un profil inactif reste
 * modifiable, et un administrateur qui ajuste dix contraintes sur un profil que
 * le solveur n'ouvrira jamais doit pouvoir s'en apercevoir depuis l'écran.
 */
function BandeauProfil({
  profiles,
  profile,
  loading,
  onSelect,
  onActivate,
  activating,
  onCreate,
  onDelete,
}: {
  profiles: ConstraintProfile[]
  profile: ConstraintProfile | null
  loading: boolean
  onSelect: (id: number | null) => void
  onActivate: () => void
  activating: boolean
  onCreate: () => void
  onDelete: () => void
}) {
  if (loading && profiles.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-2xl border border-brand-border bg-white p-4 text-sm text-brand-textMuted dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
        <Loader2 size={15} className="animate-spin" /> Chargement des profils…
      </div>
    )
  }

  if (profiles.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-brand-border bg-white p-3.5 dark:border-slate-700 dark:bg-slate-900">
      <div className="min-w-0">
        <label
          htmlFor="profil-contraintes"
          className="block text-[11px] font-medium uppercase tracking-wide text-brand-textMuted dark:text-slate-500"
        >
          Profil de contraintes
        </label>
        <select
          id="profil-contraintes"
          value={profile?.idConstraintProfile ?? ''}
          onChange={(e) => onSelect(e.target.value ? Number(e.target.value) : null)}
          className="mt-1 w-64 rounded-xl border border-brand-border bg-white px-3 py-2 text-sm font-medium text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
        >
          <option value="">Sélectionner un profil</option>
          {profiles.map((p) => (
            <option key={p.idConstraintProfile} value={p.idConstraintProfile}>
              {p.name}
              {p.active ? ' — utilisé pour la génération' : ''}
            </option>
          ))}
        </select>
      </div>

      {profile &&
        (profile.active ? (
          <Badge variant="success" className="mt-5 gap-1.5 px-2.5 py-1">
            <CheckCircle2 size={13} /> Utilisé pour la génération
          </Badge>
        ) : (
          <Button size="sm" variant="outline" className="mt-5" onClick={onActivate} loading={activating}>
            <CheckCircle2 size={14} /> Utiliser pour la génération
          </Button>
        ))}

      <div className="ml-auto mt-5 flex items-center gap-2">
        <Button size="sm" variant="outline" onClick={onCreate}>
          <Plus size={14} /> Nouveau profil
        </Button>
        {profile && (
          <button
            type="button"
            title="Supprimer ce profil"
            onClick={onDelete}
            className="rounded-xl border border-brand-border p-2 text-brand-textMuted transition-colors hover:bg-red-50 hover:text-danger dark:border-slate-700 dark:text-slate-400 dark:hover:bg-red-950/30"
          >
            <Trash2 size={15} />
          </button>
        )}
      </div>
    </div>
  )
}

/**
 * Une contrainte, telle qu'un directeur la lit.
 *
 * Le titre en français, le titre en arabe juste dessous, puis l'explication
 * dans les deux langues : rien qui suppose de savoir ce qu'est un solveur ou
 * une portée. Le code technique n'apparaît que sur les règles de
 * l'établissement, en tout petit, parce qu'il sert au support.
 */
function CarteContrainte({
  item,
  arabeVisible,
  onToggle,
  onImportance,
  onEdit,
  onDelete,
}: {
  item: Item
  arabeVisible: boolean
  onToggle: () => void
  onImportance: (importance: ImportanceLevel) => void
  onEdit: () => void
  onDelete: () => void
}) {
  const meta = TYPE_META[item.type]
  const TypeIcon = meta.icon

  const titre = item.kind === 'catalogue' ? item.libelle.nom : item.regle.name
  const titreAr = item.kind === 'catalogue' ? item.libelle.nomAr : ''
  const description =
    item.kind === 'catalogue' ? item.libelle.description : item.regle.summary ?? ''
  const descriptionAr = item.kind === 'catalogue' ? item.libelle.descriptionAr : ''

  return (
    <article
      className={cn(
        'rounded-2xl border bg-white p-4 transition-all dark:bg-slate-900',
        'border-brand-border hover:border-brand-blue/40 hover:shadow-sm dark:border-slate-700',
        !item.enabled && 'opacity-60'
      )}
    >
      <div className="flex items-start gap-3">
        <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-xl', meta.ring)}>
          <TypeIcon size={18} className={meta.accent} />
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">{titre}</h3>
            <Badge variant={item.enabled ? 'success' : 'default'}>
              {item.enabled ? 'Active' : 'Inactive'}
            </Badge>
            {item.kind === 'custom' && (
              <span className="inline-flex items-center gap-1 rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-medium text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300">
                <Sparkles size={10} /> Règle de l’établissement
              </span>
            )}
          </div>

          {arabeVisible && titreAr && (
            <p dir="rtl" lang="ar" className="mt-1 text-sm font-medium text-brand-text dark:text-slate-200">
              {titreAr}
            </p>
          )}

          {description && (
            <p className="mt-1 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
              {description}
            </p>
          )}

          {arabeVisible && descriptionAr && (
            <p
              dir="rtl"
              lang="ar"
              className="mt-1 text-xs leading-loose text-brand-textMuted dark:text-slate-400"
            >
              {descriptionAr}
            </p>
          )}

          {item.kind === 'custom' && item.regle.naturalLanguageRequest && (
            <p className="mt-1.5 text-xs italic leading-relaxed text-brand-textMuted dark:text-slate-500">
              Demande d’origine : « {item.regle.naturalLanguageRequest} »
            </p>
          )}

          <div className="mt-2.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-brand-textMuted dark:text-slate-500">
            <span className="inline-flex items-center gap-1.5">
              <span className={cn('h-1.5 w-1.5 rounded-full', item.enabled ? meta.dot : 'bg-slate-300 dark:bg-slate-600')} />
              {TYPE_LABELS[item.type]}
              {arabeVisible && (
                <span dir="rtl" lang="ar">
                  · {TYPE_LABELS_AR[item.type]}
                </span>
              )}
            </span>

            {item.kind === 'catalogue' && (
              <span className="inline-flex items-center gap-1.5">
                {(() => {
                  const CatIcon = CATEGORY_ICONS[item.category]
                  return <CatIcon size={12} className="shrink-0" />
                })()}
                {CATEGORY_LABELS[item.category]}
                {arabeVisible && (
                  <span dir="rtl" lang="ar">
                    · {CATEGORY_LABELS_AR[item.category]}
                  </span>
                )}
              </span>
            )}

            {item.kind === 'custom' && (
              <span className="font-mono text-[10px]">{item.regle.code}</span>
            )}
          </div>
        </div>

        {/* Commandes : un interrupteur, et selon l'origine une priorité ou
            les actions d'édition. */}
        <div className="flex shrink-0 items-center gap-2">
          {item.kind === 'catalogue' && (
            <label className="hidden flex-col items-end gap-1 sm:flex">
              <span className="text-[10px] uppercase tracking-wide text-brand-textMuted dark:text-slate-500">
                Priorité
              </span>
              <select
                value={item.importance}
                onChange={(e) => onImportance(e.target.value as ImportanceLevel)}
                disabled={!item.enabled}
                title={
                  item.enabled
                    ? 'Priorité accordée par le générateur'
                    : 'Activez la contrainte pour régler sa priorité'
                }
                className="rounded-lg border border-brand-border bg-white px-2 py-1 text-xs text-brand-text disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
              >
                {IMPORTANCE_OPTIONS.map((o) => (
                  <option key={o} value={o}>
                    {IMPORTANCE_LABELS[o]}
                  </option>
                ))}
              </select>
            </label>
          )}

          {item.kind === 'custom' && (
            <>
              <button
                type="button"
                onClick={onEdit}
                aria-label={`Modifier ${titre}`}
                className="rounded-lg p-2 text-brand-textMuted transition-colors hover:bg-brand-bgSecondary hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-800"
              >
                <Pencil size={15} />
              </button>
              <button
                type="button"
                onClick={onDelete}
                aria-label={`Supprimer ${titre}`}
                className="rounded-lg p-2 text-brand-textMuted transition-colors hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-950/30"
              >
                <Trash2 size={15} />
              </button>
            </>
          )}

          <Switch
            checked={item.enabled}
            onChange={onToggle}
            label={`${item.enabled ? 'Désactiver' : 'Activer'} ${titre}`}
          />
        </div>
      </div>
    </article>
  )
}

/** Interrupteur accessible. */
function Switch({
  checked,
  onChange,
  label,
}: {
  checked: boolean
  onChange: () => void
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onChange}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue focus-visible:ring-offset-2',
        'dark:focus-visible:ring-offset-slate-900',
        checked ? 'bg-emerald-500' : 'bg-slate-300 dark:bg-slate-700'
      )}
    >
      <span
        className={cn(
          'inline-block h-4 w-4 transform rounded-full bg-white shadow-sm transition-transform',
          checked ? 'translate-x-6' : 'translate-x-1'
        )}
      />
    </button>
  )
}
