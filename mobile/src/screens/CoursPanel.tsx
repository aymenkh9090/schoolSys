import { useRef, useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
import { File } from 'expo-file-system'
import { useMutation } from '@tanstack/react-query'
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'

import { coursApi } from '../api'
import type { FichierAEnvoyer } from '../api/client'
import type { CoursChatMessage, DocumentDepose } from '../api/types'
import { Markdown } from '../components/Markdown'
import { Notice } from '../components/ui'
import { colors, radius, shadow } from '../theme'

// Des exemples, pas un menu : ils montrent l'étendue de ce qu'on peut demander
// — expliquer, faire produire, interroger — et se remplacent par n'importe
// quelle phrase. C'est l'enseignant qui décide de ce dont il a besoin.
const EXEMPLES = [
  'Résume ce cours en dix lignes',
  'Explique la partie la plus difficile, pour des 8ᵉ',
  'Propose 4 exercices, du plus simple au plus difficile',
  'Fais un QCM de 5 questions avec le corrigé à la fin',
]

interface Bulle {
  role: 'user' | 'assistant'
  texte: string
  duree?: number
}

/**
 * Conversation autour d'un cours que l'enseignant attache.
 *
 * Le service ne décide pas de ce qu'il faut produire : il fournit le cours au
 * modèle et transmet la demande. Un écran qui aurait imposé « résumé puis
 * exercices » aurait interdit « explique-moi la partie 3 » — la demande de
 * l'enseignant serait passée après celle du développeur.
 */
export function CoursPanel() {
  const [document, setDocument] = useState<DocumentDepose | null>(null)
  const [bulles, setBulles] = useState<Bulle[]>([])
  const [saisie, setSaisie] = useState('')
  const listeRef = useRef<ScrollView>(null)

  const depot = useMutation({
    mutationFn: (f: FichierAEnvoyer) => coursApi.deposer(f),
    onSuccess: (d) => {
      setDocument(d)
      setBulles([])
    },
  })

  const demande = useMutation({
    mutationFn: (message: string) =>
      coursApi.demander(
        document!.document_id,
        message,
        bulles.map<CoursChatMessage>((b) => ({ role: b.role, content: b.texte }))
      ),
    onSuccess: (r) =>
      setBulles((b) => [...b, { role: 'assistant', texte: r.answer, duree: r.duration_ms }]),
    onError: (e: Error) =>
      setBulles((b) => [...b, { role: 'assistant', texte: `⚠ ${e.message}` }]),
  })

  /**
   * Le sélecteur vient d'`expo-file-system`, et non d'`expo-document-picker`.
   *
   * Ce dernier dépose sa copie dans un cache partagé par toutes les expériences
   * d'Expo Go ; `expo-file-system` n'autorise la lecture que dans le répertoire
   * de l'expérience en cours, et refuse ce chemin — « missing read permission ».
   * En laissant le module qui LIT le fichier être celui qui le CHOISIT, la
   * question de la permission ne se pose plus : elle est réglée par
   * construction, pas contournée.
   */
  async function joindre() {
    const resultat = await File.pickFileAsync({ mimeTypes: 'application/pdf' })
    if (resultat.canceled) return

    const f = resultat.result
    console.log('[cours] fichier choisi', {
      uri: f.uri,
      name: f.name,
      type: f.type,
      size: f.size,
    })
    depot.reset()
    demande.reset()
    depot.mutate({
      name: f.name || 'cours.pdf',
      type: f.type || 'application/pdf',
      bytes: () => f.bytes(),
    })
  }

  function envoyer(texte: string) {
    const message = texte.trim()
    if (!message || !document || demande.isPending) return
    setBulles((b) => [...b, { role: 'user', texte: message }])
    setSaisie('')
    demande.mutate(message)
  }

  const occupe = depot.isPending || demande.isPending

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      {/* ── Le document attaché ──────────────────────────────────────────── */}
      {document && (
        <View style={styles.piece}>
          <View style={styles.pieceIcone}>
            <Ionicons name="document-text" size={17} color={colors.primary} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.pieceNom} numberOfLines={1}>
              {document.nom_fichier}
            </Text>
            <Text style={styles.pieceDetail}>
              {document.pages} page(s) · {document.caracteres_lus} caractères
              {document.tronque ? ' · document tronqué' : ''}
            </Text>
          </View>
          <Pressable onPress={joindre} hitSlop={10} disabled={occupe}>
            <Ionicons name="swap-horizontal-outline" size={19} color={colors.textMuted} />
          </Pressable>
        </View>
      )}

      <ScrollView
        ref={listeRef}
        contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 16 }}
        onContentSizeChange={() => listeRef.current?.scrollToEnd({ animated: true })}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── Rien d'attaché ─────────────────────────────────────────────── */}
        {!document && !depot.isPending && (
          <>
            <Pressable onPress={joindre} style={styles.depot}>
              <View style={styles.depotIcone}>
                <Ionicons name="add" size={30} color={colors.white} />
              </View>
              <Text style={styles.depotTitre}>Joindre un cours en PDF</Text>
              <Text style={styles.depotTexte}>
                Ensuite, demandez ce que vous voulez : une explication, des exercices,
                un QCM avec son corrigé.
              </Text>
            </Pressable>

            <View style={styles.limites}>
              <Limite
                icon="text-outline"
                texte="Le texte du PDF doit être sélectionnable : un cours scanné est refusé, l’assistant ne devine pas."
              />
              <Limite
                icon="cloud-offline-outline"
                texte="Le document reste en mémoire le temps de la préparation, puis s’efface. Rien n’est écrit sur disque."
              />
              <Limite
                icon="hardware-chip-outline"
                texte="Le modèle tourne sur la machine de l’établissement : rien ne sort du réseau."
              />
            </View>

          </>
        )}

        {depot.isError && <Notice tone="error" text={(depot.error as Error).message} />}

        {depot.isPending && (
          <View style={styles.attente}>
            <ActivityIndicator color={colors.primary} />
            <Text style={styles.attenteTexte}>Lecture du document…</Text>
          </View>
        )}

        {/* ── Document attaché, pas encore de question ───────────────────── */}
        {document && bulles.length === 0 && !demande.isPending && (
          <>
            {document.tronque && (
              <Notice
                text={`Document long : seuls les ${document.caracteres_lus} premiers caractères ont été retenus. Les réponses ne couvriront pas la fin du chapitre.`}
              />
            )}
            <Text style={styles.invite}>Que voulez-vous en faire ?</Text>
            {EXEMPLES.map((e) => (
              <Pressable key={e} onPress={() => envoyer(e)} style={styles.exemple}>
                <Ionicons name="arrow-forward-circle-outline" size={16} color={colors.primary} />
                <Text style={styles.exempleTexte}>{e}</Text>
              </Pressable>
            ))}
            <Text style={styles.libre}>…ou écrivez votre demande en bas de l’écran.</Text>
          </>
        )}

        {/* ── La conversation ────────────────────────────────────────────── */}
        {bulles.map((b, i) =>
          b.role === 'user' ? (
            <View key={i} style={styles.bulleMoi}>
              <Text style={styles.texteMoi}>{b.texte}</Text>
            </View>
          ) : (
            <View key={i} style={styles.bulleAssistant}>
              <View style={styles.signature}>
                <Ionicons name="sparkles" size={12} color={colors.violet} />
                <Text style={styles.signatureTexte}>Assistant</Text>
              </View>
              <Markdown texte={b.texte} />
              {b.duree !== undefined && (
                <Text style={styles.duree}>{(b.duree / 1000).toFixed(1)} s</Text>
              )}
            </View>
          )
        )}

        {demande.isPending && (
          <View style={[styles.bulleAssistant, styles.enAttente]}>
            <ActivityIndicator size="small" color={colors.violet} />
            <Text style={styles.corps}>Lecture du cours et rédaction…</Text>
          </View>
        )}

        {bulles.some((b) => b.role === 'assistant') && (
          <Notice text="Réponses produites par le modèle : relisez-les avant la classe. Les corrigés et les calculs, en particulier, se vérifient — c’est là que se trompe un modèle de cette taille." />
        )}
      </ScrollView>

      {/* ── Saisie ──────────────────────────────────────────────────────── */}
      <View style={styles.zoneSaisie}>
        <Pressable
          onPress={joindre}
          disabled={occupe}
          style={[styles.joindre, occupe && { opacity: 0.4 }]}
        >
          <Ionicons
            name={document ? 'swap-horizontal' : 'add'}
            size={22}
            color={colors.primary}
          />
        </Pressable>
        <TextInput
          value={saisie}
          onChangeText={setSaisie}
          editable={!!document && !occupe}
          placeholder={
            document ? 'Ex : fais un QCM de 5 questions…' : 'Joignez d’abord un cours PDF'
          }
          placeholderTextColor={colors.textMuted}
          style={styles.champSaisie}
          multiline
        />
        <Pressable
          onPress={() => envoyer(saisie)}
          disabled={!saisie.trim() || !document || occupe}
          style={[
            styles.envoyer,
            (!saisie.trim() || !document || occupe) && { opacity: 0.4 },
          ]}
        >
          <Ionicons name="arrow-up" size={20} color={colors.white} />
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  )
}

