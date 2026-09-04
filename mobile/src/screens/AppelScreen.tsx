import { useLayoutEffect, useState } from 'react'
import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'

import { appelApi, organisationApi } from '../api'
import type { AppelReponse, LigneAppelReponse, StatutPresence } from '../api/types'
import { Badge, Button, Loading, Notice } from '../components/ui'
import { colors, radius, shadow } from '../theme'
import type { RootStackParamList } from '../navigation'

type Props = NativeStackScreenProps<RootStackParamList, 'Appel'>

const STATUTS: StatutPresence[] = ['PRESENT', 'ABSENT', 'RETARD', 'EXCLU']

const LIBELLES: Record<StatutPresence, string> = {
  PRESENT: 'Présent',
  ABSENT: 'Absent',
  RETARD: 'Retard',
  EXCLU: 'Exclu',
}

const COULEURS: Record<StatutPresence, string> = {
  PRESENT: colors.success,
  ABSENT: colors.danger,
  RETARD: colors.warning,
  EXCLU: colors.violet,
}

function initiales(nom: string): string {
  const parties = nom.trim().split(/\s+/)
  return ((parties[0]?.[0] ?? '') + (parties[1]?.[0] ?? '')).toUpperCase() || '?'
}

/** `LocalDateTime` attendu par le backend : heure locale, sans fuseau ni « Z ». */
function maintenantLocal(): string {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
}

export function AppelScreen({ route, navigation }: Props) {
  const { appelId, titre } = route.params
  const qc = useQueryClient()
  const [exclusion, setExclusion] = useState<{ ligneId: number; nom: string } | null>(null)
  const [raison, setRaison] = useState('')

  useLayoutEffect(() => {
    navigation.setOptions({ title: titre })
  }, [navigation, titre])

  const appelQuery = useQuery({
    queryKey: ['appel', appelId],
    queryFn: () => appelApi.get(appelId),
  })
  const appel = appelQuery.data

  const { data: eleves = [] } = useQuery({
    queryKey: ['eleves', appel?.groupeClasseId],
    queryFn: () => organisationApi.elevesDeClasse(appel!.groupeClasseId),
    enabled: !!appel,
  })

  const modifier = useMutation({
    mutationFn: (v: {
      ligneId: number
      statut: StatutPresence
      arriveeAt?: string
      raisonExclusion?: string
    }) =>
      appelApi.modifierStatut(v.ligneId, {
        statut: v.statut,
        arriveeAt: v.arriveeAt,
        raisonExclusion: v.raisonExclusion,
      }),
    // Mise à jour optimiste : en classe, l'enseignant tape vite et le Wi-Fi de
    // l'établissement n'est pas toujours rapide. Le bouton doit répondre au
    // doigt, quitte à revenir en arrière si le serveur refuse.
    onMutate: async (v) => {
      await qc.cancelQueries({ queryKey: ['appel', appelId] })
      const precedent = qc.getQueryData<AppelReponse>(['appel', appelId])
      qc.setQueryData<AppelReponse>(['appel', appelId], (a) =>
        a
          ? {
              ...a,
              lignesAppel: a.lignesAppel.map((l) =>
                l.id === v.ligneId ? { ...l, statut: v.statut } : l
              ),
            }
          : a
      )
      return { precedent }
    },
    onError: (e: Error, _v, ctx) => {
      if (ctx?.precedent) qc.setQueryData(['appel', appelId], ctx.precedent)
      Alert.alert('Modification refusée', e.message)
    },
    onSettled: () => {
      setExclusion(null)
      setRaison('')
      void qc.invalidateQueries({ queryKey: ['appel', appelId] })
    },
  })

  const cloturer = useMutation({
    mutationFn: () => appelApi.verrouiller(appelId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['appel', appelId] })
      void qc.invalidateQueries({ queryKey: ['appels-du-jour'] })
    },
    onError: (e: Error) => Alert.alert('Clôture impossible', e.message),
  })

  function nomEleve(id: number): string {
    const e = eleves.find((x) => x.idEleve === id)
    return e ? `${e.nom} ${e.prenom}` : `Élève #${id}`
  }

  function choisir(ligne: LigneAppelReponse, statut: StatutPresence) {
    if (ligne.statut === statut) return
    if (statut === 'EXCLU') {
      setRaison('')
      setExclusion({ ligneId: ligne.id, nom: nomEleve(ligne.eleveId) })
      return
    }
    // Le retard est saisi au moment où l'élève entre : l'heure d'arrivée est
    // celle du geste. Le web demande une saisie parce qu'il régularise souvent
    // après coup ; en classe, la demander ferait perdre le bénéfice du tap.
    modifier.mutate({
      ligneId: ligne.id,
      statut,
      arriveeAt: statut === 'RETARD' ? maintenantLocal() : undefined,
    })
  }

  if (appelQuery.isLoading) return <Loading text="Chargement de la feuille d'appel…" />
  if (appelQuery.isError || !appel)
    return (
      <View style={{ padding: 16 }}>
        <Notice tone="error" text={(appelQuery.error as Error)?.message ?? 'Appel introuvable.'} />
      </View>
    )

  const compteurs = appel.lignesAppel.reduce<Record<StatutPresence, number>>(
    (acc, l) => {
      acc[l.statut] += 1
      return acc
    },
    { PRESENT: 0, ABSENT: 0, RETARD: 0, EXCLU: 0 }
  )

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={styles.barre}>
        {STATUTS.map((s) => (
          <View key={s} style={[styles.compteur, { backgroundColor: `${COULEURS[s]}12` }]}>
            <Text style={[styles.compteurNombre, { color: COULEURS[s] }]}>{compteurs[s]}</Text>
            <Text style={[styles.compteurLibelle, { color: COULEURS[s] }]}>{LIBELLES[s]}</Text>
          </View>
        ))}
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32, gap: 10 }}>
        {appel.estVerrouille && (
          <Notice text="Séance clôturée : les statuts sont figés. Une régularisation passe par le circuit de justificatif, depuis le web." />
        )}

        {appel.lignesAppel.length === 0 ? (
          <Notice text="Aucun élève rattaché à cette séance." />
        ) : (
          appel.lignesAppel.map((ligne) => (
            <View key={ligne.id} style={styles.ligne}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                <View
                  style={[
                    styles.pastille,
                    { backgroundColor: `${COULEURS[ligne.statut]}1A` },
                  ]}
                >
                  <Text style={[styles.pastilleTexte, { color: COULEURS[ligne.statut] }]}>
                    {initiales(nomEleve(ligne.eleveId))}
                  </Text>
                </View>
                <Text style={styles.nom} numberOfLines={1}>
                  {nomEleve(ligne.eleveId)}
                </Text>
                {ligne.estJustifie && <Badge text="Justifié" color={colors.primary} />}
              </View>
              <View style={styles.statuts}>
                {STATUTS.map((s) => {
                  const actif = ligne.statut === s
                  return (
                    <Pressable
                      key={s}
                      disabled={appel.estVerrouille}
                      onPress={() => choisir(ligne, s)}
                      style={[
                        styles.bouton,
                        actif
                          ? { backgroundColor: COULEURS[s], borderColor: COULEURS[s] }
                          : { borderColor: colors.border, backgroundColor: colors.white },
                        appel.estVerrouille && !actif && { opacity: 0.35 },
                      ]}
                    >
                      <Text
                        style={[
                          styles.boutonTexte,
                          { color: actif ? colors.white : colors.textMuted },
                        ]}
                      >
                        {LIBELLES[s]}
                      </Text>
                    </Pressable>
                  )
                })}
              </View>
            </View>
          ))
        )}

        <View style={{ gap: 10, marginTop: 8 }}>
          <Button
            title="Cahier de séance et assistant"
            variant="outline"
            onPress={() => navigation.navigate('Cahier', { appelId, titre })}
          />
          {!appel.estVerrouille && (
            <Button
              title="Clôturer la séance"
              variant="danger"
              loading={cloturer.isPending}
              onPress={() =>
                Alert.alert(
                  'Clôturer la séance ?',
                  'Les présences saisies seront figées : plus aucune modification ne sera possible ici.',
                  [
                    { text: 'Annuler', style: 'cancel' },
                    { text: 'Clôturer', style: 'destructive', onPress: () => cloturer.mutate() },
                  ]
                )
              }
            />
          )}
        </View>
      </ScrollView>

      <Modal visible={!!exclusion} transparent animationType="fade" onRequestClose={() => setExclusion(null)}>
        <View style={styles.fond}>
          <View style={styles.boite}>
            <Text style={styles.boiteTitre}>Raison de l'exclusion</Text>
            <Text style={styles.boiteSousTitre}>{exclusion?.nom}</Text>
            <TextInput
              value={raison}
              onChangeText={setRaison}
              placeholder="Ex : perturbation du cours"
              placeholderTextColor={colors.textMuted}
              style={styles.champ}
              autoFocus
            />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 14 }}>
              <View style={{ flex: 1 }}>
                <Button title="Annuler" variant="outline" onPress={() => setExclusion(null)} />
              </View>
              <View style={{ flex: 1 }}>
                <Button
                  title="Confirmer"
                  loading={modifier.isPending}
                  disabled={!raison.trim()}
                  onPress={() =>
                    modifier.mutate({
                      ligneId: exclusion!.ligneId,
                      statut: 'EXCLU',
                      raisonExclusion: raison.trim(),
                    })
                  }
                />
              </View>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  )
}

