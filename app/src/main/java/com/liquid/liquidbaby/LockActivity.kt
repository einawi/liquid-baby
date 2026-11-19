package com.liquid.liquidbaby

import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lock screen for the parent phone: PIN, and fingerprint when the phone supports it. Built with Compose. */
class LockActivity : ComponentActivity() {

    private var shownKey = ""

    private var builtAr: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLookTo()
        shownKey = ThemeWatch.key()
        builtAr = Tr.isAr()
        setContent { LockScreen() }
        if (Build.VERSION.SDK_INT >= 28) askFingerprint()
    }

    override fun onResume() {
        super.onResume()
        if (shownKey != ThemeWatch.key()) {
            recreate()
            return
        }
        if (builtAr != Tr.isAr()) recreate()
    }

    private fun unlockWith(pin: String, onWrong: () -> Unit) {
        if (Lock.checkPin(pin)) {
            Lock.unlocked()
            finish()
        } else {
            onWrong()
        }
    }

    @Composable
    private fun LockScreen() {
        val dir = if (Tr.isAr()) LayoutDirection.Rtl else LayoutDirection.Ltr
        val text = Color(Look.TEXT)
        val sub = Color(Look.SUB)
        val glass = RoundedCornerShape(18.dp)
        val glassColors = ButtonDefaults.buttonColors(containerColor = Color(Look.BUTTON), contentColor = text)
        val accentColors = ButtonDefaults.buttonColors(containerColor = Color(Themes.current().accent), contentColor = Color.White)
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(Tr.pick("Liquid Baby is locked", "مراقب الطفل مقفل"), fontSize = 28.sp, color = text)
                Text(
                    Tr.pick("Enter your PIN to open the parent screen.", "أدخل الرمز لفتح شاشة الأب."),
                    fontSize = 15.sp,
                    color = sub
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(8)
                        error = ""
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotEmpty()) {
                    Text(error, fontSize = 14.sp, color = text)
                }

                Button(
                    onClick = {
                        unlockWith(pin) {
                            error = Tr.pick("Wrong PIN", "الرمز غير صحيح")
                            pin = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = glass,
                    colors = accentColors
                ) { Text(Tr.pick("Unlock", "فتح القفل"), fontSize = 16.sp) }

                if (Build.VERSION.SDK_INT >= 28) {
                    Button(
                        onClick = { askFingerprint() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = glass,
                        colors = glassColors
                    ) { Text(Tr.pick("Use fingerprint", "استخدم البصمة"), fontSize = 15.sp) }
                }
            }
        }
    }

    private fun askFingerprint() {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Liquid Baby")
                .setDescription(Tr.pick("Confirm with your fingerprint", "أكّد بالبصمة"))
                .setNegativeButton(Tr.pick("Use PIN", "استخدم الرمز"), mainExecutor) { _, _ -> }
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    Lock.unlocked()
                    finish()
                }
            })
        } catch (e: Exception) {
            Diag.i("Fingerprint not available: " + e.message)
        }
    }
}
