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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Demonstrações animadas dos gestos (mesmo estilo do HandsOn do computador):
// à esquerda a mão fazendo o gesto, à direita a telinha do tablet mostrando o resultado.
// ─────────────────────────────────────────────────────────────────────────────

private val OURO = Color.rgb(232, 176, 74)
private val OURO_CLARO = Color.rgb(255, 222, 150)
private val VERMELHO = Color.rgb(224, 38, 43)
private val APAGADO = Color.rgb(135, 95, 40)
private val ESCURO = Color.rgb(16, 8, 10)
private val TRACO = Color.rgb(70, 40, 25)

private fun ease(k: Double): Double { val x = k.coerceIn(0.0, 1.0); return x * x * (3 - 2 * x) }
private fun fase(u: Double, a: Double, b: Double) = ease((u - a) / (b - a))
private fun passos(u: Double, vararg lim: Double) = lim.count { u >= it }
private fun anel(u: Double, a: Double, b: Double): Double? =
    if (u < a || u > b + 0.04) null else ((u - a) / (b - a)).coerceIn(0.0, 1.0)

private fun mix(p: DoubleArray, q: DoubleArray, k: Double) = doubleArrayOf(p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k)

private val POL_DENTRO = doubleArrayOf(-0.10, 0.40)
private val POL_FORA = doubleArrayOf(-0.86, 0.16)
private val POL_LONGE = doubleArrayOf(-1.02, 0.0)
private val POL_L = doubleArrayOf(-1.08, 0.40)
private val POL_LADO = doubleArrayOf(-1.10, 0.30)
private val POL_CIMA = doubleArrayOf(-0.52, -0.40)
private val POL_BAIXO = doubleArrayOf(-0.46, 1.22)
private val POL_DOBRADO = doubleArrayOf(0.0, 0.40)
private val POL_ZOOM_FECHADO = doubleArrayOf(-0.58, -0.40)

private val NOS = arrayOf(doubleArrayOf(-0.34, 0.14), doubleArrayOf(-0.11, 0.06), doubleArrayOf(0.12, 0.1), doubleArrayOf(0.33, 0.2))
private val COMP = doubleArrayOf(0.72, 0.8, 0.72, 0.56)
private val ANG = doubleArrayOf(-11.0, -3.0, 6.0, 15.0)

/** Pose de uma mão desenhada. */
class Mao(
    val d: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0),   // dedo esticado (0..1)
    val a: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0),   // ângulo extra de cada dedo
    val pol: DoubleArray = POL_DENTRO,                         // ponta do polegar
    val pin: Double = 0.0,                                     // pinça com o indicador
    val pinM: Double = 0.0,                                    // pinça com o médio
    val x: Double = 0.0, val y: Double = 0.0, val s: Double = 1.0,
    val esp: Int = 1,                                          // -1 = mão espelhada
    val costas: Boolean = false                                // mostra as costas da mão
)

private fun d(vararg v: Int) = DoubleArray(4) { v[it].toDouble() }

class Quadro(val maos: List<Mao>, val anel: Double? = null, val passo: Int = 0, val rastro: Boolean = false)

/** Traços com brilho (um halo largo e transparente + o traço de verdade). */
class Neon {
    val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val cheio = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    var esc = 1f
    lateinit var c: Canvas

    private fun halo(cor: Int, esp: Float) {
        linha.color = cor; linha.alpha = 55; linha.strokeWidth = esp * 3.2f
    }

    private fun nucleo(cor: Int, esp: Float) {
        linha.color = cor; linha.alpha = 255; linha.strokeWidth = esp
    }

    fun reta(x0: Float, y0: Float, x1: Float, y1: Float, cor: Int, esp: Float = 1.5f, brilho: Boolean = true) {
        val e = esp * esc
        if (brilho) { halo(cor, e); c.drawLine(x0, y0, x1, y1, linha) }
        nucleo(cor, e); c.drawLine(x0, y0, x1, y1, linha)
    }

    fun caminho(p: Path, cor: Int, esp: Float = 1.5f, fundo: Int? = null, brilho: Boolean = true) {
        if (fundo != null) { cheio.color = fundo; c.drawPath(p, cheio) }
        val e = esp * esc
        if (brilho) { halo(cor, e); c.drawPath(p, linha) }
        nucleo(cor, e); c.drawPath(p, linha)
    }

    fun circulo(x: Float, y: Float, r: Float, cor: Int, esp: Float = 1.5f, preencher: Boolean = false, brilho: Boolean = true) {
        if (preencher) {
            if (brilho) { cheio.color = cor; cheio.alpha = 60; c.drawCircle(x, y, r * 1.7f, cheio) }
            cheio.color = cor; cheio.alpha = 255; c.drawCircle(x, y, r, cheio)
            return
        }
        val e = esp * esc
        if (brilho) { halo(cor, e); c.drawCircle(x, y, r, linha) }
        nucleo(cor, e); c.drawCircle(x, y, r, linha)
    }

