package com.example.spiningwheel

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spinwheel.ui.SpinWheelWidgetReceiver

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AddWidgetScreen()
            }
        }
    }
}

@Composable
private fun AddWidgetScreen() {
    val context = LocalContext.current

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Spin Wheel Widget",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Tap the button below to add the Spin Wheel widget to your home screen.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            Button(onClick = { requestAddWidget(context) }) {
                Text(text = "Add Widget to Home Screen")
            }
        }
    }
}

/**
 * Uses [AppWidgetManager.requestPinAppWidget] to ask the launcher to add
 * the Spin Wheel widget. Available on Android 8.0+ on launchers that
 * support pinned widgets (most modern launchers, including Pixel Launcher
 * and the stock AOSP launcher). On launchers that don't support pinning
 * we fall back to a toast asking the user to long-press the home screen.
 */
private fun requestAddWidget(context: android.content.Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, SpinWheelWidgetReceiver::class.java)

    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        appWidgetManager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(
            context,
            "Your launcher doesn't support adding widgets programmatically. " +
                "Long-press your home screen and pick the Spin Wheel widget manually.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
