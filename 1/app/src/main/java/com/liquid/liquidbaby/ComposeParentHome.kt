package com.liquid.liquidbaby

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The parent home screen in Jetpack Compose. The layout is written in Compose; the complex parts
 * (the talk buttons, the battery ring and the sound visual) are the existing views, shown inside
 * Compose, so their touch, recording and level logic stays exactly as it is.
 */
class ComposeParentHomeState {
    var status by mutableStateOf("")
    var address by mutableStateOf("")
    var battery by mutableStateOf("")
    var running by mutableStateOf(false)
    var connected by mutableStateOf(false)
}

@Composable
fun ParentHomeScreen(
    state: ComposeParentHomeState,
    onToggleMonitoring: () -> Unit,
    onLive: () -> Unit,
    onListen: () -> Unit,
    onRecordings: () -> Unit,
    talkView: (Activity) -> View,
    partnerView: (Activity) -> View,
    ringView: (Activity) -> View,
    soundView: (Activity) -> View,
    activity: Activity
) {
    val glass = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onToggleMonitoring,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = glass
        ) {
            Text(
                Tr.pick(
                    if (state.running) "Stop monitoring" else "Start monitoring",
                    if (state.running) "أوقف المراقبة" else "ابدأ المراقبة"
                ),
                fontSize = 16.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF, 0xFF, 0xFF, 0x2E), glass)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AndroidView(factory = { ringView(activity) }, modifier = Modifier.size(44.dp))
                Text(state.status, fontSize = 15.sp)
            }
            Text(state.address, fontSize = 13.sp)
            Text(state.battery, fontSize = 15.sp)
            AndroidView(factory = { soundView(activity) }, modifier = Modifier.fillMaxWidth().height(70.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onLive, modifier = Modifier.weight(1f).height(72.dp), shape = glass) {
                Text(Tr.pick("Live camera", "الكاميرا المباشرة"), fontSize = 15.sp)
            }
            Button(onClick = onListen, modifier = Modifier.weight(1f).height(72.dp), shape = glass) {
                Text(Tr.pick("Listen", "استماع"), fontSize = 15.sp)
            }
        }

        AndroidView(factory = { talkView(activity) }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { partnerView(activity) }, modifier = Modifier.fillMaxWidth())

        Button(onClick = onRecordings, modifier = Modifier.fillMaxWidth().height(52.dp), shape = glass) {
            Text(Tr.pick("Recordings", "التسجيلات"), fontSize = 15.sp)
        }
    }
}
