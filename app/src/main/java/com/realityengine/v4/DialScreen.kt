package com.realityengine.v4

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

/** Dial surface modeled after the compact native-cellular reference UI. */
class DialScreen(
    private val context: Context,
    private val contacts: ContactIndex,
    private val onCall: (String) -> Unit
) {
    val number = EditText(context)
    val contactMatch = TextView(context)
    val error = TextView(context)

    private val cyan = Color.rgb(47, 231, 247)
    private val green = Color.rgb(48, 214, 143)
    private val text = Color.rgb(235, 244, 248)
    private val muted = Color.rgb(91, 111, 128)
    private val keyFill = Color.rgb(5, 20, 38)

    fun build(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(8), dp(22), dp(8))
        }

        number.apply {
            hint = "Enter number to call"
            setHintTextColor(muted)
            setTextColor(text)
            gravity = Gravity.CENTER
            background = null
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isSingleLine = true
            RealityTypography.displayMedium(this, 25f)
        }
        root.addView(number, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, 0, 0, dp(2)) })

        contactMatch.apply {
            setTextColor(cyan)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            RealityTypography.displayMedium(this, 13f)
        }
        root.addView(contactMatch, LinearLayout.LayoutParams(-1, dp(24)))

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
            "*" to "", "0" to "+", "#" to ""
        )
        keys.forEach { (digit, letters) ->
            val key = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = circle(keyFill, Color.rgb(10, 47, 78))
                isClickable = true
                isFocusable = true
                addView(TextView(context).apply {
                    text = digit
                    setTextColor(text)
                    gravity = Gravity.CENTER
                    RealityTypography.displayMedium(this, 24f)
                })
                if (letters.isNotEmpty()) addView(TextView(context).apply {
                    text = letters
                    setTextColor(muted)
                    gravity = Gravity.CENTER
                    RealityTypography.technical(this, 9f)
                })
                setOnClickListener { number.append(digit) }
            }
            grid.addView(key, GridLayout.LayoutParams().apply {
                width = dp(78)
                height = dp(78)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(13), dp(7), dp(13), dp(7))
            })
        }
        root.addView(grid)

        val call = TextView(context).apply {
            text = "☎"
            setTextColor(Color.rgb(0, 28, 22))
            gravity = Gravity.CENTER
            background = circle(green, green)
            RealityTypography.displayMedium(this, 27f)
            setOnClickListener { onCall(number.text.toString().trim()) }
        }
        root.addView(call, LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(10), 0, dp(4)) })

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

    private fun circle(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
