package com.liquid.liquidbaby

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/** Everything the Compose home shows. ParentActivity writes these values from refresh(). */
class ComposeParentHomeState {
    var paired by mutableStateOf(false)
    var keysBroken by mutableStateOf(false)
    var childName by mutableStateOf("")
    var status by mutableStateOf("")
    var statusColor by mutableStateOf(0xFF6B7785.toInt())
    var running by mutableStateOf(false)
    var vpnWarning by mutableStateOf(false)
    var address by mutableStateOf("")
    var battery by mutableStateOf("")
    var batteryDot by mutableStateOf(0xFF8899AA.toInt())
    var temp by mutableStateOf("")
    var tempDot by mutableStateOf(0xFF8899AA.toInt())
    var listening by mutableStateOf(false)
}

/** Classic views that Compose shows through AndroidView. Each factory runs once and stores its view in ParentActivity. */
class ParentViews(
    val ring: (Context) -> View,
    val sound: (Context) -> View,
    val meter: (Context) -> View,
    val talk: (Context) -> View,
    val partner: (Context) -> View
)

@Composable
fun ParentHomeTop(
    state: ComposeParentHomeState,
    views: ParentViews,
    onToggle: () -> Unit,
    onVpn: () -> Unit,
    onLive: () -> Unit,
    onListen: () -> Unit,
    onPair: () -> Unit
) {
    val text = Color(Look.TEXT)
    val sub = Color(Look.SUB)
    val glass = RoundedCornerShape(22.dp)
    val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
    val accentColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)
    val panel = Modifier.fillMaxWidth().background(Color(Look.BUTTON), glass).padding(16.dp)

    Text(Tr.pick("Parent", "وحدة الأبوين"), fontSize = 28.sp, color = text)

    Box(
        modifier = Modifier
            .background(Color(state.statusColor), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(state.status, color = Color.White, fontSize = 14.sp)
    }

    Button(
        onClick = onToggle,
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

    if (state.vpnWarning) {
        Button(onClick = onVpn, modifier = Modifier.fillMaxWidth(), shape = glass, colors = glassColors) {
            Text(
                Tr.pick(
                    "VPN is blocking the connection: open VPN settings",
                    "الـ VPN يمنع الاتصال: افتح إعدادات الـ VPN"
                ),
                fontSize = 14.sp
            )
        }
    }

    if (!state.paired) {
        Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Tr.pick("No child phone yet", "لم يتم اقتران جوال طفل بعد"), color = text, fontSize = 17.sp)
            Button(onClick = onPair, modifier = Modifier.fillMaxWidth(), shape = glass, colors = accentColors) {
                Text(Tr.pick("Pair with the child phone", "اقرن مع جوال الطفل"))
            }
            Text(
                Tr.pick(
                    "On the child phone, tap 'Allow a new parent phone to pair'.",
                    "على جوال الطفل، اضغط 'السماح بإقران جوال أبوين جديد'."
                ),
                color = sub,
                fontSize = 13.sp
            )
            if (state.keysBroken) {
                Text(
                    Tr.pick("Pairing keys could not be read on this phone.", "تعذّر قراءة مفاتيح الاقتران على هذا الجوال."),
                    color = text,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    Column(modifier = panel, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AndroidView(factory = { ctx -> views.ring(ctx) }, modifier = Modifier.size(44.dp))
            Text(state.childName, color = text, fontSize = 17.sp)
        }
        Text(state.address, color = sub, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).background(Color(state.batteryDot), CircleShape))
            Text(state.battery, color = text, fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).background(Color(state.tempDot), CircleShape))
            Text(state.temp, color = sub, fontSize = 15.sp)
        }
        AndroidView(factory = { ctx -> views.meter(ctx) }, modifier = Modifier.fillMaxWidth().height(14.dp))
        AndroidView(factory = { ctx -> views.sound(ctx) }, modifier = Modifier.fillMaxWidth().height(70.dp))
        Text(Tr.pick("Sound in the room", "صوت الغرفة"), color = sub, fontSize = 13.sp)
    }

    Text(Tr.pick("Monitor", "المراقبة"), color = sub, fontSize = 13.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onLive,
            modifier = Modifier.weight(1f).height(92.dp),
            shape = glass,
            colors = glassColors
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Tr.pick("Live camera", "الكاميرا المباشرة"), fontSize = 16.sp)
                Text(Tr.pick("See the room", "شاهد الغرفة"), fontSize = 13.sp)
            }
        }
        Button(
            onClick = onListen,
            modifier = Modifier.weight(1f).height(92.dp),
            shape = glass,
            colors = if (state.listening) accentColors else glassColors
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Tr.pick(if (state.listening) "Stop" else "Listen", if (state.listening) "أوقف" else "استماع"),
                    fontSize = 16.sp
                )
                Text(
                    Tr.pick(
                        if (state.listening) "Listening to the room" else "Sound only",
                        if (state.listening) "يستمع للغرفة الآن" else "صوت فقط"
                    ),
                    fontSize = 13.sp
                )
            }
        }
    }

    AndroidView(factory = { ctx -> views.talk(ctx) }, modifier = Modifier.fillMaxWidth())
    AndroidView(factory = { ctx -> views.partner(ctx) }, modifier = Modifier.fillMaxWidth())
}
