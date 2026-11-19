package com.liquid.liquidbaby

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose page for a classic screen: Compose provides the scrolling, the system-bar padding and the
 * text direction; the screen's own views are built inside by makeRoot.
 */
@Composable
fun ClassicPage(makeRoot: (Context) -> View) {
    val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            AndroidView(factory = makeRoot, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Same background, blur and night-mode setup the classic screens used. */
fun Activity.applyLookTo() {
    val nightBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    Themes.systemDark = nightBits == Configuration.UI_MODE_NIGHT_YES
    val blur = Build.VERSION.SDK_INT >= 31 && Themes.current().blur > 0
    window.setBackgroundDrawable(Look.background(if (blur) 210 else 255))
    if (blur) {
        try {
            window.setBackgroundBlurRadius(Themes.current().blur)
        } catch (e: Throwable) {
            Diag.i("Window blur not available: " + e.message)
        }
    }
}

/** A screen that can ask for the microphone and get the answer back through the Activity Result API. */
interface MicHost {
    fun askMic(onGranted: () -> Unit)
}

/** Holds one microphone request at a time: the callback runs only if the permission is granted. */
class MicAsk(private val host: androidx.activity.ComponentActivity) : MicHost {
    private var pending: (() -> Unit)? = null

    private val launcher = host.registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val callback = pending
        pending = null
        if (granted) {
            callback?.invoke()
        } else {
            android.widget.Toast.makeText(
                host,
                Tr.pick("Allow the microphone to talk.", "اسمح بالميكروفون للتحدث."),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun askMic(onGranted: () -> Unit) {
        pending = onGranted
        launcher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}

/** Everything that changes how a screen looks. A screen recreates itself when this changes. */
object ThemeWatch {
    fun key(): String = Themes.mode() + "|" + Themes.style() + "|" + Themes.isDark() + "|" + Tr.isAr()
}

/** Screen-in animation: the newer call where the phone has it, the older one otherwise. */
fun Activity.animateScreenIn() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.screen_in, R.anim.screen_out)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_in, R.anim.screen_out)
    }
}
