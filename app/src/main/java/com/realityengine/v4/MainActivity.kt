package com.realityengine.v4

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(10, 10, 14))
        }

        val title = TextView(this).apply {
            text = "REALITY ENGINE"
            textSize = 25f
            setTextColor(Color.WHITE)
        }
        root.addView(title)

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 20)
        }
        root.addView(status)

        val number = EditText(this).apply {
            hint = "Phone number"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(number, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val call = Button(this).apply {
            text = "CALL"
            setOnClickListener {
                val value = number.text.toString().trim()
                if (value.isNotEmpty()) {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$value")))
                }
            }
        }
        root.addView(call)

        val defaultPhone = Button(this).apply {
            text = "MAKE DEFAULT PHONE APP"
            setOnClickListener { requestDefaultPhoneRole() }
        }
        root.addView(defaultPhone)

        val settings = Button(this).apply {
            text = "PHONE SETTINGS"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
        root.addView(settings)

        val recents = TextView(this).apply {
            text = "\nRECENTS\n\nNo calls yet"
            textSize = 17f
            setTextColor(Color.LTGRAY)
        }
        root.addView(recents)

        val contacts = TextView(this).apply {
            text = "\nCONTACTS\n\nContacts will be available after permission is granted."
            textSize = 17f
            setTextColor(Color.LTGRAY)
        }
        root.addView(contacts)

        setContentView(root)
        updateRoleStatus()
    }

    private fun updateRoleStatus() {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val isDefault = telecom.defaultDialerPackage == packageName
        status.text = if (isDefault) {
            "✓ DEFAULT PHONE APP\nReality Engine controls the phone role."
        } else {
            "PHONE APP NOT SET\nReality Engine can be used as the default phone app."
        }
    }

    private fun requestDefaultPhoneRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_DIALER_ROLE
                )
            }
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    companion object {
        private const val REQUEST_DIALER_ROLE = 1001
    }
}
