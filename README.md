# Spin Wheel – Full Project

A Kotlin Android **Spin Wheel** UI library with a React Native wrapper.

---

## Repository Layout

```
SpiningWheel/
├── docs/                          # 📖 Project documentation
│   ├── GUIDELINES.md              #    Code style, tech choices
│   ├── PHASES.md                  #    Implementation roadmap
│   ├── ARCHITECTURE.md            #    System design & data flow
│   └── API.md                     #    Public API reference
│
├── spinwheel/                     # 📦 Android library module (:spinwheel)
│   ├── build.gradle.kts
│   └── src/main/java/com/example/spinwheel/
│       ├── SpinWheelScreen.kt     #    Public entry-point Composable
│       ├── SpinWheelViewModel.kt  #    State management
│       ├── data/
│       │   ├── WheelConfig.kt     #    @Serializable JSON models
│       │   └── ConfigRepository.kt#    OkHttp + SharedPrefs caching
│       └── ui/
│           ├── ImageLoader.kt     #    OkHttp-based async image decode
│           └── SpinWheelComposable.kt  # Layered Compose UI + animation
│
├── app/                           # 📱 Native Android demo app
│   └── src/main/java/com/example/spiningwheel/
│       └── MainActivity.kt        #    Hosts SpinWheelScreen
│
├── react-native-spinwheel/        # 🔌 React Native wrapper (.tgz)
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── index.ts               #    Public API barrel export
│   │   ├── SpinWheelView.tsx      #    React component
│   │   ├── NativeSpinWheel.ts     #    requireNativeComponent binding
│   │   └── types.ts               #    TypeScript interfaces
│   └── android/
│       └── src/main/java/com/spinwheel/
│           ├── SpinWheelViewManager.kt  # Native view bridge
│           ├── SpinWheelModule.kt       # JS-callable methods
│           └── SpinWheelPackage.kt      # ReactPackage registrar
│
└── demo-rn-app/                   # 🎮 Demo React Native application
    ├── App.tsx
    ├── index.js
    ├── package.json
    └── README.md                  # ← Setup & run instructions
```

---

## Quick Start – Native Android Demo

1. Open the project in Android Studio.
2. Edit `app/src/main/java/.../MainActivity.kt`:
   ```kotlin
   private const val CONFIG_URL = "https://your-host.com/wheel-config.json"
   ```
3. Run the `:app` configuration on a device/emulator.

---

## Quick Start – React Native

### 1. Pack the library
```bash
cd react-native-spinwheel
npm install
npm pack
# → react-native-spinwheel-1.0.0.tgz
```

### 2. Install in a React Native project
```bash
npm install ./react-native-spinwheel-1.0.0.tgz
```

### 3. Register in `MainApplication.kt`
```kotlin
import com.spinwheel.SpinWheelPackage

override fun getPackages() = PackageList(this).packages.apply {
    add(SpinWheelPackage())
}
```

### 4. Use in JS/TS
```tsx
import { SpinWheelView } from 'react-native-spinwheel';

<SpinWheelView
  configUrl="https://your-host.com/wheel-config.json"
  style={{ flex: 1 }}
  onSpinEnd={({ nativeEvent }) => console.log(nativeEvent.segment)}
/>
```

---

## JSON Config Schema

```json
{
  "network": {
    "assets": {
      "host": "https://drive.google.com/uc?export=download&id="
    }
  },
  "wheel": {
    "assets": {
      "background": "<GDRIVE_FILE_ID>",
      "frame":      "<GDRIVE_FILE_ID>",
      "spin":       "<GDRIVE_FILE_ID>",
      "wheel":      "<GDRIVE_FILE_ID>"
    },
    "spinDurationMs": 3000,
    "segments": 8
  }
}
```

Asset URL = `network.assets.host` + `wheel.assets.<key>`

---

## Dependencies

| Library | Purpose |
|---------|---------|
| `com.squareup.okhttp3:okhttp:4.12.0` | Remote config + image fetching |
| `kotlinx-serialization-json:1.7.3` | JSON config parsing |
| `kotlinx-serialization-cbor:1.7.3` | CBOR serialization support |
| `SharedPreferences` (Android) | Config cache TTL persistence |
| Jetpack Compose (BOM 2026.02.01) | Spin wheel UI + animations |
| `lifecycle-viewmodel-compose:2.10.0` | Lifecycle-aware state |

---

## Documentation

| Document | Contents |
|----------|---------|
| [`docs/GUIDELINES.md`](docs/GUIDELINES.md) | Code style, security, caching strategy |
| [`docs/PHASES.md`](docs/PHASES.md) | Implementation phases & task checklist |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Component diagram, data flow, state machine |
| [`docs/API.md`](docs/API.md) | Full Kotlin + TypeScript API reference |
| [`demo-rn-app/README.md`](demo-rn-app/README.md) | Demo RN app setup |
