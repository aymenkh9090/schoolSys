import { Ionicons } from '@expo/vector-icons'
import { useNavigation } from '@react-navigation/native'
import type { NativeStackNavigationProp } from '@react-navigation/native-stack'
import { useQuery } from '@tanstack/react-query'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'

import { appelApi, organisationApi } from '../api'
import { Badge, Button, Card, Loading, Notice } from '../components/ui'
import {
  DAY_LABELS,
  SESSION_TYPE_LABELS,
  dayCodeOf,
  hhmm,
  isOngoing,
  nowMinutes,
  todayIso,
  toMinutes,
} from '../planning'
import { colors, radius } from '../theme'
import { useMonPlanning } from '../useMonPlanning'
import { useOuvrirAppel } from '../useOuvrirAppel'
import type { RootStackParamList } from '../navigation'

type Destination = 'appel' | 'cahier'

/**
 * Choix de la séance, avant l'appel ou avant le cahier.
 *
 * Les deux gestes partent du même endroit — une séance du jour — et diffèrent
 * seulement par leur destination. Deux écrans quasi identiques auraient divergé
 * à la première correction.
 *
 * L'écran est le contenu de deux onglets, et non une étape de pile : « Appel »
 * et « Cahier de texte » sont des endroits où l'on retourne dix fois par jour,
 * pas des parcours qu'on ouvre et qu'on referme.
 */
function ChoixSeance({ destination }: { destination: Destination }) {
  const insets = useSafeAreaInsets()
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>()
  const { moi, moiLoading, moiError, publie, vueLoading, seancesDuJour } = useMonPlanning()

  const { data: classes = [] } = useQuery({
    queryKey: ['classes'],
    queryFn: organisationApi.classes,
    enabled: !!moi,
  })

  // Séances déjà ouvertes aujourd'hui : c'est par là qu'on revient dans un
  // appel commencé, et le seul chemin qui reste si aucun emploi du temps n'a
  // encore été publié.
  const { data: dejaOuvertes = [] } = useQuery({
    queryKey: ['appels-du-jour', moi?.idEnseignant],
    queryFn: () => appelApi.lister({ date: todayIso(), enseignantId: moi!.idEnseignant }),
    enabled: !!moi,
  })

  function aller(appelId: number, titre: string, groupeClasseId?: number) {
    // Deux appels distincts plutôt qu'un nom de route calculé : les paramètres
    // des deux écrans ne sont pas les mêmes, et le typage doit pouvoir le dire.
    if (destination === 'cahier') {
      navigation.navigate('CahierSeance', { appelId, titre })
    } else {
      navigation.navigate('FeuilleAppel', { appelId, titre, groupeClasseId })
    }
  }

  const { ouvrir } = useOuvrirAppel(moi?.idEnseignant, aller)

  const maintenant = nowMinutes()
  const appelDe = (planningId: number) =>
    dejaOuvertes.find((a) => a.seancePlanningId === planningId)
  const nomClasse = (id: number) =>
    classes.find((c) => c.idClasse === id)?.code ?? `Classe #${id}`

  const action = destination === 'cahier' ? 'Remplir le cahier' : "Faire l'appel"

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={[styles.entete, { paddingTop: insets.top + 14 }]}>
        <View style={styles.enteteIcone}>
          <Ionicons
            name={destination === 'cahier' ? 'book' : 'people'}
            size={19}
            color={colors.primary}
          />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.titre}>
            {destination === 'cahier' ? 'Cahier de texte' : "Faire l'appel"}
          </Text>
          <Text style={styles.soustitre}>
            {destination === 'cahier'
              ? 'Choisissez la séance à consigner'
              : 'Choisissez la séance à appeler'}
          </Text>
        </View>
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32, gap: 12 }}>
        {moiLoading ? (
          <Loading />
        ) : moiError ? (
          <Notice tone="error" text="Aucune fiche enseignant n'est rattachée à ce compte." />
        ) : (
          <>
            <Notice
              text={
                destination === 'cahier'
                  ? "Le cahier se rattache à l'appel de la séance : celui-ci s'ouvre si ce n'est pas déjà fait."
                  : 'Toute la classe est présente par défaut — vous ne marquez que les exceptions, puis vous enregistrez.'
              }
            />

            <Text style={styles.section}>Mes cours — {DAY_LABELS[dayCodeOf()]}</Text>

            {!publie ? (
              <Notice text="Aucun emploi du temps publié pour le moment." />
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
                      enCours && {
                        borderColor: colors.primaryLight,
                        backgroundColor: colors.primarySoft,
                      },
                      passee && !enCours && !deja && { opacity: 0.55 },
                    ]}
                  >
                    <View style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
                      <View style={styles.creneau}>
                        <Text style={styles.heureDebut}>{hhmm(s.startTime)}</Text>
                        <Text style={styles.heureFin}>{hhmm(s.endTime)}</Text>
                      </View>
                      <View style={{ flex: 1 }}>
                        <View style={styles.ligneTitre}>
                          <Text style={styles.matiere}>{s.subjectName}</Text>
                          {enCours && <Badge text="En cours" color={colors.success} />}
                          {deja && <Badge text="Appel ouvert" color={colors.primaryDark} />}
                        </View>
                        <Text style={styles.details}>
                          {s.classCode}
                          {s.roomCode ? ` · ${s.roomCode}` : ''} ·{' '}
                          {SESSION_TYPE_LABELS[s.sessionType] ?? s.sessionType}
                        </Text>
                      </View>
                    </View>
                    <View style={{ marginTop: 12 }}>
                      <Button
                        title={action}
                        variant={enCours || deja ? 'primary' : 'outline'}
                        loading={ouvrir.isPending && ouvrir.variables?.id === s.id}
                        onPress={() =>
                          deja
                            ? aller(
                                deja.id,
                                `${s.classCode} · ${s.subjectName}`,
                                deja.groupeClasseId
                              )
                            : ouvrir.mutate(s)
                        }
                      />
                    </View>
                  </Card>
                )
              })
            )}

            {dejaOuvertes.length > 0 && (
              <>
                <Text style={styles.section}>Séances déjà ouvertes</Text>
                {dejaOuvertes.map((a) => (
                  <Pressable
                    key={a.id}
                    onPress={() => aller(a.id, nomClasse(a.groupeClasseId), a.groupeClasseId)}
                  >
                    <Card>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.matiere}>Classe {nomClasse(a.groupeClasseId)}</Text>
                          <Text style={styles.details}>
                            {a.lignesAppel?.length ?? 0} élèves · {a.anneeAcademique}
                          </Text>
                        </View>
                        <Badge
                          text={a.estVerrouille ? 'Clôturée' : 'Ouverte'}
                          color={a.estVerrouille ? colors.textMuted : colors.success}
                        />
                      </View>
                    </Card>
                  </Pressable>
                ))}
              </>
            )}
          </>
        )}
      </ScrollView>
    </View>
  )
}

/** Onglet « Appel ». */
export function ChoixSeanceAppel() {
  return <ChoixSeance destination="appel" />
}

/** Onglet « Cahier de texte ». */
export function ChoixSeanceCahier() {
  return <ChoixSeance destination="cahier" />
}

const styles = StyleSheet.create({
  entete: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingHorizontal: 16,
    paddingBottom: 14,
  },
  enteteIcone: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  titre: { fontSize: 18, fontWeight: '800', color: colors.text },
  soustitre: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  section: {
    fontSize: 12,
    fontWeight: '800',
    color: colors.primary,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginTop: 6,
  },
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
  details: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
})