    fun arco(x: Float, y: Float, r: Float, ini: Float, varre: Float, cor: Int, esp: Float = 1.5f) {
        val o = RectF(x - r, y - r, x + r, y + r)
        val e = esp * esc
        halo(cor, e); c.drawArc(o, ini, varre, false, linha)
        nucleo(cor, e); c.drawArc(o, ini, varre, false, linha)
    }

    fun ret(l: Float, t: Float, r: Float, b: Float, cor: Int, esp: Float = 1.5f, preencher: Boolean = false,
            raio: Float = 0f, fundo: Int? = null) {
        val o = RectF(l, t, r, b)
        if (fundo != null) { cheio.color = fundo; c.drawRoundRect(o, raio, raio, cheio) }
        if (preencher) {
            cheio.color = cor; cheio.alpha = 255; c.drawRoundRect(o, raio, raio, cheio)
            return
        }
        val e = esp * esc
        halo(cor, e); c.drawRoundRect(o, raio, raio, linha)
        nucleo(cor, e); c.drawRoundRect(o, raio, raio, linha)
    }

    fun escrever(s: String, x: Float, y: Float, tam: Float, cor: Int, centro: Boolean = false) {
        texto.textSize = tam * esc; texto.color = cor
        val xx = if (centro) x - texto.measureText(s) / 2 else x
        c.drawText(s, xx, y + tam * esc * 0.36f, texto)
    }
}

/** Desenha a mão no traço dos cartões. Devolve a ponta do polegar (x, y). */
fun desenharMao(n: Neon, cx: Float, cy: Float, t: Float, m: Mao, cor: Int, destaque: Int? = null): FloatArray {
    val s = (m.s * t).toFloat()
    val ox = cx + (m.x * t).toFloat(); val oy = cy + (m.y * t).toFloat()
    fun px(q: DoubleArray) = ox + m.esp * q[0].toFloat() * s
    fun py(q: DoubleArray) = oy + q[1].toFloat() * s
    val e = max(1.6f, s * 0.085f) / n.esc
    val palma = Path()
    val pp = arrayOf(doubleArrayOf(-0.42, 0.18), doubleArrayOf(0.4, 0.22), doubleArrayOf(0.36, 0.8),
        doubleArrayOf(-0.3, 0.86), doubleArrayOf(-0.46, 0.55))
    pp.forEachIndexed { i, q -> if (i == 0) palma.moveTo(px(q), py(q)) else palma.lineTo(px(q), py(q)) }
    palma.close()
    n.caminho(palma, cor, e, fundo = ESCURO)
    if (m.costas) {                                   // costas da mão: tendões e nós marcados
        for (i in 0 until 4) {
            val b = NOS[i]
            n.reta(px(b), py(b) + 0.06f * s, px(doubleArrayOf(b[0] * 0.5, 0.7)), py(doubleArrayOf(0.0, 0.7)), APAGADO, e * 0.5f, false)
            n.circulo(px(b), py(b) + 0.02f * s, e * 0.9f, cor, 1f, preencher = true, brilho = false)
        }
    }
    val pontas = ArrayList<DoubleArray>()
    for (i in 0 until 4) {
        var curva = 0.0
        if (i == 0) curva += 105 * m.pin
        if (i == 1) curva += 118 * m.pinM
        val b = NOS[i]
        val comp = COMP[i] * (0.26 + 0.74 * m.d[i])
        val ang = Math.toRadians(ANG[i] + m.a[i])
        val j = doubleArrayOf(b[0] + sin(ang) * comp * 0.55, b[1] - cos(ang) * comp * 0.55)
        val a2 = ang - Math.toRadians(curva)
        val p = doubleArrayOf(j[0] + sin(a2) * comp * 0.45, j[1] - cos(a2) * comp * 0.45)
        n.reta(px(b), py(b), px(j), py(j), cor, e)
        n.reta(px(j), py(j), px(p), py(p), cor, e)
        if (m.d[i] > 0.6) n.circulo(px(p), py(p), e * 1.2f * n.esc, cor, preencher = true, brilho = false)
        pontas.add(p)
    }
    var alvo = m.pol
    if (m.pin > 0) alvo = mix(alvo, pontas[0], m.pin)
    if (m.pinM > 0) alvo = mix(alvo, pontas[1], m.pinM)
    val base = doubleArrayOf(-0.42, 0.62)
    val meio = doubleArrayOf((base[0] + alvo[0]) / 2 - 0.09, (base[1] + alvo[1]) / 2 + 0.05)
    n.reta(px(base), py(base), px(meio), py(meio), cor, e)
    n.reta(px(meio), py(meio), px(alvo), py(alvo), cor, e)
    n.circulo(px(alvo), py(alvo), e * 1.2f * n.esc, cor, preencher = true, brilho = false)
    if (destaque != null && max(m.pin, m.pinM) > 0.9) {
        n.circulo(px(alvo), py(alvo), e * 4f * n.esc, destaque, 2f)
        n.circulo(px(alvo), py(alvo), e * 1.8f * n.esc, destaque, preencher = true)
    }
    return floatArrayOf(px(alvo), py(alvo))
}

