package com.nx.android.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import com.nx.android.data.PrefsStore
import com.nx.android.game.Native
import org.json.JSONObject
import java.io.File

/**
 * 虚拟手柄的绘制层（android-virtual-pad）：只画，不碰触点——命中测试与输入分流全在宿主
 * （触点原样转发，front_android.c 的 touch_event 分流），所以触控行为与拆分前逐一致。
 * 绘制优先用数据包自带的主题贴图（overlay/theme.tsv + 图册，与宿主主题同一套数据），
 * 没有主题时退回矢量绘制。布局与实时状态从宿主读取（dm_pad_layout_json / dm_pad_state_json，
 * 控件中心/半径与命中测试同一份数据），版本号或窗口尺寸变化即重读；物理手柄接入时隐藏，
 * 宿主的暂停菜单或布局编辑器画着自己的界面时也让位。
 */
class PadView(context: Context, private val prefs: PrefsStore, private val pack: File?) : View(context) {

    companion object {
        const val KEY_SHOWN = "pad.shown"
        const val KEY_OPACITY = "pad.opacity"
        private val LABELS = mapOf("plus" to "+", "minus" to "−")
    }

    private data class Ctrl(val n: String, val kind: String, val btn: Long, val cx: Float, val cy: Float, val hw: Float, val hh: Float)
    private class Sp(val ctl: String, val state: String, val ax: Int, val ay: Int, val aw: Int, val ah: Int, val ox: Float, val oy: Float)

    private var ctrls: List<Ctrl> = emptyList()
    private var layoutName = "tablet"
    private var layoutVer = -1L
    private var layoutW = 0f        // 布局 fractions 算出时的宿主窗口尺寸（padLayout 的 w/h）
    private var layoutH = 0f
    private var stateVer = -1
    private var pressed: Set<Int> = emptySet()
    private var btnBits = 0L
    private var pads = 0
    private var hostUi = false      // 宿主自己画着界面（暂停菜单 / 布局编辑器）：手柄让位
    private var knobs = floatArrayOf(0f, 0f, 0f, 0f)   // lx ly rx ry，±1（窗口坐标，向下为正）

    private var themeReady = false
    private var themeAlpha = 1f
    private var themeScale = 1f       // atlas texels per CSS px
    private var themeTravel = 10f     // CSS px
    private var designW = 0f
    private var designH = 0f
    private var atlas: Bitmap? = null
    private var themeSprites: List<Sp> = emptyList()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val spPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    var shown: Boolean
        get() = prefs.bool(KEY_SHOWN, true)
        set(v) { prefs.put(KEY_SHOWN, v); pushLayer(); invalidate() }
    var opacity: Int
        get() = prefs.str(KEY_OPACITY).toIntOrNull() ?: 60
        set(v) { prefs.put(KEY_OPACITY, v.coerceIn(10, 100).toString()); invalidate() }

    private fun visible(): Boolean = shown && !hostUi && pads == 0

    /**
     * 宿主那边的手柄层（虚拟手柄与 ☰ 菜单按钮是一层）：画出来才回答触摸，这一层藏起来时那一块的触摸还给游戏。
     * 宿主开着的时候（它自己的面板要用这一层）不回答：宿主的面板自己收触摸。
     * 推给宿主的是「画不画」，宿主那边据此决定那片区域答不答触摸（front_android.c 的 ov_answer）。
     */
    private var layerDrawn = true   // 上次告诉宿主的状态
    private fun pushLayer() {
        val v = visible()
        if (v == layerDrawn) return
        layerDrawn = v
        runCatching { Native.padLayer(v) }   // 宿主还没载入（这里在 System.load 之前）或旧宿主没有这个入口：跳过
    }

    private val loop = object : Runnable {
        override fun run() {
            tick()
            postOnAnimationDelayed(this, if (visible()) 16 else 250)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        fetchLayout()
        loadTheme()
        postOnAnimationDelayed(loop, 100)   // 第一次 tick 在宿主载入之后，手柄层的事那时才推给宿主
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && (w != ow || h != oh)) fetchLayout()   // 尺寸一变（旋转/满屏切换）重拉布局：宿主按当前窗口算的， fractions 不再带着启动时的竖屏尺寸
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(loop)
        super.onDetachedFromWindow()
    }

