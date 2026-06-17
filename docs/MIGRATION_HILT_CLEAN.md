# Spin Wheel – Hilt + Clean Architecture Migration Guide

## Overview

This guide describes the migration from the current manual `WidgetSyncService` /
`SpinWheelRepository` setup to a **Hilt-injected Clean Architecture** layout.

Goal:
* Dependency injection via **Hilt**
* **Singleton** repository injected into the `CoroutineWorker`
* Repository owns **RemoteDataSource** + **LocalDataSource** abstractions
* **UseCase** performs the async image-download pipeline
* Worker awaits both JSON fetch and image download before updating the widget
* Widget UI state derives from the local data source; default is "Loading",
  then updates automatically when new data is stored

---

## 1. New module structure

```
spinwheel/src/main/java/com/example/spinwheel/
├── di/
│   ├── AppModule.kt              ── OkHttp, Json, SharedPreferences providers
│   └── HiltWorkerFactoryModule.kt ── Disables default WorkerFactory; we ship our own
├── data/
│   ├── WheelConfig.kt            (unchanged — @Serializable models)
│   ├── remote/
│   │   ├── RemoteDataSource.kt        ── interface
│   │   └── RemoteDataSourceImpl.kt    ── Firebase RC + OkHttp
│   └── local/
│       ├── LocalDataSource.kt         ── interface
│       └── LocalDataSourceImpl.kt     ── SharedPreferences + filesDir (bitmaps)
├── domain/
│   ├── model/                    ── plain Kotlin models (SpinWheelUiState, etc.)
│   ├── repository/
│   │   └── SpinWheelRepository.kt ── interface
│   └── usecase/
│       ├── GetWheelConfigJsonUseCase.kt
│       └── DownloadWheelImagesUseCase.kt
├── ui/
│   └── widget/
│       ├── SpinWheelGlanceWidget.kt   (state-driven rendering)
│       └── SpinWheelWidgetReceiver.kt (HiltWorkerFactory-aware)
└── work/
    ├── WidgetSyncWorker.kt           ── @HiltWorker; awaits UseCases
    └── WidgetWorkScheduler.kt        ── enqueues via HiltWorkerFactory

app/src/main/java/com/example/spiningwheel/
├── SpiningWheelApplication.kt        ── @HiltAndroidApp
├── MainActivity.kt                   ── injects the use cases it needs
```

---

## 2. Hilt setup

### 2.1 Plugin + dependencies

Add to `gradle/libs.versions.toml`:

```toml
[versions]
hilt = "2.52"
hiltWork = "1.2.0"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hintag" }   # alias like "hilt"
androidx-hilt-work = { group = "androidx.hilt",      name = "hilt-work",       version.ref = "hiltWork" }
androidx-hilt-compiler = { group = "androidx.hilt",  name = "hilt-compiler",   version.ref = "hiltWork" }
androidx-hilt-navigation = { group = "androidx.hilt",name = "hilt-navigation-fragment", version.ref = "hiltWork" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library       = { id = "com.android.library",    version.ref = "agp" }
kotlin-android       = { id = "org.jetbrains.kotlin.android",          version.ref = "kotlin" }
kotlin-kapt          = { id = "org.jetbrains.kotlin.kapt",             version.ref = "kotlin" }
kotlin-compose       = { id = "org.jetbrains.kotlin.plugin.compose",   version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt-android         = { id = "com.google.dagger.hilt.android",        version.ref = "hilt" }
google-services      = { id = "com.google.gms.google-services",        version.ref = "googleServices" }
```

### 2.2 Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.compose)       apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt)          apply false
    alias(libs.plugins.hilt.android)         apply false
    alias(libs.plugins.google.services)      apply false
}
```

### 2.3 `spinwheel/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)         // ← new
}

android { /* … existing … */ }

dependencies {
    // Existing
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-config")
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)
}

kapt { correctErrorTypes = true }
```

### 2.4 `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)         // ← new
    alias(libs.plugins.google.services)
}

dependencies {
    implementation(project(":spinwheel"))
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.core.ktx)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)
}
```

### 2.5 Application class (in `:app` module)

```kotlin
@HiltAndroidApp
class SpiningWheelApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)   // ← critical: uses Hilt-managed workers
            .build()

    // Make WorkManager use Configuration.Provider on app start
    override fun onCreate() { super.onCreate() }
}
```

Then in `AndroidManifest.xml`:

```xml
<application
    android:name=".SpiningWheelApplication"
    …>
