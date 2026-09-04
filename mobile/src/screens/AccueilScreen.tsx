import { useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
import type { CompositeScreenProps } from '@react-navigation/native'
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs'
import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'

import { appelApi } from '../api'
import { Badge, Button, Card, Loading, Notice, SectionTitle, Stat, Tile } from '../components/ui'
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
import { getHost } from '../config'
import { colors, radius } from '../theme'
import { useMonPlanning } from '../useMonPlanning'
import { useOuvrirAppel } from '../useOuvrirAppel'
import type { RootStackParamList, TabParamList } from '../navigation'

type Props = CompositeScreenProps<
  BottomTabScreenProps<TabParamList, 'Accueil'>,
  NativeStackScreenProps<RootStackParamList>
>

const SEMAINE = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

function dateLongue(): string {
  return new Date().toLocaleDateString('fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}

export function AccueilScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets()
  const qc = useQueryClient()
  const [rafraichit, setRafraichit] = useState(false)
  const { moi, moiLoading, moiError, publie, vue, vueLoading, seancesDuJour, refetch } =
    useMonPlanning()

  const { data: appelsDuJour = [] } = useQuery({
    queryKey: ['appels-du-jour', moi?.idEnseignant],
    queryFn: () => appelApi.lister({ date: todayIso(), enseignantId: moi!.idEnseignant }),
    enabled: !!moi,
  })

  const { ouvrir } = useOuvrirAppel(moi?.idEnseignant, (appelId, titre) =>
    navigation.navigate('Appel', { appelId, titre })
  )

  function seDeconnecter() {
    Alert.alert('Se déconnecter ?', 'La session sera fermée sur ce téléphone.', [
      { text: 'Annuler', style: 'cancel' },
      { text: 'Se déconnecter', style: 'destructive', onPress: () => void authStore.logout() },
    ])
  }

  const affectations = (moi?.affectations ?? []).filter((a) => a.isActive)
  const mesClasses = Array.from(new Set(affectations.map((a) => a.classGroupCode)))
  const seancesSemaine = SEMAINE.reduce((n, j) => n + sessionsOfDay(vue, j).length, 0)

  const maintenant = nowMinutes()
  const prochaine = seancesDuJour.find((s) => toMinutes(s.startTime) > maintenant)
  // Une séance dont l'appel est déjà ouvert ne se rouvre pas : on y retourne.
  const appelDe = (planningId: number) =>
    appelsDuJour.find((a) => a.seancePlanningId === planningId)

  return (
    <ScrollView
      style={{ backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: 28 }}
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
      <View style={[styles.bandeau, { paddingTop: insets.top + 16 }]}>
        <View style={styles.bandeauHaut}>
          <View style={styles.avatar}>
            <Text style={styles.avatarTexte}>
              {(moi?.nom?.[0] ?? '') + (moi?.prenom?.[0] ?? '') || 'ES'}
            </Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.bonjour} numberOfLines={1}>
              {moi?.nomComplet ?? authStore.displayName()}
            </Text>
            <Text style={styles.sousBonjour}>
              {dateLongue()}
              {moi?.codeEnseignant ? ` · ${moi.codeEnseignant}` : ''}
            </Text>
          </View>
          <Pressable onPress={seDeconnecter} hitSlop={12} style={styles.quitter}>
            <Ionicons name="log-out-outline" size={16} color={colors.white} />
            <Text style={styles.quitterTexte}>Quitter</Text>
          </Pressable>
        </View>

        <View style={styles.stats}>
          <Stat valeur={seancesDuJour.length} libelle="cours aujourd'hui" />
          <View style={styles.separateur} />
          <Stat valeur={mesClasses.length} libelle="classes" />
          <View style={styles.separateur} />
          <Stat valeur={seancesSemaine} libelle="séances / semaine" />
        </View>
      </View>

      <View style={{ padding: 16, gap: 16 }}>
        {moiLoading ? (
          <Loading text="Chargement de votre fiche…" />
        ) : moiError ? (
          <Notice
            tone="error"
            text="Aucune fiche enseignant n'est rattachée à ce compte. L'application mobile s'adresse aux enseignants ; les autres rôles passent par le web."
          />
        ) : (
          <>
            {/* ── Actions ─────────────────────────────────────────────── */}
            <SectionTitle titre="Que voulez-vous faire ?" icon="flash-outline" />
            <View style={styles.grille}>
              <Tile
                icon="clipboard-outline"
                titre="Faire l'appel"
                detail="Présences de la séance en cours"
                onPress={() => navigation.navigate('Seances', { destination: 'appel' })}
              />
              <Tile
                icon="book-outline"
                titre="Cahier de séance"
                detail="Consigner ce qui a été fait"
                couleur={colors.primaryDark}
                onPress={() => navigation.navigate('Seances', { destination: 'cahier' })}
              />
              <Tile
                icon="sparkles-outline"
                titre="Assistant IA"
                detail="Interroger vos cahiers en français"
                couleur={colors.violet}
                onPress={() => navigation.navigate('Assistant')}
              />
              <Tile
                icon="document-text-outline"
                titre="Préparer un cours"
                detail="Joindre un PDF et demander ce qu'on veut"
                couleur={colors.violet}
                onPress={() => navigation.navigate('Assistant')}
              />
              <Tile
                icon="calendar-outline"
                titre="Mon emploi du temps"
                detail="La semaine, jour par jour"
                couleur={colors.primaryLight}
                onPress={() => navigation.navigate('Planning')}
              />
              <Tile
                icon="people-outline"
                titre="Mes classes"
                detail={
                  mesClasses.length
                    ? `${mesClasses.join(', ')} · ${affectations.length} affectation(s)`
                    : 'Classes et élèves dont j’ai la charge'
                }
                couleur={colors.navy}
                onPress={() => navigation.navigate('Classes')}
              />
            </View>

            {/* ── Cours du jour ───────────────────────────────────────── */}
            <SectionTitle titre={`Mes cours — ${DAY_LABELS[dayCodeOf()]}`} icon="today-outline" />

            {!publie ? (
              <Notice text="Aucun emploi du temps publié pour le moment — vos cours apparaîtront ici dès la publication." />
            ) : vueLoading ? (
              <Loading />
            ) : seancesDuJour.length === 0 ? (
              <Notice text={`Aucun cours prévu aujourd'hui (${DAY_LABELS[dayCodeOf()]}).`} />
            ) : (
              seancesDuJour.map((s) => {
                const enCours = isOngoing(s)
                const passee = toMinutes(s.endTime) <= maintenant
                const deja = appelDe(s.id)
                return (
                  <Card
                    key={s.id}
                    style={[
                      enCours && { borderColor: colors.primaryLight, backgroundColor: colors.primarySoft },
                      passee && !enCours && !deja && { opacity: 0.55 },
                    ]}
                  >
                    <View style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
                      <View style={styles.creneau}>
                        <Text style={styles.heureDebut}>{hhmm(s.startTime)}</Text>
                        <Text style={styles.heureFin}>{hhmm(s.endTime)}</Text>
                      </View>
                      <View style={{ flex: 1, gap: 4 }}>
                        <View style={styles.ligneTitre}>
                          <Text style={styles.matiere}>{s.subjectName}</Text>
                          {enCours && <Badge text="En cours" color={colors.success} />}
                          {!enCours && prochaine?.id === s.id && (
                            <Badge text="Prochaine" color={colors.primary} />
                          )}
                          {deja && <Badge text="Appel ouvert" color={colors.primaryDark} />}
                        </View>
                        <Text style={styles.details}>
                          {s.classCode}
                          {s.roomCode ? ` · ${s.roomCode}` : ''} ·{' '}
                          {SESSION_TYPE_LABELS[s.sessionType] ?? s.sessionType}
                          {s.groupLabel ? ` · ${s.groupLabel}` : ''}
                        </Text>
                      </View>
                    </View>
                    <View style={{ marginTop: 12 }}>
                      <Button
                        title={deja ? "Reprendre l'appel" : "Faire l'appel"}
                        variant={enCours || deja ? 'primary' : 'outline'}
                        loading={ouvrir.isPending && ouvrir.variables?.id === s.id}
                        onPress={() =>
                          deja
                            ? navigation.navigate('Appel', {
                                appelId: deja.id,
                                titre: `${s.classCode} · ${s.subjectName}`,
                              })
                            : ouvrir.mutate(s)
                        }
                      />
                    </View>
                  </Card>
                )
              })
            )}
            <SectionTitle titre="Mon compte" icon="person-circle-outline" />
            <Card>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <View style={styles.compteIcone}>
                  <Ionicons name="person" size={18} color={colors.primary} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.compteNom}>{moi?.nomComplet ?? authStore.displayName()}</Text>
                  <Text style={styles.compteDetail}>
                    {moi?.specialite ? `${moi.specialite} · ` : ''}serveur {getHost()}
                  </Text>
                </View>
              </View>
              <View style={{ marginTop: 12 }}>
                <Button title="Se déconnecter" variant="danger" onPress={seDeconnecter} />
              </View>
            </Card>
          </>
        )}
      </View>
    </ScrollView>
  )
}

