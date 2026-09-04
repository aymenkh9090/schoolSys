import type { CahierSeanceReponse } from '@/api/absence.api'
import { formatDate } from '@/lib/utils'

export function CahierContenu({ cahier }: { cahier: CahierSeanceReponse }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
      <div>
        <p className="text-xs font-medium text-brand-textMuted uppercase mb-0.5">Sujet</p>
        <p className="text-brand-text dark:text-slate-200">{cahier.sujet || '—'}</p>
      </div>
      <div>
        <p className="text-xs font-medium text-brand-textMuted uppercase mb-0.5">Chapitre</p>
        <p className="text-brand-text dark:text-slate-200">{cahier.chapitre || '—'}</p>
      </div>
      <div className="sm:col-span-2">
        <p className="text-xs font-medium text-brand-textMuted uppercase mb-0.5">Activités réalisées</p>
        <p className="text-brand-text dark:text-slate-200 whitespace-pre-wrap">{cahier.activites || '—'}</p>
      </div>
      <div className="sm:col-span-2">
        <p className="text-xs font-medium text-brand-textMuted uppercase mb-0.5">Travail demandé</p>
        <p className="text-brand-text dark:text-slate-200 whitespace-pre-wrap">{cahier.travailDemande || '—'}</p>
        {cahier.dateEcheance && (
          <p className="text-xs text-brand-textMuted mt-1">Échéance : {formatDate(cahier.dateEcheance)}</p>
        )}
      </div>
      {cahier.remarques && (
        <div className="sm:col-span-2">
          <p className="text-xs font-medium text-brand-textMuted uppercase mb-0.5">Remarques</p>
          <p className="text-brand-text dark:text-slate-200 whitespace-pre-wrap">{cahier.remarques}</p>
        </div>
      )}
    </div>
  )
}
