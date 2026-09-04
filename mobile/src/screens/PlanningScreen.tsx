import { useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'

import type { SessionView } from '../api/types'
import { Loading, Notice } from '../components/ui'
import {
  DAY_LABELS,
  SESSION_TYPE_LABELS,
  dayCodeOf,
  hhmm,
  isOngoing,
  nowMinutes,
  sessionsOfDay,
  toMinutes,
} from '../planning'
import { colors, radius, shadow } from '../theme'
import { useMonPlanning } from '../useMonPlanning'

const SEMAINE = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const HAUTEUR_BARRE = 46

/**
 * L'emploi du temps de la semaine, un jour à la fois.
 *
 * Le web affiche une grille horaire ; la reproduire sur un téléphone donnerait
 * des cases de trois millimètres. Deux vues remplacent la grille : un graphe de
 * la semaine, qui montre d'un coup d'œil où la charge se concentre — et qui
 * sert en même temps de sélecteur, plutôt que d'ajouter une rangée d'onglets
 * au-dessus — puis la journée choisie en frise horaire.
 */
export function PlanningScreen() {
  const insets = useSafeAreaInsets()
  const [jour, setJour] = useState(() => {
    const aujourdhui = dayCodeOf()
    return SEMAINE.includes(aujourdhui) ? aujourdhui : 'MONDAY'
  })
  const { moi, publie, vue, vueLoading, moiError } = useMonPlanning()

  const parJour = SEMAINE.map((j) => ({ jour: j, seances: sessionsOfDay(vue, j) }))
  const maximum = Math.max(1, ...parJour.map((d) => d.seances.length))
  const total = parJour.reduce((n, d) => n + d.seances.length, 0)

  const seances = parJour.find((d) => d.jour === jour)?.seances ?? []
  const maintenant = nowMinutes()
  const estAujourdhui = jour === dayCodeOf()

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={[styles.entete, { paddingTop: insets.top + 14 }]}>
        <Text style={styles.titre}>Mon emploi du temps</Text>
        <Text style={styles.soustitre}>
          {total} séance(s) cette semaine
          {moi?.affectations?.[0]?.schoolYearNom ? ` · ${moi.affectations[0].schoolYearNom}` : ''}
        </Text>
      </View>

      {moiError ? (
        <View style={{ padding: 16 }}>
          <Notice tone="error" text="Aucune fiche enseignant n'est rattachée à ce compte." />
        </View>
      ) : !publie ? (
        <View style={{ padding: 16 }}>
          <Notice text="Aucun emploi du temps publié pour le moment — votre semaine apparaîtra ici dès la publication." />
        </View>
      ) : vueLoading ? (
        <Loading />
      ) : (
        <>
          {/* ── La semaine, en barres ─────────────────────────────────────── */}
          <View style={styles.semaine}>
            {parJour.map(({ jour: j, seances: s }) => {
              const actif = j === jour
              const cejour = j === dayCodeOf()
              return (
                <Pressable key={j} onPress={() => setJour(j)} style={styles.colonne}>
                  <Text style={[styles.compte, actif && { color: colors.primary }]}>
                    {s.length || ''}
                  </Text>
                  <View style={styles.rail}>
                    <View
                      style={[
                        styles.barre,
                        {
                          height: Math.max(4, (s.length / maximum) * HAUTEUR_BARRE),
                          backgroundColor: actif
                            ? colors.primary
                            : s.length
                              ? colors.primaryLight
                              : colors.border,
                        },
                      ]}
                    />
                  </View>
                  <Text
                    style={[
                      styles.jourLabel,
                      actif && { color: colors.primary, fontWeight: '800' },
                    ]}
                  >
                    {DAY_LABELS[j].slice(0, 3)}
                  </Text>
                  <View style={[styles.point, cejour && { backgroundColor: colors.primary }]} />
                </Pressable>
              )
            })}
          </View>

          {/* ── La journée, en frise ──────────────────────────────────────── */}
          <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>
            <View style={styles.enTeteJour}>
              <Text style={styles.jourTitre}>{DAY_LABELS[jour]}</Text>
              {estAujourdhui && (
                <View style={styles.aujourdhui}>
                  <Text style={styles.aujourdhuiTexte}>aujourd’hui</Text>
                </View>
              )}
              <Text style={styles.jourCompte}>
                {seances.length ? `${seances.length} séance(s)` : ''}
              </Text>
            </View>

            {seances.length === 0 ? (
              <View style={styles.vide}>
                <Ionicons name="cafe-outline" size={30} color={colors.textMuted} />
                <Text style={styles.videTexte}>
                  Aucun cours le {DAY_LABELS[jour].toLowerCase()}.
                </Text>
              </View>
            ) : (
              seances.map((s, i) => (
                <Creneau
                  key={s.id}
                  seance={s}
                  premier={i === 0}
                  dernier={i === seances.length - 1}
                  enCours={estAujourdhui && isOngoing(s)}
                  passee={estAujourdhui && toMinutes(s.endTime) <= maintenant}
                />
              ))
            )}
          </ScrollView>
        </>
      )}
    </View>
  )
}