```

…and (`androidx.startup.InitializationProvider` is added automatically by the
Hilt AGP plugin — no need to add it manually).

### 2.6 Disable the default WorkManager initializer

`AndroidManifest.xml` in `:app`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

This forces WorkManager to use `Configuration.Provider` and pick up the
`HiltWorkerFactory`.

---

## 3. Clean Architecture layers

### 3.1 Remote layer

```kotlin
// data/remote/RemoteDataSource.kt
interface RemoteDataSource {
    /** Returns the `wheel_config` JSON string, or null on failure. */
    suspend fun fetchConfigJson(): String?

    /** Downloads the bytes of a single image URL. */
    suspend fun fetchImage(url: String): ByteArray
}

// data/remote/RemoteDataSourceImpl.kt
class RemoteDataSourceImpl @Inject constructor(
    private val rcFetcherProvider: Provider<RemoteConfigFetcher>, // can keep your existing RC helper
    @IoDispatcher private val io: CoroutineDispatcher,
) : RemoteDataSource {

    override suspend fun fetchConfigJson(): String? =
        withContext(io) { rcFetcherProvider.get().fetchWheelConfigJson() }

    override suspend fun fetchImage(url: String): ByteArray = withContext(io) {
        client.fetchBytes(url).also { check(it.size > 100) { "Image too small" } }
    }
}
```

### 3.2 Local layer

```kotlin
// data/local/LocalDataSource.kt
interface LocalDataSource {
    // Config (JSON)
    suspend fun saveConfigJson(json: String)
    suspend fun getConfigJson(): String?

    // Image URLs (metadata about where each file came from)
    suspend fun saveImageUrls(urls: Map<AssetKey, String>)
    suspend fun getImageUrls(): Map<AssetKey, String>

    // Image bytes (raw files)
    suspend fun saveImageBytes(key: AssetKey, bytes: ByteArray)
    suspend fun getImageBytes(key: AssetKey): ByteArray?

    // Last sync timestamp
    suspend fun setLastSync(epochMillis: Long)
    suspend fun getLastSync(): Long

    /** Notifies observers that data has changed (for widget state). */
    val updates: Flow<Unit>
}

enum class AssetKey { BG, WHEEL, FRAME, SPIN }
```

```kotlin
// data/local/LocalDataSourceImpl.kt
class LocalDataSourceImpl @Inject constructor(
    @ApplicationContext context: Context,
) : LocalDataSource {

    private val prefs = context.getSharedPreferences("spinwheel_prefs", MODE_PRIVATE)
    private val filesDir = context.filesDir
    private val updatesFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 16)

    override val updates: Flow<Unit> = updatesFlow.asSharedFlow()

    override suspend fun saveImageBytes(key: AssetKey, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(filesDir, "${key.name.lowercase()}.bin").writeBytes(bytes)
    }

    override suspend fun getImageBytes(key: AssetKey): ByteArray? = withContext(Dispatchers.IO) {
        File(filesDir, "${key.name.lowercase()}.bin").takeIf { it.exists() && it.length() > 0 }?.readBytes()
    }

    override suspend fun saveImageUrls(urls: Map<AssetKey, String>) { /* prefs edits → KEY=bg → URL */ }
    override suspend fun getImageUrls(): Map<AssetKey, String> { /* reads same */ }

    /** …same for JSON + timestamp… */

    private suspend fun emitUpdate() { updatesFlow.emit(Unit) }
}
```

### 3.3 Repository

```kotlin
// domain/repository/SpinWheelRepository.kt
interface SpinWheelRepository {
    /** Step 1 — fetch JSON from RemoteConfig + persist it. Returns parsed config. */
    suspend fun fetchAndCacheConfig(): SpinWheelConfig?

    /** Reads currently-cached config. Returns null if none. */
    fun observeConfig(): Flow<SpinWheelConfig?>

    /** Reads currently-cached image bytes (combined payload for the widget). */
    fun observeImages(): Flow<ImagePayload?>

    /** Clears all local data. */
    suspend fun clearAll()
}

