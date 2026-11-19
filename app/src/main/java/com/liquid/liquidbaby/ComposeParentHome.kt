package com.liquid.liquidbaby

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
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
    var talking by mutableStateOf(false)
    var pressMode by mutableStateOf(false)
    var partnerConnected by mutableStateOf(false)
    var partnerSending by mutableStateOf(false)
    var partnerTalking by mutableStateOf(false)
}

/** Classic views that Compose shows through AndroidView. Each factory runs once and stores its view in ParentActivity. */
class ParentViews(
    val ring: (Context) -> View,
    val sound: (Context) -> View,
    val meter: (Context) -> View
)

@Composable
fun ParentHomeTop(
    state: ComposeParentHomeState,
    views: ParentViews,
    onToggle: () -> Unit,
    onVpn: () -> Unit,
    onLive: () -> Unit,
    onListen: () -> Unit,
    onPair: () -> Unit,
    onChildHoldStart: () -> Unit,
    onChildHoldEnd: () -> Unit,
    onChildTap: () -> Unit,
    onPartnerStart: () -> Unit,
    onPartnerEnd: () -> Unit,
    onPartnerTap: () -> Unit,
    onTalkMode: (Boolean) -> Unit
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

    val childIdle = if (state.pressMode) Tr.pick("Tap to talk", "اضغط للتحدث")
    else Tr.pick("Hold to talk", "اضغط مع الاستمرار للتحدث")
    val childTalking = if (state.pressMode) Tr.pick("Talking… tap to stop", "تتحدث… اضغط للإيقاف")
    else Tr.pick("Talking… release to stop", "تتحدث… ارفع إصبعك للإيقاف")
    TalkCard(
        text = if (state.talking) childTalking else childIdle,
        pressMode = state.pressMode,
        active = state.talking,
        enabled = true,
        dim = false,
        onHoldStart = onChildHoldStart,
        onHoldEnd = onChildHoldEnd,
        onTap = onChildTap,
        onMode = onTalkMode,
        notice = ""
    )

    val partnerSending = state.partnerSending
    val partnerIdle = if (state.pressMode) Tr.pick("Tap to talk to partner", "اضغط للتحدث مع الشريك")
    else Tr.pick("Hold to talk to partner", "اضغط مع الاستمرار للتحدث مع الشريك")
    val partnerTalkingText = if (state.pressMode) Tr.pick("Talking… tap to stop", "أتحدث… اضغط للإيقاف")
    else Tr.pick("Talking… release to stop", "أتحدث… ارفع إصبعك للإيقاف")
    val partnerText = when {
        partnerSending -> partnerTalkingText
        !state.partnerConnected -> partnerIdle + "\n" + Tr.pick("Partner not connected", "الشريك غير متصل")
        else -> partnerIdle
    }
    TalkCard(
        text = partnerText,
        pressMode = state.pressMode,
        active = partnerSending,
        enabled = state.partnerConnected || partnerSending,
        dim = !state.partnerConnected && !partnerSending,
        onHoldStart = onPartnerStart,
        onHoldEnd = onPartnerEnd,
        onTap = onPartnerTap,
        onMode = onTalkMode,
        notice = if (state.partnerTalking) Tr.pick("Partner is talking to you", "الشريك يتحدث إليك") else ""
    )
}

/**
 * One talk card, shared by the child and partner buttons: hold or tap. The Hold / Tap switch sits on its own
 * row at the top, the label is below it, so the two never overlap.
 */
@Composable
fun TalkCard(
    text: String,
    pressMode: Boolean,
    active: Boolean,
    enabled: Boolean,
    dim: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onTap: () -> Unit,
    onMode: (Boolean) -> Unit,
    notice: String
) {
    var pressed by remember { mutableStateOf(false) }
    val textColor = Color(Look.TEXT)
    val bg = if (active) Color(Themes.current().accent).copy(alpha = 0.6f) else Color(Look.BUTTON)
    val pointer = if (enabled) Modifier.pointerInput(pressMode) {
        detectTapGestures(
            onPress = {
                pressed = true
                try {
                    if (!pressMode) {
                        onHoldStart()
                        tryAwaitRelease()
                        onHoldEnd()
                    } else {
                        tryAwaitRelease()
                    }
                } finally {
                    pressed = false
                }
            },
            onTap = { if (pressMode) onTap() }
        )
    } else Modifier

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .alpha(if (dim) 0.45f else 1f)
                .scale(if (pressed) 0.97f else 1f)
                .background(bg, RoundedCornerShape(26.dp))
                .then(pointer)
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .background(Color(0x66000000), RoundedCornerShape(14.dp))
                        .padding(start = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (pressMode) Tr.pick("Tap", "نقرة") else Tr.pick("Hold", "ضغط"),
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Switch(checked = pressMode, onCheckedChange = onMode)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (notice.isNotEmpty()) {
            Text(notice, fontSize = 12.sp, color = Color(0xFFB4C0DC), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
