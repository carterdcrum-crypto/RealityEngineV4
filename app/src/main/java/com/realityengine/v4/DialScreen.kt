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

    private val ice = RealityVisuals.Colors.Cyan
    private val iceSoft = RealityVisuals.Colors.CyanSoft
    private val lilac = RealityVisuals.Colors.Lilac
    private val green = RealityVisuals.Colors.Green
    private val primaryText = RealityVisuals.Colors.Text
    private val muted = RealityVisuals.Colors.TextDim
    private val keyFill = Color.rgb(11, 19, 39)

    fun build(): View {
        val root = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(6))
        }

        root.addView(TextView(context).apply {
            text = "SECURE DIAL // NATIVE CELLULAR"
            gravity = Gravity.CENTER
            setTextColor(iceSoft)
            RealityTypography.technical(this, 9.5f)
        }, LinearLayout.LayoutParams(-1, dp(34)).apply {
            setMargins(dp(14), 0, dp(14), dp(9))
        })

        val numberPlate = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = RealityVisuals.panel(
                context,
                fill = RealityVisuals.Colors.PanelStrong,
                stroke = iceSoft,
                radiusDp = 23f,
                strokeDp = 1,
            )
            setPadding(dp(20), dp(9), dp(20), dp(9))
        }

        number.apply {
            hint = "Enter number"
            setHintTextColor(Color.rgb(101, 115, 150))
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = null
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isSingleLine = true
            includeFontPadding = false
            letterSpacing = .025f
            RealityTypography.displayMedium(this, 31f)
        }
        numberPlate.addView(number, LinearLayout.LayoutParams(-1, dp(66)))

        contactMatch.apply {
            setTextColor(lilac)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            includeFontPadding = false
            RealityTypography.displayMedium(this, 10.5f)
        }
        numberPlate.addView(contactMatch, LinearLayout.LayoutParams(-1, dp(22)))
        root.addView(numberPlate, LinearLayout.LayoutParams(-1, dp(104)).apply {
            setMargins(dp(3), 0, dp(3), dp(14))
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
                    stroke = Color.rgb(92, 113, 168),
                    radiusDp = 24f,
                    strokeDp = 1,
                )
                isClickable = true
                isFocusable = true
                addView(TextView(context).apply {
                    text = digit
                    setTextColor(primaryText)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    RealityTypography.displayMedium(this, 27f)
                }, LinearLayout.LayoutParams(-1, dp(36)))
                if (letters.isNotEmpty()) addView(TextView(context).apply {
                    text = letters
                    setTextColor(iceSoft)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    letterSpacing = .11f
                    RealityTypography.technical(this, 9f)
                }, LinearLayout.LayoutParams(-1, dp(17)))
                setOnClickListener {
                    RealityVisuals.pulseOnce(this)
                    number.append(digit)
                }
            }
            grid.addView(key, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(78)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))

        val actions = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), 0)
        }

        val backspace = ImageButton(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            contentDescription = "Backspace"
            setImageResource(R.drawable.ic_re_backspace)
            imageTintList = ColorStateList.valueOf(iceSoft)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = RealityVisuals.panel(
                context,
                fill = Color.rgb(10, 18, 37),
                stroke = lilac,
                radiusDp = 22f,
                strokeDp = 1,
            )
            setPadding(dp(17), dp(17), dp(17), dp(17))
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
        actions.addView(backspace, LinearLayout.LayoutParams(0, dp(64), .30f).apply {
            setMargins(0, dp(4), dp(8), dp(4))
        })

        val connect = LinearLayout(context).apply {
            tag = RealityVisuals.HUD_OWNED_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = RealityVisuals.panel(
                context,
                fill = Color.rgb(18, 83, 52),
                stroke = green,
                radiusDp = 24f,
                strokeDp = 1,
            )
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_re_call)
                imageTintList = ColorStateList.valueOf(Color.rgb(239, 255, 245))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(25), dp(25)).apply { setMargins(0, 0, dp(9), 0) })
            addView(TextView(context).apply {
                text = "CONNECT"
                setTextColor(Color.rgb(239, 255, 245))
                gravity = Gravity.CENTER
                RealityTypography.technical(this, 13f)
            })
            setOnClickListener {
                RealityVisuals.pulseOnce(this)
                onCall(number.text.toString().trim())
            }
        }
        actions.addView(connect, LinearLayout.LayoutParams(0, dp(64), .70f).apply {
            setMargins(dp(8), dp(4), 0, dp(4))
        })
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))

        error.apply {
            setTextColor(Color.rgb(255, 122, 154))
            gravity = Gravity.CENTER
            includeFontPadding = false
            RealityTypography.displayMedium(this, 10f)
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
