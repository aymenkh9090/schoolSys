import { Ban, Plus, Quote, Star, Trash2 } from 'lucide-react'

import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { cn } from '@/lib/utils'
import type {
  ConstraintDsl,
  DslAggregateMetric,
  DslCondition,
  DslFieldDescriptor,
  DslSchema,
  DslScope,
  DslSeverity,
} from '@/api/planning.api'
import {
  DEFAULT_WEIGHTS,
  describeRule,
  isAggregateScope,
  METRIC_LABELS,
  SCOPE_HELP,
  SCOPE_LABELS,
  SEVERITY_HELP,
  SEVERITY_LABELS,
  SEVERITY_VARIANTS,
} from './dslLabels'

interface Props {
  schema: DslSchema
  value: ConstraintDsl
  onChange: (rule: ConstraintDsl) => void
}

/**
 * Construction visuelle d'une règle DSL.
 *
 * Le formulaire est organisé en étapes numérotées qui suivent l'ordre dans
 * lequel un responsable d'établissement formule une contrainte : ce qu'il veut
 * (interdire / favoriser), sur quoi ça porte, dans quels cas, et à quel point
 * c'est impératif. Le vocabulaire du DSL — portée, action, sévérité, poids —
 * n'apparaît nulle part : il n'a de sens que pour le moteur.
 *
 * Toutes les listes déroulantes proviennent du catalogue du backend : champs,
 * opérateurs compatibles avec le type du champ, valeurs admises. L'interface ne
 * connaît donc aucun nom de champ en dur — ajouter un champ côté serveur le rend
 * disponible ici au rechargement, et en retirer un le fait disparaître, sans
 * livraison frontend.
 *
 * Le formulaire empêche les erreurs les plus fréquentes (opérateur incompatible,
 * valeur hors liste), mais il ne PRÉTEND PAS valider : le verdict reste celui du
 * backend, obtenu en analysant la règle. Deux validations concurrentes finissent
 * toujours par diverger, et c'est la plus permissive qui décide.
 */
