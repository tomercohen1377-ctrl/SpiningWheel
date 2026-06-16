# Spin Wheel – Implementation Phases

## Phase Overview

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
Docs       Android   RN        Demo App  QA & Pack
```

---

## Phase 1 – Documentation & Project Setup ✅

**Goal**: Establish guidelines, architecture, and Gradle configuration before any code is written.

### Tasks
- [x] Create `docs/GUIDELINES.md`
- [x] Create `docs/PHASES.md`
- [x] Create `docs/ARCHITECTURE.md`
- [x] Create `docs/API.md`
- [x] Update `gradle/libs.versions.toml` (add OkHttp, kotlinx-serialization)
- [x] Update `settings.gradle.kts` (include `:spinwheel` module)
- [x] Update root `build.gradle.kts` (add serialization plugin)

### Deliverable
Working Gradle sync with all new dependencies resolvable.

---

## Phase 2 – Android Spin Wheel Library (`:spinwheel`) ✅

**Goal**: A fully functional, self-contained Android library that fetches config, downloads assets, and renders an animated spin wheel.

### Tasks

#### 2.1 – Data Layer
- [x] `data/WheelConfig.kt` – `@Serializable` data models for JSON config
- [x] `data/ConfigRepository.kt` – OkHttp fetch, SharedPreferences caching, image download

#### 2.2 – UI Layer
- [x] `ui/ImageLoader.kt` – OkHttp-based composable image loader with disk cache
- [x] `ui/SpinWheelComposable.kt` – Layered Compose UI (bg → wheel → frame → spin button)

#### 2.3 – ViewModel
- [x] `SpinWheelViewModel.kt` – Exposes state flows; orchestrates repository calls

#### 2.4 – Demo App Integration
- [x] Update `app/build.gradle.kts` to depend on `:spinwheel`
- [x] Update `MainActivity.kt` to render `SpinWheelScreen`

### Deliverable
Native Android demo app showing the animated spin wheel on a device/emulator.

---

## Phase 3 – React Native Wrapper (`react-native-spinwheel`) ✅

**Goal**: A self-contained npm-publishable package that wraps the Android spinwheel as a RN native view.

### Tasks

#### 3.1 – Android Native Side
- [x] `SpinWheelViewManager.kt` – `SimpleViewManager<ComposeView>` exposing props
- [x] `SpinWheelModule.kt` – Native module for JS-to-native method calls
- [x] `SpinWheelPackage.kt` – Registers module + view manager

#### 3.2 – JavaScript / TypeScript Side
- [x] `src/types.ts` – Shared TS types (`SpinWheelConfig`, `SpinWheelProps`)
- [x] `src/NativeSpinWheel.ts` – `requireNativeComponent` binding
- [x] `src/SpinWheelView.tsx` – Typed React component wrapper
- [x] `src/index.ts` – Public API barrel export

#### 3.3 – Package Config
- [x] `package.json` – name, version, peerDeps, files
- [x] `tsconfig.json` – strict TypeScript config
- [x] `android/build.gradle` – compiles against `:spinwheel` AAR

### Deliverable
Running `npm pack` inside `react-native-spinwheel/` produces a `.tgz` installable via `npm install ./react-native-spinwheel-*.tgz`.

---

## Phase 4 – Demo React Native App (`demo-rn-app`) ✅

**Goal**: A runnable RN app that installs the `.tgz` library and renders the spin wheel.

### Tasks
- [x] `package.json` – installs RN + the local `.tgz`
- [x] `App.tsx` – single-screen demo with config input + SpinWheelView
- [x] `index.js` – entry point

### Deliverable
`npx react-native run-android` launches the demo on a device/emulator.

---

## Phase 5 – QA, Packaging & Documentation ✅

**Goal**: Everything is tested, documented, and ready for handoff.

### Tasks
- [ ] Verify Gradle sync succeeds
- [ ] Run demo app on emulator; confirm spin animation
- [ ] Run RN demo app; confirm native view renders
- [ ] Run `npm pack`; verify `.tgz` contents
- [ ] Final review of all KDoc / TSDoc comments
- [ ] Update `docs/API.md` with any final API changes

---

## JSON Config Schema Reference

```json
{
  "network": {
    "assets": {
      "host": "https://drive.google.com/uc?export=download&id="
    }
  },
  "wheel": {
    "assets": {
      "background": "<DRIVE_FILE_ID_bg>",
      "frame":      "<DRIVE_FILE_ID_frame>",
      "spin":       "<DRIVE_FILE_ID_spin>",
      "wheel":      "<DRIVE_FILE_ID_wheel>"
    },
    "spinDurationMs": 3000,
    "segments": 8
  }
}
```

Asset URL construction: `fullUrl = network.assets.host + wheel.assets.<key>`
