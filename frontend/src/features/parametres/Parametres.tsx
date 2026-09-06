import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import {
  ArrowRight,
  Bell,
  BookOpen,
  CalendarDays,
  Building2,
  Clock,
  Info,
  Languages,
  Lock,
  Moon,
  Palette,
  School,
  Settings,
  ShieldCheck,
  Star,
  Sun,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { SectionTitle } from '@/components/ui/SectionTitle'
import { Tabs } from '@/components/ui/Tabs'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { tenantApi } from '@/api/tenant.api'
import { organisationApi } from '@/api/organisation.api'
import { authApi } from '@/api/auth.api'
import { useAuth } from '@/hooks/useAuth'
import { useThemeStore } from '@/store/themeStore'
import { cn } from '@/lib/utils'
import { ROLE_VARIANTS, mainRole, roleLabel } from '@/lib/roles'

type TabKey = 'profil' | 'pedagogie' | 'annee' | 'horaires' | 'notifs' | 'theme' | 'securite'

const TABS = [
  { key: 'profil' as const, label: 'Profil', icon: School },
  { key: 'pedagogie' as const, label: 'Pédagogie', icon: BookOpen },
  { key: 'annee' as const, label: 'Année', icon: CalendarDays },
  { key: 'horaires' as const, label: 'Horaires', icon: Clock },
  { key: 'notifs' as const, label: 'Notifs', icon: Bell },
  { key: 'theme' as const, label: 'Thème', icon: Palette },
  { key: 'securite' as const, label: 'Sécurité', icon: ShieldCheck },
]

const DAYS = [
  { key: 'MONDAY', label: 'Lundi' },
  { key: 'TUESDAY', label: 'Mardi' },
  { key: 'WEDNESDAY', label: 'Mercredi' },
  { key: 'THURSDAY', label: 'Jeudi' },
  { key: 'FRIDAY', label: 'Vendredi' },
  { key: 'SATURDAY', label: 'Samedi' },
  { key: 'SUNDAY', label: 'Dimanche' },
]

const TYPE_LABELS: Record<string, string> = {
  PRIMAIRE: 'École primaire',
  COLLEGE: 'Collège',
  SECONDAIRE: 'Lycée / Secondaire',
}

const NOTIF_PREFS_KEY = 'smartschool.notifPrefs'

const NOTIF_OPTIONS = [
  { key: 'planning', label: 'Génération de planning', description: 'Job terminé, conflits détectés' },
  { key: 'absences', label: 'Absences & retards', description: "Sessions d'appel non clôturées" },
  { key: 'users', label: 'Comptes utilisateurs', description: 'Créations et réinitialisations' },
]

const card = 'rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900'

export default function Parametres() {
  const [tab, setTab] = useState<TabKey>('profil')

  return (
    <div className="space-y-6">
      <PageHero
        title="Paramètres"
        subtitle="Configuration de l'école et préférences personnelles"
        icon={Settings}
        variant="soft"
      />

      <Tabs tabs={TABS} value={tab} onChange={setTab} />

      {tab === 'profil' && <ProfilTab />}
      {tab === 'pedagogie' && <PedagogieTab />}
      {tab === 'annee' && <AnneeTab />}
      {tab === 'horaires' && <HorairesTab />}
      {tab === 'notifs' && <NotifsTab />}
      {tab === 'theme' && <ThemeTab />}
      {tab === 'securite' && <SecuriteTab />}
    </div>
  )
}

/** Encart informatif (limitation d'API, contexte). */
function Note({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2 rounded-lg border border-blue-200 bg-blue-50 p-3 dark:border-blue-500/20 dark:bg-blue-500/10">
      <Info size={15} className="mt-0.5 shrink-0 text-brand-blue" />
      <p className="text-xs text-blue-900 dark:text-blue-200">{children}</p>
    </div>
  )
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-brand-textMuted dark:text-slate-400">{label}</p>
      <p className="mt-1 text-sm font-medium text-brand-text dark:text-slate-100">{value || '—'}</p>
    </div>
  )
}

