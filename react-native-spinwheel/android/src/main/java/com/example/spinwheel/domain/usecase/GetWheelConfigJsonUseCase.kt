package com.example.spinwheel.domain.usecase

import com.example.spinwheel.domain.SpinWheelRepository

/**
 * Use case: fetch the `wheel_config` JSON from [configUrl] and persist it.
 *
 * Behaviourally equivalent to the Firebase-RC variant but driven by the
 * JS bridge's URL argument instead of a hard-coded RC key.
 */
class GetWheelConfigJsonUseCase(
    private val repository: SpinWheelRepository,
) {
    suspend operator fun invoke(configUrl: String): com.example.spinwheel.domain.SpinWheelConfig? =
        repository.fetchAndCacheConfig(configUrl)
}
