# Spin Wheel – Project Guidelines

## Overview

This project delivers a **Kotlin-based Android Spin Wheel UI component** exposed to React Native as a reusable `.tgz` library.

---

## Project Modules

| Module | Type | Purpose |
|--------|------|---------|
| `:spinwheel` | Android Library (AAR) | Core spin-wheel logic, UI, networking, caching |
| `:app` | Android Application | Demo app showcasing the spin wheel natively |
| `react-native-spinwheel/` | React Native Library | RN wrapper around the native spinwheel |
| `demo-rn-app/` | React Native App | End-to-end demo consuming the RN library |

---

## Technology Choices

### Android Library (`:spinwheel`)

| Concern | Library | Reason |
|---------|---------|--------|
| Networking | `com.squareup.okhttp3:okhttp` | Required by spec; battle-tested |
| JSON parsing | `kotlinx-serialization-json` | Kotlin-native, fast, zero reflection |
| Binary/CBOR parsing | `kotlinx-serialization-cbor` | Required by spec; compact encoding |
| Persistence | `SharedPreferences` | Required by spec; lightweight KV store |
| UI | Jetpack Compose | Modern declarative Android UI |
| Images | Custom OkHttp loader | Avoids extra dependency; uses OkHttp already present |
| ViewModel | `lifecycle-viewmodel-compose` | Lifecycle-aware state container |
| Animations | Compose `Animatable` | Smooth rotation with easing curves |

### React Native Wrapper

| Concern | Library |
|---------|---------|
| Native view bridge | `ReactContext` + `ViewManager` |
| Compose in RN | `ComposeView` (AndroidX) |
| JS / TS layer | TypeScript strict mode |

---

## Code Style

- **Kotlin**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Coroutines**: All I/O on `Dispatchers.IO`; UI updates on `Dispatchers.Main`
- **Serialization**: All models annotated with `@Serializable`; use `@SerialName` when JSON keys differ from field names
- **No reflection**: Rely only on code-gen (kapt/KSP not needed; kotlinx-serialization uses its own plugin)
- **Nullability**: Prefer explicit non-null types; document when `null` is a valid domain value

---

## Security & Networking Guidelines

1. Only HTTPS URLs should be used for remote config and assets.
2. OkHttp's default 10-second connection/read timeout applies; override in `OkHttpClientProvider` if needed.
3. The JSON config URL should be stored in `BuildConfig` or passed at runtime — **never hardcoded** in released code.
4. Images from Google Drive are fetched via the direct download URL pattern:
   ```
   https://drive.google.com/uc?export=download&id=<FILE_ID>
   ```

---

## Caching Strategy

| Data | Storage | TTL |
|------|---------|-----|
| JSON config | SharedPreferences (raw JSON string) + `last_config_fetch_time` | 1 hour |
| Image assets | App cache directory (`context.cacheDir/spinwheel/`) | Until cache cleared |

---

## Contributing

1. Never commit credentials or API keys.
2. Each feature should be in its own Kotlin file under the appropriate package.
3. Add `@Preview` Composables for all UI components.
4. Document public APIs with KDoc.