    /** 布局重读（版本或窗口尺寸变化）+ 状态轮询；按压变化才触觉反馈，状态/版本变化才重绘 */
    private fun tick() {
        runCatching {
            val st = JSONObject(Native.padState())
            val v = st.optLong("v", -1)
            hostUi = st.optBoolean("e", false) || st.optBoolean("m", false)   // e: 编辑器 m: 宿主菜单
            pads = st.optInt("pads", 0)
            pushLayer()   // 宿主的面板开着、手柄接上了：那一层不画了，宿主也不该再回答那里的触摸
            // 窗口尺寸也要比：旋转/满屏切换时 fetchLayout 可能跑在宿主拿到新 Surface 之前，fractions 是
            // 旧宽高比的，而 ver 不因尺寸变化而走——按 w/h 不匹配每帧重拉，宿主窗口一更新就自愈
            if (v != layoutVer || layoutW != width.toFloat() || layoutH != height.toFloat()) fetchLayout()
            if (hostUi || pads > 0 || !shown) { invalidate(); return }
            btnBits = st.optLong("btn", 0)
            val p = st.optJSONArray("p")
            val np = p?.length() ?: 0
            val npSet = buildSet { for (i in 0 until np) add(p!!.optInt(i)) }
            val changed = npSet != pressed
            pressed = npSet
            val k = st.optJSONArray("k")
            if (k != null && k.length() == 2) {
                val ka = k.optJSONArray(0) ?: return
                val kb = k.optJSONArray(1) ?: return
                knobs[0] = ka.optDouble(0, 0.0).toFloat(); knobs[1] = ka.optDouble(1, 0.0).toFloat()
                knobs[2] = kb.optDouble(0, 0.0).toFloat(); knobs[3] = kb.optDouble(1, 0.0).toFloat()
            }
            if (changed) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            invalidate()
        }
    }