const styles = StyleSheet.create({
  barre: {
    flexDirection: 'row',
    gap: 8,
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  compteur: { flex: 1, alignItems: 'center', paddingVertical: 8, borderRadius: radius.md },
  compteurNombre: { fontSize: 20, fontWeight: '800' },
  compteurLibelle: { fontSize: 10, fontWeight: '700', marginTop: 1 },
  pastille: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pastilleTexte: { fontSize: 12, fontWeight: '800' },
  ligne: {
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: 12,
    gap: 10,
    ...shadow,
  },
  nom: { flex: 1, fontSize: 15, fontWeight: '600', color: colors.text },
  statuts: { flexDirection: 'row', gap: 6 },
  bouton: {
    flex: 1,
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingVertical: 10,
    alignItems: 'center',
  },
  boutonTexte: { fontSize: 12, fontWeight: '700' },
  fond: {
    flex: 1,
    backgroundColor: '#00000066',
    justifyContent: 'center',
    padding: 24,
  },
  boite: { backgroundColor: colors.white, borderRadius: radius.lg, padding: 18, ...shadow },
  boiteTitre: { fontSize: 16, fontWeight: '800', color: colors.text },
  boiteSousTitre: { fontSize: 13, color: colors.textMuted, marginTop: 2, marginBottom: 14 },
  champ: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 11,
    fontSize: 15,
    color: colors.text,
  },
})
