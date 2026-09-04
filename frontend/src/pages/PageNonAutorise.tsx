import { ShieldOff } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useNavigate } from 'react-router-dom'

export default function PageNonAutorise() {
  const navigate = useNavigate()
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
      <div className="p-4 bg-red-50 rounded-full">
        <ShieldOff size={40} className="text-danger" />
      </div>
      <h1 className="text-xl font-bold text-brand-text">Accès non autorisé</h1>
      <p className="text-brand-textMuted text-sm max-w-sm">
        Vous n'avez pas les permissions nécessaires pour accéder à cette page.
      </p>
      <Button onClick={() => navigate(-1)} variant="outline">
        Retour
      </Button>
    </div>
  )
}
