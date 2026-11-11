import {
  Inter_400Regular,
  Inter_500Medium,
  Inter_600SemiBold,
  Inter_700Bold,
  useFonts,
} from "@expo-google-fonts/inter";
import * as Notifications from "expo-notifications";
import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import React, { useEffect } from "react";
import { Platform } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { KeyboardProvider } from "react-native-keyboard-controller";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { ErrorBoundary } from "@/components/ErrorBoundary";
import { NutritionProvider } from "@/context/NutritionContext";
import { PreferencesProvider, usePreferences } from "@/context/PreferencesContext";
import { RestTimerProvider } from "@/context/RestTimerContext";
import { WorkoutProvider } from "@/context/WorkoutContext";

SplashScreen.preventAutoHideAsync();

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/** Android notification channels, (re)named in the app language. */
function NotificationChannels() {
  const { t, language } = usePreferences();
  useEffect(() => {
    if (Platform.OS !== "android") return;
    const channel = (name: string) => ({
      name,
      importance: Notifications.AndroidImportance.HIGH,
      sound: "default",
      vibrationPattern: [0, 250, 250, 250],
    });
    Notifications.setNotificationChannelAsync("supplements", channel(t("channel.supplements"))).catch(() => {});
    Notifications.setNotificationChannelAsync("rest-timer", channel(t("channel.restTimer"))).catch(() => {});
  }, [language]);
  return null;
}

function RootLayoutNav() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen
        name="start-workout"
        options={{
          headerShown: false,
          presentation: "modal",
        }}
      />
      <Stack.Screen
        name="workout"
        options={{
          headerShown: false,
        }}
      />
      <Stack.Screen
        name="exercise-picker"
        options={{
          headerShown: false,
          presentation: "modal",
        }}
      />
      <Stack.Screen
        name="exercise-history"
        options={{
          headerShown: false,
        }}
      />
    </Stack>
  );
}

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
  });

  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) return null;

  return (
    <SafeAreaProvider>
      <PreferencesProvider>
        <NotificationChannels />
        <ErrorBoundary>
          <WorkoutProvider>
            <NutritionProvider>
              <RestTimerProvider>
                <GestureHandlerRootView>
                  <KeyboardProvider>
                    <RootLayoutNav />
                  </KeyboardProvider>
                </GestureHandlerRootView>
              </RestTimerProvider>
            </NutritionProvider>
          </WorkoutProvider>
        </ErrorBoundary>
      </PreferencesProvider>
    </SafeAreaProvider>
  );
}
