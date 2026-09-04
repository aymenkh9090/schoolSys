import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CircleUserRound, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import * as authStore from '@/auth/authStore'
import { LanguageToggle } from '@/components/auth/LanguageToggle'
import { PasswordField } from '@/components/auth/PasswordField'
import { WtmBrand } from '@/components/auth/WtmBrand'
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
const PLATFORM_ILLUSTRATION = '/images/platform-illustration.svg'

// Accent violet de la plateforme
const FIELD = `${PILL_FIELD} focus:ring-[#6d28d9]`
const PASSWORD = `${PILL_PASSWORD} focus:ring-[#6d28d9]`
const BUTTON = `${PILL_BUTTON} bg-[#6d28d9] hover:bg-[#5b21b6] focus-visible:ring-[#6d28d9]`
const LINK = `${PORTAL_LINK} hover:text-[#6d28d9] dark:hover:text-violet-300`

export default function LoginSuperAdmin() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  // Fichier absent ou illisible : le panneau droit retombe sur l'aplat de marque.
  const [imageFailed, setImageFailed] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      await authStore.login(email, password)
      navigate('/', { replace: true })
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
          <WtmBrand />
          <LanguageToggle />
        </div>

        <div className="flex flex-1 items-center">
          <div className="w-full py-10 lg:max-w-md xl:max-w-lg">
            <span className="inline-flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-[#6d28d9] dark:text-violet-300">
              <ShieldCheck className="h-3.5 w-3.5" />
              {t('login.superAdminSpace')}
            </span>

            <h1 className={`${PORTAL_TITLE} mt-2 text-[#002243]`}>{t('login.platformTitle')}</h1>

            <div className={PORTAL_CARD}>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                {t('login.connectToYourAccount')}
              </p>

              <form onSubmit={handleSubmit} className="mt-5 space-y-4">
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
                  placeholder={t('login.superAdminEmailPlaceholder')}
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
                  onClick={() => toast.info(t('login.forgotPasswordHintSuperAdmin'))}
                  className={LINK}
                >
                  {t('login.forgotPassword')}
                </button>
                <button
                  type="button"
                  onClick={() => toast.info(t('login.supportHint'))}
                  className={LINK}
                >
                  {t('login.supportContact')}
                </button>
              </div>
            </div>
          </div>
        </div>

        <p className="text-center text-sm text-slate-500 dark:text-slate-400">
          {t('login.memberOfEstablishment')}{' '}
          <a
            href="/etablissement/login"
            className="font-medium text-[#6d28d9] hover:underline dark:text-violet-300"
          >
            {t('login.establishmentLogin')}
          </a>
        </p>
      </main>

      {/* ── Droite : illustration de la plateforme, sinon aplat de marque ──── */}
      <aside className="relative hidden overflow-hidden bg-[#6d28d9] lg:block">
        {!imageFailed ? (
          <img
            src={PLATFORM_ILLUSTRATION}
            alt=""
            aria-hidden="true"
            onError={() => setImageFailed(true)}
            className="h-full w-full object-cover object-left rtl:-scale-x-100"
          />
        ) : (
          <div className="flex h-full items-end bg-gradient-to-br from-[#8b5cf6] to-[#3b1a80] p-14">
            <p className="max-w-sm text-2xl font-semibold leading-snug text-white">
              {t('login.platformHeroTitle')}
            </p>
          </div>
        )}
      </aside>
    </div>
  )
}