export function DslRuleBuilder({ schema, value, onChange }: Props) {
  const fieldsByName = new Map(schema.fields.map((f) => [f.name, f]))
  const aggregate = isAggregateScope(value.scope)

  const patch = (partial: Partial<ConstraintDsl>) => onChange({ ...value, ...partial })

  const setScope = (scope: DslScope) => {
    // Changer de portée change la nature de la règle : une portée agrégée exige
    // un seuil, une portée LESSON l'interdit. On aligne le bloc `aggregate` ici
    // plutôt que de laisser le backend refuser une combinaison que le formulaire
    // aurait lui-même produite.
    if (isAggregateScope(scope)) {
      patch({
        scope,
        aggregate: value.aggregate ?? { metric: 'TOTAL_HOURS', operator: 'GREATER_THAN', value: 3 },
      })
    } else {
      const { aggregate: _dropped, ...rest } = value
      onChange({ ...rest, scope })
    }
  }

  // Le poids suit le niveau d'exigence tant que l'utilisateur ne l'a pas repris
  // en main dans les options avancées : un administrateur n'a aucun moyen de
  // choisir « 37 sur 1000 » de façon éclairée, et le défaut par niveau est déjà
  // celui que le solveur attend.
  const setSeverity = (severity: DslSeverity) => {
    const followsDefault = Object.values(DEFAULT_WEIGHTS).includes(value.weight)
    patch({ severity, weight: followsDefault ? DEFAULT_WEIGHTS[severity] : value.weight })
  }

  const updateCondition = (index: number, condition: DslCondition) => {
    const conditions = [...value.conditions]
    conditions[index] = condition
    patch({ conditions })
  }

  const addCondition = () => {
    const first = schema.fields[0]
    patch({
      conditions: [
        ...value.conditions,
        { field: first.name, operator: first.operators[0], value: first.allowedValues[0] ?? '' },
      ],
    })
  }

  const removeCondition = (index: number) =>
    patch({ conditions: value.conditions.filter((_, i) => i !== index) })

  return (
    <div className="space-y-5">
      {/* ── 1. Intention ────────────────────────────────────────────────── */}
      <Step number={1} title="Que doit faire le planning ?">
        <div className="grid gap-2 sm:grid-cols-2">
          <ChoiceCard
            icon={Ban}
            title="Éviter"
            help="Le planning ne placera pas les séances décrites ci-dessous."
            selected={value.action === 'PENALIZE'}
            onSelect={() => patch({ action: 'PENALIZE' })}
          />
          <ChoiceCard
            icon={Star}
            title="Privilégier"
            help="Le planning essaiera de placer les séances décrites ci-dessous."
            selected={value.action === 'REWARD'}
            onSelect={() => patch({ action: 'REWARD' })}
          />
        </div>
      </Step>

      {/* ── 2. Portée ───────────────────────────────────────────────────── */}
      <Step
        number={2}
        title="Cette règle regarde quoi ?"
        subtitle="Une séance à la fois, ou un cumul d’heures sur une journée / une semaine."
      >
        <Select
          value={value.scope}
          onChange={(e) => setScope(e.target.value as DslScope)}
          options={schema.scopes.map((s) => ({
            value: s.name,
            label: SCOPE_LABELS[s.name as DslScope] ?? s.label,
          }))}
        />
        <p className="mt-1.5 text-xs text-brand-textMuted dark:text-slate-400">
          {SCOPE_HELP[value.scope]}
        </p>
      </Step>

      {/* ── 3. Seuil (portées agrégées uniquement) ──────────────────────── */}
      {aggregate && value.aggregate && (
        <Step number={3} title="Quelle limite ne pas franchir ?">
          <div className="grid gap-2 sm:grid-cols-3">
            <Select
              label="On compte"
              value={value.aggregate.metric}
              onChange={(e) =>
                patch({
                  aggregate: { ...value.aggregate!, metric: e.target.value as DslAggregateMetric },
                })
              }
              options={schema.aggregateMetrics.map((m) => ({
                value: m.name,
                label: METRIC_LABELS[m.name] ?? m.label,
              }))}
            />
            <Select
              label="Et le total ne doit pas"
              value={value.aggregate.operator}
              onChange={(e) => patch({ aggregate: { ...value.aggregate!, operator: e.target.value } })}
              options={[
                { value: 'GREATER_THAN', label: 'dépasser' },
                { value: 'GREATER_THAN_OR_EQUAL', label: 'atteindre' },
                { value: 'LESS_THAN', label: 'rester sous' },
              ]}
            />
            <Input
              label="Cette valeur"
              type="number"
              min={0}
              step="0.5"
              value={value.aggregate.value}
              onChange={(e) =>
                patch({
                  aggregate: { ...value.aggregate!, value: Number(e.currentTarget.value) },
                })
              }
            />
          </div>
        </Step>
      )}

      {/* ── 4. Conditions ───────────────────────────────────────────────── */}
      <Step
        number={aggregate ? 4 : 3}
        title={aggregate ? 'Faut-il ne compter que certaines séances ?' : 'Quelles séances sont concernées ?'}
        subtitle={
          aggregate
            ? 'Facultatif. Sans précision, toutes les séances sont comptées.'
            : 'Décrivez-les : matière, jour, heure, niveau… Ajoutez autant de précisions que nécessaire.'
        }
        action={
          <button
            type="button"
            onClick={addCondition}
            className="inline-flex items-center gap-1 rounded-lg border border-brand-border px-2.5 py-1.5 text-xs font-medium text-brand-text transition-colors hover:bg-brand-bgSecondary dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            <Plus size={13} /> Ajouter une précision
          </button>
        }
      >
        {value.conditions.length > 1 && (
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className="text-xs text-brand-textMuted dark:text-slate-400">
              La règle s’applique quand :
            </span>
            {(['AND', 'OR'] as const).map((logic) => (
              <button
                key={logic}
                type="button"
                onClick={() => patch({ logic })}
                className={cn(
                  'rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors',
                  (value.logic ?? 'AND') === logic
                    ? 'bg-brand-blue text-white'
                    : 'border border-brand-border text-brand-textMuted dark:border-slate-700 dark:text-slate-400'
                )}
              >
                {logic === 'AND' ? 'toutes ces précisions sont vraies' : 'au moins une est vraie'}
              </button>
            ))}
          </div>
        )}

        <div className="space-y-2">
          {value.conditions.map((condition, index) => (
            <ConditionRow
              key={index}
              index={index}
              logic={value.logic ?? 'AND'}
              schema={schema}
              field={fieldsByName.get(condition.field)}
              condition={condition}
              onChange={(next) => updateCondition(index, next)}
              onRemove={() => removeCondition(index)}
            />
          ))}

          {value.conditions.length === 0 && (
            <p className="rounded-lg border border-dashed border-brand-border px-3 py-4 text-center text-xs text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
              {aggregate
                ? 'Aucune précision : toutes les séances seront comptées.'
                : 'Cliquez sur « Ajouter une précision » pour désigner les séances concernées.'}
            </p>
          )}
        </div>
      </Step>

      {/* ── 5. Exigence ─────────────────────────────────────────────────── */}
      <Step
        number={aggregate ? 5 : 4}
        title="À quel point est-ce impératif ?"
        subtitle="C’est ce qui décide de la marge laissée au planning quand tout ne peut pas être respecté."
      >
        <div className="grid gap-2 sm:grid-cols-3">
          {(schema.severities.map((s) => s.name) as DslSeverity[]).map((severity) => (
            <button
              key={severity}
              type="button"
              onClick={() => setSeverity(severity)}
              className={cn(
                'rounded-lg border p-3 text-left transition-colors',
                value.severity === severity
                  ? 'border-brand-blue bg-brand-blue/5 dark:border-brand-blue dark:bg-brand-blue/10'
                  : 'border-brand-border hover:bg-brand-bgSecondary/60 dark:border-slate-700 dark:hover:bg-slate-800/50'
              )}
            >
              <span
                className={cn(
                  'flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-100'
                )}
              >
                <span
                  className={cn(
                    'h-2 w-2 shrink-0 rounded-full',
                    SEVERITY_VARIANTS[severity] === 'danger' && 'bg-red-500',
                    SEVERITY_VARIANTS[severity] === 'warning' && 'bg-amber-500',
                    SEVERITY_VARIANTS[severity] === 'info' && 'bg-blue-500'
                  )}
                />
                {SEVERITY_LABELS[severity]}
              </span>
              <span className="mt-1 block text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
                {SEVERITY_HELP[severity]}
              </span>
            </button>
          ))}
        </div>
      </Step>

      {/* ── Relecture en français ───────────────────────────────────────── */}
      <div className="flex items-start gap-2.5 rounded-lg border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
        <Quote size={15} className="mt-0.5 shrink-0 text-brand-blue" />
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-brand-textMuted dark:text-slate-400">
            Ce que vous avez écrit
          </p>
          <p className="mt-1 text-sm leading-relaxed text-brand-text dark:text-slate-100">
            {describeRule(value, schema)}
          </p>
        </div>
      </div>

      {/* Le poids reste accessible, mais hors du chemin principal : il ne se
          règle utilement qu'en connaissant la fonction de score du solveur. */}
      <details className="rounded-lg border border-brand-border px-3 py-2 dark:border-slate-700">
        <summary className="cursor-pointer text-xs font-medium text-brand-textMuted dark:text-slate-400">
          Options avancées
        </summary>
        <div className="mt-3 max-w-xs">
          <Input
            label="Poids dans le calcul (1 à 1000)"
            type="number"
            min={1}
            max={1000}
            value={value.weight}
            onChange={(e) => patch({ weight: Number(e.currentTarget.value) })}
          />
          <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
            Réglé automatiquement selon le niveau choisi. Ne le modifiez que pour départager
            deux règles de même niveau.
          </p>
        </div>
      </details>
    </div>
  )
}