data class SpinWheelConfig( /* parsed fields: host, spinDuration, etc. */ )
data class ImagePayload(val bg: ByteArray, val wheel: ByteArray, val frame: ByteArray?, val spin: ByteArray?) {
    // see note below on ByteArray equals/hashCode
}
```

```kotlin
// data/repository/SpinWheelRepositoryImpl.kt
@Singleton
class SpinWheelRepositoryImpl @Inject constructor(
    private val remote: RemoteDataSource,
    private val local: LocalDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SpinWheelRepository {

    override suspend fun fetchAndCacheConfig(): SpinWheelConfig? = withContext(io) {
        val json = remote.fetchConfigJson() ?: return@withContext null
        local.saveConfigJson(json)
        // parse → domain
        val parsed = Json.decodeFromString<WheelConfigResponse>(json)
        parsed.data.firstOrNull()?.let { dto ->
            SpinWheelConfig(
                host         = dto.network.assets.host,
                spinDuration = dto.wheel.rotation.duration,
                ids          = mapOf(
                    AssetKey.BG    to dto.wheel.assets.bg,
                    AssetKey.WHEEL to dto.wheel.assets.wheel,
                    AssetKey.FRAME to dto.wheel.assets.wheelFrame,
                    AssetKey.SPIN  to dto.wheel.assets.wheelSpin,
                ),
            )
        }
    }

    override fun observeConfig(): Flow<SpinWheelConfig?> =
        local.updates.map { local.getConfigJson()?.let { parse(it) } }

    override fun observeImages(): Flow<ImagePayload?> =
        local.updates.map { /* reads 4 byte arrays → ImagePayload */ }

    override suspend fun clearAll() = withContext(io) {
        local.clear()
    }
}
```

### 3.4 Use cases

Following the user's spec, **the use case awaits all four images in parallel**:

```kotlin
// domain/usecase/DownloadWheelImagesUseCase.kt
class DownloadWheelImagesUseCase @Inject constructor(
    private val remote: RemoteDataSource,
    private val local: LocalDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Awaits all four image downloads in parallel, then saves them. */
    suspend operator fun invoke(config: SpinWheelConfig): Boolean = withContext(io) {
        val urls = buildUrls(config)
        local.saveImageUrls(urls)

        // 4 coroutines launched in parallel — `awaitAll` blocks until ALL finish
        val bytes: List<Pair<AssetKey, ByteArray>> = urls.entries
            .map { (key, url) ->
                async {
                    try {
                        key to remote.fetchImage(url)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch $key", e)
                        key to ByteArray(0)   // mark as missing; let caller decide
                    }
                }
            }.awaitAll()

        bytes.forEach { (k, data) ->
            if (data.isNotEmpty()) local.saveImageBytes(k, data)
        }
        local.setLastSync(System.currentTimeMillis())
        bytes.all { it.second.isNotEmpty() }
    }

    private fun buildUrls(c: SpinWheelConfig): Map<AssetKey, String> {
        val base = if (c.host.endsWith('/')) c.host else "${c.host}/"
        return c.ids.mapValues { (_, id) -> base + id }
    }
}
```

```kotlin
// domain/usecase/GetWheelConfigJsonUseCase.kt
class GetWheelConfigJsonUseCase @Inject constructor(
    private val repo: SpinWheelRepository,
) {
    suspend operator fun invoke(): SpinWheelConfig? = repo.fetchAndCacheConfig()
}
```

---

## 4. Worker

A `@HiltWorker` is constructed by `HiltWorkerFactory` and gets all annotations
processed at build time. Inside `doWork()` we **await the use case before
updating the widget**, so the user can never see a stale "loading" screen when
downloads already finished.

```kotlin
// work/WidgetSyncWorker.kt
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val getConfig: GetWheelConfigJsonUseCase,
    private val downloadImages: DownloadWheelImagesUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 1) get JSON
        val config = getConfig() ?: return Result.retry()
        // 2) get images (awaits all 4)
        val ok = downloadImages(config)
        if (!ok) return Result.retry()

        // 3) notify widget — only now, after ALL data is on disk
        SpinWheelGlanceWidget().updateAll(appContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "spinwheel_sync",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                        .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                        .build()
                )
        }
    }
}
```

`updateAll` is just a small helper that fetches every Glance id and calls
`update(...)`:

```kotlin
suspend fun GlanceAppWidget.updateAll(context: Context) {
    val mgr = GlanceAppWidgetManager(context)
    mgr.getGlanceIds(this::class.java).forEach { update(context, it) }
}
```

---

## 5. Widget becomes state-driven

The widget no longer reads files directly. It observes the **local data
source** so any write triggers redraw:

```kotlin
// ui/widget/SpinWheelGlanceWidget.kt
class SpinWheelGlanceWidget @Inject constructor(
    private val local: LocalDataSource,
    private val repo: SpinWheelRepository,
) : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Pull the current state on every render
        val payload = repo.getCachedImagesBlocking()
        provideContent {
            when (payload) {
                null -> LoadingContent()
                else -> WheelContent(payload)
            }
        }
    }
}
```

`getCachedImagesBlocking()` reads 4 byte arrays from disk + decodes to
`Bitmap`. Returning `null` (any missing file) triggers the loading screen.

To get redraws on data change, the receiver / worker calls `updateAll` after
each successful sync (see Section 4).

---

## 6. Receiver

```kotlin
@AndroidEntryPoint
class SpinWheelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpinWheelGlanceWidget(get(), get())

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSyncWorker.enqueue(context)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, mgr, ids)
        WidgetSyncWorker.enqueue(context)
    }
}
```

`Hilt` is injected via `@AndroidEntryPoint` + the `get()` calls inside the
companion-like initialization (because `glanceAppWidget` is a property).

---

## 7. Dispatchers module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
}

@Retention(AnnotationRetention.BINARY) @Qualifier annotation class IoDispatcher
@Retention(AnnotationRetention.BINARY) @Qualifier annotation class DefaultDispatcher
@Retention(AnnotationRetention.BINARY) @Qualifier annotation class MainDispatcher
```

