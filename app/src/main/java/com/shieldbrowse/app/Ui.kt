package com.shieldbrowse.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Classic Glass design tokens (same system as FocusLock v1.13+). */
object Ui {
    const val BG = 0xFF160B36.toInt()
    const val CARD = 0x2BFFFFFF.toInt()       // white alpha 43
    const val RAISED = 0x40FFFFFF.toInt()     // white alpha 64
    const val STROKE = 0x73FFFFFF.toInt()     // white alpha 115
    const val ACCENT = 0xFFE879C6.toInt()
    const val TEXT = Color.WHITE
    const val SECONDARY = 0xFFDCD6F7.toInt()
    const val MUTED = 0xFFACA3D9.toInt()
    const val VIOLET = 0xFF8B5CF6.toInt()
    const val PINK = 0xFFEC6BD8.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun aurora(ctx: Context): LayerDrawable {
        val grad = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF160A33.toInt(), 0xFF3B1470.toInt(), 0xFF7C2D92.toInt(),
                0xFFC2439D.toInt(), 0xFF241259.toInt()))
        return LayerDrawable(arrayOf(grad))
    }

    fun cardBg(ctx: Context, radius: Int = 24): GradientDrawable = GradientDrawable().apply {
        setColor(CARD)
        setStroke(dp(ctx, 1), STROKE)
        cornerRadius = dp(ctx, radius).toFloat()
    }

    fun card(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.background = cardBg(ctx)
        l.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        l.layoutParams = lp
        l.elevation = dp(ctx, 6).toFloat()
        return l
    }

    fun text(ctx: Context, s: String, size: Float = 15f, color: Int = TEXT,
             bold: Boolean = false): TextView {
        val tv = TextView(ctx)
        tv.text = s
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return tv
    }

    fun gap(ctx: Context, h: Int = 10): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, h))
        return v
    }

    fun cardHeader(ctx: Context, parent: LinearLayout, title: String, info: String?) {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val tv = text(ctx, title, 17f, bold = true)
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)
        if (info != null) {
            val b = TextView(ctx)
            b.text = "i"
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setTextColor(TEXT)
            b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(CARD)
            bg.setStroke(dp(ctx, 1), STROKE)
            b.background = bg
            b.layoutParams = LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28))
            b.setOnClickListener {
                AlertDialog.Builder(ctx).setTitle(title).setMessage(info)
                    .setPositiveButton("OK", null).show()
            }
            row.addView(b)
        }
        parent.addView(row)
        parent.addView(gap(ctx, 8))
    }

    fun primaryButton(ctx: Context, label: String, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        val bg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(VIOLET, PINK))
        bg.cornerRadius = dp(ctx, 999).toFloat()
        b.background = bg
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48))
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6))
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    fun ghostButton(ctx: Context, label: String, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.setTextColor(TEXT)
        b.isAllCaps = false
        val bg = GradientDrawable()
        bg.setColor(CARD)
        bg.setStroke(dp(ctx, 1), STROKE)
        bg.cornerRadius = dp(ctx, 999).toFloat()
        b.background = bg
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48))
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6))
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    /** Small toolbar icon-text button. */
    fun toolButton(ctx: Context, label: String, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 19f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(TEXT)
        val lp = LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42))
        tv.layoutParams = lp
        tv.setOnClickListener { onClick() }
        return tv
    }

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