function Creneau({
  seance,
  premier,
  dernier,
  enCours,
  passee,
}: {
  seance: SessionView
  premier: boolean
  dernier: boolean
  enCours: boolean
  passee: boolean
}) {
  const accent = enCours ? colors.primary : passee ? colors.border : colors.primaryLight

  return (
    <View style={styles.creneau}>
      {/* Rail horaire : l'heure, puis le fil qui relie les séances entre elles. */}
      <View style={styles.railHoraire}>
        <Text style={[styles.heure, enCours && { color: colors.primary }]}>
          {hhmm(seance.startTime)}
        </Text>
        <View style={styles.filColonne}>
          <View style={[styles.fil, premier && { backgroundColor: 'transparent' }]} />
          <View style={[styles.pastille, { borderColor: accent }]}>
            {enCours && <View style={styles.pastillePleine} />}
          </View>
          <View style={[styles.fil, dernier && { backgroundColor: 'transparent' }]} />
        </View>
      </View>

      <View style={[styles.carte, passee && !enCours && { opacity: 0.55 }]}>
        <View style={[styles.liseret, { backgroundColor: accent }]} />
        <View style={{ flex: 1, padding: 12, gap: 5 }}>
          <View style={styles.ligneTitre}>
            <Text style={styles.matiere} numberOfLines={1}>
              {seance.subjectName}
            </Text>
            {enCours && (
              <View style={styles.badgeEnCours}>
                <Text style={styles.badgeTexte}>en cours</Text>
              </View>
            )}
          </View>
          <View style={styles.meta}>
            <Info icon="people-outline" texte={seance.classCode ?? '—'} />
            {seance.roomCode ? <Info icon="location-outline" texte={seance.roomCode} /> : null}
            <Info
              icon="school-outline"
              texte={SESSION_TYPE_LABELS[seance.sessionType] ?? seance.sessionType}
            />
          </View>
          <Text style={styles.plage}>
            {hhmm(seance.startTime)} – {hhmm(seance.endTime)}
          </Text>
        </View>
      </View>
    </View>
  )
}

function Info({ icon, texte }: { icon: keyof typeof Ionicons.glyphMap; texte: string }) {
  return (
    <View style={styles.info}>
      <Ionicons name={icon} size={12} color={colors.textMuted} />
      <Text style={styles.infoTexte}>{texte}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  entete: {
    backgroundColor: colors.navy,
    paddingHorizontal: 16,
    paddingBottom: 16,
    borderBottomLeftRadius: radius.xl,
    borderBottomRightRadius: radius.xl,
  },
  titre: { color: colors.white, fontSize: 19, fontWeight: '800' },
  soustitre: { color: '#cbd5e1', fontSize: 12, marginTop: 3 },

  semaine: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: colors.white,
    marginHorizontal: 16,
    marginTop: -10,
    paddingVertical: 12,
    paddingHorizontal: 6,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadow,
  },
  colonne: { flex: 1, alignItems: 'center', gap: 4 },
  compte: { fontSize: 11, fontWeight: '800', color: colors.textMuted, height: 14 },
  rail: { height: HAUTEUR_BARRE, justifyContent: 'flex-end' },
  barre: { width: 16, borderRadius: 4 },
  jourLabel: { fontSize: 11, fontWeight: '600', color: colors.textMuted },
  point: { width: 4, height: 4, borderRadius: 2, backgroundColor: 'transparent' },

  enTeteJour: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12 },
  jourTitre: { fontSize: 17, fontWeight: '800', color: colors.text },
  aujourdhui: {
    backgroundColor: colors.primarySoft,
    borderRadius: 999,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  aujourdhuiTexte: { fontSize: 10, fontWeight: '700', color: colors.primary },
  jourCompte: { flex: 1, textAlign: 'right', fontSize: 12, color: colors.textMuted },

  vide: { alignItems: 'center', gap: 10, paddingVertical: 40 },
  videTexte: { fontSize: 13, color: colors.textMuted },

  creneau: { flexDirection: 'row', gap: 10 },
  railHoraire: { width: 54, flexDirection: 'row', gap: 6 },
  heure: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.text,
    marginTop: 12,
    fontVariant: ['tabular-nums'],
  },
  filColonne: { alignItems: 'center', width: 12 },
  fil: { flex: 1, width: 2, backgroundColor: colors.border },
  pastille: {
    width: 11,
    height: 11,
    borderRadius: 6,
    borderWidth: 2,
    backgroundColor: colors.white,
    marginVertical: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pastillePleine: { width: 4, height: 4, borderRadius: 2, backgroundColor: colors.primary },

  carte: {
    flex: 1,
    flexDirection: 'row',
    marginBottom: 10,
    backgroundColor: colors.white,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    ...shadow,
  },
  liseret: { width: 4 },
  ligneTitre: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  matiere: { flex: 1, fontSize: 15, fontWeight: '700', color: colors.text },
  badgeEnCours: {
    backgroundColor: colors.primary,
    borderRadius: 999,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  badgeTexte: { fontSize: 10, fontWeight: '700', color: colors.white },
  meta: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  info: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  infoTexte: { fontSize: 12, color: colors.textMuted },
  plage: { fontSize: 11, color: colors.textMuted, fontVariant: ['tabular-nums'] },
})
