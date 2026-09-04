import { Building2, Users, Activity, TrendingUp } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { tenantApi } from '@/api/tenant.api'
import { Button } from '@/components/ui/Button'
import { useNavigate } from 'react-router-dom'

export default function SuperAdminDashboard() {
  const navigate = useNavigate()
  const { data: tenants = [] } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => tenantApi.list(),
  })

  const active = tenants.filter((t) => t.active).length

  return (
    <div>
      <PageHeader
        title="Tableau de bord"
        description="Vue d'ensemble de la plateforme SchoolSys"
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => navigate('/super-admin/abonnements')}>
              Abonnements
            </Button>
            <Button onClick={() => navigate('/super-admin/etablissements')}>
              Gérer les établissements
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          title="Établissements"
          value={tenants.length}
          icon={Building2}
          color="blue"
        />
        <StatCard
          title="Actifs"
          value={active}
          icon={Activity}
          color="green"
        />
        <StatCard
          title="Inactifs"
          value={tenants.length - active}
          icon={TrendingUp}
          color="amber"
        />
        <StatCard
          title="Total utilisateurs"
          value="—"
          icon={Users}
          color="red"
        />
      </div>

      <div className="bg-white rounded-xl border border-brand-border p-6">
        <h2 className="font-semibold text-brand-text mb-4">
          Derniers établissements
        </h2>
        <div className="space-y-2">
          {tenants.slice(0, 5).map((t) => (
            <div
              key={t.id}
              className="flex items-center justify-between py-2 border-b border-brand-border last:border-0"
            >
              <div>
                <p className="text-sm font-medium text-brand-text">{t.name}</p>
                <p className="text-xs text-brand-textMuted">{t.code}</p>
              </div>
              <span
                className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  t.active
                    ? 'bg-emerald-100 text-emerald-700'
                    : 'bg-red-100 text-red-700'
                }`}
              >
                {t.active ? 'Actif' : 'Inactif'}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
