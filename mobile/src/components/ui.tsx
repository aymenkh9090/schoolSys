/**
 * Briques d'interface communes aux trois écrans. Volontairement peu nombreuses :
 * l'application a trois écrans, une bibliothèque de composants y coûterait plus
 * qu'elle ne rapporte.
 */

import type { ReactNode } from 'react'
import { Ionicons } from '@expo/vector-icons'
import {
  ActivityIndicator,
  Pressable,
  type StyleProp,
  StyleSheet,
  Text,
  TextInput,
  type TextInputProps,
  View,
  type ViewStyle,
} from 'react-native'

import { colors, radius, shadow } from '../theme'

type IconName = keyof typeof Ionicons.glyphMap

export function Button({
  title,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
}: {
  title: string
  onPress: () => void
  variant?: 'primary' | 'outline' | 'danger'
  loading?: boolean
  disabled?: boolean
}) {
  const inactive = disabled || loading
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [
        styles.button,
        variant === 'primary' && { backgroundColor: colors.primary, ...shadow },
        variant === 'danger' && { backgroundColor: colors.danger, ...shadow },
        variant === 'outline' && {
          backgroundColor: colors.white,
          borderWidth: 1,
          borderColor: colors.border,
        },
        (pressed || inactive) && { opacity: 0.6 },
      ]}
    >
      {loading && (
        <ActivityIndicator size="small" color={variant === 'outline' ? colors.primary : colors.white} />
      )}
      <Text style={[styles.buttonText, variant === 'outline' && { color: colors.text }]}>
        {title}
      </Text>
    </Pressable>
  )
}

export function Field({
  label,
  hint,
  ...props
}: TextInputProps & { label: string; hint?: string }) {
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.textMuted}
        {...props}
        style={[styles.input, props.multiline && { height: 88, textAlignVertical: 'top' }]}
      />
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </View>
  )
}

export function Badge({
  text,
  color = colors.textMuted,
}: {
  text: string
  color?: string
}) {
  return (
    <View style={[styles.badge, { backgroundColor: `${color}1A`, borderColor: `${color}55` }]}>
      <Text style={[styles.badgeText, { color }]}>{text}</Text>
    </View>
  )
}

export function Card({
  children,
  style,
}: {
  children: ReactNode
  style?: StyleProp<ViewStyle>
}) {
  return <View style={[styles.card, style]}>{children}</View>
}

/** Message d'erreur ou d'état vide — même traitement visuel partout. */
export function Notice({ text, tone = 'info' }: { text: string; tone?: 'info' | 'error' }) {
  const color = tone === 'error' ? colors.danger : colors.primary
  return (
    <View style={[styles.notice, { backgroundColor: `${color}12`, borderColor: `${color}44` }]}>
      <Text style={{ color: tone === 'error' ? colors.danger : colors.navy, fontSize: 13, lineHeight: 19 }}>
        {text}
      </Text>
    </View>
  )
}

export function Loading({ text = 'Chargement…' }: { text?: string }) {
  return (
    <View style={{ paddingVertical: 32, alignItems: 'center', gap: 10 }}>
      <ActivityIndicator color={colors.primary} />
      <Text style={{ color: colors.textMuted, fontSize: 13 }}>{text}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    // 46 px de haut : au-dessus des 44 px recommandés pour une cible tactile,
    // et l'appel se tape debout, parfois d'une seule main.
    paddingVertical: 14,
    paddingHorizontal: 18,
    borderRadius: radius.md,
  },
  buttonText: { color: colors.white, fontSize: 15, fontWeight: '700', letterSpacing: 0.2 },
  label: { fontSize: 13, fontWeight: '600', color: colors.text, marginBottom: 6 },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 13,
    fontSize: 15,
    color: colors.text,
  },
  hint: { fontSize: 11, color: colors.textMuted, marginTop: 5 },
  badge: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 9,
    paddingVertical: 3,
  },
  badgeText: { fontSize: 11, fontWeight: '700' },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 15,
    ...shadow,
  },
  notice: {
    borderWidth: 1,
    borderRadius: radius.md,
    padding: 12,
  },
  tile: {
    flex: 1,
    minWidth: '46%',
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: 14,
    gap: 8,
    ...shadow,
  },
  tileIcone: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tileTitre: { fontSize: 14, fontWeight: '700', color: colors.text },
  tileDetail: { fontSize: 11, color: colors.textMuted, lineHeight: 15 },
  stat: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 10,
  },
  statValeur: { fontSize: 22, fontWeight: '800', color: colors.white },
  statLibelle: { fontSize: 11, color: '#cbd5e1', marginTop: 2 },
  sectionTitre: { flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 6 },
  sectionTitreTexte: {
    fontSize: 12,
    fontWeight: '800',
    color: colors.primary,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
})

// ── Briques du tableau de bord ──────────────────────────────────────────────

/**
 * Tuile d'action : une icône, un titre, une ligne d'explication.
 *
 * L'icône n'est pas décorative — sur un écran de 6 pouces parcouru en marchant
 * vers sa classe, c'est elle qu'on vise, pas le libellé qu'on lit.
 */
export function Tile({
  icon,
  titre,
  detail,
  couleur = colors.primary,
  onPress,
}: {
  icon: IconName
  titre: string
  detail: string
  couleur?: string
  onPress: () => void
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.tile, pressed && { opacity: 0.7, transform: [{ scale: 0.98 }] }]}
    >
      <View style={[styles.tileIcone, { backgroundColor: `${couleur}18` }]}>
        <Ionicons name={icon} size={22} color={couleur} />
      </View>
      <Text style={styles.tileTitre}>{titre}</Text>
      <Text style={styles.tileDetail} numberOfLines={2}>
        {detail}
      </Text>
    </Pressable>
  )
}

/** Chiffre du jour : la valeur d'abord, son sens ensuite. */
export function Stat({ valeur, libelle }: { valeur: number | string; libelle: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statValeur}>{valeur}</Text>
      <Text style={styles.statLibelle}>{libelle}</Text>
    </View>
  )
}

export function SectionTitle({ titre, icon }: { titre: string; icon?: IconName }) {
  return (
    <View style={styles.sectionTitre}>
      {icon && <Ionicons name={icon} size={15} color={colors.primary} />}
      <Text style={styles.sectionTitreTexte}>{titre}</Text>
    </View>
  )
}