// ── Habillage d'une étape ───────────────────────────────────────────────────

function Step({
  number,
  title,
  subtitle,
  action,
  children,
}: {
  number: number
  title: string
  subtitle?: string
  action?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <section>
      <div className="mb-2 flex flex-wrap items-start justify-between gap-2">
        <div className="flex items-start gap-2">
          <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-blue text-[11px] font-semibold text-white">
            {number}
          </span>
          <div>
            <p className="text-sm font-medium text-brand-text dark:text-slate-200">{title}</p>
            {subtitle && (
              <p className="mt-0.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
                {subtitle}
              </p>
            )}
          </div>
        </div>
        {action}
      </div>
      <div className="pl-7">{children}</div>
    </section>
  )
}

function ChoiceCard({
  icon: Icon,
  title,
  help,
  selected,
  onSelect,
}: {
  icon: React.ElementType
  title: string
  help: string
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        'flex items-start gap-2.5 rounded-lg border p-3 text-left transition-colors',
        selected
          ? 'border-brand-blue bg-brand-blue/5 dark:border-brand-blue dark:bg-brand-blue/10'
          : 'border-brand-border hover:bg-brand-bgSecondary/60 dark:border-slate-700 dark:hover:bg-slate-800/50'
      )}
    >
      <Icon
        size={16}
        className={cn(
          'mt-0.5 shrink-0',
          selected ? 'text-brand-blue' : 'text-brand-textMuted dark:text-slate-400'
        )}
      />
      <span className="min-w-0">
        <span className="block text-sm font-medium text-brand-text dark:text-slate-100">{title}</span>
        <span className="mt-0.5 block text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
          {help}
        </span>
      </span>
    </button>
  )
}

// ── Une condition ───────────────────────────────────────────────────────────

