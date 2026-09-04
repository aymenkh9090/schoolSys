import { useState } from 'react'
import { Settings2, Plus } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { Badge } from '@/components/ui/Badge'
import { subscriptionApi, type SubscriptionOverview } from '@/api/subscription.api'
import { ModalCreationAbonnement } from './ModalCreationAbonnement'
import { ModalGestionAbonnement } from './ModalGestionAbonnement'

const planLabels: Record<string, string> = {
  FREE: 'Gratuit',
  STANDARD: 'Standard',
  PREMIUM: 'Premium',
}

function statusBadge(row: SubscriptionOverview) {
  if (!row.status) {
    return <Badge variant="default">Aucun abonnement</Badge>
  }
  if (row.status === 'CANCELLED') {
    return <Badge variant="danger">Annulé</Badge>
  }
  if (row.status === 'EXPIRED') {
    return <Badge variant="danger">Expiré</Badge>
  }
  if (row.daysRemaining !== null && row.daysRemaining <= 7) {
    return <Badge variant="warning">Expire bientôt</Badge>
  }
  return <Badge variant="success">Actif</Badge>
}

export default function ListeAbonnements() {
  const [createTarget, setCreateTarget] = useState<SubscriptionOverview | null>(null)
  const [manageTarget, setManageTarget] = useState<SubscriptionOverview | null>(null)

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['subscriptions'],
    queryFn: () => subscriptionApi.overview(),
  })

  const columns: Column<SubscriptionOverview>[] = [
    {
      key: 'tenant',
      header: 'Établissement',
      render: (r) => (
        <div>
          <p className="text-sm font-medium text-brand-text">{r.tenantName}</p>
          <p className="text-xs text-brand-textMuted">{r.tenantCode}</p>
        </div>
      ),
    },
    {
      key: 'plan',
      header: 'Plan',
      render: (r) => (r.plan ? planLabels[r.plan] ?? r.plan : '—'),
    },
    {
      key: 'status',
      header: 'Statut',
      render: statusBadge,
    },
    {
      key: 'endDate',
      header: 'Échéance',
      render: (r) => r.endDate ?? '—',
    },
    {
      key: 'daysRemaining',
      header: 'Jours restants',
      render: (r) =>
        r.daysRemaining === null ? '—' : r.daysRemaining < 0 ? `Dépassé de ${-r.daysRemaining} j` : `${r.daysRemaining} j`,
    },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <div className="flex justify-end">
          {r.subscriptionId === null ? (
            <Button size="sm" variant="outline" onClick={() => setCreateTarget(r)}>
              <Plus size={14} />
              Créer un abonnement
            </Button>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setManageTarget(r)}>
              <Settings2 size={14} />
              Gérer
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Abonnements"
        description="Suivi des abonnements et de la facturation des établissements"
      />

      <DataTable
        columns={columns}
        data={rows}
        keyField="tenantId"
        loading={isLoading}
        emptyMessage="Aucun établissement enregistré"
      />

      <ModalCreationAbonnement
        tenantId={createTarget?.tenantId ?? null}
        tenantName={createTarget?.tenantName}
        onClose={() => setCreateTarget(null)}
      />

      <ModalGestionAbonnement subscription={manageTarget} onClose={() => setManageTarget(null)} />
    </div>
  )
}
