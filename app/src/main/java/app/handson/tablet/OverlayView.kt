package app.handson.tablet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** Desenha o HUD vermelho e dourado por cima de tudo: cursor, anel, avisos, mini câmera e ajuda. */
class OverlayView(context: Context) : View(context) {

    var painel: Painel? = null
    var miniCamera = true
    private var escondidoAte = 0L

    private val dp = resources.displayMetrics.density
    private val OURO = Color.rgb(232, 176, 74)
    private val VERMELHO = Color.rgb(224, 38, 43)
    private val FUNDO = Color.argb(215, 22, 8, 8)

    private val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cheio = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val onde = IntArray(2)
    private val ret = RectF()

    private val OSSOS = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
        intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
        intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
        intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
        intArrayOf(13, 17), intArrayOf(0, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20)
    )

    fun esconderPor(ms: Long) {
        escondidoAte = SystemClock.uptimeMillis() + ms
        postInvalidate()
    }

    override fun onDraw(c: Canvas) {
        val p = painel ?: return
        if (SystemClock.uptimeMillis() < escondidoAte) return
        getLocationOnScreen(onde)
        c.save()
        c.translate(-onde[0].toFloat(), -onde[1].toFloat())   // desenha em coordenadas da tela inteira
        val agora = SystemClock.uptimeMillis() / 1000.0

        if (p.ajuda) desenharAjuda(c)
        if (miniCamera) desenharMini(c, p)

        if (p.pausado) {
            pilula(c, "PAUSADO  ·  quatro dedos para voltar", width / 2f + onde[0], 46 * dp, OURO, 16f)
        } else if (p.maoVista && p.cursorX >= 0) {
            desenharCursor(c, p)
        }
        if (agora < p.avisoAte && p.aviso.isNotEmpty()) {
            pilula(c, p.aviso, width / 2f + onde[0], (if (p.pausado) 92 else 46) * dp, OURO, 18f)
        }
        if (p.dica.isNotEmpty() && p.maoVista) {
            pilula(c, p.dica, width / 2f + onde[0], height - 70 * dp + onde[1], Color.WHITE, 15f)
        }
        c.restore()
    }

    private fun desenharCursor(c: Canvas, p: Painel) {
        val x = p.cursorX; val y = p.cursorY
        val r = 17 * dp
        // brilho
        cheio.color = Color.argb(45, 224, 38, 43)
        c.drawCircle(x, y, r * 1.8f, cheio)
        // anel dourado
        traco.color = OURO; traco.strokeWidth = 2.5f * dp
        c.drawCircle(x, y, r, traco)
        // quanto a pinça está fechada (arco vermelho)
        if (p.pinca > 0.05f && !p.apertado) {
            traco.color = VERMELHO; traco.strokeWidth = 3.5f * dp
            ret.set(x - r, y - r, x + r, y + r)
            c.drawArc(ret, -90f, 360f * p.pinca, false, traco)
        }
        // centro
        cheio.color = if (p.apertado) VERMELHO else Color.argb(230, 224, 38, 43)
        c.drawCircle(x, y, if (p.apertado) r * 0.75f else 5 * dp, cheio)
        // anel de "segurar"
        if (p.anel > 0.01f) {
            val ra = r + 11 * dp
            traco.color = Color.argb(70, 232, 176, 74); traco.strokeWidth = 5 * dp
            c.drawCircle(x, y, ra, traco)
            traco.color = OURO
            ret.set(x - ra, y - ra, x + ra, y + ra)
            c.drawArc(ret, -90f, 360f * p.anel, false, traco)
            pilula(c, p.anelNome, x, y + ra + 26 * dp, OURO, 15f)
        } else {
            val nome = when (p.pose) {
                "dois" -> "Rolando"
                "mindinho" -> if (SystemClock.uptimeMillis() / 1000.0 < p.zoomAte)
                    (if (p.zoomDir > 0) "Zoom +" else "Zoom -") else "Volume  ·  lados = zoom"
                else -> null
            }
            if (nome != null) pilula(c, nome, x, y + r + 30 * dp, OURO, 14f)
        }
    }

    private fun desenharMini(c: Canvas, p: Painel) {
        val w = 190 * dp; val h = 142 * dp
        val dir = width + onde[0] - 16 * dp
        val topo = onde[1] + 16 * dp
        ret.set(dir - w, topo, dir, topo + h)
        cheio.color = FUNDO
        c.drawRoundRect(ret, 14 * dp, 14 * dp, cheio)
        traco.color = Color.argb(150, 232, 176, 74); traco.strokeWidth = 1.5f * dp
        c.drawRoundRect(ret, 14 * dp, 14 * dp, traco)
        // área que cobre a tela inteira
        val aw = (Ajustes.larguraArea * w).toFloat(); val ah = (Ajustes.alturaArea * h).toFloat()
        val ax = ret.centerX() - aw / 2; val ay = ret.centerY() - ah / 2
        traco.color = Color.argb(70, 232, 176, 74); traco.strokeWidth = 1f * dp
        c.drawRect(ax, ay, ax + aw, ay + ah, traco)
        // esqueleto da mão
        val pts = p.pontos
        if (pts.size == 21) {
            traco.color = OURO; traco.strokeWidth = 1.6f * dp
            for (o in OSSOS) {
                val a = pts[o[0]]; val b = pts[o[1]]
                c.drawLine(ret.left + (a.x * w).toFloat(), ret.top + (a.y * h).toFloat(),
                    ret.left + (b.x * w).toFloat(), ret.top + (b.y * h).toFloat(), traco)
            }
            cheio.color = VERMELHO
            for (q in pts) c.drawCircle(ret.left + (q.x * w).toFloat(), ret.top + (q.y * h).toFloat(), 2.2f * dp, cheio)
        } else {
            texto.textSize = 13 * dp; texto.color = Color.argb(200, 255, 255, 255)
            val s = "Mostre a mão"
            c.drawText(s, ret.centerX() - texto.measureText(s) / 2, ret.centerY() + 5 * dp, texto)
        }
        // título e estado
        cheio.color = if (p.maoVista) Color.rgb(90, 220, 120) else Color.rgb(130, 130, 130)
        c.drawCircle(ret.left + 14 * dp, ret.top + 15 * dp, 4 * dp, cheio)
        texto.textSize = 12 * dp; texto.color = OURO
        c.drawText("HandsOn", ret.left + 24 * dp, ret.top + 19 * dp, texto)
        texto.color = Color.argb(200, 255, 255, 255); texto.textSize = 11 * dp
        val estado = if (p.pausado) "pausado" else (p.pose?.let { NOMES_POSE[it] } ?: "")
        c.drawText(estado, ret.left + 10 * dp, ret.bottom - 9 * dp, texto)
        val fps = "${p.fps.toInt()} fps"
        c.drawText(fps, ret.right - 10 * dp - texto.measureText(fps), ret.top + 19 * dp, texto)
    }

    private fun desenharAjuda(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        val x0 = onde[0].toFloat(); val y0 = onde[1].toFloat()
        cheio.color = Color.argb(185, 8, 2, 2)
        c.drawRect(x0, y0, x0 + W, y0 + H, cheio)
        val colunas = if (W > H) 4 else 2
        val linhas = (GUIA_GESTOS.size + colunas - 1) / colunas
        val margem = 24 * dp
        val topo = y0 + 74 * dp
        val base = y0 + H - 64 * dp
        val cw = (W - margem * 2 - (colunas - 1) * 12 * dp) / colunas
        val ch = min(92 * dp, (base - topo - (linhas - 1) * 10 * dp) / linhas)
        texto.textSize = 26 * dp; texto.color = OURO
        val titulo = "HandsOn  ·  gestos"
        c.drawText(titulo, x0 + W / 2 - texto.measureText(titulo) / 2, y0 + 50 * dp, texto)
        GUIA_GESTOS.forEachIndexed { i, g ->
            val col = i % colunas; val lin = i / colunas
            val l = x0 + margem + col * (cw + 12 * dp)
            val t = topo + lin * (ch + 10 * dp)
            ret.set(l, t, l + cw, t + ch)
            cheio.color = FUNDO
            c.drawRoundRect(ret, 12 * dp, 12 * dp, cheio)
            traco.color = Color.argb(120, 232, 176, 74); traco.strokeWidth = 1.2f * dp
            c.drawRoundRect(ret, 12 * dp, 12 * dp, traco)
            cheio.color = VERMELHO
            c.drawRect(l, t + 14 * dp, l + 4 * dp, t + ch - 14 * dp, cheio)
            val s1 = ch / 92f / dp
            texto.color = OURO; texto.textSize = 17 * dp * s1.coerceIn(0.75f, 1f)
            c.drawText(cortar(g.faz, cw - 28 * dp), l + 16 * dp, t + ch * 0.34f, texto)
            texto.color = Color.WHITE; texto.textSize = 14 * dp * s1.coerceIn(0.75f, 1f)
            c.drawText(cortar(g.gesto, cw - 28 * dp), l + 16 * dp, t + ch * 0.60f, texto)
            texto.color = Color.argb(170, 255, 255, 255); texto.textSize = 12 * dp * s1.coerceIn(0.75f, 1f)
            c.drawText(cortar(g.dica, cw - 28 * dp), l + 16 * dp, t + ch * 0.83f, texto)
        }
        texto.textSize = 15 * dp; texto.color = Color.argb(220, 255, 255, 255)
        val rodape = "Gestos com anel: segure até o anel dourado fechar  ·  mão aberta parada 1 s fecha esta ajuda"
        c.drawText(cortar(rodape, W - 40 * dp), x0 + W / 2 - min(texto.measureText(rodape), W - 40 * dp) / 2, y0 + H - 28 * dp, texto)
    }

    private fun cortar(s: String, max: Float): String {
        if (texto.measureText(s) <= max) return s
        var t = s
        while (t.length > 1 && texto.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun pilula(c: Canvas, s: String, cx: Float, cy: Float, cor: Int, tamanho: Float) {
        texto.textSize = tamanho * dp
        val tw = texto.measureText(s)
        val ph = tamanho * dp * 1.9f
        ret.set(cx - tw / 2 - 16 * dp, cy - ph / 2, cx + tw / 2 + 16 * dp, cy + ph / 2)
        cheio.color = FUNDO
        c.drawRoundRect(ret, ph / 2, ph / 2, cheio)
        traco.color = Color.argb(160, Color.red(cor), Color.green(cor), Color.blue(cor)); traco.strokeWidth = 1.3f * dp
        c.drawRoundRect(ret, ph / 2, ph / 2, traco)
        texto.color = cor
        c.drawText(s, cx - tw / 2, cy + tamanho * dp * 0.36f, texto)
    }
}
