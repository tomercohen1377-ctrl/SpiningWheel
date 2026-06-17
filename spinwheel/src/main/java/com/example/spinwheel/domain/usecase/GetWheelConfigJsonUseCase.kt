package com.example.spinwheel.domain.usecase

import com.example.spinwheel.domain.SpinWheelConfig
import com.example.spinwheel.domain.SpinWheelRepository

/**
 * Single-purpose use case for **step 1** of the worker pipeline:
 *
 * ```kotlin
 * val cfg = getWheelConfigJsonUseCase()
 * if (cfg == null) Result.retry()
 * ```
 *
 * Suspend — returns immediately after the repo finishes saving to local storage.
 */
class GetWheelConfigJsonUseCase(
    private val repository: SpinWheelRepository,
) {
    suspend operator fun invoke(): SpinWheelConfig? = repository.fetchAndCacheConfig()
}
