package com.liquid.liquidbaby

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Everything the Compose child home shows. ChildActivity writes these values from refresh(). */
class ChildHomeState {
    var statusText by mutableStateOf("")
    var statusColor by mutableStateOf(0xFF6B7785.toInt())
    var running by mutableStateOf(false)
    var hero by mutableStateOf("")
    var heroSub by mutableStateOf("")
    var address by mutableStateOf("")
    var vpnWarn by mutableStateOf(false)
    var camNeeded by mutableStateOf(false)
    var pairNote by mutableStateOf("")
    var batteryWarn by mutableStateOf("")
}

/** Classic views shown through AndroidView: the sound meter, the sound visual and the paired-parents list. */
class ChildViews(
    val meter: (Context) -> View,
    val sound: (Context) -> View,
    val parents: (Context) -> View
)

@Composable
fun ChildHomeTop(
    state: ChildHomeState,
    views: ChildViews,
    onMain: () -> Unit,
    onVpn: () -> Unit,
    onCam: () -> Unit,
    onPair: () -> Unit,
    onDark: () -> Unit,
    onBattery: () -> Unit
) {
    val text = Color(Look.TEXT)
    val sub = Color(Look.SUB)
    val glass = RoundedCornerShape(22.dp)
    val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
    val accentColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)
    val panel = Modifier.fillMaxWidth().background(Color(Look.BUTTON), glass).padding(16.dp)

    Text(Tr.pick("Child", "وحدة الطفل"), fontSize = 28.sp, color = text)

    Box(modifier = Modifier.background(Color(state.statusColor), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(state.statusText, color = Color.White, fontSize = 14.sp)
    }

    if (state.vpnWarn) {
        Button(onClick = onVpn, modifier = Modifier.fillMaxWidth(), shape = glass, colors = glassColors) {
            Text(
                Tr.pick("VPN is blocking the connection: open VPN settings", "الـ VPN يمنع الاتصال: افتح إعدادات الـ VPN"),
                fontSize = 14.sp
            )
        }
    }

    Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.hero, color = text, fontSize = 22.sp)
        Text(state.heroSub, color = sub, fontSize = 14.sp)
        Text(state.address, color = sub, fontSize = 13.sp)
        AndroidView(factory = { ctx -> views.meter(ctx) }, modifier = Modifier.fillMaxWidth().height(14.dp))
        AndroidView(factory = { ctx -> views.sound(ctx) }, modifier = Modifier.fillMaxWidth().height(70.dp))
        Text(Tr.pick("Sound in the room", "صوت الغرفة"), color = sub, fontSize = 13.sp)
    }

    Button(
        onClick = onMain,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = glass,
        colors = if (state.running) glassColors else accentColors
    ) {
        Text(
            Tr.pick(
                if (state.running) "Stop monitoring" else "Start monitoring",
                if (state.running) "أوقف المراقبة" else "ابدأ المراقبة"
            ),
            fontSize = 16.sp
        )
    }

    if (state.camNeeded) {
        Button(onClick = onCam, modifier = Modifier.fillMaxWidth(), shape = glass, colors = glassColors) {
            Text(
                Tr.pick("Allow the camera (needed for the live camera)", "اسمح بالكاميرا (لازمة للكاميرا المباشرة)"),
                fontSize = 14.sp
            )
        }
    }

    Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.pairNote, color = sub, fontSize = 14.sp)
        Button(onClick = onPair, modifier = Modifier.fillMaxWidth(), shape = glass, colors = accentColors) {
            Text(Tr.pick("Allow a new parent phone to pair", "السماح بإقران جوال أبوين جديد"))
        }
    }

    Text(
        Tr.pick(
            "Keep port forwarding and UPnP OFF on your router. The phones only talk on the home network.",
            "أبقِ توجيه المنافذ (Port Forwarding) و UPnP مطفيين في الراوتر. الجوالان يتصلان داخل الشبكة المنزلية فقط."
        ),
        color = sub,
        fontSize = 12.sp
    )
    Text(Tr.pick("Paired parent phones", "جوالات الأبوين المقترنة"), color = sub, fontSize = 13.sp)
    AndroidView(factory = { ctx -> views.parents(ctx) }, modifier = Modifier.fillMaxWidth())

    Text(Tr.pick("Options", "خيارات"), color = sub, fontSize = 13.sp)
    Button(onClick = onDark, modifier = Modifier.fillMaxWidth().height(52.dp), shape = glass, colors = glassColors) {
        Text(Tr.pick("Dark screen (touch to wake)", "شاشة مطفأة (المس للعودة)"), fontSize = 15.sp)
    }
    Button(onClick = onBattery, modifier = Modifier.fillMaxWidth().height(52.dp), shape = glass, colors = glassColors) {
        Text(Tr.pick("Keep running: battery settings", "البقاء في الخلفية: إعدادات البطارية"), fontSize = 15.sp)
    }
    Text(state.batteryWarn, color = sub, fontSize = 13.sp)
}
