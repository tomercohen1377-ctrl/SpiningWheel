/**
 * Demo app for react-native-spinwheel.
 *
 * This app is the **management console** for the Spin Wheel home-screen widget.
 * The widget itself lives in the Android home-screen process and cannot be
 * embedded in a React Native view — this app triggers asset syncs and reports
 * the widget's last-sync status.
 *
 * Flow:
 *  1. App opens → calls getLastFetchTime() to show current sync state.
 *  2. User taps "Sync Widget" → calls syncWidgetConfiguration(CONFIG_URL).
 *  3. Native module downloads JSON + 4 images from Google Drive via OkHttp.
 *  4. Widget instances on the home screen refresh automatically.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import SpinWheelModule from 'react-native-spinwheel';

// ─── Config ───────────────────────────────────────────────────────────────── //

const DRIVE = 'https://drive.google.com/uc?export=download&id=';

/**
 * Remote JSON config — fetched for spin settings (duration, easing).
 * NOTE: The JSON's `network.assets.host` is a placeholder; real asset URLs
 * are provided explicitly below.
 */
const CONFIG_URL = `${DRIVE}1TCOGD961TPmtp2EQbvOj6T6wQVkZbjur`;

// Direct Drive download URLs for each image asset
const BG_URL    = `${DRIVE}1LQBHiIrO92sZ1lFaaqkH_yE5G7A6tK5B`;  // bg (AVIF → saved as PNG)
const WHEEL_URL = `${DRIVE}1gRxQmL7kLnxlTKRk6TKa-YaRKcf61tI9`;  // wheel.png
const FRAME_URL = `${DRIVE}10cFF-MGK_rbEh8TnprrmS0uHBOUN7wjN`;   // wheel-frame.png
const SPIN_URL  = `${DRIVE}1qx0XNFz6wueMRES02D0QS27fMDfxoBAJ`;   // wheel-spin.png

// ─── Component ────────────────────────────────────────────────────────────── //

export default function App(): React.JSX.Element {
  const [loading, setLoading]     = useState<boolean>(false);
  const [lastSync, setLastSync]   = useState<string>('Never');

  // Load last-sync timestamp on mount, and auto-sync on first launch so the
  // user doesn't need to tap the button. After the auto-sync (or after a
  // manual one) we still respect "Never" state and re-attempt.
  useEffect(() => {
    (async () => {
      await refreshTimestamp();
      try {
        const ts = await SpinWheelModule.getLastFetchTime();
        if (ts === 0) {
          // First open after install: silently sync so the home-screen widget
          // works without any user action.
          setLoading(true);
          const ok = await SpinWheelModule.syncWidgetConfiguration(
            CONFIG_URL, BG_URL, WHEEL_URL, FRAME_URL, SPIN_URL
          );
          if (ok) {
            await refreshTimestamp();
          }
          setLoading(false);
        }
      } catch {
        // best-effort; the manual Sync button still works as a fallback
      }
    })();
  }, []);

  const refreshTimestamp = useCallback(async () => {
    try {
      const ts = await SpinWheelModule.getLastFetchTime();
      setLastSync(ts > 0 ? new Date(ts).toLocaleString() : 'Never');
    } catch {
      setLastSync('Unknown');
    }
  }, []);

  const handleSync = useCallback(async () => {
    setLoading(true);
    try {
      const ok = await SpinWheelModule.syncWidgetConfiguration(
        CONFIG_URL, BG_URL, WHEEL_URL, FRAME_URL, SPIN_URL
      );
      if (ok) {
        await refreshTimestamp();
        Alert.alert(
          '✅ Synced',
          'Assets downloaded. Add the "Spin Wheel" widget to your home screen!'
        );
      } else {
        Alert.alert('Sync Failed', 'Could not download assets. Check your network.');
      }
    } catch (err: any) {
      Alert.alert('Error', err?.message ?? 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, [refreshTimestamp]);

  const handleClear = useCallback(async () => {
    await SpinWheelModule.clearCache();
    await refreshTimestamp();
    Alert.alert('Cache Cleared', 'All cached assets removed.');
  }, [refreshTimestamp]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor="#0D0D1A" />

      <View style={styles.container}>
        {/* ── Header ── */}
        <Text style={styles.title}>🎡 Spin Wheel Widget</Text>
        <Text style={styles.subtitle}>
          Sync assets from Google Drive to power the Android home-screen widget.
        </Text>

        {/* ── Status card ── */}
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Last Sync</Text>
          <Text style={styles.cardValue}>{lastSync}</Text>
        </View>

        {/* ── Sync button ── */}
        {loading ? (
          <ActivityIndicator size="large" color="#4FC3F7" style={{ marginTop: 24 }} />
        ) : (
          <TouchableOpacity style={styles.btnPrimary} onPress={handleSync}>
            <Text style={styles.btnText}>Sync Widget Assets</Text>
          </TouchableOpacity>
        )}

        {/* ── Clear cache ── */}
        <TouchableOpacity style={styles.btnSecondary} onPress={handleClear}>
          <Text style={styles.btnTextSecondary}>Clear Cache</Text>
        </TouchableOpacity>

        {/* ── Instructions ── */}
        <View style={styles.instructions}>
          <Text style={styles.instrTitle}>How to add the widget</Text>
          <Text style={styles.instrStep}>1. Tap "Sync Widget Assets" above.</Text>
          <Text style={styles.instrStep}>2. Long-press your home screen.</Text>
          <Text style={styles.instrStep}>3. Tap "Widgets" → find "Spin Wheel".</Text>
          <Text style={styles.instrStep}>4. Drag it onto your home screen.</Text>
          <Text style={styles.instrStep}>5. Tap the spin button on the widget!</Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────── //

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: '#0D0D1A',
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#8888AA',
    textAlign: 'center',
    marginBottom: 32,
    lineHeight: 20,
  },
  card: {
    backgroundColor: '#1A1A2E',
    borderRadius: 12,
    paddingVertical: 16,
    paddingHorizontal: 32,
    alignItems: 'center',
    marginBottom: 32,
    borderWidth: 1,
    borderColor: '#2A2A4A',
  },
  cardLabel: {
    fontSize: 12,
    color: '#6666AA',
    letterSpacing: 1,
    marginBottom: 4,
    textTransform: 'uppercase',
  },
  cardValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#4FC3F7',
  },
  btnPrimary: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 10,
    marginBottom: 12,
  },
  btnText: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 16,
  },
  btnSecondary: {
    paddingHorizontal: 24,
    paddingVertical: 10,
    marginBottom: 40,
  },
  btnTextSecondary: {
    color: '#8888AA',
    fontSize: 14,
  },
  instructions: {
    alignSelf: 'stretch',
    backgroundColor: '#1A1A2E',
    borderRadius: 12,
    padding: 20,
    borderWidth: 1,
    borderColor: '#2A2A4A',
  },
  instrTitle: {
    color: '#FFFFFF',
    fontWeight: 'bold',
    fontSize: 14,
    marginBottom: 10,
  },
  instrStep: {
    color: '#AAAACC',
    fontSize: 13,
    lineHeight: 22,
  },
});
