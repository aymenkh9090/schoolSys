import { useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
import { useQuery } from '@tanstack/react-query'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { useNavigation } from '@react-navigation/native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'

import { organisationApi } from '../api'
import type { TeacherAssignment } from '../api/types'
import { Badge, Card, Loading, Notice } from '../components/ui'
import { colors, radius } from '../theme'
import { useMonPlanning } from '../useMonPlanning'

/** Élèves d'une classe, chargés seulement quand la carte est dépliée. */
function Eleves({ classeId }: { classeId: number }) {
  const { data: eleves = [], isLoading } = useQuery({
    queryKey: ['eleves', classeId],
    queryFn: () => organisationApi.elevesDeClasse(classeId),
  })

  if (isLoading) return <Loading text="Chargement des élèves…" />
  if (eleves.length === 0)
    return <Text style={styles.vide}>Aucun élève actif dans cette classe.</Text>

  return (
    <View style={{ gap: 6, marginTop: 10 }}>
      {eleves.map((e, i) => (
        <View key={e.idEleve} style={styles.eleve}>
          <Text style={styles.rang}>{i + 1}</Text>
          <Text style={styles.nomEleve} numberOfLines={1}>
            {e.nom} {e.prenom}
          </Text>
        </View>
      ))}
    </View>
  )
}

/**
 * Les classes dont l'enseignant a la charge.
 *
 * Une classe peut apparaître dans plusieurs affectations — une par type de
 * séance (cours, TD…) — d'où le regroupement : le professeur pense « la 71 »,
 * pas « les trois affectations qui concernent la 71 ».
 */
export function ClassesScreen() {
  const insets = useSafeAreaInsets()
  const navigation = useNavigation()
  const [ouverte, setOuverte] = useState<number | null>(null)
  const { moi, moiLoading, moiError } = useMonPlanning()

  const affectations = (moi?.affectations ?? []).filter((a) => a.isActive)

  const parClasse = affectations.reduce<Record<number, TeacherAssignment[]>>((acc, a) => {
    ;(acc[a.classGroupId] ??= []).push(a)
    return acc
  }, {})

  const classes = Object.entries(parClasse).map(([id, liste]) => ({
    id: Number(id),
    code: liste[0].classGroupCode,
    niveau: liste[0].levelNom,
    effectif: liste[0].classGroupNbEleve,
    matieres: Array.from(new Set(liste.map((a) => a.subjectLib))),
  }))

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={[styles.entete, { paddingTop: insets.top + 14 }]}>
        {/* L'écran s'ouvre depuis l'accueil et se referme : le retour vit dans
            le bandeau, l'en-tête natif étant masqué pour ne pas doubler le
            titre. */}
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} style={styles.retour}>
          <Ionicons name="chevron-back" size={18} color={colors.white} />
          <Text style={styles.retourTexte}>Accueil</Text>
        </Pressable>
        <Text style={styles.titre}>Mes classes</Text>
        <Text style={styles.soustitre}>
          {classes.length} classe(s) · {affectations.length} affectation(s)
          {affectations[0] ? ` · ${affectations[0].schoolYearNom}` : ''}
        </Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 32 }}>
        {moiLoading ? (
          <Loading />
        ) : moiError ? (
          <Notice tone="error" text="Aucune fiche enseignant n'est rattachée à ce compte." />
        ) : classes.length === 0 ? (
          <Notice text="Aucune affectation active. L'administration doit vous affecter à une classe." />
        ) : (
          classes.map((c) => {
            const deplie = ouverte === c.id
            return (
              <Card key={c.id}>
                <Pressable
                  onPress={() => setOuverte(deplie ? null : c.id)}
                  style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}
                >
                  <View style={styles.pastille}>
                    <Text style={styles.pastilleTexte}>{c.code}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.nomClasse}>Classe {c.code}</Text>
                    <Text style={styles.details}>
                      {c.niveau} · {c.effectif} élèves
                    </Text>
                    <View style={{ flexDirection: 'row', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
                      {c.matieres.map((m) => (
                        <Badge key={m} text={m} color={colors.primary} />
                      ))}
                    </View>
                  </View>
                  <Ionicons
                    name={deplie ? 'chevron-up' : 'chevron-down'}
                    size={18}
                    color={colors.textMuted}
                  />
                </Pressable>

                {deplie && <Eleves classeId={c.id} />}
              </Card>
            )
          })
        )}
      </ScrollView>
    </View>
  )
}

const styles = StyleSheet.create({
  retour: { flexDirection: 'row', alignItems: 'center', gap: 2, marginBottom: 8, marginLeft: -4 },
  retourTexte: { color: colors.white, fontSize: 13, fontWeight: '600' },
  entete: {
    backgroundColor: colors.navy,
    paddingHorizontal: 16,
    paddingBottom: 14,
    borderBottomLeftRadius: radius.xl,
    borderBottomRightRadius: radius.xl,
  },
  titre: { color: colors.white, fontSize: 19, fontWeight: '800' },
  soustitre: { color: '#cbd5e1', fontSize: 12, marginTop: 3 },
  pastille: {
    width: 46,
    height: 46,
    borderRadius: radius.md,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pastilleTexte: { fontSize: 15, fontWeight: '800', color: colors.primary },
  nomClasse: { fontSize: 15, fontWeight: '700', color: colors.text },
  details: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  eleve: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  rang: { width: 20, textAlign: 'right', fontSize: 11, color: colors.textMuted },
  nomEleve: { flex: 1, fontSize: 14, color: colors.text },
  vide: { fontSize: 13, color: colors.textMuted, marginTop: 10 },
})