---

## 8. Hilt modules (`di/`)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun provideRemoteDataSource(impl: RemoteDataSourceImpl): RemoteDataSource = impl

    @Provides @Singleton
    fun provideLocalDataSource(impl: LocalDataSourceImpl): LocalDataSource = impl

    @Provides @Singleton
    fun provideRepository(impl: SpinWheelRepositoryImpl): SpinWheelRepository = impl
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerBindingModule {
    @Binds @IntoMap
    @WorkerKey(WidgetSyncWorker::class)
    abstract fun bindWidgetSyncWorker(factory: WidgetSyncWorker.Factory): ChildWorkerFactory
}
```

(`@WorkerKey` is a small helper you define; it mirrors the Hilt
multi-binding pattern for workers. Combined with `HiltWorkerFactory` it lets
WorkManager pick the right factory.)

---

## 9. Migration steps (sequence)

1. Add Hilt plugins + dependencies to both modules
2. Create `@HiltAndroidApp Application`
3. Disable default `WorkManagerInitializer` and switch to `HiltWorkerFactory`
4. Split `WidgetSyncService` → `RemoteDataSource` + `LocalDataSource`
5. Extract `SpinWheelRepository` into `domain/repository` (interface) +
   `data/repository` (impl)
6. Create `GetWheelConfigJsonUseCase` + `DownloadWheelImagesUseCase`
   (downloads 4 images with `awaitAll`)
7. Convert `WidgetSyncWorker` to `@HiltWorker`, inject use cases, call them
   sequentially — fetch config first, then download images, finally update
   widget
8. Update `SpinWheelGlanceWidget` to read state from local data source;
   default to "Loading"
9. Update `MainActivity` to inject needed use cases (via `@AndroidEntryPoint`)
10. Build + verify both modules compile, run lint, and exercise on device:
    * install fresh APK
    * add widget without opening app
    * expect wheel to render directly from the worker's awaited result

---

## 10. What this buys us

* **Testability** — repository / use cases can be unit-tested with fakes for
  remote + local; worker can be tested with an in-memory local
* **Single source of truth** — every component reads from `LocalDataSource`,
  which is observed; updates flow back to the widget automatically
* **No fire-and-forget** — neither the repository nor the worker returns
  until the use case finishes, and the widget update happens inside the same
  `doWork` so the process can't die
* **Configurability** — dispatchers/OkHttp client injected via `@Provides` —
  tests can swap them
* **Scalability** — adding more assets = add a new `AssetKey` enum entry
  only. The four-parallel `awaitAll` loop already generalises