// ── Profil ──────────────────────────────────────────────────────────────────
function ProfilTab() {
  const { user, roles } = useAuth()
  const { data: tenant, isLoading } = useQuery({ queryKey: ['tenant-me'], queryFn: tenantApi.me })
  const role = mainRole(roles)

  return (
    <div className="space-y-6">
      <div className={card}>
        <SectionTitle title="Informations de l'école" icon={Building2} accent="teal" />
        {isLoading ? (
          <p className="py-6 text-sm text-brand-textMuted dark:text-slate-400">Chargement…</p>
        ) : (
          <>
            <div className="mb-5 flex items-center gap-4">
              {tenant?.logo ? (
                <img src={tenant.logo} alt="" className="h-16 w-16 rounded-xl object-cover" />
              ) : (
                <span className="flex h-16 w-16 items-center justify-center rounded-xl bg-brand-teal text-2xl font-bold text-white">
                  {tenant?.name?.[0]?.toUpperCase() ?? '?'}
                </span>
              )}
              <div>
                <p className="text-lg font-bold text-brand-text dark:text-slate-100">{tenant?.name ?? '—'}</p>
                <p className="text-sm text-brand-textMuted dark:text-slate-400">
                  {tenant?.type ? TYPE_LABELS[tenant.type] ?? tenant.type : '—'}
                </p>
              </div>
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="Code établissement" value={<span className="font-mono">{tenant?.code}</span>} />
              <Field label="Type" value={tenant?.type ? TYPE_LABELS[tenant.type] ?? tenant.type : '—'} />
              <Field label="Identifiant interne" value={`#${tenant?.id ?? '—'}`} />
            </div>
          </>
        )}
      </div>

      <div className={card}>
        <SectionTitle title="Mon compte" icon={School} accent="blue" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Nom" value={`${user?.given_name ?? ''} ${user?.family_name ?? ''}`.trim()} />
          <Field label="Identifiant" value={(user as Record<string, unknown> | undefined)?.preferred_username as string} />
          <Field
            label="Rôle"
            value={role ? <Badge variant={ROLE_VARIANTS[role]}>{roleLabel(roles)}</Badge> : '—'}
          />
        </div>
      </div>
    </div>
  )
}

