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
import android.widget.GridLayout
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
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(10, 10, 14))
        }

        root.addView(TextView(this).apply {
            text = "REALITY ENGINE"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        root.addView(status)

        number = EditText(this).apply {
            hint = "Phone number"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(number, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val keypad = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = true
        }
        val keys = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        keys.forEach { key ->
            keypad.addView(Button(this).apply {
                text = key
                textSize = 22f
                setOnClickListener { number.append(key) }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = 72
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        root.addView(keypad, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(Button(this).apply {
            text = "⌫"
            setOnClickListener {
                val text = number.text
                if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
            }
        })

        root.addView(Button(this).apply {
            text = "CALL"
            textSize = 18f
            setOnClickListener { placeCall(number.text.toString().trim()) }
        })

        error = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        root.addView(error)

        root.addView(Button(this).apply {
            text = "MAKE DEFAULT PHONE APP"
            setOnClickListener { requestDefaultPhoneRole() }
        })

        root.addView(Button(this).apply {
            text = "PHONE SETTINGS"
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        })

        root.addView(TextView(this).apply {
            text = "\nRECENTS\n\nNo calls yet\n\nCONTACTS\n\nContacts will be added in the next phone layer."
            textSize = 16f
            setTextColor(Color.LTGRAY)
        })

        setContentView(root)
        updateRoleStatus()
    }

    private fun updateRoleStatus() {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        status.text = if (telecom.defaultDialerPackage == packageName) {
            "✓ DEFAULT PHONE APP"
        } else {
            "PHONE APP NOT SET"
        }
    }

    private fun requestDefaultPhoneRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
            } else updateRoleStatus()
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun placeCall(value: String) {
        error.text = ""
        if (value.isEmpty()) {
            error.text = "Enter a phone number first."
            return
        }
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", value, null)
        if (telecom.defaultDialerPackage != packageName) {
            error.text = "Reality Engine is not the default phone app."
            return
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE)
            return
        }
        try {
            telecom.placeCall(uri, null)
        } catch (e: SecurityException) {
            error.text = "Call permission was not granted."
        } catch (e: Exception) {
            error.text = "Unable to place call: ${e.javaClass.simpleName}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PHONE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            placeCall(number.text.toString().trim())
        } else if (requestCode == REQUEST_CALL_PHONE) {
            error.text = "Phone permission is required to place calls."
        }
    }

    companion object {
        private const val REQUEST_DIALER_ROLE = 1001
        private const val REQUEST_CALL_PHONE = 1002
    }
}
