import { useLayoutEffect, useState } from 'react'
import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, KeyboardAvoidingView, Platform, ScrollView, View } from 'react-native'

import { cahierApi } from '../api'
import { ApiError } from '../api/client'
import { Button, Field, Loading, Notice } from '../components/ui'
import { colors } from '../theme'
import type { RootStackParamList } from '../navigation'

type Props = NativeStackScreenProps<RootStackParamList, 'Cahier'>

/**
 * Cahier de séance : ce qui a réellement été fait en classe.
 *
 * C'est ce texte, et lui seul, que l'assistant retrouvera plus tard — un cahier
 * vide n'est pas indexé. La saisie est donc le geste qui alimente tout le reste.
 */
export function CahierScreen({ route, navigation }: Props) {
  const { appelId, titre } = route.params
  const qc = useQueryClient()
  const [charge, setCharge] = useState(false)
  const [champs, setChamps] = useState({
    sujet: '',
    chapitre: '',
    activites: '',
    travailDemande: '',
    dateEcheance: '',
    remarques: '',
  })

  useLayoutEffect(() => {
    navigation.setOptions({ title: `Cahier · ${titre}` })
  }, [navigation, titre])

  // 404 = cahier pas encore rempli, ce qui est l'état normal en début de
  // séance : pas une erreur, et surtout pas une raison de réessayer.
  const cahierQuery = useQuery({
    queryKey: ['cahier', appelId],
    queryFn: () => cahierApi.recuperer(appelId),
    retry: false,
  })

  if (cahierQuery.data && !charge) {
    const c = cahierQuery.data
    setCharge(true)
    setChamps({
      sujet: c.sujet ?? '',
      chapitre: c.chapitre ?? '',
      activites: c.activites ?? '',
      travailDemande: c.travailDemande ?? '',
      dateEcheance: c.dateEcheance ?? '',
      remarques: c.remarques ?? '',
    })
  }

  const enregistrer = useMutation({
    mutationFn: () =>
      cahierApi.enregistrer(appelId, {
        ...champs,
        dateEcheance: champs.dateEcheance.trim() || undefined,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cahier', appelId] })
      Alert.alert(
        'Cahier enregistré',
        'La séance est consignée : elle devient consultable depuis l’assistant.'
      )
    },
    onError: (e: Error) => Alert.alert('Enregistrement impossible', e.message),
  })

  const introuvable = cahierQuery.isError && (cahierQuery.error as ApiError)?.status === 404

  if (cahierQuery.isLoading) return <Loading />
  if (cahierQuery.isError && !introuvable)
    return (
      <View style={{ padding: 16 }}>
        <Notice tone="error" text={(cahierQuery.error as Error).message} />
      </View>
    )

  const verrouille = cahierQuery.data?.estVerrouille ?? false

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.bg }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: 40 }}
        keyboardShouldPersistTaps="handled"
      >
        {verrouille && (
          <View style={{ marginBottom: 14 }}>
            <Notice text="Ce cahier est verrouillé : il ne peut plus être modifié." />
          </View>
        )}

        <Field
          label="Sujet de la séance"
          value={champs.sujet}
          editable={!verrouille}
          onChangeText={(v) => setChamps((c) => ({ ...c, sujet: v }))}
          placeholder="Ex : les fractions équivalentes"
        />
        <Field
          label="Chapitre"
          value={champs.chapitre}
          editable={!verrouille}
          onChangeText={(v) => setChamps((c) => ({ ...c, chapitre: v }))}
          placeholder="Ex : chapitre 3 — nombres rationnels"
        />
        <Field
          label="Activités menées"
          value={champs.activites}
          editable={!verrouille}
          onChangeText={(v) => setChamps((c) => ({ ...c, activites: v }))}
          multiline
          placeholder="Ce qui a réellement été fait en classe"
        />
        <Field
          label="Travail demandé"
          value={champs.travailDemande}
          editable={!verrouille}
          onChangeText={(v) => setChamps((c) => ({ ...c, travailDemande: v }))}
          multiline
          placeholder="Exercices, préparation, devoir"
        />
        <Field
          label="À rendre le"
          value={champs.dateEcheance}
          editable={!verrouille}
          onChangeText={(v) => setChamps((c) => ({ ...c, dateEcheance: v }))}
          placeholder="AAAA-MM-JJ"
          hint="Format ISO — laisser vide si aucun travail n’est à rendre."
        />
        <Field
          label="Remarques"
          value={champs.remarques}
          editable={!verrouille}
          onChangeText={(v) => setChamps((c) => ({ ...c, remarques: v }))}
          multiline
          placeholder="Incident, retard de progression, note pour la prochaine fois"
        />

        <View style={{ gap: 10 }}>
          {!verrouille && (
            <Button
              title="Enregistrer le cahier"
              loading={enregistrer.isPending}
              onPress={() => enregistrer.mutate()}
            />
          )}
          <Button
            title="Interroger l’assistant"
            variant="outline"
            onPress={() => navigation.navigate('Tabs', { screen: 'Assistant' })}
          />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}
