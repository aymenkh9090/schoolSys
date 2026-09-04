import { cn } from '@/lib/utils'

interface StatCardProps {
  title: string
  value: string | number
  icon?: React.ElementType
  trend?: { value: number; label: string }
  color?: 'blue' | 'green' | 'amber' | 'red' | 'purple' | 'yellow'
  className?: string
}

const colorMap = {
  blue:   { bg: 'bg-blue-50 dark:bg-blue-500/10',     icon: 'text-brand-blue',              badge: 'text-brand-blue' },
  green:  { bg: 'bg-emerald-50 dark:bg-emerald-500/10', icon: 'text-emerald-600 dark:text-emerald-400', badge: 'text-emerald-600 dark:text-emerald-400' },
  amber:  { bg: 'bg-amber-50 dark:bg-amber-500/10',   icon: 'text-amber-600 dark:text-amber-400',   badge: 'text-amber-600 dark:text-amber-400' },
  red:    { bg: 'bg-red-50 dark:bg-red-500/10',       icon: 'text-red-600 dark:text-red-400',       badge: 'text-red-600 dark:text-red-400' },
  purple: { bg: 'bg-purple-50 dark:bg-purple-500/10', icon: 'text-purple-600 dark:text-purple-400', badge: 'text-purple-600 dark:text-purple-400' },
  yellow: { bg: 'bg-yellow-50 dark:bg-yellow-500/10', icon: 'text-yellow-600 dark:text-yellow-400', badge: 'text-yellow-600 dark:text-yellow-400' },
}

export function StatCard({ title, value, icon: Icon, trend, color = 'blue', className }: StatCardProps) {
  const colors = colorMap[color]
  return (
    <div className={cn('bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5', className)}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-brand-textMuted dark:text-slate-400 font-medium">{title}</p>
          <p className="text-2xl font-bold text-brand-text dark:text-slate-100 mt-1">{value}</p>
          {trend && (
            <p className={cn('text-xs mt-1', colors.badge)}>
              {trend.value > 0 ? '+' : ''}
              {trend.value}% {trend.label}
            </p>
          )}
        </div>
        {Icon && (
          <div className={cn('p-2 rounded-lg', colors.bg)}>
            <Icon size={20} className={colors.icon} />
          </div>
        )}
      </div>
    </div>
  )
}
