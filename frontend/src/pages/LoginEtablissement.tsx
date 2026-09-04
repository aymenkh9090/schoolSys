import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, CircleUserRound, School } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import * as authStore from '@/auth/authStore'
import { tenantApi, type PublicTenant } from '@/api/tenant.api'
import { LanguageToggle } from '@/components/auth/LanguageToggle'
import { PasswordField } from '@/components/auth/PasswordField'
import { PortalBrand } from '@/components/auth/PortalBrand'
import {
  PILL_BUTTON,
  PILL_FIELD,
  PILL_PASSWORD,
  PORTAL_CARD,
  PORTAL_COLUMN,
  PORTAL_LINK,
  PORTAL_LINK_ROW,
  PORTAL_PAGE,
  PORTAL_TITLE,
} from '@/components/auth/portalStyles'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'

/** Visuel du panneau droit — voir public/images/README.md pour le remplacer. */
const PORTAL_ILLUSTRATION = '/images/login-illustration.jpg'

// Accent bleu du portail établissement
const FIELD = `${PILL_FIELD} focus:ring-[#084d90]`
const PASSWORD = `${PILL_PASSWORD} focus:ring-[#084d90]`
const BUTTON = `${PILL_BUTTON} bg-[#084d90] hover:bg-[#063b70] focus-visible:ring-[#084d90]`
const LINK = `${PORTAL_LINK} hover:text-[#084d90] dark:hover:text-sky-300`

export default function LoginEtablissement() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  // Étape 1 : code établissement — Étape 2 : identifiants
  const [tenant, setTenant] = useState<PublicTenant | null>(null)
  const [code, setCode] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  // Fichier absent ou illisible : le panneau droit retombe sur l'aplat de marque.
  const [imageFailed, setImageFailed] = useState(false)

  async function handleFindTenant(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      const found = await tenantApi.publicByCode(code.trim().toLowerCase())
      setTenant(found)
    } catch {
      toast.error(t('login.noEstablishmentFound'))
    } finally {
      setLoading(false)
    }
  }

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      await authStore.login(email, password)

      const roles =
        (authStore.getState().tokenParsed?.realm_access?.roles as string[]) ?? []
      if (roles.includes('PARENT')) navigate('/parent/dashboard', { replace: true })
      else if (roles.includes('STUDENT')) navigate('/eleve/dashboard', { replace: true })
      else navigate('/', { replace: true })
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('login.loginFailed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={PORTAL_PAGE}>
      {/* ── Gauche : marque + formulaire ───────────────────────────────────── */}
      <main className={PORTAL_COLUMN}>
        <div className="flex items-center justify-between gap-4">
          <PortalBrand />
          <LanguageToggle />
        </div>

        <div className="flex flex-1 items-center">
          <div className="w-full py-10 lg:max-w-md xl:max-w-lg">
            <h1 className={`${PORTAL_TITLE} text-[#0d2644]`}>{t('login.portalTitle')}</h1>

            <div className={PORTAL_CARD}>
              {!tenant ? (
                <>
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    {t('login.establishmentCodeHint')}
                  </p>

                  <form onSubmit={handleFindTenant} className="mt-5 space-y-4">
                    <Input
                      id="code"
                      icon={School}
                      type="text"
                      required
                      autoFocus
                      value={code}
                      onChange={(e) => setCode(e.target.value)}
                      aria-label={t('login.establishmentCode')}
                      placeholder={t('login.establishmentCode')}
                      className={FIELD}
                    />

                    <Button type="submit" loading={loading} size="lg" className={BUTTON}>
                      {t('login.continue')}
                    </Button>
                  </form>

                  <div className={PORTAL_LINK_ROW}>
                    <button
                      type="button"
                      onClick={() => toast.info(t('login.forgotPasswordHint'))}
                      className={LINK}
                    >
                      {t('login.forgotPassword')}
                    </button>
                    <button
                      type="button"
                      onClick={() => toast.info(t('login.requestAccessHint'))}
                      className={LINK}
                    >
                      {t('login.requestAccess')}
                    </button>
                  </div>
                </>
              ) : (
                <>
                  <div className="flex items-center gap-3">
                    {tenant.logo ? (
                      <img
                        src={tenant.logo}
                        alt={tenant.name}
                        className="h-10 w-10 shrink-0 rounded-xl border border-brand-border object-cover dark:border-slate-700"
                      />
                    ) : null}
                    <div className="min-w-0">
                      <p className="truncate text-base font-semibold text-[#0d2644] dark:text-slate-100">
                        {tenant.name}
                      </p>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {t('login.connectToYourAccount')}
                      </p>
                    </div>
                  </div>

                  <form onSubmit={handleLogin} className="mt-5 space-y-4">
                    <Input
                      id="email"
                      icon={CircleUserRound}
                      type="email"
                      required
                      autoFocus
                      autoComplete="username"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      aria-label={t('login.email')}
                      placeholder={t('login.email')}
                      className={FIELD}
                    />

                    <PasswordField
                      id="password"
                      required
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      aria-label={t('login.password')}
                      placeholder={t('login.password')}
                      className={PASSWORD}
                    />

                    <Button type="submit" loading={loading} size="lg" className={BUTTON}>
                      {t('login.submit')}
                    </Button>
                  </form>

                  <div className={PORTAL_LINK_ROW}>
                    <button
                      type="button"
                      onClick={() => toast.info(t('login.forgotPasswordHint'))}
                      className={LINK}
                    >
                      {t('login.forgotPassword')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setTenant(null)}
                      className={`${LINK} inline-flex items-center gap-1.5`}
                    >
                      <ArrowLeft className="h-3.5 w-3.5 rtl:rotate-180" />
                      {t('login.changeEstablishment')}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>

        <p className="text-center text-sm text-slate-500 dark:text-slate-400">
          <a href="/login" className="font-medium text-[#084d90] hover:underline dark:text-sky-300">
            {t('login.superAdminLogin')}
          </a>
        </p>
      </main>

      {/* ── Droite : illustration du portail, sinon aplat de marque ────────── */}
      <aside className="relative hidden overflow-hidden bg-[#2f8fe6] lg:block">
        {!imageFailed ? (
          <img
            src={PORTAL_ILLUSTRATION}
            alt=""
            aria-hidden="true"
            onError={() => setImageFailed(true)}
            className="h-full w-full object-cover object-left rtl:-scale-x-100"
          />
        ) : (
          <div className="flex h-full items-end bg-gradient-to-br from-[#3ea0f0] to-[#084d90] p-14">
            <p className="max-w-sm text-2xl font-semibold leading-snug text-white">
              {t('login.establishmentHeroTitle')}
            </p>
          </div>
        )}
      </aside>
    </div>
  )
}
