# Spin Wheel – API Reference

## Android (Kotlin) API

---

### `SpinWheelScreen` (Composable)

Top-level entry point for embedding the spin wheel in any Compose host.

```kotlin
@Composable
fun SpinWheelScreen(
    configUrl: String,
    modifier: Modifier = Modifier,
    onSpinEnd: ((segmentIndex: Int) -> Unit)? = null
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `configUrl` | `String` | HTTPS URL pointing to the remote JSON config |
| `modifier` | `Modifier` | Standard Compose modifier (size, padding, etc.) |
| `onSpinEnd` | `(Int) -> Unit?` | Optional callback with the winning segment index (0-based) |

**Usage (Activity)**
```kotlin
setContent {
    SpinWheelScreen(
        configUrl = "https://example.com/wheel-config.json",
        modifier = Modifier.fillMaxSize()
    ) { segment ->
        Log.d("SpinWheel", "Landed on segment $segment")
    }
}
```

---

### `SpinWheelViewModel`

```kotlin
class SpinWheelViewModel(application: Application) : AndroidViewModel(application)
```

#### Properties

| Name | Type | Description |
|------|------|-------------|
| `uiState` | `StateFlow<SpinWheelUiState>` | Current UI state observable |

#### Methods

```kotlin
fun loadConfig(configUrl: String)
```
Fetches (or loads from cache) the remote JSON config and all image assets.
Transitions state: `Idle → Loading → Ready | Error`

```kotlin
fun spin()
```
Triggers the spin animation. No-op if already spinning.

---

### `SpinWheelUiState` (Sealed Class)

```kotlin
sealed class SpinWheelUiState {
    object Idle    : SpinWheelUiState()
    object Loading : SpinWheelUiState()
    data class Ready(
        val config: WheelConfig,
        val background: ImageBitmap,
        val wheel:      ImageBitmap,
        val frame:      ImageBitmap,
        val spinButton: ImageBitmap,
        val isSpinning: Boolean = false
    ) : SpinWheelUiState()
    data class Error(val message: String) : SpinWheelUiState()
}
```

---

### `WheelConfig` (Data Model)

```kotlin
@Serializable
data class WheelConfig(
    val network: NetworkConfig,
    val wheel: WheelSettings
)

@Serializable
data class NetworkConfig(val assets: AssetsNetwork)

@Serializable
data class AssetsNetwork(val host: String)

@Serializable
data class WheelSettings(
    val assets: WheelAssets,
    @SerialName("spinDurationMs") val spinDurationMs: Long = 3000L,
    val segments: Int = 8
)

@Serializable
data class WheelAssets(
    val background: String,
    val frame: String,
    val spin: String,
    val wheel: String
)
```

---

### `ConfigRepository`

```kotlin
class ConfigRepository(
    private val context: Context,
    private val client: OkHttpClient
)
```

#### Methods

```kotlin
suspend fun getConfig(url: String): WheelConfig
```
Returns cached config (< 1 h old) or fetches fresh from `url`.

```kotlin
suspend fun getImageFile(url: String, filename: String): File
```
Returns a cached file or downloads and caches the image at `url`.

---

## JSON Configuration Schema

```json
{
  "network": {
    "assets": {
      "host": "https://drive.google.com/uc?export=download&id="
    }
  },
  "wheel": {
    "assets": {
      "background": "GDRIVE_FILE_ID",
      "frame":      "GDRIVE_FILE_ID",
      "spin":       "GDRIVE_FILE_ID",
      "wheel":      "GDRIVE_FILE_ID"
    },
    "spinDurationMs": 3000,
    "segments": 8
  }
}
```

**Asset URL Construction**
```
fullUrl = network.assets.host + wheel.assets.<key>
```
Example:
```
"https://drive.google.com/uc?export=download&id=" + "1aBcDeFgHiJkL"
= "https://drive.google.com/uc?export=download&id=1aBcDeFgHiJkL"
```

---

## React Native API

### `<SpinWheelView />`

```tsx
import { SpinWheelView } from 'react-native-spinwheel';

<SpinWheelView
  configUrl="https://example.com/wheel-config.json"
  style={{ width: 300, height: 300 }}
  onSpinEnd={(event) => console.log('Segment:', event.nativeEvent.segment)}
/>
```

#### Props

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `configUrl` | `string` | ✅ | Remote JSON config URL |
| `style` | `StyleProp<ViewStyle>` | ✅ | View dimensions (required for native view) |
| `onSpinEnd` | `(e: SpinEndEvent) => void` | ❌ | Fires when spin animation completes |

#### `SpinEndEvent`

```ts
interface SpinEndEvent {
  nativeEvent: {
    segment: number;   // 0-based index of winning segment
  };
}
```

---

### `SpinWheelModule` (Native Module)

Accessible via `NativeModules.SpinWheelModule`.

```ts
import { NativeModules } from 'react-native';
const { SpinWheelModule } = NativeModules;

// Programmatically trigger a spin
SpinWheelModule.spin();

// Clear the local cache
SpinWheelModule.clearCache();
```

---

## SharedPreferences Keys

| Key | Type | Description |
|-----|------|-------------|
| `spinwheel_last_fetch_time` | `Long` | Unix timestamp (ms) of last successful config fetch |
| `spinwheel_cached_config` | `String` | Raw JSON string of the last fetched config |

---

## Cache Behaviour

| Asset | Location | Invalidated |
|-------|----------|-------------|
| JSON config | SharedPreferences | After 1 hour or manual `clearCache()` |
| Images | `context.cacheDir/spinwheel/` | On `context.cacheDir` clear or manual `clearCache()` |
