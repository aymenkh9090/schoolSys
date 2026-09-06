import { useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
import type { CompositeScreenProps } from '@react-navigation/native'
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs'
import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'

import { appelApi, cahierApi, organisationApi } from '../api'
import type { SessionView, SignalementEleve } from '../api/types'
import { Loading, Notice } from '../components/ui'
import { getHost } from '../config'
import * as authStore from '../auth/authStore'
import {
  DAY_LABELS,
  SESSION_TYPE_LABELS,
  dayCodeOf,
  hhmm,
  isOngoing,
  nowMinutes,
  sessionsOfDay,
  todayIso,
  toMinutes,
} from '../planning'
import { colors, radius, shadow } from '../theme'
import { useMonPlanning } from '../useMonPlanning'
import { useOuvrirAppel } from '../useOuvrirAppel'
import type { RootStackParamList, TabParamList } from '../navigation'

type Props = CompositeScreenProps<
  BottomTabScreenProps<TabParamList, 'Accueil'>,
  NativeStackScreenProps<RootStackParamList>
>

/** Les six jours ouvrés d'un collège tunisien, dimanche exclu. */
const SEMAINE = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const RACCOURCIS_ASSISTANT = [
  { icon: 'document-text-outline' as const, titre: 'Résume ma\ndernière séance', question: "Résume ma dernière séance et ce qui reste à faire." },
  { icon: 'book-outline' as const, titre: 'Prépare le cahier\nde texte', question: "Aide-moi à rédiger le cahier de texte de mon cours d'aujourd'hui." },
]

function dateLongue(): string {
  return new Date().toLocaleDateString('fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

function heureCourante(): string {
  return new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
}

function initiales(nom: string): string {
  const parties = nom.trim().split(/\s+/)
  return ((parties[0]?.[0] ?? '') + (parties[1]?.[0] ?? '')).toUpperCase() || '?'
}

/** « ce matin », « cet après-midi », « hier », sinon la date. */
function momentDit(dateIso: string, heure?: string): string {
  if (dateIso.slice(0, 10) !== todayIso()) {
    const d = new Date(`${dateIso.slice(0, 10)}T00:00:00`)
    return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
  }
  if (!heure) return "aujourd'hui"
  return toMinutes(heure) < 12 * 60 ? 'ce matin' : 'cet après-midi'
}

/**
 * L'accueil : la journée d'un enseignant en un écran.
 *
 * L'ordre des cartes est celui de l'heure qui vient — faire l'appel, remplir le
 * cahier, demander à l'assistant — et non celui de l'architecture du logiciel.
 * Un enseignant ouvre l'application entre deux portes, avec trente élèves qui
 * attendent : la première chose sous le pouce doit être le bouton qu'il allait
 * chercher.
 *
 * Les absences y ont leur place, et pas au fond d'un écran de statistiques.
 * « Absents récents » montre ce que la classe traîne — y compris ce qu'un
 * collègue a signalé plus tôt — tant que la vie scolaire ne l'a pas justifié.
 */
export function AccueilScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets()
  const qc = useQueryClient()
  const [rafraichit, setRafraichit] = useState(false)
  const [question, setQuestion] = useState('')
  const { moi, moiLoading, moiError, publie, vue, vueLoading, seancesDuJour, refetch } =
    useMonPlanning()

  const { data: appelsDuJour = [] } = useQuery({
    queryKey: ['appels-du-jour', moi?.idEnseignant],
    queryFn: () => appelApi.lister({ date: todayIso(), enseignantId: moi!.idEnseignant }),
    enabled: !!moi,
  })

  const { data: classes = [] } = useQuery({
    queryKey: ['classes'],
    queryFn: organisationApi.classes,
    enabled: !!moi,
  })

  const maintenant = nowMinutes()

  /**
   * La séance qui compte : celle en cours, sinon la suivante, sinon la dernière
   * de la journée. C'est elle qui donne son contexte à tout l'écran — le
   * bandeau, le bouton d'appel, le cahier.
   */
  const seance: SessionView | undefined =
    seancesDuJour.find(isOngoing) ??
    seancesDuJour.find((s) => toMinutes(s.startTime) > maintenant) ??
    seancesDuJour[seancesDuJour.length - 1]

  const appelDeLaSeance = seance
    ? appelsDuJour.find((a) => a.seancePlanningId === seance.id)
    : undefined

  const classeId =
    appelDeLaSeance?.groupeClasseId ?? classes.find((c) => c.code === seance?.classCode)?.idClasse

  const { data: eleves = [] } = useQuery({
    queryKey: ['eleves', classeId],
    queryFn: () => organisationApi.elevesDeClasse(classeId!),
    enabled: !!classeId,
  })

  // Ce que la classe traîne, tous professeurs confondus, tant que la vie
  // scolaire n'a pas justifié.
  const { data: signalements = [] } = useQuery({
    queryKey: ['signalements', classeId],
    queryFn: () => appelApi.signalements(classeId!),
    enabled: !!classeId,
  })

  // 404 tant que rien n'est saisi : c'est l'état normal en début de séance, pas
  // une panne, et surtout pas une raison de réessayer.
  const { data: cahier } = useQuery({
    queryKey: ['cahier', appelDeLaSeance?.id],
    queryFn: () => cahierApi.recuperer(appelDeLaSeance!.id),
    enabled: !!appelDeLaSeance,
    retry: false,
  })

  const { ouvrir } = useOuvrirAppel(moi?.idEnseignant, (appelId, titre, groupeClasseId) =>
    navigation.navigate('FeuilleAppel', { appelId, titre, groupeClasseId })
  )

  function seDeconnecter() {
    Alert.alert('Se déconnecter ?', 'La session sera fermée sur ce téléphone.', [
      { text: 'Annuler', style: 'cancel' },
      { text: 'Se déconnecter', style: 'destructive', onPress: () => void authStore.logout() },
    ])
  }

  function lancerAppel() {
    if (appelDeLaSeance && seance) {
      navigation.navigate('FeuilleAppel', {
        appelId: appelDeLaSeance.id,
        titre: `${seance.classCode} · ${seance.subjectName}`,
        groupeClasseId: appelDeLaSeance.groupeClasseId,
      })
      return
    }
    if (seance) {
      ouvrir.mutate(seance)
      return
    }
    navigation.navigate('Appel')
  }

  function demanderAssistant(texte: string) {
    const propre = texte.trim()
    if (!propre) return
    setQuestion('')
    navigation.navigate('Assistant', { question: propre })
  }

  function nomEleve(id: number): string {
    const e = eleves.find((x) => x.idEleve === id)
    return e ? `${e.nom} ${e.prenom}` : `Élève #${id}`
  }

  const presents = appelDeLaSeance?.lignesAppel.filter((l) => l.statut === 'PRESENT').length ?? 0
  const absents =
    appelDeLaSeance?.lignesAppel.filter((l) => l.statut === 'ABSENT' || l.statut === 'EXCLU')
      .length ?? 0

  const dernierAppel = [...appelsDuJour].sort((a, b) =>
    (b.ouvertureAt ?? '').localeCompare(a.ouvertureAt ?? '')
  )[0]

  // Un signalement par élève : le plus récent. Répéter le même élève trois fois
  // remplirait la carte sans rien apprendre de plus.
  const recents: SignalementEleve[] = Object.values(
    signalements.reduce<Record<number, SignalementEleve>>((acc, s) => {
      const garde = acc[s.eleveId]
      if (!garde || s.dateSeance > garde.dateSeance) acc[s.eleveId] = s
      return acc
    }, {})
  ).sort((a, b) => b.dateSeance.localeCompare(a.dateSeance))

  const contexte = seance
    ? `${seance.classCode} · ${seance.subjectName}`
    : (moi?.specialite ?? 'Enseignant')

  /**
   * Les classes dont l'enseignant a la charge, regroupées par classe.
   *
   * Une même classe revient autant de fois qu'il y enseigne de matières : les
   * lister à plat afficherait « 7B » quatre fois. On regroupe, et la matière
   * devient le détail.
   */
  const mesClasses = Object.values(
    (moi?.affectations ?? [])
      .filter((a) => a.isActive)
      .reduce<Record<string, { code: string; niveau: string; effectif: number; matieres: string[] }>>(
        (acc, a) => {
          const entree = acc[a.classGroupCode] ?? {
            code: a.classGroupCode,
            niveau: a.levelNom,
            effectif: a.classGroupNbEleve,
            matieres: [],
          }
          if (!entree.matieres.includes(a.subjectLib)) entree.matieres.push(a.subjectLib)
          acc[a.classGroupCode] = entree
          return acc
        },
        {}
      )
  ).sort((a, b) => a.code.localeCompare(b.code))

  /** Charge de la semaine, jour par jour — la même vue que l'écran planning. */
  const parJour = SEMAINE.map((jour) => ({ jour, nombre: sessionsOfDay(vue, jour).length }))
  const seancesSemaine = parJour.reduce((n, j) => n + j.nombre, 0)
  const maxJour = Math.max(1, ...parJour.map((j) => j.nombre))
  const aujourdhui = dayCodeOf()

  return (
    <ScrollView
      style={{ backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: 28 }}
      keyboardShouldPersistTaps="handled"
      refreshControl={
        <RefreshControl
          refreshing={rafraichit}
          tintColor={colors.primary}
          onRefresh={() => {
            setRafraichit(true)
            refetch()
            void qc.invalidateQueries().finally(() => setRafraichit(false))
          }}
        />
      }
    >
      {/* ── Bandeau ─────────────────────────────────────────────────────── */}
      <View style={[styles.entete, { paddingTop: insets.top + 14 }]}>
        <View style={styles.avatar}>
          <Ionicons name="school" size={24} color={colors.white} />
        </View>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={styles.bonjour} numberOfLines={1}>
            Bonjour {moi?.nomComplet ?? authStore.displayName()}
          </Text>
          <Text style={styles.contexte} numberOfLines={1}>
            {contexte}
          </Text>
        </View>
        <Pressable
          onPress={() => navigation.navigate('Appel')}
          hitSlop={10}
          style={styles.cloche}
          accessibilityLabel={
            recents.length > 0
              ? `${recents.length} absence(s) non justifiée(s) dans la classe`
              : 'Aucune absence en attente'
          }
        >
          <Ionicons name="notifications-outline" size={20} color={colors.primary} />
          {recents.length > 0 && <View style={styles.pastilleCloche} />}
        </Pressable>

        {/* La déconnexion vit DANS le bandeau, hors de toute condition.
            Placée plus bas dans la page, elle disparaissait précisément
            lorsqu'on en a le plus besoin : un compte sans fiche enseignant
            n'affiche que son message d'erreur, et l'utilisateur restait
            enfermé dans une session qu'il ne pouvait plus quitter. */}
        <Pressable
          onPress={seDeconnecter}
          hitSlop={10}
          style={styles.quitter}
          accessibilityLabel="Se déconnecter"
        >
          <Ionicons name="log-out-outline" size={20} color={colors.danger} />
        </Pressable>
      </View>

      <View style={styles.ligneDate}>
        <Ionicons name="calendar-outline" size={15} color={colors.primary} />
        <Text style={styles.dateTexte}>{dateLongue()}</Text>
        <Text style={styles.point}>•</Text>
        <Ionicons name="time-outline" size={15} color={colors.primary} />
        <Text style={styles.dateTexte}>{heureCourante()}</Text>
      </View>

      <View style={{ padding: 16, gap: 14 }}>
        {moiLoading ? (
          <Loading text="Chargement de votre fiche…" />
        ) : moiError ? (
          <Notice
            tone="error"
            text="Aucune fiche enseignant n'est rattachée à ce compte. L'application mobile s'adresse aux enseignants ; les autres rôles passent par le web."
          />
        ) : (
          <>
            {/* ── Faire l'appel ──────────────────────────────────────── */}
            <View style={styles.carte}>
              <Pressable style={styles.carteEntete} onPress={() => navigation.navigate('Appel')}>
                <View style={[styles.carteIcone, { backgroundColor: colors.primarySoft }]}>
                  <Ionicons name="people" size={20} color={colors.primary} />
                </View>
                <Text style={styles.carteTitre}>Faire l’appel</Text>
                <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
              </Pressable>

              {!publie ? (
                <Notice text="Aucun emploi du temps publié : vos séances apparaîtront ici dès la publication." />
              ) : vueLoading ? (
                <Loading />
              ) : (
                <>
                  {seance && (
                    <Text style={styles.seanceLigne}>
                      {hhmm(seance.startTime)}–{hhmm(seance.endTime)} · {seance.classCode} ·{' '}
                      {SESSION_TYPE_LABELS[seance.sessionType] ?? seance.sessionType}
                      {seance.roomCode ? ` · ${seance.roomCode}` : ''}
                    </Text>
                  )}

                  <Pressable
                    onPress={lancerAppel}
                    disabled={ouvrir.isPending}
                    style={({ pressed }) => [
                      styles.grandBouton,
                      (pressed || ouvrir.isPending) && { opacity: 0.75 },
                    ]}
                  >
                    <Ionicons name="clipboard-outline" size={19} color={colors.white} />
                    <Text style={styles.grandBoutonTexte}>
                      {ouvrir.isPending
                        ? 'Ouverture…'
                        : appelDeLaSeance
                        ? "Reprendre l'appel"
                        : "Lancer l'appel"}
                    </Text>
                  </Pressable>

                  {appelDeLaSeance ? (
                    <View style={styles.compteurs}>
                      <View style={styles.compteur}>
                        <Ionicons name="checkmark-circle" size={17} color={colors.success} />
                        <Text style={styles.compteurTexte}>{presents} présents</Text>
                      </View>
                      <View style={styles.compteur}>
                        <Ionicons name="remove-circle" size={17} color={colors.warning} />
                        <Text style={styles.compteurTexte}>{absents} absents</Text>
                      </View>
                    </View>
                  ) : (
                    <Text style={styles.aideCarte}>
                      L’appel de cette séance n’est pas encore ouvert.
                    </Text>
                  )}
                </>
              )}

              {/* ── Absents récents ────────────────────────────────────
                  Le cœur du suivi : ce qui vient d'un collègue est marqué comme
                  tel, et reste affiché tant que la vie scolaire n'a pas
                  justifié. Aucun geste ici ne le fait disparaître. */}
              <View style={styles.separateurH} />
              <View style={styles.sousEntete}>
                <Text style={styles.sousTitre}>Absents non justifiés</Text>
                {recents.length > 3 && (
                  <Pressable onPress={() => navigation.navigate('Appel')}>
                    <Text style={styles.voirTout}>Voir tout</Text>
                  </Pressable>
                )}
              </View>

              {!classeId ? (
                <Text style={styles.aideCarte}>
                  Le suivi s’affiche dès qu’une séance du jour est identifiée.
                </Text>
              ) : recents.length === 0 ? (
                <Text style={styles.aideCarte}>
                  Rien en attente pour cette classe : toutes les absences sont justifiées.
                </Text>
              ) : (
                recents.slice(0, 3).map((s) => (
                  <View key={s.ligneAppelId} style={styles.ligneEleve}>
                    <View style={styles.pastilleEleve}>
                      <Text style={styles.pastilleEleveTexte}>{initiales(nomEleve(s.eleveId))}</Text>
                    </View>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={styles.nomEleve} numberOfLines={1}>
                        {nomEleve(s.eleveId)}
                      </Text>
                      <Text style={styles.detailEleve} numberOfLines={1}>
                        {momentDit(s.dateSeance, s.heureDebut)}
                        {s.matiere ? ` · ${s.matiere}` : ''}
                        {s.enseignant ? ` · ${s.enseignant}` : ''}
                      </Text>
                    </View>
                    <View
                      style={[
                        styles.etiquette,
                        { backgroundColor: s.statut === 'EXCLU' ? colors.exclu : colors.warning },
                      ]}
                    >
                      <Text style={styles.etiquetteTexte}>
                        {s.statut === 'EXCLU' ? 'Exclu' : 'Absent'}
                      </Text>
                    </View>
                  </View>
                ))
              )}

              {dernierAppel && (
                <View style={styles.pied}>
                  <Ionicons name="time-outline" size={13} color={colors.textMuted} />
                  <Text style={styles.piedTexte}>
                    Dernier appel : aujourd’hui à{' '}
                    {new Date(dernierAppel.ouvertureAt).toLocaleTimeString('fr-FR', {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </Text>
                </View>
              )}
            </View>

            {/* ── Cahier de texte ────────────────────────────────────── */}
            <View style={styles.carte}>
              <Pressable style={styles.carteEntete} onPress={() => navigation.navigate('Cahier')}>
                <View style={[styles.carteIcone, { backgroundColor: '#ede9fe' }]}>
                  <Ionicons name="book" size={19} color={colors.primaryDark} />
                </View>
                <Text style={styles.carteTitre}>Cahier de texte</Text>
                <View
                  style={[
                    styles.badgeEtat,
                    cahier?.sujet ? { backgroundColor: '#dcfce7' } : { backgroundColor: '#ffedd5' },
                  ]}
                >
                  <Text
                    style={[
                      styles.badgeEtatTexte,
                      { color: cahier?.sujet ? '#15803d' : '#c2410c' },
                    ]}
                  >
                    {cahier?.sujet ? 'Rempli' : 'À compléter'}
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
              </Pressable>

              <Text style={styles.aideCarte}>Cours d’aujourd’hui</Text>
              <Text style={styles.cahierSujet} numberOfLines={2}>
                {cahier?.sujet || seance?.subjectName || 'Aucune séance identifiée'}
              </Text>
              {!!cahier?.activites && (
                <Text style={styles.cahierTexte} numberOfLines={3}>
                  {cahier.activites}
                </Text>
              )}

              <Pressable
                style={styles.lienCarte}
                onPress={() =>
                  appelDeLaSeance && seance
                    ? navigation.navigate('CahierSeance', {
                        appelId: appelDeLaSeance.id,
                        titre: `${seance.classCode} · ${seance.subjectName}`,
                      })
                    : navigation.navigate('Cahier')
                }
              >
                <Ionicons name="create-outline" size={16} color={colors.primary} />
                <Text style={styles.lienCarteTexte}>Remplir le cahier de texte</Text>
                <Ionicons name="chevron-forward" size={15} color={colors.primary} />
              </Pressable>
            </View>

            {/* ── Assistant IA ───────────────────────────────────────── */}
            <View style={styles.carteAssistant}>
              <View style={styles.assistantEntete}>
                <Ionicons name="sparkles" size={18} color={colors.white} />
                <Text style={styles.assistantTitre}>Assistant IA</Text>
                <Pressable
                  onPress={() => navigation.navigate('Assistant')}
                  hitSlop={10}
                  style={styles.assistantBoutonIcone}
                >
                  <Ionicons name="create-outline" size={16} color={colors.white} />
                </Pressable>
              </View>
              <Text style={styles.assistantSlogan}>
                Gagnez du temps, je suis là pour vous aider ✨
              </Text>

              <View style={styles.raccourcis}>
                {RACCOURCIS_ASSISTANT.map((r) => (
                  <Pressable
                    key={r.titre}
                    onPress={() => demanderAssistant(r.question)}
                    style={({ pressed }) => [styles.raccourci, pressed && { opacity: 0.75 }]}
                  >
                    <Ionicons name={r.icon} size={17} color={colors.white} />
                    <Text style={styles.raccourciTexte}>{r.titre}</Text>
                  </Pressable>
                ))}
              </View>

              <View style={styles.zoneSaisie}>
                <TextInput
                  value={question}
                  onChangeText={setQuestion}
                  placeholder="Demandez à l’assistant…"
                  placeholderTextColor="#c4b5fd"
                  style={styles.champ}
                  onSubmitEditing={() => demanderAssistant(question)}
                  returnKeyType="send"
                />
                <Pressable
                  onPress={() => demanderAssistant(question)}
                  style={styles.envoyer}
                  accessibilityLabel="Envoyer à l’assistant"
                >
                  <Ionicons name="send" size={16} color={colors.white} />
                </Pressable>
              </View>
            </View>

            {/* ── Consultation ───────────────────────────────────────── */}
            <View style={styles.raccourcisBas}>
              <Pressable
                style={({ pressed }) => [styles.tuile, pressed && { opacity: 0.75 }]}
                onPress={() => navigation.navigate('Planning')}
              >
                <Ionicons name="calendar-outline" size={19} color={colors.primary} />
                <Text style={styles.tuileTitre}>Mon emploi du temps</Text>
              </Pressable>
              <Pressable
                style={({ pressed }) => [styles.tuile, pressed && { opacity: 0.75 }]}
                onPress={() => navigation.navigate('Classes')}
              >
                <Ionicons name="people-outline" size={19} color={colors.primary} />
                <Text style={styles.tuileTitre}>Mes classes</Text>
              </Pressable>
            </View>

            {/* ── Mon compte ─────────────────────────────────────────
                La déconnexion a sa propre carte, avec un bouton plein et non
                un lien gris : c'est un geste rare mais qu'on cherche
                activement — prêter son téléphone, changer de session en salle
                des profs — et un lien discret en bas de page se cherche
                justement trop longtemps. */}
            <View style={styles.carte}>
              <View style={styles.carteEntete}>
                <View style={[styles.carteIcone, { backgroundColor: colors.primarySoft }]}>
                  <Ionicons name="person" size={19} color={colors.primary} />
                </View>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={styles.compteNom} numberOfLines={1}>
                    {moi?.nomComplet ?? authStore.displayName()}
                  </Text>
                  <Text style={styles.compteDetail} numberOfLines={1}>
                    {moi?.codeEnseignant ? `${moi.codeEnseignant} · ` : ''}
                    {moi?.specialite ? `${moi.specialite} · ` : ''}serveur {getHost()}
                  </Text>
                </View>
              </View>

              {/* ── Mes classes ─────────────────────────────────────
                  Ce que l'enseignant cherche dans son profil, ce n'est pas son
                  identité — il la connaît — mais son périmètre : quelles
                  classes, quelles matières, combien d'élèves. */}
              <View style={styles.separateurH} />
              <Text style={styles.sousTitre}>
                Mes classes {mesClasses.length > 0 ? `(${mesClasses.length})` : ''}
              </Text>
              {mesClasses.length === 0 ? (
                <Text style={styles.aideCarte}>
                  Aucune affectation active pour l’année en cours.
                </Text>
              ) : (
                mesClasses.map((c) => (
                  <View key={c.code} style={styles.ligneClasse}>
                    <View style={styles.jetonClasse}>
                      <Text style={styles.jetonClasseTexte}>{c.code}</Text>
                    </View>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={styles.classeMatieres} numberOfLines={1}>
                        {c.matieres.join(', ')}
                      </Text>
                      <Text style={styles.classeDetail} numberOfLines={1}>
                        {c.niveau}
                        {c.effectif ? ` · ${c.effectif} élèves` : ''}
                      </Text>
                    </View>
                  </View>
                ))
              )}

              {/* ── Mon planning ────────────────────────────────────
                  Six barres valent mieux qu'une grille : on veut savoir où la
                  semaine pèse, pas relire l'emploi du temps case par case —
                  celui-ci est à un doigt, juste en dessous. */}
              <View style={styles.separateurH} />
              <Text style={styles.sousTitre}>Mon planning</Text>
              {!publie ? (
                <Text style={styles.aideCarte}>
                  Aucun emploi du temps publié pour le moment.
                </Text>
              ) : (
                <>
                  <View style={styles.semaine}>
                    {parJour.map(({ jour, nombre }) => {
                      const actif = jour === aujourdhui
                      return (
                        <View key={jour} style={styles.colonneJour}>
                          <Text style={[styles.nombreJour, actif && { color: colors.primary }]}>
                            {nombre}
                          </Text>
                          <View style={styles.rail}>
                            <View
                              style={[
                                styles.barre,
                                {
                                  height: `${Math.max(6, (nombre / maxJour) * 100)}%`,
                                  backgroundColor: actif ? colors.primary : colors.primaryLight,
                                  opacity: nombre === 0 ? 0.25 : 1,
                                },
                              ]}
                            />
                          </View>
                          <Text style={[styles.libelleJour, actif && { color: colors.primary, fontWeight: '800' }]}>
                            {DAY_LABELS[jour].slice(0, 3)}
                          </Text>
                        </View>
                      )
                    })}
                  </View>
                  <Text style={styles.aideCarte}>
                    {seancesSemaine} séance{seancesSemaine > 1 ? 's' : ''} cette semaine ·{' '}
                    {seancesDuJour.length} aujourd’hui
                  </Text>
                </>
              )}

              <Pressable style={styles.lienCarte} onPress={() => navigation.navigate('Planning')}>
                <Ionicons name="calendar-outline" size={16} color={colors.primary} />
                <Text style={styles.lienCarteTexte}>Voir mon emploi du temps</Text>
                <Ionicons name="chevron-forward" size={15} color={colors.primary} />
              </Pressable>

              <Pressable
                onPress={seDeconnecter}
                style={({ pressed }) => [styles.deconnexion, pressed && { opacity: 0.75 }]}
              >
                <Ionicons name="log-out-outline" size={17} color={colors.white} />
                <Text style={styles.deconnexionTexte}>Se déconnecter</Text>
              </Pressable>
            </View>
          </>
        )}
      </View>
    </ScrollView>
  )
}

const styles = StyleSheet.create({
  entete: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.white,
    paddingHorizontal: 16,
    paddingBottom: 10,
  },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: 18,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bonjour: { fontSize: 20, fontWeight: '800', color: colors.text },
  contexte: { fontSize: 13, color: colors.textMuted, marginTop: 2 },
  cloche: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pastilleCloche: {
    position: 'absolute',
    top: 8,
    right: 9,
    width: 9,
    height: 9,
    borderRadius: 5,
    backgroundColor: colors.danger,
    borderWidth: 1.5,
    borderColor: colors.white,
  },
  quitter: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#fecaca',
    backgroundColor: '#fef2f2',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ligneDate: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: colors.white,
    paddingHorizontal: 16,
    paddingBottom: 14,
  },
  dateTexte: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.text,
    textTransform: 'capitalize',
  },
  point: { color: colors.textMuted, marginHorizontal: 2 },

  carte: {
    backgroundColor: colors.white,
    borderRadius: radius.xl,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 15,
    gap: 10,
    ...shadow,
  },
  carteEntete: { flexDirection: 'row', alignItems: 'center', gap: 11 },
  carteIcone: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  carteTitre: { flex: 1, fontSize: 17, fontWeight: '800', color: colors.text },
  badgeEtat: { borderRadius: 999, paddingHorizontal: 9, paddingVertical: 3 },
  badgeEtatTexte: { fontSize: 11, fontWeight: '700' },
  seanceLigne: { fontSize: 12, color: colors.textMuted, fontVariant: ['tabular-nums'] },

  grandBouton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 9,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingVertical: 15,
  },
  grandBoutonTexte: { color: colors.white, fontSize: 16, fontWeight: '800' },

  compteurs: { flexDirection: 'row', gap: 18, paddingVertical: 2 },
  compteur: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  compteurTexte: { fontSize: 13, fontWeight: '600', color: colors.text },
  aideCarte: { fontSize: 12, color: colors.textMuted, lineHeight: 17 },

  separateurH: { height: 1, backgroundColor: colors.border, marginTop: 2 },
  sousEntete: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sousTitre: { fontSize: 14, fontWeight: '800', color: colors.text },
  voirTout: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.primary,
    backgroundColor: colors.primarySoft,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
    overflow: 'hidden',
  },

  ligneEleve: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  pastilleEleve: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.bgSecondary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pastilleEleveTexte: { fontSize: 12, fontWeight: '800', color: colors.textMuted },
  nomEleve: { fontSize: 14, fontWeight: '700', color: colors.text },
  detailEleve: { fontSize: 11, color: colors.textMuted, marginTop: 1 },
  etiquette: { borderRadius: 999, paddingHorizontal: 11, paddingVertical: 5 },
  etiquetteTexte: { color: colors.white, fontSize: 12, fontWeight: '700' },

  pied: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 2 },
  piedTexte: { fontSize: 11, color: colors.textMuted },

  cahierSujet: { fontSize: 15, fontWeight: '700', color: colors.text },
  cahierTexte: { fontSize: 12, color: colors.textMuted, lineHeight: 18 },
  lienCarte: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 11,
    marginTop: 2,
  },
  lienCarteTexte: { flex: 1, fontSize: 14, fontWeight: '700', color: colors.primary },

  carteAssistant: {
    backgroundColor: colors.primary,
    borderRadius: radius.xl,
    padding: 16,
    gap: 12,
    ...shadow,
  },
  assistantEntete: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  assistantTitre: { flex: 1, color: colors.white, fontSize: 17, fontWeight: '800' },
  assistantBoutonIcone: {
    width: 32,
    height: 32,
    borderRadius: radius.sm,
    backgroundColor: '#ffffff2e',
    alignItems: 'center',
    justifyContent: 'center',
  },
  assistantSlogan: {
    color: '#ede9fe',
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 19,
  },
  raccourcis: { flexDirection: 'row', gap: 10 },
  raccourci: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#ffffff26',
    borderRadius: radius.md,
    paddingVertical: 11,
    paddingHorizontal: 11,
  },
  raccourciTexte: { flex: 1, color: colors.white, fontSize: 11, fontWeight: '700', lineHeight: 15 },
  zoneSaisie: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#ffffff1f',
    borderRadius: 999,
    paddingLeft: 16,
    paddingRight: 5,
    paddingVertical: 5,
  },
  champ: { flex: 1, color: colors.white, fontSize: 14, paddingVertical: 8 },
  envoyer: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.primaryDark,
    alignItems: 'center',
    justifyContent: 'center',
  },

  raccourcisBas: { flexDirection: 'row', gap: 12 },
  tuile: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    paddingVertical: 13,
    paddingHorizontal: 12,
    ...shadow,
  },
  tuileTitre: { flex: 1, fontSize: 12, fontWeight: '700', color: colors.text },
  compteNom: { fontSize: 15, fontWeight: '800', color: colors.text },
  ligneClasse: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  jetonClasse: {
    minWidth: 44,
    borderRadius: radius.sm,
    backgroundColor: colors.primarySoft,
    paddingVertical: 6,
    paddingHorizontal: 9,
    alignItems: 'center',
  },
  jetonClasseTexte: { fontSize: 13, fontWeight: '800', color: colors.primary },
  classeMatieres: { fontSize: 13, fontWeight: '700', color: colors.text },
  classeDetail: { fontSize: 11, color: colors.textMuted, marginTop: 1 },
  semaine: { flexDirection: 'row', gap: 8, alignItems: 'flex-end', paddingVertical: 2 },
  colonneJour: { flex: 1, alignItems: 'center', gap: 4 },
  nombreJour: { fontSize: 12, fontWeight: '800', color: colors.textMuted },
  rail: {
    width: '100%',
    height: 46,
    borderRadius: radius.sm,
    backgroundColor: colors.bgSecondary,
    justifyContent: 'flex-end',
    overflow: 'hidden',
  },
  barre: { width: '100%', borderRadius: radius.sm },
  libelleJour: { fontSize: 10, fontWeight: '600', color: colors.textMuted },
  compteDetail: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  deconnexion: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: colors.danger,
    borderRadius: radius.md,
    paddingVertical: 13,
  },
  deconnexionTexte: { fontSize: 14, fontWeight: '800', color: colors.white },
})
