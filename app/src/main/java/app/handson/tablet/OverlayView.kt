package app.handson.tablet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Desenha o HUD vermelho e dourado por cima de tudo: cursor, dedo, menu, setas, avisos e ajuda. */
class OverlayView(context: Context) : View(context) {

    var painel: Painel? = null
    private var escondidoAte = 0L

    private val dp = resources.displayMetrics.density
    private val OURO = Color.rgb(232, 176, 74)
    private val OURO_CLARO = Color.rgb(255, 222, 150)
    private val VERMELHO = Color.rgb(224, 38, 43)
    private val FUNDO = Color.argb(222, 22, 8, 8)

    private val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cheio = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val onde = IntArray(2)
    private val ret = RectF()
    private val neon = Neon()

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
        val agora = p.agora
        val cx = width / 2f + onde[0]

        if (p.ajuda) desenharAjuda(c)
        if (p.menuAberto) desenharMenu(c, p)
        desenharDeslize(c, p, agora)

        if (p.pausado) {
            pilula(c, "PAUSADO  ·  quatro dedos para voltar", cx, 46 * dp, OURO, 16f)
        } else if (p.maoVista && p.cursorX >= 0 && !p.menuAberto) {
            desenharCursor(c, p, agora)
        }
        if (agora < p.avisoAte && p.aviso.isNotEmpty()) {
            pilula(c, p.aviso, cx, (if (p.pausado) 92 else 46) * dp, OURO, 18f)
        }
        if (p.dica.isNotEmpty() && p.maoVista && !p.ajuda) {
            pilula(c, p.dica, cx, height - 70 * dp + onde[1], Color.WHITE, 15f)
        }
        c.restore()
    }

    private fun desenharCursor(c: Canvas, p: Painel, agora: Double) {
        val x = p.cursorX; val y = p.cursorY
        val r = 17 * dp
        cheio.color = Color.argb(45, 224, 38, 43)
        c.drawCircle(x, y, r * 1.8f, cheio)
        traco.color = OURO; traco.strokeWidth = 2.5f * dp
        c.drawCircle(x, y, r, traco)
        // marcas de mira girando
        val rot = ((SystemClock.uptimeMillis() / 6) % 360).toFloat()
        ret.set(x - r - 6 * dp, y - r - 6 * dp, x + r + 6 * dp, y + r + 6 * dp)
        traco.strokeWidth = 1.5f * dp; traco.color = OURO_CLARO
        c.drawArc(ret, rot, 40f, false, traco); c.drawArc(ret, rot + 180, 40f, false, traco)
        if (p.pinca > 0.05f && !p.apertado && p.pose == "mouse") {
            traco.color = VERMELHO; traco.strokeWidth = 3.5f * dp
            ret.set(x - r, y - r, x + r, y + r)
            c.drawArc(ret, -90f, 360f * p.pinca, false, traco)
        }
        cheio.color = if (p.apertado) VERMELHO else Color.argb(230, 224, 38, 43)
        c.drawCircle(x, y, if (p.apertado) r * 0.75f else 5 * dp, cheio)
        // dedo virtual encostado (rolagem / arrasto)
        if (p.dedoAtivo && p.dedoX >= 0) {
            cheio.color = Color.argb(70, 224, 38, 43); c.drawCircle(p.dedoX, p.dedoY, 30 * dp, cheio)
            cheio.color = Color.argb(200, 255, 222, 150); c.drawCircle(p.dedoX, p.dedoY, 9 * dp, cheio)
            traco.color = VERMELHO; traco.strokeWidth = 2f * dp; c.drawCircle(p.dedoX, p.dedoY, 22 * dp, traco)
        }
        // anel de "segurar" (pausar / menu)
        if (p.anel > 0.01f) {
            val ra = r + 13 * dp
            traco.color = Color.argb(70, 232, 176, 74); traco.strokeWidth = 5 * dp
            c.drawCircle(x, y, ra, traco)
            traco.color = OURO
            ret.set(x - ra, y - ra, x + ra, y + ra)
            c.drawArc(ret, -90f, 360f * p.anel, false, traco)
            pilula(c, p.anelNome, x, y + ra + 26 * dp, OURO, 15f)
            return
        }
        val nome = when {
            p.pose == "zoom" -> if (agora < p.zoomAte) (if (p.zoomDir > 0) "Zoom +" else "Zoom −") else "Zoom: abra ou feche os dedos"
            p.pose == "mindinho" -> if (agora - p.volumeEm < 0.6) (if (p.volumeDir > 0) "Volume +" else "Volume −") else "Volume: suba ou desça a mão"
            p.pose == "dois" -> "Rolando"
            p.pose == "punho" -> "Cursor parado"
            p.pose == "mao" -> "Deslize: ← → voltar · ↑ início · ↓ avisos"
            else -> null
        }
        if (nome != null) pilula(c, nome, x, y + r + 34 * dp, OURO, 14f)
        if (p.pose == "zoom") {             // dois pontinhos que abrem/fecham
            val k = if (agora < p.zoomAte) (if (p.zoomDir > 0) 1.5f else 0.6f) else 1f
            cheio.color = OURO_CLARO
            c.drawCircle(x - 30 * dp * k, y, 5 * dp, cheio); c.drawCircle(x + 30 * dp * k, y, 5 * dp, cheio)
        }
    }

    private fun desenharDeslize(c: Canvas, p: Painel, agora: Double) {
        val k = ((agora - p.deslizeEm) / 0.6).toFloat()
        if (k < 0f || k > 1f) return
        val a = (255 * (1 - k)).toInt()
        val cx = width / 2f + onde[0]; val cy = height / 2f + onde[1]
        val (dx, dy) = when (p.deslize) {
            "esquerda" -> -1f to 0f; "direita" -> 1f to 0f; "cima" -> 0f to -1f; else -> 0f to 1f
        }
        val comp = 140 * dp; val desl = 60 * dp * k
        val x0 = cx - dx * comp / 2 + dx * desl; val y0 = cy - dy * comp / 2 + dy * desl
        val x1 = x0 + dx * comp; val y1 = y0 + dy * comp
        traco.color = Color.argb(a, 224, 38, 43); traco.strokeWidth = 16 * dp
        c.drawLine(x0, y0, x1, y1, traco)
        val pts = Path()
        val px = -dy; val py = dx
        pts.moveTo(x1 + dx * 36 * dp, y1 + dy * 36 * dp)
        pts.lineTo(x1 + px * 30 * dp, y1 + py * 30 * dp)
        pts.lineTo(x1 - px * 30 * dp, y1 - py * 30 * dp)
        pts.close()
        cheio.color = Color.argb(a, 224, 38, 43); c.drawPath(pts, cheio)
        traco.color = Color.argb(a, 255, 222, 150); traco.strokeWidth = 3 * dp
        c.drawLine(x0, y0, x1, y1, traco)
    }

    private fun desenharMenu(c: Canvas, p: Painel) {
        val cx = width / 2f + onde[0]; val cy = height / 2f + onde[1]
        cheio.color = Color.argb(150, 8, 2, 2)
        c.drawRect(onde[0].toFloat(), onde[1].toFloat(), onde[0] + width.toFloat(), onde[1] + height.toFloat(), cheio)
        val r1 = min(width, height) * 0.36f; val r0 = r1 * 0.38f
        val n = MENU_ITENS.size
        for (i in 0 until n) {
            val ang = -90f + i * 45f
            val sel = i == p.menuSel
            val path = Path()
            ret.set(cx - r1, cy - r1, cx + r1, cy + r1)
            path.arcTo(ret, ang - 21f, 42f)
            ret.set(cx - r0, cy - r0, cx + r0, cy + r0)
            path.arcTo(ret, ang + 21f, -42f)
            path.close()
            cheio.color = if (sel) Color.argb(200, 150, 18, 22) else FUNDO
            c.drawPath(path, cheio)
            traco.color = if (sel) OURO_CLARO else Color.argb(170, 232, 176, 74); traco.strokeWidth = (if (sel) 2.5f else 1.2f) * dp
            c.drawPath(path, traco)
            val ra = Math.toRadians(ang.toDouble())
            val rm = (r0 + r1) / 2
            val tx = cx + (cos(ra) * rm).toFloat(); val ty = cy + (sin(ra) * rm).toFloat()
            texto.textSize = 16 * dp; texto.color = if (sel) Color.WHITE else OURO
            val nome = MENU_ITENS[i].second
            c.drawText(nome, tx - texto.measureText(nome) / 2, ty + 6 * dp, texto)
            if (sel && p.menuProg > 0f) {
                traco.color = OURO_CLARO; traco.strokeWidth = 5 * dp
                ret.set(cx - r1 - 10 * dp, cy - r1 - 10 * dp, cx + r1 + 10 * dp, cy + r1 + 10 * dp)
                c.drawArc(ret, ang - 21f, 42f * p.menuProg, false, traco)
            }
        }
        // centro
        cheio.color = Color.argb(240, 18, 7, 8); c.drawCircle(cx, cy, r0 - 8 * dp, cheio)
        traco.color = VERMELHO; traco.strokeWidth = 2 * dp; c.drawCircle(cx, cy, r0 - 8 * dp, traco)
        texto.textSize = 20 * dp; texto.color = OURO_CLARO
        c.drawText("MENU", cx - texto.measureText("MENU") / 2, cy - 4 * dp, texto)
        texto.textSize = 11 * dp; texto.color = Color.argb(210, 255, 255, 255)
        for ((k, s) in listOf("mova a mão até o item", "pinça ou espere: abre", "punho: fecha").withIndex()) {
            c.drawText(s, cx - texto.measureText(s) / 2, cy + (16 + 15 * k) * dp, texto)
        }
    }

    private fun desenharAjuda(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        val x0 = onde[0].toFloat(); val y0 = onde[1].toFloat()
        cheio.color = Color.argb(195, 8, 2, 2)
        c.drawRect(x0, y0, x0 + W, y0 + H, cheio)
        val colunas = if (W > H) 4 else 2
        val linhas = (GUIA_GESTOS.size + colunas - 1) / colunas
        val margem = 24 * dp
        val topo = y0 + 80 * dp
        val base = y0 + H - 64 * dp
        val cw = (W - margem * 2 - (colunas - 1) * 12 * dp) / colunas
        val ch = min(96 * dp, (base - topo - (linhas - 1) * 10 * dp) / linhas)
        texto.textSize = 26 * dp; texto.color = OURO
        val titulo = "HandsOn  ·  gestos"
        c.drawText(titulo, x0 + W / 2 - texto.measureText(titulo) / 2, y0 + 52 * dp, texto)
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
            val s1 = (ch / 96f / dp).coerceIn(0.75f, 1f)
            val lente = ch * 0.36f
            val lx = l + 14 * dp + lente; val ly = t + ch / 2
            cheio.color = Color.rgb(16, 8, 10); c.drawCircle(lx, ly, lente, cheio)
            traco.color = Color.argb(140, 232, 176, 74); traco.strokeWidth = 1f * dp
            c.drawCircle(lx, ly, lente, traco)
            DEMOS[g.demo]?.let { dm ->
                neon.c = c; neon.esc = dp * 0.55f
                for (m in dm.mao(dm.icone).maos) desenharMao(neon, lx, ly - lente * 0.08f, lente * 0.62f, m, OURO, VERMELHO)
            }
            val tx = lx + lente + 10 * dp
            val tw = l + cw - tx - 8 * dp
            texto.color = OURO; texto.textSize = 17 * dp * s1
            c.drawText(cortar(g.faz, tw), tx, t + ch * 0.34f, texto)
            texto.color = Color.WHITE; texto.textSize = 14 * dp * s1
            c.drawText(cortar(g.gesto, tw), tx, t + ch * 0.60f, texto)
            texto.color = Color.argb(170, 255, 255, 255); texto.textSize = 12 * dp * s1
            c.drawText(cortar(g.dica, tw), tx, t + ch * 0.83f, texto)
        }
        texto.textSize = 15 * dp; texto.color = Color.argb(220, 255, 255, 255)
        val rodape = "Mão aberta parada = menu (lá está \"Ajuda\" para fechar)  ·  teclado: Ctrl + Alt + H"
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
