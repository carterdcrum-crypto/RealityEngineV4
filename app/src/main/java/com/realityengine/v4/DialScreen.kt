package com.realityengine.v4

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
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
    private val keyFill = Color.rgb(2, 17, 31)

    fun build(): View {
        val root = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        root.addView(TextView(context).apply {
            text = "SECURE DIAL // NATIVE CELLULAR"
            gravity = Gravity.CENTER
            setTextColor(magenta)
            letterSpacing = .17f
            RealityTypography.technical(this, 10f)
        }, LinearLayout.LayoutParams(-1, dp(32)).apply {
            setMargins(dp(14), 0, dp(14), dp(8))
        })

        val numberPlate = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(
                context,
                fill = Color.rgb(2, 21, 39),
                stroke = cyan,
                radiusDp = 16f,
                strokeDp = 2,
            )
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }

        number.apply {
            hint = "ENTER NUMBER"
            setHintTextColor(Color.rgb(74, 116, 143))
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = null
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isSingleLine = true
            includeFontPadding = false
            RealityTypography.displayMedium(this, 33f)
        }
        numberPlate.addView(number, LinearLayout.LayoutParams(-1, dp(66)))

        contactMatch.apply {
            setTextColor(cyan)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            includeFontPadding = false
            RealityTypography.technical(this, 10f)
        }
        numberPlate.addView(contactMatch, LinearLayout.LayoutParams(-1, dp(22)))
        root.addView(numberPlate, LinearLayout.LayoutParams(-1, dp(102)).apply {
            setMargins(dp(6), 0, dp(6), dp(12))
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
            tag = RealityVisuals.HUD_OWNED_TAG
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
                tag = RealityVisuals.HUD_OWNED_TAG
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = RealityVisuals.panel(
                    context,
                    fill = keyFill,
                    stroke = cyan,
                    radiusDp = 13f,
                    strokeDp = 2,
                )
                isClickable = true
                isFocusable = true
                addView(TextView(context).apply {
                    text = digit
                    setTextColor(primaryText)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    RealityTypography.displayMedium(this, 29f)
                }, LinearLayout.LayoutParams(-1, dp(38)))
                if (letters.isNotEmpty()) addView(TextView(context).apply {
                    text = letters
                    setTextColor(Color.rgb(105, 155, 199))
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    letterSpacing = .12f
                    RealityTypography.technical(this, 10f)
                }, LinearLayout.LayoutParams(-1, dp(18)))
                setOnClickListener {
                    RealityVisuals.pulseOnce(this)
                    number.append(digit)
                }
            }
            grid.addView(key, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(82)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(5), dp(6), dp(5))
            })
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))

        val actions = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), 0)
        }

        val backspace = ImageButton(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            contentDescription = "Backspace"
            setImageResource(R.drawable.ic_re_backspace)
            imageTintList = ColorStateList.valueOf(cyan)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = RealityVisuals.panel(
                context,
                fill = Color.rgb(2, 18, 31),
                stroke = cyan,
                radiusDp = 12f,
                strokeDp = 2,
            )
            setPadding(dp(18), dp(18), dp(18), dp(18))
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener {
                RealityVisuals.pulseOnce(this)
                val value = number.text
                if (value.isNotEmpty()) value.delete(value.length - 1, value.length)
            }
            setOnLongClickListener {
                number.text.clear()
                true
            }
        }
        actions.addView(backspace, LinearLayout.LayoutParams(0, dp(68), .34f).apply {
            setMargins(0, dp(4), dp(7), dp(4))
        })

        val call = TextView(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            text = "▮▯▮  CONNECT"
            setTextColor(green)
            gravity = Gravity.CENTER
            letterSpacing = .08f
            background = RealityVisuals.panel(
                context,
                fill = Color.rgb(0, 45, 20),
                stroke = green,
                radiusDp = 14f,
                strokeDp = 2,
            )
            includeFontPadding = false
            RealityTypography.technical(this, 14f)
            setOnClickListener {
                RealityVisuals.pulseOnce(this)
                onCall(number.text.toString().trim())
            }
        }
        actions.addView(call, LinearLayout.LayoutParams(0, dp(68), .66f).apply {
            setMargins(dp(7), dp(4), 0, dp(4))
        })
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))

        error.apply {
            setTextColor(Color.rgb(255, 76, 132))
            gravity = Gravity.CENTER
            includeFontPadding = false
            RealityTypography.technical(this, 10f)
        }
        root.addView(error, LinearLayout.LayoutParams(-1, dp(18)))
        return root
    }

    fun setNumber(value: String) {
        number.setText(value)
        number.setSelection(number.length())
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
