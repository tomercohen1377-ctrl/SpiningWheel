package com.example.spinwheel.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────── //
//  Matches the ACTUAL remote JSON schema:                                     //
//  https://drive.google.com/uc?export=download&id=1TCOGD961TPmtp2EQbvOj6T6wQVkZbjur
//                                                                             //
//  {                                                                          //
//    "data": [ { "id": "wheel_minimal", "network": { "assets": { "host": "" },//
//                "wheel": { "rotation": { ... }, "assets": { "bg": "", ... } }//
//             } ],                                                            //
//    "meta": { "version": 1, "copyright": "Tapp" }                           //
//  }                                                                          //
// ─────────────────────────────────────────────────────────────────────────── //

/** Root response wrapper returned by the remote config endpoint. */
@Serializable
data class WheelConfigResponse(
    val data: List<WheelConfigItem>,
    val meta: MetaInfo
)

/** Metadata block attached to every config response. */
@Serializable
data class MetaInfo(
    val version: Int = 1,
    val copyright: String = ""
)

/**
 * One widget configuration entry.
 * `data` is a list but in practice contains one item (`data[0]`).
 */
@Serializable
data class WheelConfigItem(
    val id: String,
    val name: String = "",
    val type: String = "Widget",
    val network: NetworkConfig,
    val wheel: WheelSettings
)

// ── Network ──────────────────────────────────────────────────────────────── //

@Serializable
data class NetworkConfig(
    val attributes: NetworkAttributes = NetworkAttributes(),
    val assets: AssetsNetwork
)

@Serializable
data class NetworkAttributes(
    val refreshInterval: Int = 300,
    val networkTimeout: Long = 30_000L,
    val retryAttempts: Int = 3,
    /** Cache TTL in seconds. */
    val cacheExpiration: Long = 3_600L,
    val debugMode: Boolean = false
)

/**
 * Base-URL prefix for all image assets.
 *
 * The full asset URL is constructed as:
 * ```
 * fullUrl = host + assetId
 * ```
 *
 * For Google Drive:
 * ```
 * host  = "https://drive.google.com/uc?export=download&id="
 * asset = "<DRIVE_FILE_ID>"
 * ```
 */
@Serializable
data class AssetsNetwork(
    val host: String
)

// ── Wheel ────────────────────────────────────────────────────────────────── //

@Serializable
data class WheelSettings(
    val rotation: RotationSettings = RotationSettings(),
    val assets: WheelAssets
)

@Serializable
data class RotationSettings(
    /** Duration of one spin animation in milliseconds. */
    val duration: Long = 2_000L,
    val minimumSpins: Int = 3,
    val maximumSpins: Int = 5,
    /** Easing curve name — used for documentation/analytics; Compose uses its own easing. */
    val spinEasing: String = "easeInOutCubic"
)

/**
 * Asset identifiers / relative paths for each image layer.
 * Full URL = [AssetsNetwork.host] + [WheelAssets.<field>]
 *
 * Field names match the JSON exactly (`bg`, `wheelFrame`, `wheelSpin`, `wheel`).
 */
@Serializable
data class WheelAssets(
    /** Background image that fills the widget (bg.png). */
    val bg: String,
    /** Static frame overlay centered on the wheel (wheel-frame.png). */
    val wheelFrame: String,
    /** Spin button on top of the frame (wheel-spin.png). */
    val wheelSpin: String,
    /** The spinning wheel image (wheel.png). */
    val wheel: String
)