// ───────────── pedacinhos da telinha (coordenadas locais w x h) ─────────────

private fun seta(n: Neon, x: Float, y: Float, cor: Int) {
    val k = n.esc
    val p = Path()
    val pts = arrayOf(0f to 0f, 0f to 17f, 4f to 13f, 7f to 20f, 10f to 19f, 7f to 12f, 12f to 12f)
    pts.forEachIndexed { i, (a, b) -> if (i == 0) p.moveTo(x + a * k, y + b * k) else p.lineTo(x + a * k, y + b * k) }
    p.close()
    n.caminho(p, cor, 1.2f, fundo = Color.rgb(20, 20, 30))
}

private fun onda(n: Neon, x: Float, y: Float, u: Double, cor: Int, vararg tempos: Double) {
    for (tc in tempos) {
        val k = (u - tc) / 0.09
        if (k in 0.0..1.0) {
            val c = Color.argb((255 * (1 - k)).toInt(), Color.red(cor), Color.green(cor), Color.blue(cor))
            n.circulo(x, y, ((5 + 26 * k) * n.esc).toFloat(), c, 2f)
        }
    }
}

private fun linhas(n: Neon, x0: Float, y0: Float, x1: Float, y1: Float, cor: Int, desloca: Float = 0f, passo: Float = 11f) {
    val pa = passo * n.esc
    val w = x1 - x0
    val n0 = Math.floor((-desloca / pa).toDouble()).toInt() - 1
    for (k in n0 until n0 + ((y1 - y0) / pa).toInt() + 3) {
        val y = y0 + k * pa + desloca
        if (y < y0 + 2 || y > y1 - 3) continue
        val frac = 0.55f + 0.4f * (((k * 37) % 10 + 10) % 10) / 10f
        n.reta(x0 + 6 * n.esc, y, x0 + 6 * n.esc + (w - 14 * n.esc) * frac, y, cor, if (k % 6 == 0) 2f else 1f, false)
    }
}

private fun gradeApps(n: Neon, w: Float, h: Float, cor: Int, destaque: Int = -1, corDestaque: Int = VERMELHO) {
    val col = 4; val lin = 3
    val lado = min(w / (col + 1.2f), h / (lin + 1.4f)) * 0.62f
    for (i in 0 until col * lin) {
        val cx = w * (i % col + 0.5f) / col
        val cy = h * 0.12f + (h * 0.8f) * (i / col + 0.5f) / lin
        n.ret(cx - lado / 2, cy - lado / 2, cx + lado / 2, cy + lado / 2, if (i == destaque) corDestaque else cor,
            1.3f, raio = lado * 0.25f)
    }
}

private fun posApp(w: Float, h: Float, i: Int): FloatArray {
    val col = 4; val lin = 3
    return floatArrayOf(w * (i % col + 0.5f) / col, h * 0.12f + (h * 0.8f) * (i / col + 0.5f) / lin)
}

private fun janela(n: Neon, l: Float, t: Float, r: Float, b: Float, cor: Int) {
    n.ret(l, t, r, b, cor, 1.3f, fundo = ESCURO, raio = 6 * n.esc)
}

private fun foto(n: Neon, cx: Float, cy: Float, w: Float, h: Float, cor: Int) {
    n.ret(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, cor, 1.3f, fundo = ESCURO)
    val p = Path()
    p.moveTo(cx - w / 2 + 4, cy + h / 2 - 4); p.lineTo(cx - w * 0.15f, cy - h * 0.05f)
    p.lineTo(cx + w * 0.05f, cy + h * 0.2f); p.lineTo(cx + w * 0.22f, cy - h * 0.15f); p.lineTo(cx + w / 2 - 4, cy + h / 2 - 4)
    n.caminho(p, cor, 1.3f)
    n.circulo(cx - w * 0.28f, cy - h * 0.22f, min(w, h) * 0.08f, VERMELHO, 1.3f)
}

private fun cortina(n: Neon, w: Float, h: Float, k: Float, cor: Int) {
    if (k <= 0.01f) return
    val b = h * 0.62f * k
    n.ret(2f, 0f, w - 2, b, cor, 1.3f, fundo = Color.rgb(24, 12, 14), raio = 8 * n.esc)
    for (i in 0 until 3) {
        val y = b - h * 0.62f + h * (0.12f + 0.16f * i)
        if (y > 6) n.ret(10f, y, w - 10, y + h * 0.11f, if (i == 0) VERMELHO else APAGADO, 1f, raio = 5 * n.esc)
    }
}

// ───────────── as demonstrações ─────────────

class Demo(val dur: Double, val legendas: List<String>, val mao: (Double) -> Quadro,
           val cena: (Neon, Float, Float, Double) -> Unit, val icone: Double = 0.35)

private fun segurar(m: Mao, u: Double) = Quadro(listOf(m), anel(u, 0.18, 0.56), passos(u, 0.16, 0.58))

