package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class DialScreen(
    private val context: Context,
    private val contacts: ContactIndex,
    private val onCall: (String) -> Unit,
) {
    val number = EditText(context)
    val contactMatch = TextView(context)
    val error = TextView(context)

    private val cyan = RealityVisuals.Colors.Cyan
    private val green = RealityVisuals.Colors.Green
    private val magenta = RealityVisuals.Colors.Magenta
    private val primaryText = RealityVisuals.Colors.Text
    private val muted = RealityVisuals.Colors.TextDim
    private val keyFill = Color.rgb(5, 18, 31)

    fun build(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }

        root.addView(TextView(context).apply {
            text = "SECURE DIAL // NATIVE CELLULAR"
            gravity = Gravity.CENTER
            RealityVisuals.styleMicroLabel(this, magenta)
        }, LinearLayout.LayoutParams(-1, dp(24)))

        val numberPlate = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(context, RealityVisuals.Colors.PanelStrong, cyan, 18f)
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }

        number.apply {
            hint = "Enter number to call"
            setHintTextColor(muted)
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = null
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isSingleLine = true
            RealityTypography.displayMedium(this, 25f)
        }
        numberPlate.addView(number, LinearLayout.LayoutParams(-1, dp(54)))

        contactMatch.apply {
            setTextColor(cyan)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            RealityTypography.displayMedium(this, 12.5f)
        }
        numberPlate.addView(contactMatch, LinearLayout.LayoutParams(-1, dp(24)))
        root.addView(numberPlate, LinearLayout.LayoutParams(-1, dp(88)).apply {
            setMargins(0, 0, 0, dp(9))
        })

        number.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val raw = s?.toString().orEmpty()
                val digits = raw.filter(Char::isDigit)
                val name = if (digits.length >= 3) contacts.resolveName(raw) else null
                contactMatch.text = name.orEmpty()
                contactMatch.visibility = if (name.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
            }
        })

        val grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val keys = arrayOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to "",
        )
        keys.forEach { (digit, letters) ->
            val key = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = RealityVisuals.panel(context, keyFill, Color.rgb(18, 112, 142), 22f)
                isClickable = true
                isFocusable = true
                addView(TextView(context).apply {
                    text = digit
                    setTextColor(primaryText)
                    gravity = Gravity.CENTER
                    RealityTypography.displayMedium(this, 23f)
                })
                if (letters.isNotEmpty()) addView(TextView(context).apply {
                    text = letters
                    setTextColor(muted)
                    gravity = Gravity.CENTER
                    RealityTypography.technical(this, 8.5f)
                })
                setOnClickListener {
                    RealityVisuals.pulseOnce(this)
                    number.append(digit)
                }
            }
            grid.addView(key, GridLayout.LayoutParams().apply {
                width = dp(80)
                height = dp(72)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(9), dp(6), dp(9), dp(6))
            })
        }
        root.addView(grid)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val backspace = TextView(context).apply {
            text = "⌫"
            setTextColor(cyan)
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(context, keyFill, cyan, 20f)
            RealityTypography.displayMedium(this, 22f)
            setOnClickListener {
                val value = number.text
                if (value.isNotEmpty()) value.delete(value.length - 1, value.length)
            }
            setOnLongClickListener {
                number.text.clear()
                true
            }
        }
        actions.addView(backspace, LinearLayout.LayoutParams(dp(74), dp(58)).apply {
            setMargins(0, dp(14), dp(10), dp(4))
        })

        val call = TextView(context).apply {
            text = "CONNECT"
            setTextColor(Color.rgb(0, 28, 22))
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(context, green, green, 22f)
            RealityTypography.technical(this, 10f)
            setOnClickListener {
                RealityVisuals.pulseOnce(this)
                onCall(number.text.toString().trim())
            }
        }
        actions.addView(call, LinearLayout.LayoutParams(dp(142), dp(66)).apply {
            setMargins(dp(10), dp(10), 0, dp(4))
        })
        root.addView(actions)

        error.apply {
            setTextColor(Color.rgb(255, 91, 122))
            gravity = Gravity.CENTER
            RealityTypography.technical(this, 11f)
        }
        root.addView(error, LinearLayout.LayoutParams(-1, dp(22)))
        return root
    }

    fun setNumber(value: String) {
        number.setText(value)
        number.setSelection(number.length())
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