// ── Pédagogie ───────────────────────────────────────────────────────────────
function PedagogieTab() {
  const navigate = useNavigate()
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes', null], queryFn: organisationApi.classes.list })
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: organisationApi.rooms.list })

  const items = [
    { label: 'Niveaux actifs', value: levels.filter((l) => l.estActif).length, to: '/ecole/niveaux' },
    { label: 'Matières enseignées', value: subjects.filter((s) => s.estEnseignee).length, to: '/ecole/matieres' },
    { label: 'Classes actives', value: classes.filter((c) => c.estActif).length, to: '/ecole/classes' },
    { label: 'Salles', value: rooms.length, to: '/ecole/salles' },
  ]

  const shortcuts = [
    { label: 'Programme national', description: 'Curriculum officiel à appliquer aux niveaux', to: '/ecole/programme-national' },
    { label: "Programme de l'école", description: 'Patterns hebdomadaires par matière et niveau', to: '/ecole/programme-ecole' },
    { label: 'Affectations', description: 'Enseignant ↔ classe ↔ matière', to: '/ecole/affectations' },
    { label: 'Contraintes', description: 'Les règles que le planning doit respecter', to: '/ecole/planning/contraintes' },
  ]

  return (
    <div className="space-y-6">
      <div className={card}>
        <SectionTitle title="État du paramétrage pédagogique" icon={BookOpen} accent="teal" />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {items.map((i) => (
            <button
              key={i.label}
              onClick={() => navigate(i.to)}
              className="rounded-xl border border-brand-border p-4 text-center transition-colors hover:border-teal-300 dark:border-slate-700 dark:hover:border-teal-500/40"
            >
              <p className="text-2xl font-bold text-brand-teal dark:text-teal-400">{i.value}</p>
              <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">{i.label}</p>
            </button>
          ))}
        </div>
      </div>

      <div className={card}>
        <SectionTitle title="Raccourcis de configuration" icon={Settings} accent="purple" />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {shortcuts.map((s) => (
            <button
              key={s.to}
              onClick={() => navigate(s.to)}
              className="flex items-center justify-between gap-3 rounded-xl border border-brand-border p-4 text-left transition-colors hover:bg-brand-bgSecondary dark:border-slate-700 dark:hover:bg-slate-800"
            >
              <span>
                <span className="block text-sm font-semibold text-brand-text dark:text-slate-100">{s.label}</span>
                <span className="block text-xs text-brand-textMuted dark:text-slate-400">{s.description}</span>
              </span>
              <ArrowRight size={16} className="shrink-0 text-brand-teal" />
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Année scolaire ──────────────────────────────────────────────────────────
function AnneeTab() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })

  const setCurrent = useMutation({
    mutationFn: (year: (typeof years)[number]) =>
      organisationApi.schoolYears.update(year.idAnnee, {
        nom: year.nom,
        dateDebut: year.dateDebut,
        dateFin: year.dateFin,
        estActive: true,
        estCourante: true,
      }),
    onSuccess: () => {
      toast.success('Année courante mise à jour')
      void queryClient.invalidateQueries({ queryKey: ['school-years'] })
    },
    onError: () => toast.error("Impossible de modifier l'année courante"),
  })

  return (
    <div className={card}>
      <SectionTitle
        title="Années scolaires"
        icon={CalendarDays}
        accent="teal"
        action={
          <Button variant="outline" size="sm" onClick={() => navigate('/ecole/annees')}>
            Gérer <ArrowRight size={13} />
          </Button>
        }
      />
      <div className="space-y-2">
        {years.map((y) => (
          <div
            key={y.idAnnee}
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-border p-4 dark:border-slate-700"
          >
            <div>
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-brand-text dark:text-slate-100">{y.nom}</span>
                {y.estCourante && (
                  <Badge variant="info">
                    <Star size={10} className="me-0.5 inline" />
                    Courante
                  </Badge>
                )}
                {!y.estActive && <Badge>Inactive</Badge>}
              </div>
              <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
                {y.dateDebut} → {y.dateFin} · {y.nombreClasses} classe(s) · {y.nombreAffectations} affectation(s)
              </p>
            </div>
            {!y.estCourante && (
              <Button
                variant="outline"
                size="sm"
                loading={setCurrent.isPending && setCurrent.variables?.idAnnee === y.idAnnee}
                onClick={() => setCurrent.mutate(y)}
              >
                Définir comme courante
              </Button>
            )}
          </div>
        ))}
        {years.length === 0 && (
          <p className="py-10 text-center text-sm text-brand-textMuted dark:text-slate-400">Aucune année scolaire enregistrée</p>
        )}
      </div>
    </div>
  )
}

// ── Horaires ────────────────────────────────────────────────────────────────
function HorairesTab() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: config } = useQuery({ queryKey: ['school-config'], queryFn: organisationApi.config.get })

  const toggleDay = useMutation({
    mutationFn: ({ day, active }: { day: string; active: boolean }) => organisationApi.config.toggleDay(day, active),
    onSuccess: () => {
      toast.success('Jours ouvrés mis à jour')
      void queryClient.invalidateQueries({ queryKey: ['school-config'] })
    },
    onError: () => toast.error('Échec de la mise à jour'),
  })

  const byDay = new Map((config?.workingDays ?? []).map((d) => [d.dayOfWeek, d]))

  return (
    <div className="space-y-6">
      <div className={card}>
        <SectionTitle
          title="Jours ouvrés"
          icon={Clock}
          accent="teal"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/ecole/configuration/horaires')}>
              Configuration complète <ArrowRight size={13} />
            </Button>
          }
        />
        <div className="space-y-2">
          {DAYS.map((d) => {
            const wd = byDay.get(d.key)
            const active = wd?.active ?? false
            return (
              <div
                key={d.key}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-border p-3 dark:border-slate-700"
              >
                <div>
                  <p className="text-sm font-medium text-brand-text dark:text-slate-100">{d.label}</p>
                  <p className="text-xs text-brand-textMuted dark:text-slate-400">
                    {wd?.morningStart
                      ? `Matin ${wd.morningStart}–${wd.morningEnd}${wd.afternoonStart ? ` · Après-midi ${wd.afternoonStart}–${wd.afternoonEnd}` : ''}`
                      : 'Aucune plage horaire définie'}
                  </p>
                </div>
                <button
                  type="button"
                  disabled={!wd || toggleDay.isPending}
                  onClick={() => toggleDay.mutate({ day: d.key, active: !active })}
                  className={cn(
                    'relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-40',
                    active ? 'bg-brand-teal' : 'bg-brand-border dark:bg-slate-700'
                  )}
                  aria-pressed={active}
                  aria-label={`Basculer ${d.label}`}
                >
                  <span
                    className={cn(
                      'absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform',
                      active ? 'translate-x-[22px]' : 'translate-x-0.5'
                    )}
                  />
                </button>
              </div>
            )
          })}
        </div>
      </div>

      <div className={card}>
        <SectionTitle title="Résumé" icon={Info} accent="blue" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Durée d'un créneau" value={config?.slotDurationMinutes ? `${config.slotDurationMinutes} min` : '—'} />
          <Field label="Créneaux par semaine" value={config?.totalSlotsPerWeek ?? '—'} />
          <Field
            label="Prêt pour la génération"
            value={
              config?.isReadyForGeneration ? <Badge variant="success">Oui</Badge> : <Badge variant="warning">Non</Badge>
            }
          />
        </div>
      </div>
    </div>
  )
}

