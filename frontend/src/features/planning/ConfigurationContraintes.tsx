import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Settings, Trash2, GraduationCap, Users, BookOpen, DoorOpen, Sparkles, ShieldCheck, Scale, Heart, Library, ScrollText, Lightbulb, CheckCircle2, BookMarked } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { organisationApi } from '@/api/organisation.api'
import { planningApi, type ConstraintProfile, type ConstraintType, type ImportanceLevel, type ConstraintCategory, type ConstraintDsl, type ConstraintSuggestion } from '@/api/planning.api'
import { cn } from '@/lib/utils'
import type { ConsigneArticle, ConstraintProposal } from '@/api/aiAssistant.api'
import { ContraintesPersonnalisees } from './constraints/ContraintesPersonnalisees'
import { ContraintesOfficielles } from './constraints/ContraintesOfficielles'
import { AssistantRuleModal } from './constraints/AssistantRuleModal'
import { CustomConstraintModal } from './constraints/CustomConstraintModal'
import { SuggestionsPanel } from './constraints/SuggestionsPanel'
import { slugifyCode } from './constraints/dslLabels'

/**
 * Les quatre facettes de la configuration, dans l'ordre où on les rencontre :
 * ce que la loi impose, ce que le produit fournit, ce que l'école ajoute, ce
 * que l'analyse suggère.
 *
 * « Officielles » vient en PREMIER, et c'est un choix. L'ordre d'un jeu
 * d'onglets se lit comme un ordre de préséance : une réglementation nationale
 * placée après les réglages maison suggérerait qu'on s'en occupe une fois le
 * reste fait. C'est aussi le seul onglet où l'administrateur n'a rien à
 * rédiger — il lit du français et active.
 *
 * L'assistant conversationnel a sa propre entrée dans le menu Planning : on
 * l'ouvre pour comprendre un planning, pas pour en régler la configuration.
 */
type ConstraintTab = 'officielles' | 'catalogue' | 'custom' | 'suggestions'

const CONSTRAINT_TABS: TabItem<ConstraintTab>[] = [
  { key: 'officielles', label: 'Officielles', icon: BookMarked },
  { key: 'catalogue', label: 'Catalogue', icon: Library },
  { key: 'custom', label: 'Règles personnalisées', icon: ScrollText },
  { key: 'suggestions', label: 'Suggestions', icon: Lightbulb },
]

const TYPE_VARIANTS: Record<ConstraintType, 'danger' | 'warning' | 'info'> = {
  HARD: 'danger',
  MEDIUM: 'warning',
  SOFT: 'info',
}

const TYPE_LABELS: Record<ConstraintType, string> = {
  HARD: 'Obligatoire',
  MEDIUM: 'Fortement recommandée',
  SOFT: 'Souhaitable',
}

const TYPE_EXPLANATIONS: Record<ConstraintType, string> = {
  HARD: 'Doit toujours être respectée — le planning généré ne pourra jamais l\'enfreindre.',
  MEDIUM: 'Le solveur essaie fortement de la respecter, mais peut l\'enfreindre si aucune solution valide n\'existe autrement.',
  SOFT: 'Préférence : le solveur l\'optimise en priorité basse, sans bloquer la génération si elle n\'est pas respectée.',
}

const IMPORTANCE_LABELS: Record<ImportanceLevel, string> = {
  CRITICAL: 'Critique',
  HIGH: 'Élevée',
  MEDIUM: 'Moyenne',
  LOW: 'Faible',
}

const CATEGORY_LABELS: Record<ConstraintCategory, string> = {
  STUDENT: 'Élèves',
  TEACHER: 'Enseignants',
  SUBJECT: 'Matières',
  ROOM: 'Salles',
  PEDAGOGICAL: 'Pédagogique',
}

const CATEGORY_ICONS: Record<ConstraintCategory, typeof Users> = {
  STUDENT: Users,
  TEACHER: GraduationCap,
  SUBJECT: BookOpen,
  ROOM: DoorOpen,
  PEDAGOGICAL: Sparkles,
}

/** Une carte par niveau de contrainte : icône, accent couleur et intention. */
const TYPE_META: Record<ConstraintType, {
  icon: typeof ShieldCheck
  accent: string
  ring: string
  dot: string
}> = {
  HARD: {
    icon: ShieldCheck,
    accent: 'text-red-600 dark:text-red-400',
    ring: 'bg-red-50 dark:bg-red-500/10',
    dot: 'bg-red-500',
  },
  MEDIUM: {
    icon: Scale,
    accent: 'text-amber-600 dark:text-amber-400',
    ring: 'bg-amber-50 dark:bg-amber-500/10',
    dot: 'bg-amber-500',
  },
  SOFT: {
    icon: Heart,
    accent: 'text-blue-600 dark:text-blue-400',
    ring: 'bg-blue-50 dark:bg-blue-500/10',
    dot: 'bg-blue-500',
  },
}

