package app.handson.tablet

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A janela do HandsOn: flutua por cima dos apps e mostra a câmera com o desenho da mão.
 * Botões: pausar, tamanho (pequena / média / grande), minimizar (vira uma bolinha) e fechar (desliga).
 * Arraste pela barra de cima para mudar de lugar.
 */
class JanelaHandsOn(
    private val ctx: Context,
    private val wm: WindowManager,
    private val prefs: SharedPreferences,
    val painel: Painel,
    private val aoFechar: () -> Unit,
    private val aoPausar: () -> Unit,
) {
    private val dp = ctx.resources.displayMetrics.density
    val view = PainelView(ctx, this)
    var tamanho = prefs.getInt(Config.JANELA_TAM, 1)
        private set
    var minimizada = false
        private set
    private var visivel = true
    @Volatile var quadro: Bitmap? = null
        private set
    private var escondidaAte = 0L

    private val lp = WindowManager.LayoutParams(
        10, 10,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(Config.JANELA_X, (24 * dp).toInt())
        y = prefs.getInt(Config.JANELA_Y, (90 * dp).toInt())
    }

    private fun medidas(): Pair<Int, Int> = when {
        minimizada -> 64 to 64
        tamanho == 0 -> 250 to 214
        tamanho == 2 -> 500 to 410
        else -> 350 to 292
    }.let { (w, h) -> (w * dp).toInt() to (h * dp).toInt() }

    fun mostrar() {
        val (w, h) = medidas()
        lp.width = w; lp.height = h
        manterNaTela(false)
        try { wm.addView(view, lp) } catch (_: Exception) {}
    }

    fun remover() { try { wm.removeView(view) } catch (_: Exception) {} }

    private fun aplicar() {
        val (w, h) = medidas()
        lp.width = w; lp.height = h
        manterNaTela(false)
        try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
        prefs.edit().putInt(Config.JANELA_TAM, tamanho).apply()
        view.invalidate()
    }

    fun alternarTamanho() { if (minimizada) minimizada = false else tamanho = (tamanho + 1) % 3; aplicar() }
    fun alternarMinimizar() { minimizada = !minimizada; aplicar() }
    fun alternarVisivel() {
        if (minimizada) { minimizada = false; visivel = true }
        else visivel = !visivel
        view.visibility = if (visivel) View.VISIBLE else View.GONE
        aplicar()
    }
    fun fechar() = aoFechar()
    fun pausar() = aoPausar()

    fun esconderPor(ms: Long) {
        escondidaAte = SystemClock.uptimeMillis() + ms
        view.post { view.visibility = View.INVISIBLE }
        view.postDelayed({ if (visivel) view.visibility = View.VISIBLE }, ms)
    }

    fun mover(dx: Int, dy: Int) {
        lp.x += dx; lp.y += dy
        manterNaTela(false)
        try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
    }

    fun soltouArraste() {
        prefs.edit().putInt(Config.JANELA_X, lp.x).putInt(Config.JANELA_Y, lp.y).apply()
    }

    fun manterNaTela(atualizar: Boolean = true) {
        val m = ctx.resources.displayMetrics
        val w = if (lp.width > 0) lp.width else (64 * dp).toInt()
        val h = if (lp.height > 0) lp.height else (64 * dp).toInt()
        lp.x = lp.x.coerceIn(0, (m.widthPixels - w).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (m.heightPixels - h).coerceAtLeast(0))
        if (atualizar) try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
    }

    fun querQuadro() = visivel && !minimizada
    fun novoQuadro(b: Bitmap) { quadro = b }
    fun atualizar() { if (visivel) view.postInvalidateOnAnimation() }
}

