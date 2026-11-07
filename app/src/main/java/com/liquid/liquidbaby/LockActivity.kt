package com.liquid.liquidbaby

import android.app.Activity
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputType
import android.widget.EditText

class LockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen {
            addTitle("Liquid Baby is locked")
            addBody("Enter your PIN to open the parent screen.")
            val input = EditText(context)
            input.hint = "PIN"
            input.setTextColor(Look.TEXT)
            input.setHintTextColor(Look.SUB)
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            addView(input)
            addButton("Unlock") {
                if (Lock.checkPin(input.text.toString())) {
                    Lock.unlocked()
                    finish()
                } else {
                    toast("Wrong PIN")
                }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                addButton("Use fingerprint") { askFingerprint() }
            }
        }
        if (Build.VERSION.SDK_INT >= 28) askFingerprint()
    }

    private fun askFingerprint() {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Liquid Baby")
                .setDescription("Confirm with your fingerprint")
                .setNegativeButton("Use PIN", mainExecutor) { _, _ -> }
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
