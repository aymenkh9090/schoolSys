import { useEffect, useLayoutEffect, useMemo, useState } from 'react'
import { Ionicons } from '@expo/vector-icons'
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
import type { LigneAppelReponse, SignalementEleve, StatutPresence } from '../api/types'
import { Badge, Button, Loading, Notice } from '../components/ui'
import { hhmm, todayIso } from '../planning'
import { colors, radius, shadow, shadowHaute } from '../theme'
import type { RootStackParamList } from '../navigation'

type Props = NativeStackScreenProps<RootStackParamList, 'FeuilleAppel'>

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
  EXCLU: colors.exclu,
}

/** Ce qu'on est en train de saisir pour un élève, avant enregistrement. */
interface Brouillon {
  statut: StatutPresence
  raisonExclusion?: string
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

/** « ce matin », « hier », « lundi » — la date telle qu'on la dit en salle des profs. */
function quand(dateIso: string, heure?: string): string {
  const jour = dateIso.slice(0, 10)
  const heureCourte = heure ? ` à ${hhmm(heure)}` : ''
  if (jour === todayIso()) return `aujourd’hui${heureCourte}`

  const hier = new Date()
  hier.setDate(hier.getDate() - 1)
  const hierIso = new Date(hier.getTime() - hier.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10)
  if (jour === hierIso) return `hier${heureCourte}`

  const d = new Date(`${jour}T00:00:00`)
  return `${d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })}${heureCourte}`
}

/**
 * Feuille d'appel : la classe est présente par défaut, on marque les exceptions,
 * puis on enregistre.
 *
 * L'enregistrement est explicite, et c'est un changement de fond. Chaque tap
 * partait auparavant seul vers le serveur : l'enseignant ne savait jamais s'il
 * avait fini, une erreur de doigt était immédiatement écrite, et un réseau
 * capricieux transformait la feuille en série d'états à moitié envoyés. Ici la
 * saisie vit en local, le bandeau du bas compte ce qui reste à envoyer, et un
 * seul geste engage tout — le geste qu'on faisait déjà sur le cahier papier en
 * refermant la page.
 *
 * L'écran affiche aussi ce que les COLLÈGUES ont signalé : un élève absent en
 * première heure et non justifié apparaît ici, en deuxième, même si le cours
 * est d'une autre matière. C'est le seul moment où l'information est utile —
 * juste avant de compter les têtes.
 */
export function AppelScreen({ route, navigation }: Props) {
  const { appelId, titre, groupeClasseId } = route.params
  const qc = useQueryClient()

  const [brouillons, setBrouillons] = useState<Record<number, Brouillon>>({})
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
  const classeId = groupeClasseId ?? appel?.groupeClasseId

  const { data: eleves = [] } = useQuery({
    queryKey: ['eleves', classeId],
    queryFn: () => organisationApi.elevesDeClasse(classeId!),
    enabled: !!classeId,
  })

  // Ce que les autres enseignants ont signalé sur cette classe et que la vie
  // scolaire n'a pas encore justifié.
  const { data: signalements = [] } = useQuery({
    queryKey: ['signalements', classeId],
    queryFn: () => appelApi.signalements(classeId!),
    enabled: !!classeId,
  })

  const modifier = useMutation({
    mutationFn: (v: { ligneId: number; statut: StatutPresence; raisonExclusion?: string }) =>
      appelApi.modifierStatut(v.ligneId, {
        statut: v.statut,
        // Le retard est horodaté à l'enregistrement : c'est l'heure la plus
        // proche du geste dont on dispose sans la demander à l'enseignant.
        arriveeAt: v.statut === 'RETARD' ? maintenantLocal() : undefined,
        raisonExclusion: v.raisonExclusion,
      }),
  })

  /**
   * Envoie toutes les modifications en attente, l'une après l'autre.
   *
   * En série, et non en parallèle : le serveur journalise chaque changement de
   * statut, et trente requêtes simultanées depuis un téléphone sur le Wi-Fi de
   * l'établissement produisent surtout des délais d'attente. Une feuille
   * n'excède jamais quelques dizaines de lignes, dont une poignée modifiées.
   */
  const enregistrer = useMutation({
    mutationFn: async () => {
      const entrees = Object.entries(brouillons)
      for (const [ligneId, valeur] of entrees) {
        await modifier.mutateAsync({
          ligneId: Number(ligneId),
          statut: valeur.statut,
          raisonExclusion: valeur.raisonExclusion,
        })
      }
      return entrees.length
    },
    onSuccess: (nombre) => {
      setBrouillons({})
      void qc.invalidateQueries({ queryKey: ['appel', appelId] })
      void qc.invalidateQueries({ queryKey: ['signalements'] })
      void qc.invalidateQueries({ queryKey: ['appels-du-jour'] })
      Alert.alert(
        'Appel enregistré',
        `${nombre} élève${nombre > 1 ? 's' : ''} mis à jour. La vie scolaire voit les absences immédiatement.`
      )
    },
    onError: (e: Error) => {
      void qc.invalidateQueries({ queryKey: ['appel', appelId] })
      Alert.alert('Enregistrement incomplet', e.message)
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

  const enAttente = Object.keys(brouillons).length

  // Quitter la feuille sans enregistrer perdrait la saisie : on le dit, et on
  // laisse le choix. Un blocage pur et simple serait pire — l'enseignant a
  // parfois une bonne raison de sortir.
  useEffect(() => {
    if (enAttente === 0) return
    const stop = navigation.addListener('beforeRemove', (e) => {
      e.preventDefault()
      Alert.alert(
        'Modifications non enregistrées',
        `${enAttente} élève${enAttente > 1 ? 's' : ''} en attente. Que voulez-vous faire ?`,
        [
          { text: 'Rester sur la feuille', style: 'cancel' },
          {
            text: 'Quitter sans enregistrer',
            style: 'destructive',
            onPress: () => navigation.dispatch(e.data.action),
          },
        ]
      )
    })
    return stop
  }, [navigation, enAttente])

  function nomEleve(id: number): string {
    const e = eleves.find((x) => x.idEleve === id)
    return e ? `${e.nom} ${e.prenom}` : `Élève #${id}`
  }

  /** Statut affiché : le brouillon s'il existe, sinon celui du serveur. */
  function statutDe(ligne: LigneAppelReponse): StatutPresence {
    return brouillons[ligne.id]?.statut ?? ligne.statut
  }

  function choisir(ligne: LigneAppelReponse, statut: StatutPresence) {
    if (statutDe(ligne) === statut) return
    if (statut === 'EXCLU') {
      setRaison('')
      setExclusion({ ligneId: ligne.id, nom: nomEleve(ligne.eleveId) })
      return
    }
    poser(ligne.id, { statut })
  }

  /** Inscrit une valeur au brouillon, ou l'en retire si elle revient au serveur. */
  function poser(ligneId: number, valeur: Brouillon) {
    setBrouillons((actuels) => {
      const suite = { ...actuels }
      const serveur = appel?.lignesAppel.find((l) => l.id === ligneId)
      if (serveur && serveur.statut === valeur.statut && valeur.statut !== 'EXCLU') {
        delete suite[ligneId]
      } else {
        suite[ligneId] = valeur
      }
      return suite
    })
  }

  /** Les signalements des collègues, regroupés par élève. */
  const signalementsParEleve = useMemo(() => {
    const carte = new Map<number, SignalementEleve[]>()
    for (const s of signalements) {
      // Ceux de la séance en cours ne sont pas un antécédent : c'est la saisie
      // de l'instant, elle est déjà sous les yeux.
      if (s.seanceAppelId === appelId) continue
      const liste = carte.get(s.eleveId) ?? []
      liste.push(s)
      carte.set(s.eleveId, liste)
    }
    return carte
  }, [signalements, appelId])

  if (appelQuery.isLoading) return <Loading text="Chargement de la feuille d'appel…" />
  if (appelQuery.isError || !appel)
    return (
      <View style={{ padding: 16 }}>
        <Notice tone="error" text={(appelQuery.error as Error)?.message ?? 'Appel introuvable.'} />
      </View>
    )

  const compteurs = appel.lignesAppel.reduce<Record<StatutPresence, number>>(
    (acc, l) => {
      acc[statutDe(l)] += 1
      return acc
    },
    { PRESENT: 0, ABSENT: 0, RETARD: 0, EXCLU: 0 }
  )

  const suivis = appel.lignesAppel.filter((l) => signalementsParEleve.has(l.eleveId)).length

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

      <ScrollView
        contentContainerStyle={{
          padding: 16,
          paddingBottom: enAttente > 0 ? 120 : 32,
          gap: 10,
        }}
      >
        {appel.estVerrouille && (
          <Notice text="Séance clôturée : les statuts sont figés. Une régularisation passe par le circuit de justificatif, depuis le web." />
        )}

        {suivis > 0 && (
          <View style={styles.alerte}>
            <Ionicons name="alert-circle" size={18} color={colors.danger} />
            <Text style={styles.alerteTexte}>
              {suivis} élève{suivis > 1 ? 's' : ''} de cette classe traîne
              {suivis > 1 ? 'nt' : ''} une absence ou une exclusion que la vie scolaire n’a pas
              encore justifiée. Le détail figure sous leur nom.
            </Text>
          </View>
        )}

        {appel.lignesAppel.length === 0 ? (
          <Notice text="Aucun élève rattaché à cette séance." />
        ) : (
          appel.lignesAppel.map((ligne) => {
            const statut = statutDe(ligne)
            const modifie = brouillons[ligne.id] !== undefined
            const antecedents = signalementsParEleve.get(ligne.eleveId) ?? []
            return (
              <View
                key={ligne.id}
                style={[styles.ligne, modifie && { borderColor: colors.primaryLight }]}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                  <View style={[styles.pastille, { backgroundColor: `${COULEURS[statut]}1A` }]}>
                    <Text style={[styles.pastilleTexte, { color: COULEURS[statut] }]}>
                      {initiales(nomEleve(ligne.eleveId))}
                    </Text>
                  </View>
                  <Text style={styles.nom} numberOfLines={1}>
                    {nomEleve(ligne.eleveId)}
                  </Text>
                  {modifie && <Badge text="À enregistrer" color={colors.primary} />}
                  {ligne.estJustifie && <Badge text="Justifié" color={colors.success} />}
                </View>

                {/* Ce qu'un collègue a signalé plus tôt, et que personne n'a
                    soldé. Sous le nom, avant les boutons : c'est un élément de
                    décision, pas une note de bas de page. */}
                {antecedents.map((s) => (
                  <View key={s.ligneAppelId} style={styles.antecedent}>
                    <Ionicons
                      name={s.statut === 'EXCLU' ? 'remove-circle' : 'close-circle'}
                      size={13}
                      color={s.statut === 'EXCLU' ? colors.exclu : colors.danger}
                    />
                    <Text style={styles.antecedentTexte}>
                      <Text style={{ fontWeight: '700' }}>
                        {s.statut === 'EXCLU' ? 'Exclu' : 'Absent'}
                      </Text>{' '}
                      {quand(s.dateSeance, s.heureDebut)}
                      {s.matiere ? ` · ${s.matiere}` : ''}
                      {s.enseignant ? ` · ${s.enseignant}` : ''}
                      {s.raisonExclusion ? ` — « ${s.raisonExclusion} »` : ''}
                      {s.statutJustificatif === 'EN_ATTENTE'
                        ? ' — justificatif déposé, en attente de la vie scolaire'
                        : ' — non justifié'}
                    </Text>
                  </View>
                ))}

                <View style={styles.statuts}>
                  {STATUTS.map((s) => {
                    const actif = statut === s
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
            )
          })
        )}

        <View style={{ gap: 10, marginTop: 8 }}>
          <Button
            title="Cahier de texte de la séance"
            variant="outline"
            onPress={() => navigation.navigate('CahierSeance', { appelId, titre })}
          />
          {!appel.estVerrouille && (
            <Button
              title="Clôturer la séance"
              variant="danger"
              loading={cloturer.isPending}
              onPress={() => {
                if (enAttente > 0) {
                  Alert.alert(
                    'Enregistrez d’abord',
                    'Des modifications ne sont pas encore envoyées. Enregistrez l’appel avant de clôturer la séance.'
                  )
                  return
                }
                Alert.alert(
                  'Clôturer la séance ?',
                  'Les présences seront figées : plus aucune modification ne sera possible ici.',
                  [
                    { text: 'Annuler', style: 'cancel' },
                    { text: 'Clôturer', style: 'destructive', onPress: () => cloturer.mutate() },
                  ]
                )
              }}
            />
          )}
        </View>
      </ScrollView>

      {/* ── Barre d'enregistrement ──────────────────────────────────────────
          Elle n'apparaît qu'en présence de modifications : une barre toujours
          visible finit par se confondre avec le décor, et son bouton avec un
          ornement. */}
      {enAttente > 0 && !appel.estVerrouille && (
        <View style={styles.barreEnregistrement}>
          <View style={{ flex: 1 }}>
            <Text style={styles.barreTitre}>
              {enAttente} modification{enAttente > 1 ? 's' : ''} non enregistrée
              {enAttente > 1 ? 's' : ''}
            </Text>
            <Text style={styles.barreDetail}>
              Rien n’est transmis à la vie scolaire tant que vous n’avez pas enregistré.
            </Text>
          </View>
          <Pressable
            onPress={() => enregistrer.mutate()}
            disabled={enregistrer.isPending}
            style={({ pressed }) => [
              styles.boutonEnregistrer,
              (pressed || enregistrer.isPending) && { opacity: 0.7 },
            ]}
          >
            <Ionicons name="checkmark-circle" size={17} color={colors.white} />
            <Text style={styles.boutonEnregistrerTexte}>
              {enregistrer.isPending ? 'Envoi…' : 'Enregistrer'}
            </Text>
          </Pressable>
        </View>
      )}

      <Modal
        visible={!!exclusion}
        transparent
        animationType="fade"
        onRequestClose={() => setExclusion(null)}
      >
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
                  title="Valider"
                  disabled={!raison.trim()}
                  onPress={() => {
                    poser(exclusion!.ligneId, {
                      statut: 'EXCLU',
                      raisonExclusion: raison.trim(),
                    })
                    setExclusion(null)
                    setRaison('')
                  }}
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
  alerte: {
    flexDirection: 'row',
    gap: 9,
    alignItems: 'flex-start',
    backgroundColor: '#fef2f2',
    borderWidth: 1,
    borderColor: '#fecaca',
    borderRadius: radius.md,
    padding: 12,
  },
  alerteTexte: { flex: 1, fontSize: 12, lineHeight: 17, color: '#991b1b' },
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
  antecedent: {
    flexDirection: 'row',
    gap: 6,
    alignItems: 'flex-start',
    backgroundColor: colors.bgSecondary,
    borderRadius: radius.sm,
    paddingHorizontal: 9,
    paddingVertical: 7,
  },
  antecedentTexte: { flex: 1, fontSize: 11, lineHeight: 16, color: colors.textMuted },
  statuts: { flexDirection: 'row', gap: 6 },
  bouton: {
    flex: 1,
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingVertical: 10,
    alignItems: 'center',
  },
  boutonTexte: { fontSize: 12, fontWeight: '700' },
  barreEnregistrement: {
    position: 'absolute',
    left: 12,
    right: 12,
    bottom: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    paddingVertical: 11,
    paddingHorizontal: 13,
    ...shadowHaute,
  },
  barreTitre: { fontSize: 13, fontWeight: '800', color: colors.text },
  barreDetail: { fontSize: 11, color: colors.textMuted, marginTop: 2, lineHeight: 15 },
  boutonEnregistrer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    backgroundColor: colors.primary,
    borderRadius: radius.md,
    paddingVertical: 13,
    paddingHorizontal: 16,
  },
  boutonEnregistrerTexte: { color: colors.white, fontSize: 14, fontWeight: '800' },
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
