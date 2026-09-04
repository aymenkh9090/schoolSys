import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { KeyRound, Check } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import * as authStore from '@/auth/authStore'
import { authApi } from '@/api/auth.api'
import { AuthPageHeader } from '@/components/auth/AuthPageHeader'
import { PasswordField } from '@/components/auth/PasswordField'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/utils'

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/

function RequirementRow({ met, label }: { met: boolean; label: string }) {
  return (
    <li className={cn('flex items-center gap-1.5 transition-colors', met ? 'text-success' : 'text-brand-textMuted dark:text-slate-500')}>
      <span
        className={cn(
          'flex h-4 w-4 shrink-0 items-center justify-center rounded-full border transition-colors',
          met ? 'border-success bg-success/10' : 'border-brand-border dark:border-slate-600'
        )}
      >
        {met && <Check size={11} strokeWidth={3} />}
      </span>
      {label}
    </li>
  )
}

export default function ChangePasswordFirstLogin() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const reqLength = newPassword.length >= 8
  const reqUpper = /[A-Z]/.test(newPassword)
  const reqLower = /[a-z]/.test(newPassword)
  const reqDigit = /\d/.test(newPassword)
  const reqMatch = confirmPassword.length > 0 && newPassword === confirmPassword

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    if (!PASSWORD_PATTERN.test(newPassword)) {
      toast.error(t('firstLogin.requirements'))
      return
    }
    if (newPassword !== confirmPassword) {
      toast.error(t('firstLogin.mismatch'))
      return
    }

    setLoading(true)
    try {
      await authApi.completeFirstLogin(newPassword)
      await authStore.refreshNow()
      toast.success(t('firstLogin.success'))
      navigate('/', { replace: true })
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('firstLogin.failed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative min-h-screen bg-gradient-to-b from-amber-50 via-white to-slate-50 dark:from-slate-950 dark:via-slate-950 dark:to-slate-900">
      <div className="flex min-h-screen flex-col items-center justify-center px-4 py-12">
        <AuthPageHeader
          icon={KeyRound}
          badgeClassName="from-amber-500 to-orange-600 shadow-amber-900/20"
          tagline={t('firstLogin.tagline')}
        />

        <div className="mt-8 w-full max-w-sm rounded-2xl border border-brand-border bg-white p-8 shadow-xl shadow-slate-900/5 dark:border-slate-800 dark:bg-slate-900">
          <h1 className="text-center text-xl font-semibold text-brand-text dark:text-slate-100">
            {t('firstLogin.title')}
          </h1>
          <p className="mt-1 text-center text-sm text-brand-textMuted dark:text-slate-400">
            {t('firstLogin.subtitle')}
          </p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-5">
            <div>
              <PasswordField
                id="newPassword"
                label={t('firstLogin.newPassword')}
                hideIcon
                required
                autoFocus
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="••••••••"
                className="focus:ring-amber-500"
              />
              <ul className="mt-2.5 grid grid-cols-2 gap-x-3 gap-y-1.5 text-xs">
                <RequirementRow met={reqLength} label={t('firstLogin.reqLength')} />
                <RequirementRow met={reqUpper} label={t('firstLogin.reqUpper')} />
                <RequirementRow met={reqLower} label={t('firstLogin.reqLower')} />
                <RequirementRow met={reqDigit} label={t('firstLogin.reqDigit')} />
              </ul>
            </div>

            <div>
              <PasswordField
                id="confirmPassword"
                label={t('firstLogin.confirmPassword')}
                hideIcon
                required
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="••••••••"
                className="focus:ring-amber-500"
              />
              {confirmPassword.length > 0 && (
                <p className={cn('mt-1.5 text-xs', reqMatch ? 'text-success' : 'text-danger')}>
                  {reqMatch ? t('firstLogin.reqMatch') : t('firstLogin.mismatch')}
                </p>
              )}
            </div>

            <Button
              type="submit"
              loading={loading}
              size="lg"
              className="w-full rounded-full bg-amber-600 hover:bg-amber-700 focus-visible:ring-amber-500"
            >
              {t('firstLogin.submit')}
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}