val DEMOS: Map<String, Demo> = mapOf(
    "mover" to Demo(3.4, listOf("Aponte com o indicador", "Mova a mão: o cursor segue a palma"),
        { u -> Quadro(listOf(Mao(d = d(1, 0, 0, 0), x = 0.42 * sin(2 * PI * u), y = 0.2 * sin(4 * PI * u))), passo = passos(u, 0.45), rastro = true) },
        { n, w, h, u ->
            gradeApps(n, w, h, TRACO)
            for (j in 12 downTo 0) {
                val uu = u - j * 0.012
                val x = (w * (0.5 + 0.4 * sin(2 * PI * uu))).toFloat()
                val y = (h * (0.5 + 0.3 * sin(4 * PI * uu))).toFloat()
                if (j == 0) seta(n, x, y, OURO)
                else n.circulo(x, y, max(1f, 4f - j / 4f) * n.esc,
                    Color.argb((255 * (1 - j / 13.0)).toInt(), 224, 38, 43), preencher = true, brilho = false)
            }
        }),
    "toque" to Demo(4.0, listOf("Aponte para o app", "Encoste a ponta do polegar na do indicador", "Solte: é um toque"),
        { u -> Quadro(listOf(Mao(d = d(1, 0, 0, 0), pol = POL_FORA, pin = fase(u, 0.18, 0.28) - fase(u, 0.4, 0.48))),
            passo = passos(u, 0.16, 0.4)) },
        { n, w, h, u ->
            val abre = fase(u, 0.5, 0.62) - fase(u, 0.94, 1.0)
            val p = posApp(w, h, 5)
            gradeApps(n, w, h, APAGADO, if (u > 0.28) 5 else -1)
            if (abre > 0) {
                val k = abre.toFloat()
                janela(n, p[0] - 14 * n.esc + (4 - p[0] + 14 * n.esc) * k, p[1] - 14 * n.esc + (4 - p[1] + 14 * n.esc) * k,
                    p[0] + 14 * n.esc + (w - 4 - p[0] - 14 * n.esc) * k, p[1] + 14 * n.esc + (h - 4 - p[1] - 14 * n.esc) * k, OURO)
                if (k > 0.9) linhas(n, 8f, 14f, w - 8, h - 8, APAGADO)
            }
            onda(n, p[0], p[1], u, VERMELHO, 0.28)
            if (abre < 0.5) seta(n, p[0], p[1], OURO)
        }, icone = 0.25),
    "duplo" to Demo(4.0, listOf("Aponte para a foto", "Duas pinças rápidas, no mesmo lugar", "A foto aproxima (toque duplo)"),
        { u -> Quadro(listOf(Mao(d = d(1, 0, 0, 0), pol = POL_FORA,
            pin = fase(u, 0.2, 0.26) - fase(u, 0.28, 0.33) + fase(u, 0.36, 0.41) - fase(u, 0.43, 0.48))),
            passo = passos(u, 0.18, 0.5)) },
        { n, w, h, u ->
            val z = (1 + 0.9 * (fase(u, 0.5, 0.62) - fase(u, 0.94, 1.0))).toFloat()
            n.c.save(); n.c.clipRect(0f, 0f, w, h)
            foto(n, w / 2, h / 2, w * 0.6f * z, h * 0.62f * z, OURO)
            n.c.restore()
            onda(n, w * 0.55f, h * 0.55f, u, VERMELHO, 0.26, 0.41)
            seta(n, w * 0.55f, h * 0.55f, OURO)
        }),
    "longo" to Demo(4.0, listOf("Aponte para o app", "Faça a pinça e segure parada", "Aparece o menu do app", "Solte"),
        { u -> Quadro(listOf(Mao(d = d(1, 0, 0, 0), pol = POL_FORA, pin = fase(u, 0.15, 0.25) - fase(u, 0.7, 0.78))),
            passo = passos(u, 0.14, 0.45, 0.72)) },
        { n, w, h, u ->
            val p = posApp(w, h, 5)
            gradeApps(n, w, h, APAGADO, if (u > 0.25 && u < 0.95) 5 else -1)
            val prog = ((u - 0.25) / 0.2).coerceIn(0.0, 1.0)
            if (u in 0.25..0.47) n.arco(p[0], p[1], 22 * n.esc, -90f, (360 * prog).toFloat(), VERMELHO, 2f)
            val k = (fase(u, 0.45, 0.52) - fase(u, 0.94, 1.0)).toFloat()
            if (k > 0) {
                val l = p[0] + 10 * n.esc; val t = p[1] - 70 * n.esc * k
                n.ret(l, t, l + 90 * n.esc, p[1] - 6 * n.esc, OURO, 1.3f, fundo = ESCURO, raio = 6 * n.esc)
                for (i in 0 until 3) {
                    val y = t + (14 + 18 * i) * n.esc * k
                    if (y < p[1] - 10 * n.esc) n.reta(l + 10 * n.esc, y, l + 70 * n.esc, y, if (i == 0) VERMELHO else OURO, 1f, false)
                }
            }
            seta(n, p[0], p[1], OURO)
        }),
    "arrastar" to Demo(4.4, listOf("Aponte para a foto", "Faça a pinça e mantenha fechada",
        "Mova a mão: a foto desliza junto", "Abra os dedos para soltar"),
        { u -> Quadro(listOf(Mao(d = d(1, 0, 0, 0), pol = POL_FORA, pin = fase(u, 0.12, 0.21) - fase(u, 0.62, 0.7),
            x = 0.32 - 0.64 * fase(u, 0.27, 0.6))), passo = passos(u, 0.12, 0.27, 0.62), rastro = true) },
        { n, w, h, u ->
            val k = (fase(u, 0.27, 0.6) - fase(u, 0.95, 1.0)).toFloat()
            n.c.save(); n.c.clipRect(0f, 0f, w, h)
            foto(n, w / 2 - k * w, h / 2, w * 0.7f, h * 0.7f, OURO)
            foto(n, w / 2 - k * w + w, h / 2, w * 0.7f, h * 0.7f, OURO_CLARO)
            n.c.restore()
            for (i in 0 until 2) n.circulo(w / 2 - 8 * n.esc + i * 16 * n.esc, h - 10 * n.esc, 3.5f * n.esc,
                if ((k > 0.5) == (i == 1)) VERMELHO else APAGADO, preencher = true, brilho = false)
            val cx = w * (0.66f - 0.32f * fase(u, 0.27, 0.6).toFloat())
            seta(n, cx, h * 0.5f, OURO)
        }),
    "rolar" to Demo(4.0, listOf("Indicador e médio levantados", "Desça a mão: a página desce junto",
        "Suba a mão: a página sobe junto"),
        { u -> Quadro(listOf(Mao(d = d(1, 1, 0, 0), a = doubleArrayOf(5.0, -2.0, 0.0, 0.0), y = 0.28 * sin(2 * PI * u))),
            passo = if (u < 0.06) 0 else if (cos(2 * PI * u) > 0) 1 else 2, rastro = true) },
        { n, w, h, u ->
            val off = (0.28 * sin(2 * PI * u) * h * 1.1).toFloat()
            linhas(n, 4f, 4f, w - 14, h - 4, OURO, off)
            n.reta(w - 8, 6f, w - 8, h - 6, APAGADO, 1f, false)
            val pos = (0.5f - off / (h * 1.2f)).coerceIn(0.05f, 0.95f)
            val yb = 6 + (h - 12) * pos
            n.reta(w - 8, yb - 8 * n.esc, w - 8, yb + 8 * n.esc, VERMELHO, 3f)
        }),
    "volume" to Demo(4.0, listOf("Só o mindinho levantado", "Suba a mão: aumenta o volume", "Desça a mão: diminui"),
        { u ->
            val sobe = fase(u, 0.08, 0.32) - fase(u, 0.55, 0.85)
            Quadro(listOf(Mao(d = d(0, 0, 0, 1), y = 0.12 - 0.36 * sobe)), passo = passos(u, 0.06, 0.5), rastro = true)
        },
        { n, w, h, u ->
            val sobe = (fase(u, 0.08, 0.32) - fase(u, 0.55, 0.85)).toFloat()
            val cx = w / 2; val cy = h / 2
            val p = Path()
            val k = n.esc * 1.6f
            p.moveTo(cx - 60 * k, cy - 8 * k); p.lineTo(cx - 50 * k, cy - 8 * k); p.lineTo(cx - 36 * k, cy - 20 * k)
            p.lineTo(cx - 36 * k, cy + 20 * k); p.lineTo(cx - 50 * k, cy + 8 * k); p.lineTo(cx - 60 * k, cy + 8 * k); p.close()
            n.caminho(p, OURO, 1.5f)
            for (i in 1..3) n.arco(cx - 34 * k, cy, (8 + 8 * i) * k, -40f, 80f, if (sobe > i / 4f) VERMELHO else APAGADO, 1.5f)
            val b0 = 14 * n.esc; val b1 = h - 14 * n.esc; val bx = cx + 40 * k
            n.ret(bx - 9 * n.esc, b0, bx + 9 * n.esc, b1, APAGADO, 1f, raio = 6 * n.esc)
            val nivel = 0.3f + 0.6f * sobe
            n.ret(bx - 6 * n.esc, b1 - (b1 - b0) * nivel, bx + 6 * n.esc, b1 - 3 * n.esc, VERMELHO, preencher = true, raio = 4 * n.esc)
        }, icone = 0.2),
    "zoom" to Demo(4.4, listOf("Faça um L com o polegar e o indicador", "Abra os dedos: aproxima (zoom +)",
        "Feche os dedos: afasta (zoom −)"),
        { u ->
            val k = (0.45 + 0.55 * fase(u, 0.12, 0.38) - 0.95 * fase(u, 0.55, 0.82)).coerceIn(0.0, 1.0)
            Quadro(listOf(Mao(d = d(1, 0, 0, 0), pol = mix(POL_ZOOM_FECHADO, POL_L, k))), passo = passos(u, 0.1, 0.5))
        },
        { n, w, h, u ->
            val z = (1 + 0.7 * fase(u, 0.14, 0.4) - 0.9 * fase(u, 0.57, 0.84)).toFloat().coerceAtLeast(0.45f)
            n.c.save(); n.c.clipRect(0f, 0f, w, h)
            foto(n, w / 2, h / 2, w * 0.55f * z, h * 0.58f * z, OURO)
            n.c.restore()
            n.escrever(if (u < 0.5) "zoom +" else "zoom −", w - 30 * n.esc, 14 * n.esc, 12f, VERMELHO, centro = true)
        }, icone = 0.3),
    "voltar" to Demo(3.6, listOf("Mão aberta", "Deslize rápido para o lado", "Volta para a tela anterior"),
        { u ->
            val x = 0.38 - 0.76 * fase(u, 0.25, 0.36) + 0.76 * fase(u, 0.62, 0.95)
            Quadro(listOf(Mao(d = d(1, 1, 1, 1), pol = POL_FORA, x = x)), passo = passos(u, 0.22, 0.38), rastro = u in 0.24..0.45)
        },
        { n, w, h, u ->
            val volta = (fase(u, 0.38, 0.5) - fase(u, 0.95, 1.0)).toFloat()
            n.c.save(); n.c.clipRect(0f, 0f, w, h)
            for ((nome, ox) in listOf("2" to volta * w, "1" to (volta - 1) * w)) {
                janela(n, ox + 6, 6f, ox + w - 6, h - 6, if (nome == "1") OURO_CLARO else OURO)
                linhas(n, ox + 8, 26 * n.esc, ox + w - 8, h - 22 * n.esc, APAGADO, passo = if (nome == "2") 12f else 9f)
                n.escrever("tela $nome", ox + 14 * n.esc, h - 14 * n.esc, 11f, if (nome == "1") VERMELHO else OURO)
            }
            n.c.restore()
        }, icone = 0.1),
    "inicio" to Demo(3.6, listOf("Mão aberta", "Deslize rápido para cima", "Vai para a tela inicial"),
        { u ->
            val y = 0.32 - 0.64 * fase(u, 0.25, 0.36) + 0.64 * fase(u, 0.62, 0.95)
            Quadro(listOf(Mao(d = d(1, 1, 1, 1), pol = POL_FORA, y = y)), passo = passos(u, 0.22, 0.38), rastro = u in 0.24..0.45)
        },
        { n, w, h, u ->
            val k = (fase(u, 0.38, 0.5) - fase(u, 0.95, 1.0)).toFloat()
            gradeApps(n, w, h, if (k > 0.5) OURO else TRACO)
            if (k < 1f) {
                val m = 4 + (w * 0.4f) * k; val mv = 4 + (h * 0.4f) * k
                janela(n, m, mv, w - m, h - mv, OURO)
                if (k < 0.3f) linhas(n, m + 2, mv + 10, w - m - 2, h - mv - 4, APAGADO)
            }
        }, icone = 0.1),
    "notificacoes" to Demo(5.0, listOf("Mão aberta, mais para cima", "Deslize rápido para baixo: notificações",
        "Deslize para baixo de novo...", "...e abrem os ajustes rápidos"),
        { u ->
            val y = -0.32 + 0.64 * fase(u, 0.16, 0.26) - 0.64 * fase(u, 0.32, 0.5) + 0.64 * fase(u, 0.56, 0.66) - 0.64 * fase(u, 0.75, 0.97)
            Quadro(listOf(Mao(d = d(1, 1, 1, 1), pol = POL_FORA, y = y)), passo = passos(u, 0.14, 0.5, 0.66),
                rastro = u in 0.15..0.32 || u in 0.55..0.72)
        },
        { n, w, h, u ->
            gradeApps(n, w, h, TRACO)
            val k1 = (fase(u, 0.27, 0.37) - fase(u, 0.95, 1.0)).toFloat()
            val k2 = (fase(u, 0.67, 0.77) - fase(u, 0.95, 1.0)).toFloat()
            if (k2 > 0.01f) {
                val b = h * (0.62f + 0.3f * k2)
                n.ret(2f, 0f, w - 2, b, OURO, 1.3f, fundo = Color.rgb(24, 12, 14), raio = 8 * n.esc)
                for (i in 0 until 8) {
                    val cx = w * (0.5f + (i % 4)) / 4f; val cy = h * (0.16f + 0.24f * (i / 4)) * (0.4f + 0.6f * k2)
                    n.circulo(cx, cy, 11 * n.esc * k2, if (i == 0 || i == 5) VERMELHO else OURO, 1.4f, preencher = i == 0)
                }
                for (i in 0 until 2) n.ret(10f, b - h * (0.3f - 0.13f * i), w - 10, b - h * (0.2f - 0.13f * i), APAGADO, 1f, raio = 5 * n.esc)
            } else cortina(n, w, h, k1, OURO)
        }, icone = 0.1),
    "menu" to Demo(5.0, listOf("Mão aberta e parada", "Espere o anel encher: o menu abre", "Mova a mão até o item",
        "Faça a pinça (ou espere) para abrir"),
        { u ->
            val x = 0.3 * fase(u, 0.5, 0.6)
            val m = if (u < 0.7) Mao(d = d(1, 1, 1, 1), pol = POL_FORA, x = x)
                    else Mao(d = d(1, 0, 0, 0), pol = POL_FORA, x = x, pin = fase(u, 0.74, 0.8) - fase(u, 0.9, 0.95))
            Quadro(listOf(m), anel(u, 0.08, 0.42), passos(u, 0.08, 0.46, 0.66))
        },
        { n, w, h, u ->
            gradeApps(n, w, h, TRACO)
            val k = (fase(u, 0.43, 0.5) - fase(u, 0.8, 0.84)).toFloat()
            if (k > 0.01f) {
                n.cheio.color = Color.argb((150 * k).toInt(), 8, 2, 2); n.c.drawRect(0f, 0f, w, h, n.cheio)
                val cx = w / 2; val cy = h / 2; val r1 = min(w, h) * 0.44f * k; val r0 = r1 * 0.4f
                for (i in 0 until 8) {
                    val sel = i == 2 && u > 0.58
                    n.arco(cx, cy, (r0 + r1) / 2, -90f + i * 45f - 19f, 38f, if (sel) VERMELHO else OURO, if (sel) 7f else 3f)
                }
                n.escrever("MENU", cx, cy, 10f, OURO_CLARO, centro = true)
            }
            val flash = ((u - 0.8) / 0.08).coerceIn(0.0, 1.0)
            if (u in 0.8..0.93) { n.cheio.color = Color.argb((170 * (1 - flash)).toInt(), 255, 230, 190); n.c.drawRect(0f, 0f, w, h, n.cheio) }
            if (u > 0.82) n.escrever("captura de tela!", w / 2, h - 14 * n.esc, 12f, VERMELHO, centro = true)
        }, icone = 0.2),
    "punho" to Demo(4.8, listOf("Aponte e mova o cursor", "Feche a mão e volte: o cursor fica parado",
        "Abra e continue de onde parou"),
        { u ->
            val x = -0.35 + 0.5 * fase(u, 0.05, 0.3) - 0.5 * fase(u, 0.4, 0.6) + 0.5 * fase(u, 0.68, 0.93)
            val fechado = u in 0.33..0.64
            Quadro(listOf(Mao(d = if (fechado) d(0, 0, 0, 0) else d(1, 0, 0, 0), x = x)), passo = passos(u, 0.33, 0.64), rastro = true)
        },
        { n, w, h, u ->
            gradeApps(n, w, h, TRACO)
            val cx = w * (0.15f + 0.33f * fase(u, 0.05, 0.3).toFloat() + 0.33f * fase(u, 0.68, 0.93).toFloat())
            if (u in 0.33..0.64) n.circulo(cx + 6 * n.esc, h * 0.5f + 9 * n.esc, 16 * n.esc, VERMELHO, 1.5f)
            seta(n, cx, h * 0.5f, OURO)
        }, icone = 0.45),
    "pausar" to Demo(4.8, listOf("Quatro dedos levantados", "Dobre o polegar na palma e segure",
        "Leitura pausada: repita para voltar", "Leitura retomada"),
        { u ->
            val dobra = fase(u, 0.06, 0.16) - fase(u, 0.46, 0.52) + fase(u, 0.56, 0.62) - fase(u, 0.86, 0.92)
            Quadro(listOf(Mao(d = d(1, 1, 1, 1), a = doubleArrayOf(-3.0, 0.0, 3.0, 5.0), pol = mix(POL_FORA, POL_DOBRADO, dobra))),
                anel(u, 0.18, 0.44) ?: anel(u, 0.63, 0.84), passos(u, 0.06, 0.46, 0.86))
        },
        { n, w, h, u ->
            gradeApps(n, w, h, TRACO)
            if (u in 0.44..0.86) {
                n.ret(2f, 2f, w - 2, h - 2, VERMELHO, 2f)
                val cx = w / 2; val cy = h / 2 - 8 * n.esc
                n.ret(cx - 12 * n.esc, cy - 16 * n.esc, cx - 4 * n.esc, cy + 16 * n.esc, VERMELHO, preencher = true)
                n.ret(cx + 4 * n.esc, cy - 16 * n.esc, cx + 12 * n.esc, cy + 16 * n.esc, VERMELHO, preencher = true)
                n.escrever("PAUSADO", cx, cy + 32 * n.esc, 12f, VERMELHO, centro = true)
            } else seta(n, w * 0.55f, h * 0.45f, OURO)
        }, icone = 0.3),
)