@SuppressLint("ViewConstructor")
class PainelView(context: Context, private val j: JanelaHandsOn) : View(context) {
    private val dp = resources.displayMetrics.density
    private val OURO = Color.rgb(232, 176, 74)
    private val OURO_CLARO = Color.rgb(255, 222, 150)
    private val VERMELHO = Color.rgb(224, 38, 43)
    private val VERDE = Color.rgb(90, 220, 120)
    private val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cheio = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val foto = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // câmera em tons quentes e escurecida (estilo HUD vermelho e dourado)
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0.55f, 0.25f, 0.05f, 0f, 18f,
            0.25f, 0.32f, 0.05f, 0f, 4f,
            0.08f, 0.10f, 0.12f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
    }
    private val r = RectF()
    private val barra = 40 * dp

    // botões da barra (da direita para a esquerda): fechar, minimizar, tamanho, pausar
    private val botoes = listOf("fechar", "minimizar", "tamanho", "pausar")
    private fun botaoCentro(i: Int) = width - (22 + i * 36) * dp to barra / 2

    private val OSSOS = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4), intArrayOf(0, 5), intArrayOf(5, 6),
        intArrayOf(6, 7), intArrayOf(7, 8), intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
        intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16), intArrayOf(13, 17),
        intArrayOf(0, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20)
    )

    override fun onDraw(c: Canvas) {
        val p = j.painel
        val W = width.toFloat(); val H = height.toFloat()
        if (j.minimizada) { bolinha(c, W, H, p); return }
        // fundo
        r.set(1f, 1f, W - 1, H - 1)
        cheio.color = Color.argb(236, 18, 7, 8); c.drawRoundRect(r, 16 * dp, 16 * dp, cheio)
        traco.color = OURO; traco.strokeWidth = 1.5f * dp; c.drawRoundRect(r, 16 * dp, 16 * dp, traco)
        // barra de cima
        reator(c, 22 * dp, barra / 2, 11 * dp, p)
        texto.color = OURO; texto.textSize = 16 * dp
        c.drawText("HandsOn", 40 * dp, barra / 2 + 6 * dp, texto)
        for ((i, b) in botoes.withIndex()) botao(c, i, b, p)
        traco.color = Color.argb(90, 232, 176, 74); traco.strokeWidth = 1f * dp
        c.drawLine(10 * dp, barra, W - 10 * dp, barra, traco)
        // câmera
        val rodape = 30 * dp
        val area = RectF(10 * dp, barra + 8 * dp, W - 10 * dp, H - rodape)
        val q = j.quadro
        if (q != null) {
            val esc = minOf(area.width() / q.width, area.height() / q.height)
            val cw = q.width * esc; val ch = q.height * esc
            r.set(area.centerX() - cw / 2, area.centerY() - ch / 2, area.centerX() + cw / 2, area.centerY() + ch / 2)
            c.drawBitmap(q, null, r, foto)
        } else {
            r.set(area)
            cheio.color = Color.rgb(10, 4, 5); c.drawRoundRect(r, 8 * dp, 8 * dp, cheio)
        }
        val img = RectF(r)
        traco.color = Color.argb(160, 232, 176, 74); traco.strokeWidth = 1f * dp
        c.drawRoundRect(img, 8 * dp, 8 * dp, traco)
        // área que cobre a tela inteira
        val aw = (Ajustes.larguraArea * img.width()).toFloat(); val ah = (Ajustes.alturaArea * img.height()).toFloat()
        traco.color = Color.argb(90, 232, 176, 74)
        c.drawRect(img.centerX() - aw / 2, img.centerY() - ah / 2, img.centerX() + aw / 2, img.centerY() + ah / 2, traco)
        // mão
        val pts = p.pontos
        if (pts.size == 21) {
            traco.color = OURO; traco.strokeWidth = 2f * dp
            for (o in OSSOS) {
                val a = pts[o[0]]; val b = pts[o[1]]
                c.drawLine(img.left + (a.x * img.width()).toFloat(), img.top + (a.y * img.height()).toFloat(),
                    img.left + (b.x * img.width()).toFloat(), img.top + (b.y * img.height()).toFloat(), traco)
            }
            cheio.color = if (p.apertado) VERMELHO else OURO_CLARO
            for (i in intArrayOf(4, 8, 12, 16, 20)) {
                val a = pts[i]
                c.drawCircle(img.left + (a.x * img.width()).toFloat(), img.top + (a.y * img.height()).toFloat(), 3.2f * dp, cheio)
            }
        } else if (q == null || !p.maoVista) {
            texto.color = Color.argb(210, 255, 255, 255); texto.textSize = 14 * dp
            val s = if (q == null) "Ligando a câmera…" else "Mostre a mão"
            c.drawText(s, img.centerX() - texto.measureText(s) / 2, img.centerY() + 5 * dp, texto)
        }
        if (p.pausado) {
            cheio.color = Color.argb(120, 120, 10, 12); c.drawRoundRect(img, 8 * dp, 8 * dp, cheio)
            texto.color = Color.WHITE; texto.textSize = 18 * dp
            val s = "PAUSADO"
            c.drawText(s, img.centerX() - texto.measureText(s) / 2, img.centerY() + 6 * dp, texto)
        }
        // rodapé: gesto atual e fps
        cheio.color = when { p.pausado -> VERMELHO; p.maoVista -> VERDE; else -> Color.GRAY }
        c.drawCircle(18 * dp, H - rodape / 2, 4 * dp, cheio)
        texto.color = Color.WHITE; texto.textSize = 13 * dp
        val nome = when {
            p.pausado -> "Pausado · quatro dedos para voltar"
            p.menuAberto -> "Menu aberto"
            !p.maoVista -> "Procurando a mão"
            else -> NOMES_POSE[p.pose] ?: "Mão detectada"
        }
        c.drawText(nome, 30 * dp, H - rodape / 2 + 5 * dp, texto)
        texto.color = Color.argb(180, 232, 176, 74); texto.textSize = 11 * dp
        val f = "${p.fps.toInt()} fps"
        c.drawText(f, W - 12 * dp - texto.measureText(f), H - rodape / 2 + 4 * dp, texto)
    }

    private fun reator(c: Canvas, x: Float, y: Float, raio: Float, p: Painel) {
        traco.color = OURO; traco.strokeWidth = 1.4f * dp
        c.drawCircle(x, y, raio, traco)
        val rot = ((SystemClock.uptimeMillis() / 8) % 360).toFloat()
        r.set(x - raio * 0.7f, y - raio * 0.7f, x + raio * 0.7f, y + raio * 0.7f)
        for (k in 0 until 6) c.drawArc(r, rot + k * 60, 30f, false, traco)
        cheio.color = if (p.pausado) Color.GRAY else VERMELHO
        c.drawCircle(x, y, raio * 0.32f, cheio)
    }

    private fun botao(c: Canvas, i: Int, tipo: String, p: Painel) {
        val (x, y) = botaoCentro(i)
        val rr = 13 * dp
        cheio.color = if (tipo == "fechar") Color.argb(200, 120, 14, 16) else Color.argb(200, 40, 18, 12)
        c.drawCircle(x, y, rr, cheio)
        traco.color = if (tipo == "fechar") VERMELHO else OURO; traco.strokeWidth = 1.3f * dp
        c.drawCircle(x, y, rr, traco)
        traco.color = OURO_CLARO; traco.strokeWidth = 2f * dp
        val k = 5 * dp
        when (tipo) {
            "fechar" -> { c.drawLine(x - k, y - k, x + k, y + k, traco); c.drawLine(x - k, y + k, x + k, y - k, traco) }
            "minimizar" -> c.drawLine(x - k, y + 2 * dp, x + k, y + 2 * dp, traco)
            "tamanho" -> {        // setas diagonais
                c.drawLine(x - k, y + k, x + k, y - k, traco)
                c.drawLine(x + k, y - k, x + k - 4 * dp, y - k, traco); c.drawLine(x + k, y - k, x + k, y - k + 4 * dp, traco)
                c.drawLine(x - k, y + k, x - k + 4 * dp, y + k, traco); c.drawLine(x - k, y + k, x - k, y + k - 4 * dp, traco)
            }
            "pausar" -> if (p.pausado) {
                val t = android.graphics.Path().apply { moveTo(x - 3 * dp, y - k); lineTo(x + k, y); lineTo(x - 3 * dp, y + k); close() }
                cheio.color = OURO_CLARO; c.drawPath(t, cheio)
            } else {
                c.drawLine(x - 2.5f * dp, y - k, x - 2.5f * dp, y + k, traco); c.drawLine(x + 2.5f * dp, y - k, x + 2.5f * dp, y + k, traco)
            }
        }
    }

    private fun bolinha(c: Canvas, W: Float, H: Float, p: Painel) {
        val cx = W / 2; val cy = H / 2; val raio = W / 2 - 2 * dp
        cheio.color = Color.argb(236, 18, 7, 8); c.drawCircle(cx, cy, raio, cheio)
        traco.color = if (p.maoVista) OURO else Color.GRAY; traco.strokeWidth = 2f * dp
        c.drawCircle(cx, cy, raio, traco)
        reator(c, cx, cy, raio * 0.55f, p)
    }

    // ── toques: botões e arrastar a janela ──
    private var downX = 0f; private var downY = 0f
    private var ultX = 0f; private var ultY = 0f
    private var arrastando = false
    private var naBarra = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; ultX = e.rawX; ultY = e.rawY
                arrastando = false
                naBarra = j.minimizada || e.y < barra
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (naBarra && (arrastando || hypot(e.rawX - downX, e.rawY - downY) > 10 * dp)) {
                    arrastando = true
                    j.mover((e.rawX - ultX).toInt(), (e.rawY - ultY).toInt())
                    ultX = e.rawX; ultY = e.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (arrastando) { j.soltouArraste(); return true }
                if (j.minimizada) { j.alternarMinimizar(); return true }
                if (e.y < barra) {
                    for ((i, b) in botoes.withIndex()) {
                        val (x, y) = botaoCentro(i)
                        if (abs(e.x - x) < 18 * dp && abs(e.y - y) < 18 * dp) {
                            when (b) {
                                "fechar" -> j.fechar()
                                "minimizar" -> j.alternarMinimizar()
                                "tamanho" -> j.alternarTamanho()
                                "pausar" -> j.pausar()
                            }
                            performClick()
                            return true
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
