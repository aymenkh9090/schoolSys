import { useEffect, useRef, useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
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
import { useSafeAreaInsets } from 'react-native-safe-area-context'

import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs'

import { assistantApi } from '../api'
import { Markdown } from '../components/Markdown'
import { colors, radius } from '../theme'
import type { TabParamList } from '../navigation'
import { CoursPanel } from './CoursPanel'

// Les suggestions décrivent les deux capacités réelles de l'assistant — le
// CONTENU d'une séance et l'ÉTAT d'avancement. Reprises du web, elles évitent
// la question hors périmètre, dont le refus fait conclure que ça ne marche pas.
const SUGGESTIONS: { icon: keyof typeof Ionicons.glyphMap; texte: string }[] = [
  { icon: 'search-outline', texte: "Qu'ai-je fait lors de ma dernière séance avec la 71 ?" },
  { icon: 'bookmark-outline', texte: 'Quand ai-je traité ce chapitre, et dans quelles classes ?' },
  { icon: 'clipboard-outline', texte: 'Quel travail ai-je donné, et pour quelle date ?' },
  { icon: 'trending-up-outline', texte: 'Où en est chacune de mes classes dans le programme ?' },
]

interface Message {
  role: 'moi' | 'assistant'
  texte: string
  duree?: number
}

/** Emblème de l'assistant — le même partout, pour qu'on le reconnaisse. */
export function EmblemeAssistant({ taille = 40 }: { taille?: number }) {
  return (
    <View
      style={[
        styles.embleme,
        { width: taille, height: taille, borderRadius: taille / 3 },
      ]}
    >
      <Ionicons name="sparkles" size={taille * 0.5} color={colors.white} />
    </View>
  )
}

/**
 * Question libre sur ses propres cahiers de séance.
 *
 * L'assistant ne voit QUE les séances de l'enseignant connecté. Ce n'est pas cet
 * écran qui l'assure — aucun identifiant n'est envoyé — mais le backend, qui
 * construit le corpus à partir du compte porté par le jeton.
 */
/**
 * L'assistant a deux usages, et un seul onglet.
 *
 * Chercher dans ce qu'on a déjà fait, et préparer ce qu'on va faire : ce sont
 * deux moments opposés de la semaine, mais c'est le même interlocuteur. En
 * faire deux onglets aurait dilué la barre de navigation pour une distinction
 * qui n'existe que dans le code.
 */
export function AssistantScreen({ route }: BottomTabScreenProps<TabParamList, 'Assistant'>) {
  const insets = useSafeAreaInsets()
  const [mode, setMode] = useState<'questions' | 'cours'>('questions')

  // Une question posée depuis l'accueil arrive par les paramètres de l'onglet :
  // l'utilisateur a déjà formulé sa demande là-bas, la retaper ici serait la
  // lui faire écrire deux fois.
  const question = route.params?.question

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={[styles.entete, { paddingTop: insets.top + 12 }]}>
        <EmblemeAssistant />
        <View style={{ flex: 1 }}>
          <Text style={styles.titre}>Assistant SchoolSys</Text>
          <Text style={styles.soustitre}>
            {mode === 'questions'
              ? 'Cherche dans vos séances passées'
              : 'Travaille sur le document que vous joignez'}
          </Text>
        </View>
      </View>

      <View style={styles.segments}>
        {(['questions', 'cours'] as const).map((m) => {
          const actif = m === mode
          return (
            <Pressable
              key={m}
              onPress={() => setMode(m)}
              style={[styles.segment, actif && styles.segmentActif]}
            >
              <Ionicons
                name={m === 'questions' ? 'albums-outline' : 'document-attach-outline'}
                size={15}
                color={actif ? colors.white : colors.textMuted}
              />
              <Text style={[styles.segmentTexte, actif && { color: colors.white }]}>
                {m === 'questions' ? 'Cahiers' : 'Document'}
              </Text>
            </Pressable>
          )
        })}
      </View>

      {mode === 'questions' ? <PanneauQuestions question={question} /> : <CoursPanel />}
    </View>
  )
}

