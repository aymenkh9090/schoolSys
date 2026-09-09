import { useEffect, useState } from 'react'
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native'

import { Ionicons } from '@expo/vector-icons'

import { Button, Field, Notice } from '../components/ui'
import * as authStore from '../auth/authStore'
import { DEFAULT_HOST, getHost, loadHost, setHost } from '../config'
import { colors, radius, shadow } from '../theme'

export function LoginScreen() {
  const [email, setEmail] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [hote, setHote] = useState(getHost())
  const [reglages, setReglages] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)
  const [enCours, setEnCours] = useState(false)

  useEffect(() => {
    void loadHost().then(setHote)
  }, [])

  async function connecter() {
    setErreur(null)
    setEnCours(true)
    try {
      // L'hôte est enregistré AVANT l'appel : c'est lui qui décide à quelle
      // machine la demande de jeton est adressée.
      await setHost(hote)
      await authStore.login(email.trim(), motDePasse)
    } catch (e) {
      setErreur(
        (e as Error).message.includes('Network')
          ? `Serveur injoignable sur ${hote}. Vérifiez le Wi-Fi et l'adresse.`
          : (e as Error).message
      )
    } finally {
      setEnCours(false)
    }
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <View style={styles.logo}>
          <Ionicons name="school" size={30} color={colors.white} />
        </View>
        <Text style={styles.titre}>
          School<Text style={{ color: colors.primary }}>Sys</Text>
        </Text>
        <Text style={styles.soustitre}>Appel et cahier de séance — espace enseignant</Text>

        <View style={styles.carte}>
          <Field
            label="Adresse e-mail"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="email-address"
            placeholder="enseignant@etablissement.tn"
          />
          <Field
            label="Mot de passe"
            value={motDePasse}
            onChangeText={setMotDePasse}
            secureTextEntry
            placeholder="••••••••"
            onSubmitEditing={connecter}
          />

          {erreur ? (
            <View style={{ marginBottom: 14 }}>
              <Notice text={erreur} tone="error" />
            </View>
          ) : null}

          <Button
            title="Se connecter"
            onPress={connecter}
            loading={enCours}
            disabled={!email.trim() || !motDePasse}
          />

          <Pressable onPress={() => setReglages((v) => !v)} style={styles.lienReglages}>
            <Text style={styles.lienTexte}>
              {reglages ? 'Masquer' : 'Serveur'} · {hote}
            </Text>
          </Pressable>

          {reglages && (
            <View style={styles.reglages}>
              <Field
                label="Adresse du serveur"
                value={hote}
                onChangeText={setHote}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="numbers-and-punctuation"
                placeholder={DEFAULT_HOST}
                hint="IP du portable sur le réseau local — le téléphone ne peut pas joindre « localhost ». Ports 8080 (API), 8081 (Keycloak) et 8000 (assistant)."
              />
            </View>
          )}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
    backgroundColor: colors.bg,
  },
  logo: {
    alignSelf: 'center',
    width: 68,
    height: 68,
    borderRadius: radius.xl,
    backgroundColor: colors.navy,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow,
  },
  titre: {
    textAlign: 'center',
    fontSize: 24,
    fontWeight: '800',
    color: colors.navy,
    marginTop: 14,
  },
  soustitre: {
    textAlign: 'center',
    fontSize: 13,
    color: colors.textMuted,
    marginTop: 6,
    marginBottom: 26,
  },
  carte: {
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 20,
    ...shadow,
  },
  lienReglages: { marginTop: 16, alignItems: 'center' },
  lienTexte: { fontSize: 12, color: colors.textMuted, fontWeight: '600' },
  reglages: { marginTop: 14, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 14 },
})