/** Interrupteur accessible — remplace l'ancien couple d'icônes ToggleLeft/Right. */
function Switch({ checked, onChange, label }: { checked: boolean; onChange: () => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onChange}
      className={cn(
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue focus-visible:ring-offset-2',
        'dark:focus-visible:ring-offset-slate-900',
        checked ? 'bg-brand-blue' : 'bg-slate-300 dark:bg-slate-700'
      )}
    >
      <span
        className={cn(
          'inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow-sm transition-transform',
          checked ? 'translate-x-[1.15rem]' : 'translate-x-1'
        )}
      />
    </button>
  )
}

const IMPORTANCE_OPTIONS: ImportanceLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export default function ConfigurationContraintes() {
  const qc = useQueryClient()
  const [selectedProfile, setSelectedProfile] = useState<ConstraintProfile | null>(null)
  const [createProfileOpen, setCreateProfileOpen] = useState(false)
  const [profileName, setProfileName] = useState('')
  const [deleteProfileTarget, setDeleteProfileTarget] = useState<ConstraintProfile | null>(null)
  const [tab, setTab] = useState<ConstraintTab>('officielles')

  // Article de la circulaire en cours d'activation. Il ouvre l'assistant
  // pré-rempli avec le texte de l'article : celui-ci repasse par la traduction,
  // la validation du backend et l'analyse d'impact, exactement comme une règle
  // écrite à la main. Un article officiel n'est pas une règle déjà valide — il
  // est écrit pour des humains, pas pour un solveur.
  const [articleActive, setArticleActive] = useState<ConsigneArticle | null>(null)

  // Règle issue d'une suggestion, en attente de relecture dans l'éditeur.
  // Elle transite par cet état plutôt que d'être créée directement : une
  // proposition statistique reste une proposition tant qu'un humain ne l'a pas
  // vérifiée sur ses propres données.
  const [adopted, setAdopted] = useState<{ dsl: ConstraintDsl; name: string } | null>(null)


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

  const createProfileMutation = useMutation({
    mutationFn: () => planningApi.constraints.profiles.createDefault({ name: profileName, schoolYearId: currentYear?.idAnnee }),
    onSuccess: (profile) => {
      toast.success('Profil créé avec les contraintes par défaut')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      setSelectedProfile(profile)
      setCreateProfileOpen(false)
      setProfileName('')
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const activateProfileMutation = useMutation({
    mutationFn: (id: number) => planningApi.constraints.profiles.activate(id),
    onSuccess: (profile) => {
      toast.success(`« ${profile.name} » est désormais le profil utilisé pour la génération`)
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      setSelectedProfile(profile)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteProfileMutation = useMutation({
    mutationFn: (id: number) => planningApi.constraints.profiles.delete(id),
    onSuccess: () => {
      toast.success('Profil supprimé')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      if (selectedProfile?.idConstraintProfile === deleteProfileTarget?.idConstraintProfile) {
        setSelectedProfile(null)
      }
      setDeleteProfileTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ settingId, enabled }: { settingId: number; enabled: boolean }) =>
      planningApi.constraints.profiles.updateSetting(settingId, { enabled }),
    onSuccess: () => {
      toast.success('Contrainte mise à jour')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      if (selectedProfile) {
        planningApi.constraints.profiles.get(selectedProfile.idConstraintProfile).then(setSelectedProfile)
      }
    },
    onError: () => toast.error('Erreur'),
  })

  const updateImportanceMutation = useMutation({
    mutationFn: ({ settingId, importance }: { settingId: number; importance: ImportanceLevel }) =>
      planningApi.constraints.profiles.updateSetting(settingId, { importance }),
    onSuccess: () => {
      toast.success('Importance mise à jour')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      if (selectedProfile) {
        planningApi.constraints.profiles.get(selectedProfile.idConstraintProfile).then(setSelectedProfile)
      }
    },
    onError: () => toast.error('Erreur'),
  })

  const addSettingMutation = useMutation({
    mutationFn: ({ profileId, definitionId }: { profileId: number; definitionId: number }) =>
      planningApi.constraints.profiles.addSetting(profileId, {
        constraintDefinitionId: definitionId,
        enabled: true,
        importance: 'MEDIUM',
      }),
    onSuccess: () => {
      toast.success('Contrainte ajoutée')
      qc.invalidateQueries({ queryKey: ['constraint-profiles'] })
      if (selectedProfile) {
        planningApi.constraints.profiles.get(selectedProfile.idConstraintProfile).then(setSelectedProfile)
      }
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const profile = selectedProfile
  const settings = profile?.settings ?? []
  const availableDefinitions = definitions.filter(
    (d) => !settings.some((s) => s.definitionId === d.idConstraintDefinition)
  )

  const hardCount = settings.filter((s) => s.type === 'HARD').length
  const mediumCount = settings.filter((s) => s.type === 'MEDIUM').length
  const softCount = settings.filter((s) => s.type === 'SOFT').length
  const enabledCount = settings.filter((s) => s.enabled).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Configuration des contraintes"
        subtitle="Définissez les règles de génération du planning"
        actions={<Button onClick={() => setCreateProfileOpen(true)}><Plus size={16} /> Nouveau profil</Button>}
      />

      {/* Sélecteur profil */}
      <div className="flex gap-2 items-end">
        <div>
          <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Profil de contraintes</label>
          <select
            value={selectedProfile?.idConstraintProfile ?? ''}
            onChange={(e) => {
              const p = (profiles as ConstraintProfile[]).find((p) => p.idConstraintProfile === Number(e.target.value))
              if (p) {
                planningApi.constraints.profiles.get(p.idConstraintProfile).then(setSelectedProfile)
              } else {
                setSelectedProfile(null)
              }
            }}
            className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-64"
          >
            <option value="">Sélectionner un profil</option>
            {(profiles as ConstraintProfile[]).map((p) => (
              <option key={p.idConstraintProfile} value={p.idConstraintProfile}>
                {p.name}{p.active ? ' — utilisé pour la génération' : ''}
              </option>
            ))}
          </select>
        </div>
        {profile && (
          <button
            title="Supprimer ce profil"
            onClick={() => setDeleteProfileTarget(profile)}
            className="p-2 rounded-lg border border-brand-border dark:border-slate-700 hover:bg-red-50 dark:hover:bg-red-950/30 text-brand-textMuted hover:text-danger transition-colors"
          >
            <Trash2 size={16} />
          </button>
        )}
      </div>

      {profile && (
        <>
          <Tabs tabs={CONSTRAINT_TABS} value={tab} onChange={setTab} />

          {tab === 'officielles' && (
            <ContraintesOfficielles onActivate={setArticleActive} />
          )}

          {tab === 'catalogue' && (
        <>
          {/* Synthèse : une ligne d'activation + une carte par niveau, avec son explication en clair */}
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4">
            <div className="flex items-baseline justify-between mb-4">
              <div>
                <p className="text-sm font-semibold text-brand-text dark:text-slate-100">{profile.name}</p>
                <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-0.5">
                  <span className="font-semibold text-emerald-600 dark:text-emerald-400">{enabledCount}</span>
                  {' '}contrainte{enabledCount > 1 ? 's' : ''} active{enabledCount > 1 ? 's' : ''} sur {settings.length}
                </p>
              </div>
              {profile.active ? (
                <Badge variant="success">Profil actif</Badge>
              ) : (
                <button
                  onClick={() => activateProfileMutation.mutate(profile.idConstraintProfile)}
                  disabled={activateProfileMutation.isPending}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-brand-border dark:border-slate-700 px-3 py-1.5 text-xs font-medium text-brand-text dark:text-slate-200 hover:bg-emerald-50 dark:hover:bg-emerald-950/30 hover:text-emerald-700 dark:hover:text-emerald-400 transition-colors disabled:opacity-50"
                >
                  <CheckCircle2 size={14} /> Activer pour la génération
                </button>
              )}
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              {(['HARD', 'MEDIUM', 'SOFT'] as ConstraintType[]).map((t) => {
                const count = t === 'HARD' ? hardCount : t === 'MEDIUM' ? mediumCount : softCount
                const { icon: TypeIcon, accent, ring } = TYPE_META[t]
                return (
                  <div key={t} className="rounded-xl border border-brand-border dark:border-slate-700 p-3">
                    <div className="flex items-center gap-2">
                      <span className={cn('w-8 h-8 rounded-lg flex items-center justify-center shrink-0', ring)}>
                        <TypeIcon size={16} className={accent} />
                      </span>
                      <div className="min-w-0">
                        <p className="text-lg font-bold leading-none text-brand-text dark:text-slate-100">{count}</p>
                        <p className="text-xs font-medium text-brand-text dark:text-slate-300 truncate">{TYPE_LABELS[t]}</p>
                      </div>
                    </div>
                    <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-2 leading-relaxed">
                      {TYPE_EXPLANATIONS[t]}
                    </p>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Liste des contraintes, groupées par niveau d'exigence */}
          {(['HARD', 'MEDIUM', 'SOFT'] as ConstraintType[]).map((t) => {
            const typeSettings = settings.filter((s) => s.type === t)
            if (typeSettings.length === 0) return null
            const { icon: TypeIcon, accent, ring, dot } = TYPE_META[t]
            return (
              <section key={t} className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 overflow-hidden">
                <header className="px-4 py-3 border-b border-brand-border dark:border-slate-700 flex items-center gap-3">
                  <span className={cn('w-8 h-8 rounded-lg flex items-center justify-center shrink-0', ring)}>
                    <TypeIcon size={16} className={accent} />
                  </span>
                  <div className="min-w-0">
                    <h2 className="text-sm font-semibold text-brand-text dark:text-slate-100">{TYPE_LABELS[t]}</h2>
                    <p className="text-xs text-brand-textMuted dark:text-slate-400">
                      {typeSettings.filter((s) => s.enabled).length} active{typeSettings.filter((s) => s.enabled).length > 1 ? 's' : ''} sur {typeSettings.length}
                    </p>
                  </div>
                </header>

                <ul className="divide-y divide-brand-border dark:divide-slate-700">
                  {typeSettings.map((s) => {
                    const CatIcon = CATEGORY_ICONS[s.category]
                    return (
                      <li
                        key={s.idConstraintSetting}
                        className={cn(
                          'group px-4 py-3 flex items-start gap-3 transition-colors',
                          'hover:bg-brand-bgSecondary/60 dark:hover:bg-slate-800/40',
                          !s.enabled && 'opacity-55'
                        )}
                      >
                        {/* Pastille de niveau : repère couleur constant le long de la liste */}
                        <span className={cn('w-1.5 h-1.5 rounded-full shrink-0 mt-2', s.enabled ? dot : 'bg-slate-300 dark:bg-slate-600')} />

                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-brand-text dark:text-slate-100">{s.constraintName}</p>
                          {s.description && (
                            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-0.5 leading-relaxed">{s.description}</p>
                          )}
                          <div className="flex items-center gap-1.5 mt-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                            <CatIcon size={12} className="shrink-0" />
                            <span>{CATEGORY_LABELS[s.category]}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-3 shrink-0">
                          <label className="flex flex-col items-end gap-1">
                            <span className="text-[10px] uppercase tracking-wide text-brand-textMuted dark:text-slate-500">Priorité</span>
                            <select
                              value={s.importance}
                              onChange={(e) => updateImportanceMutation.mutate({ settingId: s.idConstraintSetting, importance: e.target.value as ImportanceLevel })}
                              className={cn(
                                'text-xs rounded-lg border px-2 py-1 transition-colors',
                                'border-brand-border dark:border-slate-700 bg-white dark:bg-slate-900',
                                'text-brand-text dark:text-slate-200',
                                'disabled:cursor-not-allowed disabled:opacity-60'
                              )}
                              disabled={!s.enabled}
                              title={s.enabled ? 'Priorité accordée par le solveur' : 'Activez la contrainte pour régler sa priorité'}
                            >
                              {IMPORTANCE_OPTIONS.map((o) => <option key={o} value={o}>{IMPORTANCE_LABELS[o]}</option>)}
                            </select>
                          </label>

                          <div className="flex flex-col items-center gap-1">
                            <span className="text-[10px] uppercase tracking-wide text-brand-textMuted dark:text-slate-500">
                              {s.enabled ? 'Active' : 'Inactive'}
                            </span>
                            <Switch
                              checked={s.enabled}
                              onChange={() => toggleMutation.mutate({ settingId: s.idConstraintSetting, enabled: !s.enabled })}
                              label={`${s.enabled ? 'Désactiver' : 'Activer'} la contrainte ${s.constraintName}`}
                            />
                          </div>
                        </div>
                      </li>
                    )
                  })}
                </ul>
              </section>
            )
          })}

          {availableDefinitions.length > 0 && (
            <div className="bg-white dark:bg-slate-900 rounded-xl border border-dashed border-brand-border dark:border-slate-700 overflow-hidden">
              <div className="px-4 py-3 bg-brand-bgSecondary dark:bg-slate-800 border-b border-brand-border dark:border-slate-700">
                <span className="text-sm font-semibold text-brand-text dark:text-slate-100">Contraintes disponibles à ajouter ({availableDefinitions.length})</span>
              </div>
              <ul className="divide-y divide-brand-border dark:divide-slate-700">
                {availableDefinitions.map((d) => {
                  const CatIcon = CATEGORY_ICONS[d.category]
                  return (
                    <li key={d.idConstraintDefinition} className="px-4 py-3 flex items-start gap-3 hover:bg-brand-bgSecondary/60 dark:hover:bg-slate-800/40 transition-colors">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-medium text-brand-text dark:text-slate-100">{d.name}</span>
                          <span title={TYPE_EXPLANATIONS[d.type]}>
                            <Badge variant={TYPE_VARIANTS[d.type]}>{TYPE_LABELS[d.type]}</Badge>
                          </span>
                        </div>
                        <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-0.5 leading-relaxed">{d.description}</p>
                        <div className="flex items-center gap-1.5 mt-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                          <CatIcon size={12} className="shrink-0" />
                          <span>{CATEGORY_LABELS[d.category]}</span>
                        </div>
                      </div>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => profile && addSettingMutation.mutate({ profileId: profile.idConstraintProfile, definitionId: d.idConstraintDefinition })}
                        loading={addSettingMutation.isPending}
                      >
                        <Plus size={12} /> Ajouter
                      </Button>
                    </li>
                  )
                })}
              </ul>
            </div>
          )}
        </>
          )}

          {tab === 'custom' && (
            <ContraintesPersonnalisees
              profileId={profile.idConstraintProfile}
              schoolYearId={profile.academicYearId}
            />
          )}

          {tab === 'suggestions' && (
            <SuggestionsPanel
              schoolYearId={profile.academicYearId}
              onAdopt={(suggestion: ConstraintSuggestion, dsl: ConstraintDsl) =>
                setAdopted({ dsl, name: suggestion.title })
              }
            />
          )}
        </>
      )}

      {/* Activer un article officiel emprunte le MÊME chemin qu'une règle
          dictée : proposition, vérification, puis confirmation explicite.
          Écrire directement en base parce que le texte vient du ministère
          serait confondre « conforme » et « applicable à cet établissement ». */}
      {profile && articleActive && (
        <AssistantRuleModal
          open
          onClose={() => setArticleActive(null)}
          profileId={profile.idConstraintProfile}
          schoolYearId={profile.academicYearId}
          initialRequest={articleActive.texte}
          originArticle={articleActive.id}
          onEditManually={(proposal: ConstraintProposal) => {
            if (proposal.dsl) {
              setAdopted({ dsl: proposal.dsl, name: `Circulaire § ${articleActive.id}` })
            }
            setArticleActive(null)
          }}
        />
      )}

      {/* Une suggestion adoptée ouvre l'éditeur pré-rempli : elle est relue et
          vérifiée comme n'importe quelle règle avant d'être enregistrée. */}
      {profile && adopted && (
        <CustomConstraintModal
          open
          onClose={() => setAdopted(null)}
          profileId={profile.idConstraintProfile}
          schoolYearId={profile.academicYearId}
          initialRule={adopted.dsl}
          initialName={adopted.name}
          initialCode={slugifyCode(adopted.name)}
        />
      )}

      {!profile && !loadingProfiles && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-12 text-center">
          <Settings size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
          <p className="text-sm text-brand-textMuted dark:text-slate-400 mb-4">Sélectionnez un profil ou créez-en un pour configurer les contraintes.</p>
          <Button onClick={() => setCreateProfileOpen(true)}><Plus size={16} /> Créer un profil par défaut</Button>
        </div>
      )}

      {/* Modal créer profil */}
      <Modal open={createProfileOpen} onClose={() => setCreateProfileOpen(false)} title="Nouveau profil de contraintes" size="sm">
        <div className="space-y-4">
          <Input label="Nom du profil *" value={profileName} onChange={(e) => setProfileName((e.target as HTMLInputElement).value)} placeholder="Profil standard 2024-2025" />
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Un profil par défaut sera créé avec toutes les contraintes standard. Vous pourrez ensuite les personnaliser.</p>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setCreateProfileOpen(false)}>Annuler</Button>
            <Button onClick={() => createProfileMutation.mutate()} loading={createProfileMutation.isPending} disabled={!profileName}>
              Créer
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteProfileTarget}
        onClose={() => setDeleteProfileTarget(null)}
        onConfirm={() => deleteProfileTarget && deleteProfileMutation.mutate(deleteProfileTarget.idConstraintProfile)}
        loading={deleteProfileMutation.isPending}
        title="Supprimer le profil"
        message={`Supprimer le profil "${deleteProfileTarget?.name}", toutes ses contraintes configurées et ses règles personnalisées ? Cette action est irréversible.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
