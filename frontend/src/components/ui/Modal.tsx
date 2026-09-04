import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  children: React.ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'full'
}

const sizes = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-2xl',
  full: 'max-w-[1400px] w-[95vw]',
}

export function Modal({ open, onClose, title, children, size = 'md' }: ModalProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
      />
      <div
        className={cn(
          'relative w-full bg-white dark:bg-slate-900 rounded-xl shadow-xl',
          'flex flex-col max-h-[90vh]',
          sizes[size]
        )}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-brand-border dark:border-slate-700">
          <h2 className="text-base font-semibold text-brand-text dark:text-slate-100">{title}</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400"
          >
            <X size={18} />
          </button>
        </div>
        <div className="overflow-y-auto flex-1 px-6 py-4 text-brand-text dark:text-slate-200">{children}</div>
      </div>
    </div>
  )
}
