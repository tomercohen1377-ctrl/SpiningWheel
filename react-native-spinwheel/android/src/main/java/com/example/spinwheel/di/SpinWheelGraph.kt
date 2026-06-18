package com.example.spinwheel.di

import android.content.Context
import com.example.spinwheel.data.local.LocalDataSource
import com.example.spinwheel.data.local.LocalDataSourceImpl
import com.example.spinwheel.data.remote.RemoteDataSource
import com.example.spinwheel.data.remote.RemoteDataSourceImpl
import com.example.spinwheel.data.repository.SpinWheelRepositoryImpl
import com.example.spinwheel.domain.SpinWheelRepository
import com.example.spinwheel.domain.usecase.DownloadWheelImagesUseCase
import com.example.spinwheel.domain.usecase.GetWheelConfigJsonUseCase

/**
 * Manual dependency-injection container.
 *
 * Holds **one instance** of each dependency for the lifetime of the
 * application process — analogous to Hilt's `@Singleton` scope. Created
 * lazily on first access via [SpinWheelGraph.get(context)].
 *
 * For Hilt migration, replace each `provide*` block below with a Hilt
 * `@Provides @Singleton` function and `@Inject` each use case constructor;
 * the call sites (widget, bridge) stay identical.
 */
object SpinWheelGraph {

    @Volatile private var INSTANCE: Graph? = null

    fun get(context: Context): Graph =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Graph(context.applicationContext).also { INSTANCE = it }
        }

    class Graph(appContext: Context) {

        // ─── Data sources (singletons in this process) ─────────────────── //
        val remote: RemoteDataSource = RemoteDataSourceImpl()
        val local: LocalDataSource   = LocalDataSourceImpl(appContext)

        // ─── Repository (singleton) ─────────────────────────────────────── //
        val repository: SpinWheelRepository =
            SpinWheelRepositoryImpl(remote, local)

        // ─── Use cases ─────────────────────────────────────────────────── //
        val getWheelConfigJsonUseCase = GetWheelConfigJsonUseCase(repository)
        val downloadWheelImagesUseCase = DownloadWheelImagesUseCase(remote, local, repository)
    }
}
