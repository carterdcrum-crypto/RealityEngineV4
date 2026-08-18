package com.realityengine.v4

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var number: EditText
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildPhoneUi()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateRoleStatus()
    }

    private fun buildPhoneUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 20)
            setBackgroundColor(Color.rgb(10, 10, 14))
        }
        root.addView(TextView(this).apply {
            text = "REALITY ENGINE"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        status = TextView(this).apply {
            textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        root.addView(status)
        number = EditText(this).apply {
            hint = "Phone number"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 26f; gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isSingleLine = true
        }
        root.addView(number, LinearLayout.LayoutParams(-1, 58.dp()))

        val keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val keys = arrayOf("1","2","3","4","5","6","7","8","9","*","0","#")
        for (row in 0 until 4) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (col in 0 until 3) {
                val key = keys[row * 3 + col]
                rowLayout.addView(Button(this).apply {
                    text = key; textSize = 22f; minWidth = 0; minHeight = 0
                    setOnClickListener { number.append(key) }
                }, LinearLayout.LayoutParams(0, 58.dp(), 1f).apply { setMargins(4, 4, 4, 4) })
            }
            keypad.addView(rowLayout, LinearLayout.LayoutParams(-1, 66.dp()))
        }
        root.addView(keypad)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(Button(this).apply {
            text = "⌫"; setOnClickListener { val t = number.text; if (t.isNotEmpty()) t.delete(t.length - 1, t.length) }
        }, LinearLayout.LayoutParams(0, 58.dp(), 1f).apply { setMargins(4, 4, 4, 4) })
        controls.addView(Button(this).apply {
            text = "CALL"; textSize = 18f; setOnClickListener { placeCall(number.text.toString().trim()) }
        }, LinearLayout.LayoutParams(0, 58.dp(), 2f).apply { setMargins(4, 4, 4, 4) })
        root.addView(controls)
        error = TextView(this).apply { textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 4, 0, 4) }
        root.addView(error)
        root.addView(Button(this).apply { text = "MAKE DEFAULT PHONE APP"; setOnClickListener { requestDefaultPhoneRole() } })
        root.addView(Button(this).apply { text = "PHONE SETTINGS"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } })
        root.addView(TextView(this).apply {
            text = "RECENTS\n\nNo calls yet\n\nCONTACTS\n\nContacts will be added next."
            textSize = 16f; setTextColor(Color.LTGRAY)
        })
        setContentView(root); updateRoleStatus()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun updateRoleStatus() {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        status.text = if (telecom.defaultDialerPackage == packageName) "✓ DEFAULT PHONE APP" else "PHONE APP NOT SET"
    }

    private fun requestDefaultPhoneRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
        } else updateRoleStatus()
    }

    private fun placeCall(value: String) {
        error.text = ""
        if (value.isEmpty()) { error.text = "Enter a phone number first."; return }
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (telecom.defaultDialerPackage != packageName) { error.text = "Reality Engine is not the default phone app."; return }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE); return
        }
        try { telecom.placeCall(Uri.fromParts("tel", value, null), null) }
        catch (e: SecurityException) { error.text = "Call permission was not granted." }
        catch (e: Exception) { error.text = "Unable to place call: ${e.javaClass.simpleName}" }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PHONE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) placeCall(number.text.toString().trim())
        else if (requestCode == REQUEST_CALL_PHONE) error.text = "Phone permission is required to place calls."
    }

    companion object { private const val REQUEST_DIALER_ROLE = 1001; private const val REQUEST_CALL_PHONE = 1002 }
}
