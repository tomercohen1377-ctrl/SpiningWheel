package com.example.spinwheel.data.repository

import com.example.spinwheel.data.WheelConfigResponse
import com.example.spinwheel.data.local.AssetKey
import com.example.spinwheel.data.local.ImageUrls
import com.example.spinwheel.data.local.LocalDataSource
import com.example.spinwheel.data.remote.RemoteDataSource
import com.example.spinwheel.domain.SpinWheelConfig
import com.example.spinwheel.domain.SpinWheelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Concrete [SpinWheelRepository]. Singleton — see
 * [com.example.spinwheel.di.SpinWheelGraph].
 */
class SpinWheelRepositoryImpl(
    private val remote: RemoteDataSource,
    private val local: LocalDataSource,
) : SpinWheelRepository {

    override suspend fun fetchAndCacheConfig(configUrl: String): SpinWheelConfig? {
        val rawJson = remote.fetchConfigJson(configUrl) ?: return null
        local.saveConfigUrl(configUrl)
        local.saveConfigJson(rawJson)
        return parseConfig(rawJson)
    }

    override suspend fun getCachedConfig(): SpinWheelConfig? {
        val raw = local.getConfigJson() ?: return null
        return parseConfig(raw)
    }

    override suspend fun saveImageUrls(urls: ImageUrls) = local.saveImageUrls(urls)

    override suspend fun getImageUrls(): ImageUrls = local.getImageUrls()

    override fun getImageBytes(key: AssetKey): ByteArray? = local.getImageBytes(key)

    override suspend fun getLastSync(): Long = local.getLastSync()

    override fun observeChanges(): Flow<Unit> = local.updates

    override suspend fun clear() = local.clear()

    // ───────────────────────────────────────────────────────────────────── //

    /** Parses the `wheel_config` JSON to a domain model. */
    private fun parseConfig(rawJson: String): SpinWheelConfig? {
        return try {
            val response = json.decodeFromString(WheelConfigResponse.serializer(), rawJson)
            val item = response.data.firstOrNull() ?: return null
            val host = item.network.assets.host.trim()
            if (host.isBlank()) return null
            SpinWheelConfig(
                host           = host,
                spinDurationMs = item.wheel.rotation.duration,
                minSpins       = item.wheel.rotation.minimumSpins,
                maxSpins       = item.wheel.rotation.maximumSpins,
                assetIds       = mapOf(
                    AssetKey.BG    to item.wheel.assets.bg,
                    AssetKey.WHEEL to item.wheel.assets.wheel,
                    AssetKey.FRAME to item.wheel.assets.wheelFrame,
                    AssetKey.SPIN  to item.wheel.assets.wheelSpin,
                ),
            )
        } catch (t: Throwable) {
            null
        }
    }
}