const styles = StyleSheet.create({
  bandeau: {
    backgroundColor: colors.navy,
    paddingHorizontal: 16,
    paddingBottom: 6,
    borderBottomLeftRadius: radius.xl,
    borderBottomRightRadius: radius.xl,
  },
  bandeauHaut: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarTexte: { color: colors.white, fontSize: 16, fontWeight: '800' },
  bonjour: { color: colors.white, fontSize: 18, fontWeight: '800' },
  sousBonjour: { color: '#cbd5e1', fontSize: 12, marginTop: 2, textTransform: 'capitalize' },
  quitter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#ffffff20',
  },
  quitterTexte: { fontSize: 12, fontWeight: '700', color: colors.white },
  compteIcone: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  compteNom: { fontSize: 14, fontWeight: '700', color: colors.text },
  compteDetail: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  stats: { flexDirection: 'row', alignItems: 'center', marginTop: 10 },
  separateur: { width: 1, height: 26, backgroundColor: '#ffffff22' },
  grille: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  creneau: {
    width: 62,
    borderRadius: radius.sm,
    backgroundColor: colors.bgSecondary,
    paddingVertical: 6,
    alignItems: 'center',
  },
  heureDebut: { fontSize: 15, fontWeight: '800', color: colors.text, fontVariant: ['tabular-nums'] },
  heureFin: { fontSize: 11, color: colors.textMuted, fontVariant: ['tabular-nums'] },
  ligneTitre: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  matiere: { fontSize: 15, fontWeight: '700', color: colors.text },
  details: { fontSize: 12, color: colors.textMuted },
})
