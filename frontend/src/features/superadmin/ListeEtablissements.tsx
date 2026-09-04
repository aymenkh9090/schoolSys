import { useState } from 'react'
import { Pencil, Plus, ToggleLeft, ToggleRight, Trash2 } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { Badge } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ModalCreationEtablissement } from './ModalCreationEtablissement'
import { ModalEditionEtablissement } from './ModalEditionEtablissement'
import { tenantApi, type Tenant } from '@/api/tenant.api'

const typeLabels: Record<Tenant['type'], string> = {
  PRIMAIRE: 'Primaire',
  COLLEGE: 'Collège',
  SECONDAIRE: 'Lycée / Secondaire',
}

export default function ListeEtablissements() {
  const qc = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Tenant | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Tenant | null>(null)

  const { data: tenants = [], isLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => tenantApi.list(),
  })

  const toggleMutation = useMutation({
    mutationFn: (t: Tenant) =>
      t.active ? tenantApi.suspend(t.id) : tenantApi.activate(t.id),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['tenants'] })
    },
    onError: () => toast.error('Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => tenantApi.delete(id),
    onSuccess: () => {
      toast.success('Établissement supprimé')
      qc.invalidateQueries({ queryKey: ['tenants'] })
      setDeleteTarget(null)
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  })

  const columns: Column<Tenant>[] = [
    { key: 'name', header: 'Nom' },
    { key: 'code', header: 'Code' },
    {
      key: 'type',
      header: 'Type',
      render: (t) => typeLabels[t.type] ?? t.type,
    },
    { key: 'plan', header: 'Plan' },
    {
      key: 'active',
      header: 'Statut',
      render: (t) => (
        <Badge variant={t.active ? 'success' : 'danger'}>
          {t.active ? 'Actif' : t.status === 'PENDING' ? 'En attente' : 'Suspendu'}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (t) => (
        <div className="flex items-center gap-2 justify-end">
          <button
            onClick={() => setEditTarget(t)}
            className="p-1.5 rounded hover:bg-brand-bgSecondary text-brand-textMuted"
            title="Modifier"
          >
            <Pencil size={16} />
          </button>
          <button
            onClick={() => toggleMutation.mutate(t)}
            className="p-1.5 rounded hover:bg-brand-bgSecondary text-brand-textMuted"
            title={t.active ? 'Désactiver' : 'Activer'}
          >
            {t.active ? <ToggleRight size={18} className="text-success" /> : <ToggleLeft size={18} />}
          </button>
          <button
            onClick={() => setDeleteTarget(t)}
            className="p-1.5 rounded hover:bg-red-50 text-brand-textMuted hover:text-danger"
            title="Supprimer"
          >
            <Trash2 size={16} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Établissements"
        description="Gérer les établissements de la plateforme"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus size={16} />
            Nouvel établissement
          </Button>
        }
      />

      <DataTable
        columns={columns}
        data={tenants}
        keyField="id"
        loading={isLoading}
        emptyMessage="Aucun établissement enregistré"
      />

      <ModalCreationEtablissement open={createOpen} onClose={() => setCreateOpen(false)} />

      <ModalEditionEtablissement tenant={editTarget} onClose={() => setEditTarget(null)} />

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        loading={deleteMutation.isPending}
        title="Supprimer l'établissement"
        message={`Êtes-vous sûr de vouloir supprimer "${deleteTarget?.name}" ? Cette action est irréversible.`}
      />
    </div>
  )
}
