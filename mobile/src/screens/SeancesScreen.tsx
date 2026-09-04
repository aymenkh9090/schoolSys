import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { useQuery } from '@tanstack/react-query'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'

import { appelApi, organisationApi } from '../api'
import { Badge, Button, Card, Loading, Notice, SectionTitle } from '../components/ui'
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

type Props = NativeStackScreenProps<RootStackParamList, 'Seances'>

/**
 * Choix de la séance, avant l'appel ou avant le cahier.
 *
 * Les deux gestes partent du même endroit — une séance du jour — et diffèrent
 * seulement par leur destination. Deux écrans quasi identiques auraient divergé
 * à la première correction.
 */
export function SeancesScreen({ route, navigation }: Props) {
  const { destination } = route.params
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

  function aller(appelId: number, titre: string) {
    navigation.replace(destination === 'cahier' ? 'Cahier' : 'Appel', { appelId, titre })
  }

  const { ouvrir } = useOuvrirAppel(moi?.idEnseignant, aller)

  const maintenant = nowMinutes()
  const appelDe = (planningId: number) =>
    dejaOuvertes.find((a) => a.seancePlanningId === planningId)
  const nomClasse = (id: number) =>
    classes.find((c) => c.idClasse === id)?.code ?? `Classe #${id}`

  const action = destination === 'cahier' ? 'Remplir le cahier' : "Faire l'appel"

  return (
    <ScrollView
      style={{ backgroundColor: colors.bg }}
      contentContainerStyle={{ padding: 16, paddingBottom: 32, gap: 12 }}
    >
      {moiLoading ? (
        <Loading />
      ) : moiError ? (
        <Notice tone="error" text="Aucune fiche enseignant n'est rattachée à ce compte." />
      ) : (
        <>
          <Notice
            text={
              destination === 'cahier'
                ? "Choisissez la séance dont vous voulez remplir le cahier. Le cahier se rattache à l'appel de la séance : celui-ci s'ouvre si ce n'est pas déjà fait."
                : "Choisissez la séance à appeler. Toute la classe est présente par défaut — vous ne marquez que les exceptions."
            }
          />

          <SectionTitle titre={`Mes cours — ${DAY_LABELS[dayCodeOf()]}`} icon="today-outline" />

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
                    enCours && { borderColor: colors.primaryLight, backgroundColor: colors.primarySoft },
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
                          ? aller(deja.id, `${s.classCode} · ${s.subjectName}`)
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
              <SectionTitle titre="Séances déjà ouvertes" icon="folder-open-outline" />
              {dejaOuvertes.map((a) => (
                <Pressable key={a.id} onPress={() => aller(a.id, nomClasse(a.groupeClasseId))}>
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
  )
}

const styles = StyleSheet.create({
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
