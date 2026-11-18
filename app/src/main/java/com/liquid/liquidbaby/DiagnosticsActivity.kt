package com.liquid.liquidbaby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Diagnostics: filter the log, and copy or share it with secrets removed. Built with Compose. */
class DiagnosticsActivity : ComponentActivity() {

    private var builtAr: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLookTo()
        builtAr = Tr.isAr()
        setContent { DiagnosticsScreen() }
    }

    override fun onResume() {
        super.onResume()
        if (builtAr != Tr.isAr()) recreate()
    }
}

private fun filterLog(text: String, filter: String): String = when (filter) {
    "errors" -> text.lines().filter { it.contains("ERROR") || it.contains("Exception") }.joinToString("\n")
    "connection" -> text.lines().filter {
        Regex("(?i)connect|parent|child|wi-fi|scan|address|broadcast|pair|listen|view|disconnect").containsMatchIn(it)
    }.joinToString("\n")
    else -> text
}

@Composable
private fun DiagnosticsScreen() {
    val ctx = LocalContext.current
    val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
    var filter by remember { mutableStateOf("all") }
    var refreshKey by remember { mutableStateOf(0) }

    val text = Color(Look.TEXT)
    val sub = Color(Look.SUB)
    val glass = RoundedCornerShape(18.dp)
    val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
    val selectedColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)

    val info = remember(refreshKey) { Diag.redact(Diag.deviceInfo(ctx)) }
    val log = remember(refreshKey, filter) { filterLog(Diag.readTail(30_000), filter) }
    val reportText = remember(refreshKey) { Diag.redact(Diag.deviceInfo(ctx) + "\n" + Diag.readTail(60_000)) }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(Tr.pick("Diagnostics", "التشخيص"), fontSize = 28.sp, color = text)
            Text(
                Tr.pick(
                    "Last errors and crashes. Secrets are removed before you copy or share.",
                    "آخر الأخطاء والأعطال. تُحذف الأسرار قبل النسخ أو المشاركة."
                ),
                fontSize = 14.sp,
                color = sub
            )

            FilterButton(Tr.pick("All", "الكل"), filter == "all", glass, glassColors, selectedColors) { filter = "all" }
            FilterButton(Tr.pick("Errors only", "الأخطاء فقط"), filter == "errors", glass, glassColors, selectedColors) { filter = "errors" }
            FilterButton(Tr.pick("Connection only", "الاتصال فقط"), filter == "connection", glass, glassColors, selectedColors) { filter = "connection" }

            Button(
                onClick = { copyToClipboard(ctx, "Liquid Baby diagnostics", reportText) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = glass,
                colors = glassColors
            ) { Text(Tr.pick("Copy", "نسخ"), fontSize = 15.sp) }

            Button(
                onClick = { shareText(ctx, "Liquid Baby diagnostics", reportText) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = glass,
                colors = glassColors
            ) { Text(Tr.pick("Share", "مشاركة"), fontSize = 15.sp) }

            Button(
                onClick = { refreshKey++ },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = glass,
                colors = glassColors
            ) { Text(Tr.pick("Refresh", "تحديث"), fontSize = 15.sp) }

            Button(
                onClick = {
                    Diag.clear()
                    refreshKey++
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = glass,
                colors = glassColors
            ) { Text(Tr.pick("Clear", "مسح"), fontSize = 15.sp) }

            Text(
                info + "\n" + (if (log.isEmpty()) Tr.pick("(no entries yet)", "(لا توجد سجلات بعد)") else log),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                color = text
            )
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    shape: RoundedCornerShape,
    normal: androidx.compose.material3.ButtonColors,
    chosen: androidx.compose.material3.ButtonColors,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = shape,
        colors = if (selected) chosen else normal
    ) {
        Text((if (selected) "✓ " else "") + label, fontSize = 15.sp)
    }
}
