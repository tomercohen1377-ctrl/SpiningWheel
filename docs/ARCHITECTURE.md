# Spin Wheel – Architecture

## High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────┐
│                   React Native App                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │           SpinWheelView (TypeScript)              │  │
│  │   props: configUrl, width, height, onSpinEnd      │  │
│  └────────────────────┬──────────────────────────────┘  │
│                       │ requireNativeComponent           │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │         SpinWheelViewManager (Kotlin)             │  │
│  │   extends SimpleViewManager<ComposeView>          │  │
│  └────────────────────┬──────────────────────────────┘  │
└───────────────────────│─────────────────────────────────┘
                        │ ComposeView.setContent { ... }
┌───────────────────────▼─────────────────────────────────┐
│              :spinwheel  Android Library                 │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │              SpinWheelScreen (Composable)         │   │
│  │  ┌─────────────────────────────────────────────┐ │   │
│  │  │  SpinWheelComposable (Composable)            │ │   │
│  │  │   Layer 1: Background Image (bg.jpeg)        │ │   │
│  │  │   Layer 2: Wheel Image  (wheel.png) ← rotates│ │   │
│  │  │   Layer 3: Frame Image  (wheel-frame.png)    │ │   │
│  │  │   Layer 4: Spin Button  (wheel-spin.png)     │ │   │
│  │  └──────────────────────┬──────────────────────┘ │   │
│  │                         │ viewModel               │   │
│  │  ┌──────────────────────▼──────────────────────┐ │   │
│  │  │         SpinWheelViewModel                   │ │   │
│  │  │  StateFlow<SpinWheelUiState>                 │ │   │
│  │  │  fun loadConfig(url)                         │ │   │
│  │  │  fun spin()                                  │ │   │
│  │  └──────────────────────┬──────────────────────┘ │   │
│  └─────────────────────────│────────────────────────┘   │
│                            │                             │
│  ┌─────────────────────────▼────────────────────────┐   │
│  │              ConfigRepository                     │   │
│  │                                                  │   │
│  │  ┌──────────────────┐  ┌───────────────────────┐ │   │
│  │  │  OkHttpClient    │  │   SharedPreferences    │ │   │
│  │  │  - fetchConfig() │  │   - lastFetchTime      │ │   │
│  │  │  - fetchImage()  │  │   - cachedConfigJson   │ │   │
│  │  └────────┬─────────┘  └───────────────────────┘ │   │
│  │           │ disk cache                            │   │
│  │  ┌────────▼─────────────────────────────────────┐│   │
│  │  │  cacheDir/spinwheel/                          ││   │
│  │  │   config.json, bg.jpeg, wheel.png, ...        ││   │
│  │  └──────────────────────────────────────────────┘│   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Data Flow

### App Start / Config Load

```
MainActivity / SpinWheelViewManager
       │
       ▼
SpinWheelViewModel.loadConfig(configUrl)
       │
       ▼
ConfigRepository.getConfig(url)
       │
       ├─── SharedPreferences.lastFetchTime < 1h? ──► return cached JSON
       │
       └─── OkHttp GET configUrl
                  │
                  ▼
            Parse JSON (kotlinx-serialization)
                  │
                  ▼
            Save to SharedPreferences + disk cache
                  │
                  ▼
            Return WheelConfig
       │
       ▼
ConfigRepository.loadImages(config)
       │
       ├─── For each asset (bg, wheel, frame, spin):
       │         URL = config.network.assets.host + config.wheel.assets.<key>
       │         Check cacheDir/spinwheel/<filename>
       │         If missing → OkHttp GET → save file → return File
       │
       ▼
ViewModel emits SpinWheelUiState.Ready(images, config)
       │
       ▼
SpinWheelComposable renders layers
```

### Spin Interaction

```
User taps SpinButton
       │
       ▼
SpinWheelViewModel.spin()
       │ (only if not already spinning)
       ▼
Animatable.animateTo(
  currentRotation + 360*5 + random(0..360),
  tween(spinDurationMs, FastOutSlowInEasing)
)
       │
       ▼
Wheel Image rotates via graphicsLayer { rotationZ = rotation.value }
       │
       ▼
OnAnimationEnd → emit SpinResultEvent (winning segment index)
```

---

## Package Structure

### `:spinwheel` Library

```
com.example.spinwheel/
├── data/
│   ├── WheelConfig.kt          @Serializable models
│   └── ConfigRepository.kt     OkHttp + SharedPrefs + disk cache
├── ui/
│   ├── ImageLoader.kt          OkHttp Compose image loader
│   └── SpinWheelComposable.kt  Layered Compose UI
├── SpinWheelViewModel.kt       Lifecycle-aware state + logic
└── SpinWheelScreen.kt          Top-level entry Composable
```

### `react-native-spinwheel/`

```
android/src/main/java/com/spinwheel/
├── SpinWheelViewManager.kt    ViewManager bridge
├── SpinWheelModule.kt         NativeModule (JS methods)
└── SpinWheelPackage.kt        ReactPackage registrar

src/
├── types.ts                   Shared TS interfaces
├── NativeSpinWheel.ts         requireNativeComponent binding
├── SpinWheelView.tsx          React component
└── index.ts                   Public API export
```

---

## State Machine

```
         loadConfig()
IDLE ──────────────► LOADING
  ▲                      │
  │      error           ▼
  │◄──────────────── ERROR
  │
  │      success
  └◄────────────────── READY
                          │
                    spin() tap
                          │
                     SPINNING
                          │
                    animation end
                          │
                     READY (again)
```

---

## Threading Model

| Operation | Thread |
|-----------|--------|
| OkHttp network calls | `Dispatchers.IO` |
| File read / write | `Dispatchers.IO` |
| Bitmap decode | `Dispatchers.IO` |
| ViewModel state updates | `Dispatchers.Main` (via `StateFlow`) |
| Compose animation | Main thread (Compose runtime) |