// ── Notifications ───────────────────────────────────────────────────────────
function NotifsTab() {
  const [prefs, setPrefs] = useState<Record<string, boolean>>(() => {
    try {
      return JSON.parse(localStorage.getItem(NOTIF_PREFS_KEY) ?? '{}') as Record<string, boolean>
    } catch {
      return {}
    }
  })

  useEffect(() => {
    localStorage.setItem(NOTIF_PREFS_KEY, JSON.stringify(prefs))
  }, [prefs])

  return (
    <div className={card}>
      <SectionTitle title="Notifications affichées" icon={Bell} accent="amber" />
      <div className="space-y-2">
        {NOTIF_OPTIONS.map((o) => {
          const enabled = prefs[o.key] ?? true
          return (
            <label
              key={o.key}
              className="flex cursor-pointer items-center justify-between gap-3 rounded-xl border border-brand-border p-4 dark:border-slate-700"
            >
              <span>
                <span className="block text-sm font-medium text-brand-text dark:text-slate-100">{o.label}</span>
                <span className="block text-xs text-brand-textMuted dark:text-slate-400">{o.description}</span>
              </span>
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setPrefs((p) => ({ ...p, [o.key]: e.target.checked }))}
                className="h-4 w-4 shrink-0 rounded accent-teal-600"
              />
            </label>
          )
        })}
      </div>
      <div className="mt-4">
        <Note>Ces préférences sont enregistrées sur cet appareil uniquement.</Note>
      </div>
    </div>
  )
}

// ── Thème & langue ──────────────────────────────────────────────────────────
function ThemeTab() {
  const { theme, setTheme } = useThemeStore()
  const { i18n } = useTranslation()

  const langs = [
    { code: 'fr', label: 'Français' },
    { code: 'ar', label: 'العربية' },
    { code: 'en', label: 'English' },
  ]

  return (
    <div className="space-y-6">
      <div className={card}>
        <SectionTitle title="Apparence" icon={Palette} accent="purple" />
        <div className="grid grid-cols-2 gap-4">
          {(['light', 'dark'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTheme(t)}
              className={cn(
                'flex flex-col items-center gap-2 rounded-xl border-2 p-6 transition-colors',
                theme === t ? 'border-brand-teal bg-teal-50 dark:bg-teal-500/10' : 'border-brand-border dark:border-slate-700'
              )}
            >
              {t === 'light' ? <Sun size={24} className="text-amber-500" /> : <Moon size={24} className="text-blue-400" />}
              <span className="text-sm font-medium text-brand-text dark:text-slate-100">{t === 'light' ? 'Clair' : 'Sombre'}</span>
            </button>
          ))}
        </div>
      </div>

      <div className={card}>
        <SectionTitle title="Langue de l'interface" icon={Languages} accent="teal" />
        <div className="grid grid-cols-3 gap-4">
          {langs.map((l) => (
            <button
              key={l.code}
              onClick={() => void i18n.changeLanguage(l.code)}
              className={cn(
                'rounded-xl border-2 p-4 text-sm font-medium transition-colors',
                i18n.language === l.code
                  ? 'border-brand-teal bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-300'
                  : 'border-brand-border text-brand-text dark:border-slate-700 dark:text-slate-200'
              )}
            >
              {l.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Sécurité ────────────────────────────────────────────────────────────────
function SecuriteTab() {
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')

  const change = useMutation({
    mutationFn: (newPassword: string) => authApi.completeFirstLogin(newPassword),
    onSuccess: () => {
      toast.success('Mot de passe modifié')
      setPassword('')
      setConfirm('')
    },
    onError: () => toast.error('Échec de la modification du mot de passe'),
  })

  const tooShort = password.length > 0 && password.length < 8
  const mismatch = confirm.length > 0 && confirm !== password

  return (
    <div className="max-w-xl space-y-6">
      <div className={card}>
        <SectionTitle title="Changer mon mot de passe" icon={Lock} accent="red" />
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (tooShort || mismatch || !password) return
            change.mutate(password)
          }}
        >
          <Input
            type="password"
            label="Nouveau mot de passe"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            error={tooShort ? 'Au moins 8 caractères' : undefined}
          />
          <Input
            type="password"
            label="Confirmer le mot de passe"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            error={mismatch ? 'Les mots de passe ne correspondent pas' : undefined}
          />
          <Button type="submit" loading={change.isPending} disabled={!password || tooShort || mismatch}>
            Mettre à jour le mot de passe
          </Button>
        </form>
      </div>

      <Note>
        Le nouveau mot de passe s'applique immédiatement. Vous restez connecté sur cette session.
      </Note>
    </div>
  )
}