function ConditionRow({
  index,
  logic,
  schema,
  field,
  condition,
  onChange,
  onRemove,
}: {
  index: number
  logic: 'AND' | 'OR'
  schema: DslSchema
  field?: DslFieldDescriptor
  condition: DslCondition
  onChange: (condition: DslCondition) => void
  onRemove: () => void
}) {
  const operatorsByName = new Map(schema.operators.map((o) => [o.name, o]))
  const arity = operatorsByName.get(condition.operator)?.arity ?? 1

  const changeField = (name: string) => {
    const next = schema.fields.find((f) => f.name === name)
    if (!next) return
    // Le champ change de type : l'opérateur et la valeur précédents peuvent ne
    // plus avoir de sens. On repart de valeurs cohérentes plutôt que de laisser
    // une combinaison invalide que l'utilisateur ne verrait pas.
    onChange({
      field: name,
      operator: next.operators.includes(condition.operator) ? condition.operator : next.operators[0],
      value: next.allowedValues[0] ?? '',
    })
  }

  const changeOperator = (operator: string) => {
    const nextArity = operatorsByName.get(operator)?.arity ?? 1
    if (nextArity === 1) {
      onChange({ field: condition.field, operator, value: condition.value ?? condition.values?.[0] ?? '' })
    } else {
      const existing = condition.values ?? (condition.value ? [condition.value] : [])
      const size = nextArity === 2 ? 2 : Math.max(1, existing.length)
      onChange({
        field: condition.field,
        operator,
        values: Array.from({ length: size }, (_, i) => existing[i] ?? ''),
      })
    }
  }

  const setValueAt = (index: number, raw: string) => {
    if (arity === 1) {
      onChange({ ...condition, value: raw, values: undefined })
      return
    }
    const values = [...(condition.values ?? [])]
    values[index] = raw
    onChange({ ...condition, value: undefined, values })
  }

  const addValue = () => onChange({ ...condition, values: [...(condition.values ?? []), ''] })

  const values = arity === 1 ? [condition.value ?? ''] : condition.values ?? ['']

  return (
    <div className="rounded-lg border border-brand-border bg-brand-bgSecondary/40 p-2.5 dark:border-slate-700 dark:bg-slate-800/40">
      {/* Le mot de liaison rend la lecture des lignes successives évidente :
          « Matière est Maths » ET « Jour est Vendredi ». */}
      <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wide text-brand-textMuted dark:text-slate-500">
        {index === 0 ? 'La séance…' : logic === 'OR' ? 'ou bien…' : 'et aussi…'}
      </p>
      <div className="grid gap-2 sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end">
        <Select
          label={index === 0 ? 'Ce qu’on regarde' : undefined}
          value={condition.field}
          onChange={(e) => changeField(e.target.value)}
          options={schema.fields.map((f) => ({ value: f.name, label: f.label }))}
        />

        <Select
          label={index === 0 ? 'Comparaison' : undefined}
          value={condition.operator}
          onChange={(e) => changeOperator(e.target.value)}
          // Opérateurs déjà filtrés par le backend selon le type du champ :
          // impossible de proposer « contient » sur une heure.
          options={(field?.operators ?? []).map((name) => ({
            value: name,
            label: operatorsByName.get(name)?.label ?? name,
          }))}
        />

        <div className="space-y-1.5">
          {index === 0 && (
            <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">
              Valeur
            </label>
          )}
          {values.map((raw, valueIndex) => (
            <ValueField
              key={valueIndex}
              field={field}
              value={raw}
              onChange={(next) => setValueAt(valueIndex, next)}
            />
          ))}
          {arity === -1 && (
            <button
              type="button"
              onClick={addValue}
              className="text-xs text-brand-blue hover:underline"
            >
              + valeur
            </button>
          )}
        </div>

        <button
          type="button"
          onClick={onRemove}
          aria-label="Supprimer cette précision"
          className="mb-0.5 rounded-lg p-2 text-brand-textMuted transition-colors hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-950/30"
        >
          <Trash2 size={14} />
        </button>
      </div>
    </div>
  )
}

/**
 * Champ de saisie adapté au type déclaré : liste fermée quand le catalogue en
 * fournit une, sélecteur d'heure pour un TIME, champ numérique pour un NUMBER.
 * C'est là que se jouent la plupart des saisies erronées.
 */
function ValueField({
  field,
  value,
  onChange,
}: {
  field?: DslFieldDescriptor
  value: string
  onChange: (value: string) => void
}) {
  if (field && field.allowedValues.length > 0) {
    return (
      <Select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        options={field.allowedValues.map((v) => ({ value: v, label: v }))}
        placeholder="Choisir…"
      />
    )
  }

  if (field?.type === 'TIME') {
    return <Input type="time" value={value} onChange={(e) => onChange(e.currentTarget.value)} />
  }

  if (field?.type === 'NUMBER') {
    return (
      <Input
        type="number"
        value={value}
        placeholder={field.example}
        onChange={(e) => onChange(e.currentTarget.value)}
      />
    )
  }

  return (
    <Input
      value={value}
      placeholder={field?.example ?? 'Valeur'}
      onChange={(e) => onChange(e.currentTarget.value)}
    />
  )
}
