package com.example.spinwheel.ui

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.spinwheel.di.SpinWheelGraph

class LoadWheelActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Clear any previous error and show loading state
        showLoading(context, glanceId)
        loadData(context, glanceId)
    }

    private suspend fun loadData(context: Context, glanceId: GlanceId) {
        val graph = SpinWheelGraph.get(context)

        val config = graph.getWheelConfigJsonUseCase()
        if (config == null) {
            showError(context, glanceId, "Could not fetch config.\nCheck Firebase Remote Config.")
            return
        }

        val ok = graph.downloadWheelImagesUseCase(config)
        if (!ok) {
            showError(context, glanceId, "Could not download images.\nCheck your network.")
            return
        }

        // Success — clear loading + error flags, show wheel
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY]  = false
            prefs.remove(SpinWheelGlanceWidget.ERROR_MESSAGE_KEY)
        }
        SpinWheelGlanceWidget().update(context, glanceId)
    }

    private suspend fun showLoading(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY] = true
            prefs.remove(SpinWheelGlanceWidget.ERROR_MESSAGE_KEY)
        }
        SpinWheelGlanceWidget().update(context, glanceId)
    }

    private suspend fun showError(context: Context, glanceId: GlanceId, message: String) {
        Log.e(TAG, "Load failed: $message")
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SpinWheelGlanceWidget.IS_LOADING_KEY]    = false
            prefs[SpinWheelGlanceWidget.ERROR_MESSAGE_KEY] = message
        }
        SpinWheelGlanceWidget().update(context, glanceId)
    }

    private companion object {
        const val TAG = "LoadWheelCallback"
    }
}