/**
 * Mostra uma demonstração. Em "miniatura", desenha só a mão parada (ícone dos cartões).
 */
class DemoView(context: Context) : View(context) {
    var demo: String = "mover"
        set(v) { field = v; inicio = SystemClock.uptimeMillis(); invalidate() }
    var miniatura = false
    private var inicio = SystemClock.uptimeMillis()
    private val n = Neon()

    fun recomecar() { inicio = SystemClock.uptimeMillis(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val dm = DEMOS[demo] ?: return
        n.c = canvas
        val W = width.toFloat(); val H = height.toFloat()
        if (miniatura) {
            n.esc = (min(W, H) / 64f).coerceIn(0.4f, 2f)
            val q = dm.mao(dm.icone)
            for (m in q.maos) desenharMao(n, W / 2, H * 0.47f, min(W, H) * 0.4f, m, OURO, VERMELHO)
            return
        }
        n.esc = (min(W / 560f, H / 260f)).coerceIn(0.6f, 3f)
        val e = n.esc
        val t = (SystemClock.uptimeMillis() - inicio) / 1000.0
        val u = (t / dm.dur) % 1.0
        val q = dm.mao(u)

        // palco da mão (radar)
        val pw = W * 0.47f
        val scx = pw / 2; val scy = (H - 34 * e) / 2
        val r = min(pw, H - 34 * e) * 0.46f
        n.circulo(scx, scy, r, TRACO, 1f, brilho = false)
        n.circulo(scx, scy, r * 0.65f, Color.rgb(50, 28, 18), 1f, brilho = false)
        val rot = ((SystemClock.uptimeMillis() / 20) % 360).toFloat()
        n.arco(scx, scy, r * 1.06f, rot, 50f, OURO, 1f)
        n.arco(scx, scy, r * 1.06f, rot + 180, 20f, OURO, 1f)
        for (ang in 0 until 360 step 15) {
            val ra = Math.toRadians(ang.toDouble())
            val r0 = if (ang % 45 == 0) r * 0.9f else r * 0.95f
            n.reta(scx + cos(ra).toFloat() * r0, scy + sin(ra).toFloat() * r0,
                scx + cos(ra).toFloat() * r, scy + sin(ra).toFloat() * r, APAGADO, 1f, false)
        }
        val tm = r * 0.6f
        val cm = scy - 0.07f * tm
        if (q.rastro) {
            for (j in 10 downTo 1) {
                val ant = dm.mao(((u - j * 0.012) % 1.0 + 1.0) % 1.0).maos[0]
                n.circulo(scx + (ant.x * tm).toFloat(), cm + ((ant.y + 0.5 * ant.s) * tm).toFloat(), max(1f, 5f - j / 2f) * e,
                    Color.argb((255 * (1 - j / 11.0)).toInt(), 224, 38, 43), preencher = true, brilho = false)
            }
        }
        for (m in q.maos) desenharMao(n, scx, cm, tm, m, OURO, VERMELHO)
        q.anel?.let { a ->
            n.arco(scx, scy, r * 0.86f, 0f, 360f, Color.rgb(70, 30, 20), 4f)
            n.arco(scx, scy, r * 0.86f, -90f, (360 * a).toFloat(), VERMELHO, 4f)
            if (a >= 1.0) n.circulo(scx, scy, r * 0.92f, VERMELHO, 2f)
        }
        n.escrever("SEU GESTO", 8 * e, 10 * e, 10f, APAGADO)

        // telinha do tablet
        val mx0 = pw + 22 * e; val mx1 = W - 10 * e
        val alturaTela = (mx1 - mx0) * 0.64f
        val my0 = max(18 * e, (H - 34 * e - alturaTela) / 2); val my1 = min(H - 40 * e, my0 + alturaTela)
        n.reta(pw + 8 * e, 14 * e, pw + 8 * e, H - 40 * e, TRACO, 1f, false)
        n.ret(mx0 - 7 * e, my0 - 7 * e, mx1 + 7 * e, my1 + 7 * e, OURO, 1.5f, fundo = Color.rgb(12, 6, 8), raio = 10 * e)
        n.circulo(mx1 + 3.5f * e, (my0 + my1) / 2, 1.6f * e, APAGADO, preencher = true, brilho = false)
        n.escrever("NO TABLET", mx0, 10 * e, 10f, APAGADO)
        canvas.save()
        canvas.translate(mx0, my0)
        canvas.clipRect(0f, 0f, mx1 - mx0, my1 - my0)
        dm.cena(n, mx1 - mx0, my1 - my0, u)
        canvas.restore()

        // legenda com os passos
        val passo = min(q.passo, dm.legendas.size - 1)
        val ly = H - 16 * e
        for (j in dm.legendas.indices) {
            val x = 12 * e + j * 14 * e
            when {
                j == passo -> n.circulo(x, ly, 4.5f * e, VERMELHO, preencher = true)
                j < passo -> n.circulo(x, ly, 3.5f * e, OURO, preencher = true, brilho = false)
                else -> n.circulo(x, ly, 3.5f * e, APAGADO, 1f, brilho = false)
            }
        }
        n.escrever(dm.legendas[passo], 12 * e + dm.legendas.size * 14 * e + 6 * e, ly, 15f, Color.WHITE)
        // barra de progresso da volta
        n.reta(W - 110 * e, 6 * e, W - 10 * e, 6 * e, TRACO, 2f, false)
        n.reta(W - 110 * e, 6 * e, W - 110 * e + 100 * e * u.toFloat(), 6 * e, OURO, 2f, false)
        postInvalidateOnAnimation()
    }
}