    private fun fetchLayout() {
        runCatching {
            val o = JSONObject(Native.padLayout())
            layoutVer = o.optLong("ver", -1)
            layoutW = o.optDouble("w", 0.0).toFloat(); layoutH = o.optDouble("h", 0.0).toFloat()
            val name = o.optString("layout", "tablet")
            if (name != layoutName) { layoutName = name; loadTheme() }   // 布局换了（旋转跨过 1.9）：图册与精灵跟着换
            val arr = o.optJSONArray("controls") ?: return
            val list = ArrayList<Ctrl>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                list.add(Ctrl(c.optString("n"), c.optString("k"), c.optLong("b"),
                              c.optDouble("x", 0.0).toFloat(), c.optDouble("y", 0.0).toFloat(),
                              c.optDouble("hw", 0.0).toFloat(), c.optDouble("hh", 0.0).toFloat()))
            }
            ctrls = list
        }
    }

    // ---- 主题：数据包的 overlay/theme.tsv + 图册（与宿主主题同源数据）----

    private fun loadTheme() {
        val packDir = pack ?: return
        runCatching {
            val tsv = File(packDir, "overlay/theme.tsv").readText()
            var atlasName: String? = null
            val sprites = ArrayList<Sp>()
            var dw = 0f; var dh = 0f
            for (raw in tsv.split('\n')) {
                val line = raw.trimEnd('\r')
                if (line.isEmpty() || line.startsWith("#")) continue
                val c = line.split('\t')
                when {
                    c.size >= 2 && c[0] == "alpha" -> themeAlpha = c[1].toFloatOrNull() ?: 1f
                    c.size >= 7 && c[0] == "layout" -> if (c[1] == layoutName) {
                        atlasName = c[4]; dw = c[2].toFloatOrNull() ?: 0f; dh = c[3].toFloatOrNull() ?: 0f
                        themeScale = c[5].toFloatOrNull() ?: 1f; themeTravel = c[6].toFloatOrNull() ?: 10f
                    }
                    c.size >= 10 && c[0] == "sprite" && c[1] == layoutName ->
                        sprites.add(Sp(c[2], c[4], c[5].toIntOrNull() ?: 0, c[6].toIntOrNull() ?: 0,
                                       c[7].toIntOrNull() ?: 0, c[8].toIntOrNull() ?: 0,
                                       c[9].toFloatOrNull() ?: 0f, c.getOrNull(10)?.toFloatOrNull() ?: 0f))
                }
            }
            val name = atlasName ?: return
            val bmp = BitmapFactory.decodeFile(File(File(packDir, "overlay"), name).absolutePath) ?: return
            atlas = bmp; designW = dw; designH = dh
            themeSprites = sprites
            themeReady = themeSprites.isNotEmpty() && dw > 0 && dh > 0
        }
    }

    private fun label(n: String) = LABELS[n] ?: n.uppercase()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visible() || ctrls.isEmpty()) return
        val W = width.toFloat(); val H = height.toFloat()
        val a = (opacity / 100f) * themeAlpha
        text.textSize = H * 0.022f

        if (themeReady && atlas != null) {
            // 主题贴图：精灵矩形在图册上，位置 = 控件中心（窗口分数 × 视图）+ 偏移（CSS px × 视图/设计高）。
            // 偏移是精灵左上角相对中心的量（theme.tsv 的约定，已含居中），不能再按中心对齐——那会把每个
            // 精灵向左上挪半个自身（基座挪得比红球多，摇杆和它的红球就错开了），与宿主 ov_tsp 一致
            val atlasB = atlas!!
            fun centre(c: Ctrl): Pair<Float, Float> = c.cx * W to c.cy * H
            fun drawSp(s: Sp, cx: Float, cy: Float) {
                val vw = s.aw / themeScale * (H / designH); val vh = s.ah / themeScale * (H / designH)
                val dx = s.ox * (H / designH); val dy = s.oy * (H / designH)
                spPaint.alpha = (255 * a).toInt()
                canvas.drawBitmap(atlasB, Rect(s.ax, s.ay, s.ax + s.aw, s.ay + s.ah),
                                  RectF(cx + dx, cy + dy, cx + dx + vw, cy + dy + vh), spPaint)
            }
            fun knobOffset(stick: String): Pair<Float, Float> {
                val i = if (stick == "stick_l") 0 else 2
                return knobs[i] * themeTravel * (H / designH) to knobs[i + 1] * themeTravel * (H / designH)
            }
            for (s in themeSprites.filter { it.state == "decor" }) {
                ctrls.firstOrNull { it.n == s.ctl }?.let { val (cx, cy) = centre(it); drawSp(s, cx, cy) }
            }
            for (s in themeSprites.filter { it.state == "knob" }) {
                val (kx, ky) = knobOffset(s.ctl)
                ctrls.firstOrNull { it.n == s.ctl }?.let { val (cx, cy) = centre(it); drawSp(s, cx + kx, cy + ky) }
            }
            for (s in themeSprites.filter { it.state == "idle" }) {
                val idx = ctrls.indexOfFirst { it.n == s.ctl }
                val held = idx >= 0 && pressed.contains(idx)
                val heldSp = themeSprites.firstOrNull { it.ctl == s.ctl && it.state == "held" }
                ctrls.firstOrNull { it.n == s.ctl }?.let { c ->
                    val (cx, cy) = centre(c)
                    if (held && heldSp != null) drawSp(heldSp, cx, cy) else drawSp(s, cx, cy)
                }
            }
            return
        }

        // 矢量退路（包里没有主题时）
        for (c in ctrls) {
            val cx = c.cx * W; val cy = c.cy * H; val rr = c.hw * W
            val held = pressed.contains(ctrls.indexOf(c))
            fill.color = Color.argb(((if (held) 55 else 25) * a).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, cy, rr, fill)
            line.color = Color.argb((190 * a).toInt(), 255, 255, 255); line.strokeWidth = 2.5f
            canvas.drawCircle(cx, cy, rr, line)
            text.color = Color.argb((220 * a).toInt(), 255, 255, 255)
            canvas.drawText(label(c.n), cx, cy + text.textSize / 3, text)
        }
    }
}
