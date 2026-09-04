import { useEffect, useState, useSyncExternalStore } from 'react'
import { Ionicons } from '@expo/vector-icons'
import { NavigationContainer } from '@react-navigation/native'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StatusBar } from 'expo-status-bar'
import { ActivityIndicator, View } from 'react-native'
import { SafeAreaProvider } from 'react-native-safe-area-context'

import * as authStore from './src/auth/authStore'
import { loadHost } from './src/config'
import type { RootStackParamList, TabParamList } from './src/navigation'
import { AccueilScreen } from './src/screens/AccueilScreen'
import { AppelScreen } from './src/screens/AppelScreen'
import { AssistantScreen } from './src/screens/AssistantScreen'
import { CahierScreen } from './src/screens/CahierScreen'
import { ClassesScreen } from './src/screens/ClassesScreen'
import { LoginScreen } from './src/screens/LoginScreen'
import { PlanningScreen } from './src/screens/PlanningScreen'
import { SeancesScreen } from './src/screens/SeancesScreen'
import { colors } from './src/theme'

const Stack = createNativeStackNavigator<RootStackParamList>()
const Tab = createBottomTabNavigator<TabParamList>()

const ICONES: Record<keyof TabParamList, keyof typeof Ionicons.glyphMap> = {
  Accueil: 'home',
  Planning: 'calendar',
  Classes: 'people',
  Assistant: 'sparkles',
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Un seul réessai : sur un réseau local qui ne répond pas, insister ne
      // fait que retarder le message d'erreur qui, lui, est actionnable.
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
})

/**
 * Quatre onglets pour ce qu'on consulte. Les écrans d'action — l'appel, le
 * cahier — s'empilent par-dessus et se referment : ils ont un début et une fin,
 * contrairement à l'accueil ou au planning, auxquels on revient sans cesse.
 */
function Onglets() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: { borderTopColor: colors.border, height: 58, paddingBottom: 6, paddingTop: 6 },
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
        tabBarIcon: ({ color, size, focused }) => (
          <Ionicons
            name={focused ? ICONES[route.name] : (`${ICONES[route.name]}-outline` as never)}
            size={size - 2}
            color={color}
          />
        ),
      })}
    >
      <Tab.Screen name="Accueil" component={AccueilScreen} />
      <Tab.Screen name="Planning" component={PlanningScreen} options={{ title: 'Planning' }} />
      <Tab.Screen name="Classes" component={ClassesScreen} />
      <Tab.Screen name="Assistant" component={AssistantScreen} />
    </Tab.Navigator>
  )
}

export default function App() {
  const [pret, setPret] = useState(false)
  const { accessToken } = useSyncExternalStore(authStore.subscribe, authStore.getState)

  useEffect(() => {
    // L'hôte se charge AVANT la reprise de session : le jeton de rafraîchissement
    // stocké ne vaut que pour le serveur auprès duquel il a été émis.
    void loadHost()
      .then(() => authStore.restoreSession())
      .finally(() => setPret(true))
  }, [])

  if (!pret) {
    return (
      <View
        style={{
          flex: 1,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: colors.bg,
        }}
      >
        <ActivityIndicator color={colors.primary} />
      </View>
    )
  }

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <StatusBar style="light" />
        {accessToken ? (
          <NavigationContainer>
            <Stack.Navigator
              screenOptions={{
                headerStyle: { backgroundColor: colors.white },
                headerTitleStyle: { color: colors.text, fontSize: 16 },
                headerTintColor: colors.primary,
                contentStyle: { backgroundColor: colors.bg },
              }}
            >
              <Stack.Screen name="Tabs" component={Onglets} options={{ headerShown: false }} />
              <Stack.Screen
                name="Seances"
                component={SeancesScreen}
                options={{ title: 'Choisir une séance' }}
              />
              <Stack.Screen name="Appel" component={AppelScreen} options={{ title: 'Appel' }} />
              <Stack.Screen name="Cahier" component={CahierScreen} options={{ title: 'Cahier' }} />
            </Stack.Navigator>
          </NavigationContainer>
        ) : (
          <LoginScreen />
        )}
      </QueryClientProvider>
    </SafeAreaProvider>
  )
}