function PanneauQuestions({ question }: { question?: string }) {
  const [messages, setMessages] = useState<Message[]>([])
  const [saisie, setSaisie] = useState('')
  const listeRef = useRef<ScrollView>(null)
  /** Dernière question reçue de l'accueil : elle ne se rejoue pas au re-rendu. */
  const posee = useRef<string | null>(null)

  const demander = useMutation({
    mutationFn: (question: string) => assistantApi.ask(question),
    onSuccess: (r) =>
      setMessages((m) => [...m, { role: 'assistant', texte: r.answer, duree: r.duration_ms }]),
    onError: (e: Error) =>
      setMessages((m) => [...m, { role: 'assistant', texte: `⚠ ${e.message}` }]),
  })

  function envoyer(texte: string) {
    const question = texte.trim()
    if (!question || demander.isPending) return
    setMessages((m) => [...m, { role: 'moi', texte: question }])
    setSaisie('')
    demander.mutate(question)
  }

  useEffect(() => {
    if (!question || posee.current === question) return
    posee.current = question
    envoyer(question)
    // `envoyer` est stable pour la durée de vie du panneau.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [question])

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        ref={listeRef}
        contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 20 }}
        onContentSizeChange={() => listeRef.current?.scrollToEnd({ animated: true })}
        keyboardShouldPersistTaps="handled"
      >
        {messages.length === 0 && (
          <>
            <View style={styles.accueil}>
              <EmblemeAssistant taille={56} />
              <Text style={styles.accueilTitre}>Que cherchez-vous dans vos cahiers ?</Text>
              <Text style={styles.accueilTexte}>
                Une séance, un chapitre, un devoir donné — écrivez-le comme vous le diriez.
                L’assistant cite la séance d’où vient sa réponse. Il lit, il n’écrit jamais.
              </Text>
            </View>
            {SUGGESTIONS.map((s) => (
              <Pressable key={s.texte} onPress={() => envoyer(s.texte)} style={styles.suggestion}>
                <Ionicons name={s.icon} size={16} color={colors.primary} />
                <Text style={styles.suggestionTexte}>{s.texte}</Text>
              </Pressable>
            ))}
          </>
        )}

        {messages.map((m, i) => (
          <View key={i} style={m.role === 'moi' ? styles.bulleMoi : styles.bulleAssistant}>
            {m.role === 'assistant' && (
              <View style={styles.signature}>
                <Ionicons name="sparkles" size={12} color={colors.violet} />
                <Text style={styles.signatureTexte}>Assistant</Text>
              </View>
            )}
            {m.role === 'moi' ? (
              <Text style={styles.texteMoi}>{m.texte}</Text>
            ) : (
              <Markdown texte={m.texte} />
            )}
            {m.duree !== undefined && (
              <Text style={styles.duree}>{(m.duree / 1000).toFixed(1)} s</Text>
            )}
          </View>
        ))}

        {demander.isPending && (
          <View style={[styles.bulleAssistant, styles.enAttente]}>
            <ActivityIndicator size="small" color={colors.violet} />
            <Text style={styles.texteAssistant}>Recherche dans vos cahiers…</Text>
          </View>
        )}
      </ScrollView>

      <View style={styles.zoneSaisie}>
        <TextInput
          value={saisie}
          onChangeText={setSaisie}
          placeholder="Ex : quand ai-je traité les fractions en 71 ?"
          placeholderTextColor={colors.textMuted}
          style={styles.champSaisie}
          multiline
        />
        <Pressable
          onPress={() => envoyer(saisie)}
          disabled={!saisie.trim() || demander.isPending}
          style={[styles.envoyer, (!saisie.trim() || demander.isPending) && { opacity: 0.4 }]}
        >
          <Ionicons name="arrow-up" size={20} color={colors.white} />
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  )
}

const styles = StyleSheet.create({
  entete: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.navy,
    paddingHorizontal: 16,
    paddingBottom: 16,
    borderBottomLeftRadius: radius.xl,
    borderBottomRightRadius: radius.xl,
  },
  // Deux pastilles posées à même le fond, sans conteneur : un bloc gris plein
  // largeur pesait plus lourd que la distinction qu'il portait.
  segments: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 2,
  },
  segment: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 7,
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
  },
  segmentActif: { backgroundColor: colors.primary, borderColor: colors.primary },
  segmentTexte: { fontSize: 13, fontWeight: '600', color: colors.textMuted },
  embleme: {
    backgroundColor: colors.violet,
    alignItems: 'center',
    justifyContent: 'center',
  },
  titre: { color: colors.white, fontSize: 17, fontWeight: '800' },
  soustitre: { color: '#cbd5e1', fontSize: 12, marginTop: 2 },
  accueil: { alignItems: 'center', gap: 10, paddingVertical: 18 },
  accueilTitre: { fontSize: 16, fontWeight: '700', color: colors.text, textAlign: 'center' },
  accueilTexte: {
    fontSize: 13,
    color: colors.textMuted,
    textAlign: 'center',
    lineHeight: 19,
    paddingHorizontal: 8,
  },
  suggestion: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    borderRadius: radius.md,
    padding: 12,
  },
  suggestionTexte: { flex: 1, fontSize: 13, color: colors.text },
  bulleMoi: {
    alignSelf: 'flex-end',
    maxWidth: '85%',
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    padding: 12,
  },
  bulleAssistant: {
    alignSelf: 'flex-start',
    maxWidth: '92%',
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: 12,
  },
  enAttente: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  signature: { flexDirection: 'row', alignItems: 'center', gap: 5, marginBottom: 6 },
  signatureTexte: { fontSize: 11, fontWeight: '700', color: colors.violet },
  texteMoi: { color: colors.white, fontSize: 14, lineHeight: 20 },
  texteAssistant: { color: colors.text, fontSize: 14, lineHeight: 20 },
  duree: { marginTop: 6, fontSize: 10, color: colors.textMuted },
  zoneSaisie: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    padding: 12,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.white,
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
