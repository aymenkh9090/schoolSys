import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Power, PowerOff, Users, Upload, Search, X,
  Mail, Phone, CheckCircle2, MousePointerClick, Layers,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ImportModal } from '@/components/ui/ImportModal'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi, type Eleve } from '@/api/organisation.api'

/** Initiales pour la pastille d'un élève, à défaut d'une photo. */
function initiales(prenom: string, nom: string): string {
  return `${prenom?.[0] ?? ''}${nom?.[0] ?? ''}`.toUpperCase() || '?'
}

const schema = z.object({
  codeEleve: z.string().min(1, 'Obligatoire'),
  nom: z.string().min(1, 'Obligatoire'),
  prenom: z.string().min(1, 'Obligatoire'),
  numIdentite: z.string().optional(),
  email: z.string().email('Email invalide').optional().or(z.literal('')),
  telephone: z.string().optional(),
  classeId: z.coerce.number().min(1, 'Obligatoire'),
})

type FormData = z.infer<typeof schema>

export default function GestionEleves() {
  const qc = useQueryClient()
  const [classeId, setClasseId] = useState<number | null>(null)
  const [niveauId, setNiveauId] = useState<number | null>(null)
  const [recherche, setRecherche] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Eleve | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Eleve | null>(null)
  const [importOpen, setImportOpen] = useState(false)

  const { data: classes = [] } = useQuery({ queryKey: ['classes'], queryFn: organisationApi.classes.list })
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })

  const { data: eleves = [], isLoading } = useQuery({
    queryKey: ['eleves', classeId],
    queryFn: () => organisationApi.eleves.byClass(classeId!),
    enabled: classeId !== null,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { classeId: classeId ?? 0 },
  })

  const prevEditing = useRef<Eleve | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? { codeEleve: editing.codeEleve, nom: editing.nom, prenom: editing.prenom, numIdentite: editing.numIdentite, email: editing.email, telephone: editing.telephone, classeId: editing.classeId }
      : { codeEleve: '', nom: '', prenom: '', classeId: classeId ?? 0 }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.eleves.update(editing.idEleve, d)
        : organisationApi.eleves.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Élève mis à jour' : 'Élève créé')
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, estActif }: { id: number; estActif: boolean }) =>
      organisationApi.eleves.toggleStatut(id, estActif),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.eleves.delete(id),
    onSuccess: () => {
      toast.success('Élève supprimé')
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  // La classe se choisit en deux temps — le niveau, puis la classe. Un
  // établissement compte quarante classes : un menu déroulant unique obligeait
  // à parcourir toute la liste pour atteindre la 7B.
  const classesDuNiveau = niveauId === null ? classes : classes.filter((c) => c.levelId === niveauId)
  const classeChoisie = classes.find((c) => c.idClasse === classeId) ?? null

  const q = recherche.trim().toLowerCase()
  const elevesFiltres = q
    ? eleves.filter((e) =>
        [e.nom, e.prenom, e.codeEleve, e.email, e.numIdentite]
          .some((v) => v?.toLowerCase().includes(q)))
    : eleves

  const actifs = elevesFiltres.filter((e) => e.estActif).length

  return (
    <div className="space-y-6">
      <PageHero
        title="Élèves"
        subtitle="Les effectifs, classe par classe"
        icon={Users}
        actions={
          <>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => setImportOpen(true)}
            >
              <Upload size={16} /> Importer
            </Button>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => { setEditing(null); setOpen(true) }}
            >
              <Plus size={16} /> Nouvel élève
            </Button>
          </>
        }
      />

      {/* Niveau puis classe : deux rangées de pastilles. La seconde ne montre
          que les classes du niveau retenu, ce qui la garde lisible. */}
      <div className="space-y-4 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div>
          <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Niveau</p>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setNiveauId(null)}
              aria-pressed={niveauId === null}
              className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                niveauId === null
                  ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                  : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
              }`}
            >
              <Layers size={14} /> Tous
            </button>
            {levels.map((l) => {
              const actif = l.idNiveau === niveauId
              const nb = classes.filter((c) => c.levelId === l.idNiveau).length
              return (
                <button
                  key={l.idNiveau}
                  // Changer de niveau invalide la classe retenue si elle n'en fait pas partie.
                  onClick={() => {
                    const suivant = actif ? null : l.idNiveau
                    setNiveauId(suivant)
                    if (suivant !== null && classeChoisie && classeChoisie.levelId !== suivant) setClasseId(null)
                  }}
                  aria-pressed={actif}
                  className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                    actif
                      ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                      : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                  }`}
                >
                  {l.nom}
                  <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                    actif ? 'bg-white/20 text-white' : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                  }`}>
                    {nb}
                  </span>
                </button>
              )
            })}
          </div>
        </div>

        <div>
          <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Classe</p>
          {classesDuNiveau.length === 0 ? (
            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              Aucune classe pour ce niveau.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {classesDuNiveau.map((c) => {
                const actif = c.idClasse === classeId
                return (
                  <button
                    key={c.idClasse}
                    onClick={() => setClasseId(actif ? null : c.idClasse)}
                    aria-pressed={actif}
                    title={`${c.code} — ${c.levelNom}`}
                    className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 font-mono text-sm font-medium transition-colors ${
                      actif
                        ? 'border-brand-blue bg-brand-blue text-white shadow-sm'
                        : 'border-brand-border bg-white text-brand-text hover:border-brand-blue/40 hover:bg-blue-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                    }`}
                  >
                    {c.code}
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {classeId === null ? (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <MousePointerClick size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">Choisissez une classe</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            Ses élèves s'afficheront ici. Les effectifs se tiennent classe par classe.
          </p>
        </div>
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatCard title="Élèves affichés" value={elevesFiltres.length} icon={Users} color="blue" />
            <StatCard title="Actifs" value={actifs} icon={CheckCircle2} color="green" />
            <StatCard title="Inactifs" value={elevesFiltres.length - actifs} icon={PowerOff} color="red" />
          </div>

          <div className="relative max-w-sm">
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500" />
            <input
              value={recherche}
              onChange={(e) => setRecherche(e.target.value)}
              placeholder="Rechercher un élève…"
              className="w-full rounded-lg border border-brand-border bg-white py-2 pl-9 pr-9 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            />
            {recherche && (
              <button
                onClick={() => setRecherche('')}
                title="Effacer la recherche"
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text dark:hover:bg-slate-800"
              >
                <X size={14} />
              </button>
            )}
          </div>

          {isLoading && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="h-28 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
              ))}
            </div>
          )}

          {!isLoading && elevesFiltres.length === 0 && (
            <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
              <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
                <Users size={22} />
              </span>
              <p className="text-sm font-medium text-brand-text dark:text-slate-200">
                {q
                  ? `Aucun élève ne correspond à « ${recherche} »`
                  : `Aucun élève dans ${classeChoisie?.code ?? 'cette classe'}`}
              </p>
              {!q && (
                <div className="mt-4 flex justify-center gap-2">
                  <Button variant="outline" onClick={() => setImportOpen(true)}><Upload size={16} /> Importer</Button>
                  <Button variant="outline" onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvel élève</Button>
                </div>
              )}
            </div>
          )}

          {/* Une carte par élève : nom, contact et statut se lisent ensemble,
              là où le tableau étalait sept colonnes dont cinq tirets. */}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {elevesFiltres.map((e) => (
              <div
                key={e.idEleve}
                className={`flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
                  e.estActif ? '' : 'opacity-70'
                }`}
              >
                <div className="flex flex-1 items-start gap-3 p-4">
                  <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                    e.estActif
                      ? 'bg-teal-50 text-brand-teal dark:bg-teal-500/15 dark:text-teal-300'
                      : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                  }`}>
                    {initiales(e.prenom, e.nom)}
                  </span>

                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">
                          {e.prenom} {e.nom}
                        </h3>
                        <p className="mt-0.5 font-mono text-xs text-brand-textMuted dark:text-slate-400">
                          {e.codeEleve}
                          {e.numIdentite ? ` · ${e.numIdentite}` : ''}
                        </p>
                      </div>
                      {!e.estActif && <Badge variant="danger">Inactif</Badge>}
                    </div>

                    {(e.email || e.telephone) && (
                      <div className="mt-2 space-y-1">
                        {e.email && (
                          <p className="flex items-center gap-1.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                            <Mail size={12} className="shrink-0" /> {e.email}
                          </p>
                        )}
                        {e.telephone && (
                          <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                            <Phone size={12} className="shrink-0" /> {e.telephone}
                          </p>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between gap-2 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                  <Badge variant="default">{e.classeCode}</Badge>
                  <div className="flex items-center gap-1">
                    <button
                      title={e.estActif ? 'Désactiver cet élève' : 'Réactiver cet élève'}
                      onClick={() => toggleMutation.mutate({ id: e.idEleve, estActif: !e.estActif })}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                    >
                      {e.estActif ? <PowerOff size={15} /> : <Power size={15} />}
                    </button>
                    <button
                      title="Modifier" onClick={() => { setEditing(e); setOpen(true) }}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                    >
                      <Pencil size={15} />
                    </button>
                    <button
                      title="Supprimer" onClick={() => setDeleteTarget(e)}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-500/10"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? "Modifier l'élève" : 'Nouvel élève'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Nom *" {...register('nom')} error={errors.nom?.message} />
            <Input label="Prénom *" {...register('prenom')} error={errors.prenom?.message} />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Code élève *" placeholder="EL001" {...register('codeEleve')} error={errors.codeEleve?.message} />
            <Input label="N° d'identité" {...register('numIdentite')} />
          </div>

          {/* Le contact est ce que la vie scolaire utilise pour joindre la
              famille : il mérite d'être annoncé, pas seulement aligné. */}
          <div className="rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-4 dark:border-slate-700 dark:bg-slate-800/40">
            <p className="mb-3 flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
              <Mail size={14} className="text-brand-textMuted dark:text-slate-400" />
              Contact
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Email" type="email" placeholder="prenom.nom@exemple.tn" {...register('email')} error={errors.email?.message} />
              <Input label="Téléphone" placeholder="+216 …" {...register('telephone')} />
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Classe *</label>
            <select
              {...register('classeId')}
              defaultValue={classeId ?? ''}
              className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              <option value="">Sélectionner…</option>
              {classes.map((c) => <option key={c.idClasse} value={c.idClasse}>{c.code} — {c.levelNom}</option>)}
            </select>
            {errors.classeId && <p className="mt-1 text-xs text-danger">{errors.classeId.message}</p>}
          </div>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : "Créer l'élève"}
            </Button>
          </div>
        </form>
      </Modal>

      <ImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        titre="Importer des élèves"
        entite={{ singulier: 'élève', pluriel: 'élèves' }}
        onImport={organisationApi.eleves.importFile}
        onImported={() => qc.invalidateQueries({ queryKey: ['eleves', classeId] })}
        templates={{
          csv: organisationApi.eleves.downloadTemplateCsv,
          excel: organisationApi.eleves.downloadTemplateExcel,
          basename: 'template_eleves',
        }}
      />

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idEleve)}
        loading={deleteMutation.isPending}
        title="Supprimer l'élève"
        message={`Supprimer ${deleteTarget?.prenom} ${deleteTarget?.nom} ?`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