function Limite({ icon, texte }: { icon: keyof typeof Ionicons.glyphMap; texte: string }) {
  return (
    <View style={styles.limite}>
      <Ionicons name={icon} size={15} color={colors.textMuted} />
      <Text style={styles.limiteTexte}>{texte}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  piece: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 11,
    marginHorizontal: 16,
    marginTop: 10,
    padding: 10,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    ...shadow,
  },
  pieceIcone: {
    width: 34,
    height: 34,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pieceNom: { fontSize: 13, fontWeight: '700', color: colors.text },
  pieceDetail: { fontSize: 11, color: colors.textMuted, marginTop: 1 },
  depot: {
    alignItems: 'center',
    gap: 10,
    paddingVertical: 30,
    paddingHorizontal: 22,
    borderRadius: radius.lg,
    borderWidth: 2,
    borderStyle: 'dashed',
    borderColor: colors.primaryLight,
    backgroundColor: colors.primarySoft,
  },
  depotIcone: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow,
  },
  depotTitre: { fontSize: 16, fontWeight: '800', color: colors.text },
  depotTexte: { fontSize: 13, color: colors.textMuted, textAlign: 'center', lineHeight: 19 },
  limites: { gap: 10, paddingHorizontal: 4 },
  limite: { flexDirection: 'row', gap: 9, alignItems: 'flex-start' },
  limiteTexte: { flex: 1, fontSize: 12, color: colors.textMuted, lineHeight: 17 },
  attente: { alignItems: 'center', gap: 10, paddingVertical: 26 },
  attenteTexte: { fontSize: 13, color: colors.textMuted },
  invite: { fontSize: 15, fontWeight: '700', color: colors.text, marginTop: 4 },
  exemple: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    borderRadius: radius.md,
    padding: 12,
  },
  exempleTexte: { flex: 1, fontSize: 13, color: colors.text },
  libre: { fontSize: 12, color: colors.textMuted, textAlign: 'center', marginTop: 2 },
  bulleMoi: {
    alignSelf: 'flex-end',
    maxWidth: '85%',
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    padding: 12,
  },
  bulleAssistant: {
    alignSelf: 'stretch',
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: 13,
    ...shadow,
  },
  enAttente: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  signature: { flexDirection: 'row', alignItems: 'center', gap: 5, marginBottom: 8 },
  signatureTexte: { fontSize: 11, fontWeight: '700', color: colors.violet },
  texteMoi: { color: colors.white, fontSize: 14, lineHeight: 20 },
  corps: { fontSize: 14, lineHeight: 21, color: colors.text },
  duree: { marginTop: 8, fontSize: 10, color: colors.textMuted },
  zoneSaisie: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    padding: 12,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.white,
  },
  joindre: {
    width: 44,
    height: 44,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.bgSecondary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  champSaisie: {
    flex: 1,
    maxHeight: 110,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 14,
    color: colors.text,
  },
  envoyer: {
    width: 44,
    height: 44,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
})
