# react-native-spinwheel

Android home-screen **Spin Wheel** widget for React Native. Triggers the native
asset pipeline (Firebase Remote Config + Google Drive images) and tells the
widget to redraw.

> The widget itself lives on the Android home screen. This package is a
> management console — a JS bridge to call into the underlying Kotlin module.

---

## Install

```bash
# From your RN app
npm install react-native-spinwheel
# or, while developing locally, point at a .tgz:
npm install ../react-native-spinwheel/react-native-spinwheel-1.0.1.tgz
```

The package declares a peer-dependency on React Native, so no autolinking
config is needed — React Native ≥ 0.60 picks the `android/` module up via
autolinking automatically.

## Use

```ts
import SpinWheelModule from 'react-native-spinwheel';

await SpinWheelModule.syncWidgetConfiguration(
  'https://.../config.json',   // Firebase RC dump or any config endpoint
  'https://.../bg.png',
  'https://.../wheel.png',
  'https://.../frame.png',
  'https://.../spin.png',
);

const lastEpoch = await SpinWheelModule.getLastFetchTime();
await SpinWheelModule.clearCache();
```

Then **long-press your Android home screen → Widgets → Spin Wheel** to drop
the widget onto your launcher.

## Native module methods

| Method | Returns | Description |
|--------|---------|-------------|
| `syncWidgetConfiguration(configUrl, bg, wheel, frame, spin)` | `Promise<boolean>` | Downloads the JSON + the four image assets and refreshes every pinned widget instance. |
| `getLastFetchTime()` | `Promise<number>` | Epoch-ms of last successful sync, or `0` if never synced. |
| `clearCache()` | `Promise<boolean>` | Deletes all cached assets and resets the timestamp. |

## Build a fresh `.tgz`

When you change anything under `src/` or `android/`:

```bash
cd react-native-spinwheel
npm install            # one-off: pulls TypeScript
npm version patch      # bumps 1.0.1 -> 1.0.2 automatically
npm run build          # runs `tsc` -> lib/
npm pack               # -> react-native-spinwheel-1.0.2.tgz
```

Then update your RN app's `dependencies.react-native-spinwheel` line to point
at the new `.tgz` and re-install.

## Compatibility

- React Native 0.60+ (autolinking).
- Android `minSdk 24`, `targetSdk 35`.
- Kotlin 2.2.10.
- Uses Compose 2024.06.00 BOM internally for the widget UI.
