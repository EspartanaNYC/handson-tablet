package app.handson.tablet

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// O "cérebro" do HandsOn: lê os 21 pontos da mão e decide o que fazer.
// Não depende do Android (pode ser testado em qualquer computador).
// ─────────────────────────────────────────────────────────────────────────────

/** Ponto da mão (imagem: x, y de 0 a 1; mundo: em metros). */
data class P3(val x: Double, val y: Double, val z: Double)

/** O que o leitor pede para o tablet fazer. */
interface Acoes {
    fun toque(x: Float, y: Float)
    fun toqueDuplo(x: Float, y: Float)
    fun toqueLongo(x: Float, y: Float, ms: Long)
    fun arrastar(caminho: List<FloatArray>, ms: Long)
    fun deslizar(x: Float, y: Float, dx: Float, dy: Float)
    fun volume(degraus: Int)
    fun zoom(x: Float, y: Float, degraus: Int)   // > 0 aproxima (afasta os dedos), < 0 afasta
    fun global(acao: String)          // voltar, inicio, recentes, notificacoes, fechar_painel, captura, ajustes_rapidos
    fun som(nome: String)
}

object Ajustes {
    // área da imagem da câmera que cobre a tela inteira (fração da imagem)
    var larguraArea = 0.70
    var alturaArea = 0.62
    var ganhoBorda = 0.3
    var suaveMin = 0.6                 // menor = cursor mais firme
    const val SUAVE_VEL = 0.025
    const val ZONA_MORTA = 2.5         // em "pixels virtuais" (tela de 1920 x 1080)

    const val PINCA_APERTA = 0.33
    const val PINCA_SOLTA = 0.48
    const val PINCA_FILTRO = 4.0
    const val PINCA_REACAO = 2.0
    const val PINCA_CONFIRMA = 2
    const val PINCA_REABRIR = 0.10
    const val PINCA_PISO = 0.01
    const val PINCA_FUNDO = 0.05

    const val TOQUE_MOVIMENTO = 30f    // pixels: andou menos que isso com a pinça = toque
    const val TOQUE_LONGO = 0.55       // segundos com a pinça parada = toque longo
    const val DUPLO_JANELA = 0.45      // segundos para a 2ª pinça virar toque duplo
    const val DUPLO_RAIO = 60f

    const val V_ABERTO = 1.7
    const val V_JUNTOS = 1.4
    const val L_ANGULO = 65.0
    const val JOINHA_DORSO = 0.01
    const val POLEGAR_DOBRADO = 1.0
    const val DEDO_ENTRA = 60.0
    const val DEDO_SAI = 95.0
    const val DEDO_BASE_ENTRA = 55.0
    const val DEDO_BASE_SAI = 78.0

    const val ROLAGEM_PASSO = 0.035    // a mão anda isto (fração da imagem) para cada deslizada
    const val ROLAGEM_GANHO = 2.6      // tamanho da deslizada na tela por movimento da mão
    const val VOLUME_PASSO = 0.045
    const val ZOOM_PASSO = 0.06

    const val SEGURAR = 0.75           // segundos segurando os gestos de "ação"
    const val ESPERA = 0.45
    const val AJUDA_SEGURAR = 1.0      // mão aberta parada para mostrar/esconder os cartões
    const val PARADO = 0.03
}

val POSE_TEMPO = mapOf(
    "mouse" to 0.05, "punho" to 0.10, "dois" to 0.13, "mao" to 0.13, "tres" to 0.13, "quatro" to 0.13,
    "joinha" to 0.13, "polegar_baixo" to 0.13, "mindinho" to 0.15, "v_aberto" to 0.13, "L" to 0.15,
    "chifre" to 0.15, "hang_loose" to 0.15, "aba_dir" to 0.13, "aba_esq" to 0.13
)

/** Gestos de segurar: pose -> (ação, nome na tela). */
val ACOES_POSE = mapOf(
    "L" to ("voltar" to "Voltar"),
    "v_aberto" to ("inicio" to "Tela inicial"),
    "aba_dir" to ("recentes" to "Apps recentes"),
    "aba_esq" to ("recentes" to "Apps recentes"),
    "joinha" to ("notificacoes" to "Notificações"),
    "polegar_baixo" to ("fechar_painel" to "Fechar painel"),
    "chifre" to ("fechar_painel" to "Fechar painel"),
    "hang_loose" to ("captura" to "Captura de tela"),
    "tres" to ("ajustes_rapidos" to "Ajustes rápidos"),
    "quatro" to ("pausar" to "Pausar / retomar"),
)

val NOMES_POSE = mapOf(
    "mouse" to "Cursor", "dois" to "Rolando", "mindinho" to "Volume e zoom", "mao" to "Mão aberta",
    "punho" to "Cursor parado", "joinha" to "Notificações", "polegar_baixo" to "Fechar painel",
    "chifre" to "Fechar painel", "v_aberto" to "Tela inicial", "L" to "Voltar", "hang_loose" to "Captura de tela",
    "aba_dir" to "Apps recentes", "aba_esq" to "Apps recentes", "tres" to "Ajustes rápidos",
    "quatro" to "Pausar / retomar"
)

fun limitar(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

private fun d3(a: P3, b: P3) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

private fun angulo(a: P3, b: P3, c: P3): Double {
    val ux = a.x - b.x; val uy = a.y - b.y; val uz = a.z - b.z
    val vx = c.x - b.x; val vy = c.y - b.y; val vz = c.z - b.z
    val n = sqrt(ux * ux + uy * uy + uz * uz) * sqrt(vx * vx + vy * vy + vz * vz)
    if (n < 1e-9) return 180.0
    return Math.toDegrees(acos(limitar((ux * vx + uy * vy + uz * vz) / n, -1.0, 1.0)))
}

/** Medidas de um quadro da mão. */
class Analise(
    val pontos: List<P3>,            // imagem (0..1), para desenhar
    val ancoraX: Double, val ancoraY: Double,
    val curvas: DoubleArray, val bases: DoubleArray,
    val fechados: BooleanArray,
    val indAberto: Boolean,
    var pinI: Double, var pinM: Double,
    val polegarFora: Boolean, val polegarDir: String, val polegarAberto: Boolean, val polegarLonge: Double,
    val angPolInd: Double, val aberturaV: Double, val polegarRazao: Double, val dorso: Double,
    val tamanho: Double,
) {
    var esticados = BooleanArray(4)
}

fun analisar(img: List<P3>, q: List<P3>, aspecto: Double): Analise {
    val palma = d3(q[0], q[9]).let { if (it < 1e-6) 1e-6 else it }
    fun dobra(a: Int, b: Int, c: Int) = 180.0 - angulo(q[a], q[b], q[c])
    val curvas = DoubleArray(4); val bases = DoubleArray(4)
    val fechados = BooleanArray(4); val abertos = BooleanArray(4)
    for ((i, base) in intArrayOf(5, 9, 13, 17).withIndex()) {
        val pip = base + 1; val dip = base + 2; val ponta = base + 3
        curvas[i] = dobra(base, pip, dip) + dobra(pip, dip, ponta)
        bases[i] = dobra(0, base, pip)
        fechados[i] = d3(q[ponta], q[0]) < d3(q[pip], q[0])
        abertos[i] = d3(q[ponta], q[base]) / palma > 0.45
    }
    val polegarFora = intArrayOf(6, 7, 10, 11).minOf { d3(q[4], q[it]) } / palma > 0.5 && dobra(2, 3, 4) < 50
    // direção do polegar na imagem (x corrigido pelo formato da imagem)
    val vx = (img[4].x - img[2].x) * aspecto
    val vy = img[4].y - img[2].y
    val n = hypot(vx, vy).let { if (it < 1e-9) 1e-9 else it }
    val dir = when {
        vy / n < -0.6 -> "cima"
        vy / n > 0.6 -> "baixo"
        vx > 0 -> "direita"
        else -> "esquerda"
    }
    val polegarAberto = dobra(2, 3, 4) < 40 && d3(q[4], q[9]) / palma > 0.75
    val u = doubleArrayOf(q[4].x - q[2].x, q[4].y - q[2].y, q[4].z - q[2].z)
    val v = doubleArrayOf(q[8].x - q[5].x, q[8].y - q[5].y, q[8].z - q[5].z)
    val nu = sqrt(u.sumOf { it * it }) * sqrt(v.sumOf { it * it })
    val ang = if (nu < 1e-9) 0.0 else Math.toDegrees(acos(limitar((u[0] * v[0] + u[1] * v[1] + u[2] * v[2]) / nu, -1.0, 1.0)))
    val aberturaV = d3(q[8], q[12]) / d3(q[5], q[9]).let { if (it < 1e-6) 1e-6 else it }
    val dorso = (intArrayOf(6, 10, 14, 18).sumOf { q[it].z } - intArrayOf(5, 9, 13, 17).sumOf { q[it].z }) / 4.0
    val larguraPalma = d3(q[5], q[17]).let { if (it < 1e-6) 1e-6 else it }
    val ax = intArrayOf(0, 5, 9, 13, 17).sumOf { img[it].x } / 5.0
    val ay = intArrayOf(0, 5, 9, 13, 17).sumOf { img[it].y } / 5.0
    val tam = hypot((img[0].x - img[9].x) * aspecto, img[0].y - img[9].y) / max(aspecto, 1.0)
    return Analise(
        img, ax, ay, curvas, bases, fechados, abertos[0],
        d3(q[4], q[8]) / palma, d3(q[4], q[12]) / palma,
        polegarFora, dir, polegarAberto, d3(q[4], q[5]) / palma, ang, aberturaV,
        d3(q[4], q[17]) / larguraPalma, dorso, tam
    )
}

class OneEuro(var minCorte: Double, var beta: Double, private val dCorte: Double = 1.0) {
    private var xAnt: Double? = null
    private var dxAnt = 0.0
    private var tAnt = 0.0
    private fun alfa(corte: Double, dt: Double): Double {
        val tau = 1.0 / (2 * Math.PI * corte)
        return 1.0 / (1.0 + tau / dt)
    }
    fun reset() { xAnt = null }
    fun filtrar(x: Double, t: Double): Double {
        val ant = xAnt
        if (ant == null) { xAnt = x; tAnt = t; return x }
        val dt = max(t - tAnt, 1e-3)
        val dx = (x - ant) / dt
        val dxF = dxAnt + alfa(dCorte, dt) * (dx - dxAnt)
        val corte = minCorte + beta * abs(dxF)
        val xF = ant + alfa(corte, dt) * (x - ant)
        xAnt = xF; dxAnt = dxF; tAnt = t
        return xF
    }
}

/** Cursor firme: filtro adaptativo + zona morta que se ajusta ao tremor da câmera. */
class Estabilizador {
    private val fx = OneEuro(Ajustes.suaveMin, Ajustes.SUAVE_VEL)
    private val fy = OneEuro(Ajustes.suaveMin, Ajustes.SUAVE_VEL)
    private var pos: DoubleArray? = null
    private var ruido = Ajustes.ZONA_MORTA / 1.2
    private var vel = 0.0
    private var tAnt = 0.0
    fun reset() { fx.reset(); fy.reset(); pos = null }
    /** x, y em pixels virtuais (1920 x 1080). */
    fun filtrar(x: Double, y: Double, t: Double): DoubleArray {
        val a = fx.filtrar(x, t); val b = fy.filtrar(y, t)
        val p = pos
        if (p == null) { pos = doubleArrayOf(a, b); tAnt = t; return pos!!.copyOf() }
        if (vel < 120) ruido += 0.04 * (hypot(x - a, y - b) - ruido)
        val zona = limitar(1.2 * ruido, Ajustes.ZONA_MORTA, 7.0)
        val firmeza = limitar(1.5 / max(ruido, 1e-3), 0.4, 1.0)
        fx.minCorte = Ajustes.suaveMin * firmeza; fy.minCorte = Ajustes.suaveMin * firmeza
        val dx = a - p[0]; val dy = b - p[1]; val d = hypot(dx, dy)
        val dt = max(t - tAnt, 1e-3); tAnt = t
        if (d > zona) {
            val k = (d - zona) / d
            p[0] += dx * k; p[1] += dy * k
            vel = d * k / dt
        } else vel = 0.0
        return p.copyOf()
    }
}

/** Posição na área (0..1) com mais alcance perto das bordas; o meio continua preciso. */
fun borda(u: Double): Double {
    val d = u - 0.5
    return limitar(0.5 + d * (1 + Ajustes.ganhoBorda * (2 * abs(d)) * (2 * abs(d))), 0.0, 1.0)
}

/** Estado para o desenho na tela (lido pela camada de desenho). */
class Painel {
    @Volatile var cursorX = -1f
    @Volatile var cursorY = -1f
    @Volatile var maoVista = false
    @Volatile var pose: String? = null
    @Volatile var apertado = false
    @Volatile var pinca = 0f                   // 0 = aberta, 1 = fechada
    @Volatile var anel = 0f                    // progresso do gesto de segurar
    @Volatile var anelNome = ""
    @Volatile var aviso = ""
    @Volatile var avisoAte = 0.0
    @Volatile var pausado = false
    @Volatile var ajuda = false
    @Volatile var dica = ""
    @Volatile var pontos: List<P3> = emptyList()
    @Volatile var fps = 0f
    @Volatile var zoomDir = 0
    @Volatile var zoomAte = 0.0
}

class Leitor(private val acoes: Acoes, var larguraTela: Int, var alturaTela: Int) {
    val painel = Painel()
    var pose: String? = null
        private set
    private var candidata: String? = null
    private var candidataDesde = 0.0
    private val dedosEst = BooleanArray(4)
    private val pinFiltroI = OneEuro(Ajustes.PINCA_FILTRO, Ajustes.PINCA_REACAO)
    private val pinFiltroM = OneEuro(Ajustes.PINCA_FILTRO, Ajustes.PINCA_REACAO)
    private val estab = Estabilizador()
    private var cursor = floatArrayOf(-1f, -1f)
    private val historico = ArrayDeque<Pair<Double, FloatArray>>()

    // pinça
    private var apertado = false
    private var armado = true
    private var quadrosFechada = 0
    private var pinMin: Double? = null
    private var tApertou = 0.0
    private var pontoApertou = floatArrayOf(0f, 0f)
    private val caminho = ArrayList<FloatArray>()
    private var ultimoToqueT = -10.0
    private var ultimoToque = floatArrayOf(0f, 0f)
    private var duploNestaPinca = false
    private var direitoPreso = false

    // gestos de segurar
    private var acaoPose: String? = null
    private var acaoInicio = 0.0
    private var acaoFeita = false
    private var acaoSomeDesde: Double? = null
    private var esperaAte = 0.0

    // rolagem / volume / ajuda
    private var rolX: Double? = null
    private var rolY: Double? = null
    private var volY: Double? = null
    private var volX: Double? = null
    private var paradoDesde: Double? = null
    private var paradoRef: DoubleArray? = null
    private var ajudaFeita = false

    private var ultimaVista = -10.0
    var pausado = false
        private set
    private var ultimoT = 0.0
    private var joinhaPalma = false

    private fun avisar(texto: String, t: Double, dur: Double = 0.9) {
        painel.aviso = texto; painel.avisoAte = t + dur
    }

    fun alternarPausa(t: Double) {
        pausado = !pausado
        painel.pausado = pausado
        soltarTudo()
        acoes.som(if (pausado) "pausa" else "volta")
        avisar(if (pausado) "Leitura pausada" else "Leitura retomada", t, 1.4)
    }

    private fun soltarTudo() {
        apertado = false; caminho.clear(); quadrosFechada = 0; pinMin = null
        painel.apertado = false
    }

    // ── quadro sem mão ──
    fun semMao(t: Double) {
        if (t - ultimaVista < 0.25) return       // a câmera perdeu a mão por um instante
        if (apertado) soltarPinca(t)
        painel.maoVista = false
        painel.pontos = emptyList()
        painel.anel = 0f
        painel.dica = ""
        acaoPose = null
        pose = null; candidata = null
        rolX = null; rolY = null; volY = null; paradoDesde = null
        estab.reset(); pinFiltroI.reset(); pinFiltroM.reset()
        cursor[0] = -1f
    }

    // ── quadro com mão ──
    fun quadro(g: Analise, t: Double) {
        ultimaVista = t; ultimoT = t
        painel.maoVista = true
        painel.pontos = g.pontos
        g.pinI = pinFiltroI.filtrar(g.pinI, t)
        g.pinM = pinFiltroM.filtrar(g.pinM, t)
        dedos(g)
        painel.pinca = limitar((0.9 - g.pinI) / (0.9 - Ajustes.PINCA_APERTA), 0.0, 1.0).toFloat()
        painel.dica = when {
            g.tamanho > 0.42 -> "Mão perto demais: afaste um pouco"
            g.tamanho < 0.10 -> "Mão longe: chegue mais perto da câmera"
            joinhaPalma && pose == "punho" -> "Notificações: joinha com as costas da mão para a câmera"
            else -> ""
        }
        val nova = classificar(g)
        estabilizar(nova, t)
        painel.pose = pose
        if (pausado) {
            if (pose == "quatro") acao("quatro", t) else { acaoPose = null; painel.anel = 0f }
            moverCursor(g, t, mover = false)
            return
        }
        if (pose != "mouse" && apertado) soltarPinca(t)
        if (pose in ACOES_POSE) acaoSomeDesde = null
        else if (acaoPose != null) {
            if (acaoFeita) acaoPose = null
            else {
                val desde = acaoSomeDesde ?: t.also { acaoSomeDesde = it }
                if (t - desde > 0.25) { acaoPose = null; acaoSomeDesde = null; painel.anel = 0f }
            }
        }
        if (pose != "mao") { paradoDesde = null; ajudaFeita = false }
        when (val p = pose) {
            "mouse" -> { moverCursor(g, t, mover = true); pinca(g, t) }
            "punho" -> moverCursor(g, t, mover = false)
            "dois" -> { moverCursor(g, t, mover = false); rolar(g) }
            "mindinho" -> { moverCursor(g, t, mover = false); volume(g) }
            "mao" -> { moverCursor(g, t, mover = true); maoAberta(g, t) }
            null -> {}
            else -> if (p in ACOES_POSE) { moverCursor(g, t, mover = false); acao(p, t) }
        }
        if (pose !in ACOES_POSE && acaoPose == null) painel.anel = 0f
    }

    private fun dedos(g: Analise) {
        for (i in 0 until 4) {
            dedosEst[i] = if (dedosEst[i]) g.curvas[i] < Ajustes.DEDO_SAI && g.bases[i] < Ajustes.DEDO_BASE_SAI
            else g.curvas[i] < Ajustes.DEDO_ENTRA && g.bases[i] < Ajustes.DEDO_BASE_ENTRA
        }
        g.esticados = dedosEst.copyOf()
    }

    private fun limiteSoltar(): Double {
        val pm = pinMin ?: return Ajustes.PINCA_SOLTA
        if (apertado && ultimoT - tApertou > 0.5) return Ajustes.PINCA_SOLTA
        return max(Ajustes.PINCA_APERTA + Ajustes.PINCA_PISO, min(Ajustes.PINCA_SOLTA, pm + Ajustes.PINCA_REABRIR))
    }

    fun classificar(g: Analise): String {
        joinhaPalma = false
        if (apertado && g.pinI < limiteSoltar()) return "mouse"
        val e = g.esticados
        if (g.pinI < Ajustes.PINCA_APERTA && g.indAberto) return "mouse"
        if (!e[1] && !e[2]) {
            if (e[0] && e[3]) return "chifre"
            if (!e[0] && e[3]) {
                val longe = g.polegarLonge > (if (pose == "hang_loose") 0.62 else 0.78)
                return if (g.polegarAberto && longe) "hang_loose" else "mindinho"
            }
            if (e[0] && !e[3] && g.polegarAberto && g.angPolInd > Ajustes.L_ANGULO - (if (pose == "L") 10 else 0)) return "L"
        }
        if (e.all { it }) {
            val lim = Ajustes.POLEGAR_DOBRADO * (if (pose == "quatro") 1.15 else 1.0)
            return if (g.polegarRazao < lim) "quatro" else "mao"
        }
        if (e[0] && e[1] && e[2] && !e[3]) return "tres"
        if (e[0] && e[1] && !e[2] && !e[3]) {
            val a = g.aberturaV
            return when {
                a > Ajustes.V_ABERTO -> "v_aberto"
                a < Ajustes.V_JUNTOS -> "dois"
                pose == "dois" || pose == "v_aberto" -> pose!!
                else -> "dois"
            }
        }
        if (g.fechados.all { it } && !g.indAberto) {
            if (g.polegarFora && g.polegarDir == "cima") {
                val lim = if (pose == "joinha") 0.0 else Ajustes.JOINHA_DORSO
                if (g.dorso > lim) return "joinha"
                joinhaPalma = true
                return "punho"
            }
            if (g.polegarFora && g.polegarDir == "baixo") return "polegar_baixo"
            if (g.polegarFora && g.polegarDir == "direita") return "aba_dir"
            if (g.polegarFora && g.polegarDir == "esquerda") return "aba_esq"
            return "punho"
        }
        return "mouse"
    }

    private fun estabilizar(nova: String, t: Double) {
        if (nova != candidata) { candidata = nova; candidataDesde = t }
        val imediato = nova == "mouse" && apertado
        if (nova != pose && (imediato || t - candidataDesde >= (POSE_TEMPO[nova] ?: 0.1))) {
            pose = nova
            if (nova == "dois") { rolX = null; rolY = null }
            if (nova == "mindinho") volY = null
        }
    }

    // ── cursor ──
    private fun moverCursor(g: Analise, t: Double, mover: Boolean) {
        if (!mover && cursor[0] >= 0) {
            painel.cursorX = cursor[0]; painel.cursorY = cursor[1]
            estab.reset()
            return
        }
        val bx = (g.ancoraX - (0.5 - Ajustes.larguraArea / 2)) / Ajustes.larguraArea
        val by = (g.ancoraY - (0.5 - Ajustes.alturaArea / 2)) / Ajustes.alturaArea
        val ux = borda(limitar(bx, 0.0, 1.0)); val uy = borda(limitar(by, 0.0, 1.0))
        val v = estab.filtrar(ux * 1920, uy * 1080, t)
        val x = (v[0] / 1920 * (larguraTela - 1)).toFloat()
        val y = (v[1] / 1080 * (alturaTela - 1)).toFloat()
        if (!apertado || !duploNestaPinca) { cursor[0] = x; cursor[1] = y }
        historico.addLast(t to floatArrayOf(x, y))
        while (historico.isNotEmpty() && t - historico.first().first > 0.3) historico.removeFirst()
        painel.cursorX = cursor[0]; painel.cursorY = cursor[1]
        if (painel.dica.isEmpty() && (bx < -0.05 || bx > 1.05 || by < -0.05 || by > 1.05))
            painel.dica = "Traga a mão um pouco mais para o centro"
    }

    /** Posição de ~80 ms atrás (o gesto de fechar os dedos mexe um pouquinho a mão). */
    private fun posicaoAntes(t: Double): FloatArray {
        for ((tt, p) in historico) if (t - tt <= 0.08) return p.copyOf()
        return cursor.copyOf()
    }

    // ── pinça: toque, toque duplo, toque longo e arrastar ──
    private fun pinca(g: Analise, t: Double) {
        val pin = g.pinI
        if (!apertado) {
            if (pin > Ajustes.PINCA_SOLTA) armado = true
            val fechada = pin < Ajustes.PINCA_APERTA && g.indAberto
            quadrosFechada = if (fechada) quadrosFechada + 1 else 0
            val confirmada = quadrosFechada >= Ajustes.PINCA_CONFIRMA + 1 ||
                (quadrosFechada >= Ajustes.PINCA_CONFIRMA && pin < Ajustes.PINCA_APERTA - Ajustes.PINCA_FUNDO)
            if (armado && confirmada) {
                apertado = true; armado = false
                painel.apertado = true
                tApertou = t; pinMin = pin
                pontoApertou = posicaoAntes(t)
                caminho.clear(); caminho.add(pontoApertou.copyOf())
                acoes.som("clique")
                // 2ª pinça logo depois de um toque, no mesmo lugar = toque duplo (na hora)
                duploNestaPinca = t - ultimoToqueT < Ajustes.DUPLO_JANELA &&
                    hypot((pontoApertou[0] - ultimoToque[0]).toDouble(), (pontoApertou[1] - ultimoToque[1]).toDouble()) < Ajustes.DUPLO_RAIO
                if (duploNestaPinca) {
                    acoes.toqueDuplo(ultimoToque[0], ultimoToque[1])
                    avisar("Toque duplo", t)
                    ultimoToqueT = -10.0
                    cursor[0] = ultimoToque[0]; cursor[1] = ultimoToque[1]
                }
            }
        } else {
            pinMin = min(pinMin ?: pin, pin)
            caminho.add(cursor.copyOf())
            if (pin > limiteSoltar()) soltarPinca(t)
        }
    }

    private fun soltarPinca(t: Double) {
        if (!apertado) return
        apertado = false
        painel.apertado = false
        pinMin = null
        armado = true
        quadrosFechada = 0
        if (duploNestaPinca) { duploNestaPinca = false; caminho.clear(); return }
        val p0 = pontoApertou
        val longe = caminho.maxOfOrNull { hypot((it[0] - p0[0]).toDouble(), (it[1] - p0[1]).toDouble()) } ?: 0.0
        val dur = t - tApertou
        when {
            longe < Ajustes.TOQUE_MOVIMENTO && dur < Ajustes.TOQUE_LONGO -> {
                acoes.toque(p0[0], p0[1]); ultimoToqueT = t; ultimoToque = p0.copyOf()
                avisar("Toque", t, 0.5)
            }
            longe < Ajustes.TOQUE_MOVIMENTO -> {
                acoes.toqueLongo(p0[0], p0[1], (dur * 1000).toLong().coerceIn(600, 1500))
                avisar("Toque longo", t)
            }
            else -> {
                val pts = simplificar(caminho)
                acoes.arrastar(pts, (dur * 1000).toLong().coerceIn(150, 4000))
                avisar("Arrastar", t, 0.6)
            }
        }
        caminho.clear()
    }

    private fun simplificar(c: List<FloatArray>): List<FloatArray> {
        if (c.size <= 2) return c.map { it.copyOf() }
        val r = ArrayList<FloatArray>()
        r.add(c.first())
        for (p in c.drop(1)) {
            val u = r.last()
            if (hypot((p[0] - u[0]).toDouble(), (p[1] - u[1]).toDouble()) > 8) r.add(p)
        }
        if (r.last() !== c.last()) r.add(c.last())
        return r
    }

    // ── rolagem: 2 dedos juntos, a página acompanha a mão ──
    private fun rolar(g: Analise) {
        val x0 = rolX; val y0 = rolY
        if (x0 == null || y0 == null) { rolX = g.ancoraX; rolY = g.ancoraY; return }
        val dx = g.ancoraX - x0; val dy = g.ancoraY - y0
        if (abs(dy) >= Ajustes.ROLAGEM_PASSO || abs(dx) >= Ajustes.ROLAGEM_PASSO * 1.3) {
            // rola o que está embaixo do cursor (ou o meio da tela)
            val cx = if (cursor[0] >= 0) cursor[0] else (larguraTela / 2).toFloat()
            val cy = if (cursor[0] >= 0) cursor[1] else (alturaTela / 2).toFloat()
            if (abs(dy) >= abs(dx)) acoes.deslizar(cx, cy, 0f, (dy * Ajustes.ROLAGEM_GANHO * alturaTela).toFloat())
            else acoes.deslizar(cx, cy, (dx * Ajustes.ROLAGEM_GANHO * larguraTela).toFloat(), 0f)
            rolX = g.ancoraX; rolY = g.ancoraY
        }
    }

    // ── só o mindinho: subir/descer = volume; levar para a direita/esquerda = zoom ──
    private fun volume(g: Analise) {
        val y0 = volY ?: run { volY = g.ancoraY; volX = g.ancoraX; return }
        val x0 = volX ?: g.ancoraX
        val dy = y0 - g.ancoraY                 // > 0: a mão subiu
        val dx = g.ancoraX - x0                 // > 0: a mão foi para a direita (imagem espelhada)
        if (abs(dy) >= Ajustes.VOLUME_PASSO && abs(dy) > abs(dx)) {
            val degraus = (dy / Ajustes.VOLUME_PASSO).toInt()
            acoes.volume(degraus)
            acoes.som("tique")
            volY = y0 - degraus * Ajustes.VOLUME_PASSO
            volX = g.ancoraX
        } else if (abs(dx) >= Ajustes.ZOOM_PASSO && abs(dx) > 1.2 * abs(dy)) {
            val degraus = (dx / Ajustes.ZOOM_PASSO).toInt()
            val cx = if (cursor[0] >= 0) cursor[0] else larguraTela / 2f
            val cy = if (cursor[0] >= 0) cursor[1] else alturaTela / 2f
            acoes.zoom(cx, cy, degraus)
            acoes.som("tique")
            painel.zoomDir = if (degraus > 0) 1 else -1
            painel.zoomAte = ultimoT + 0.8
            volX = x0 + degraus * Ajustes.ZOOM_PASSO
            volY = g.ancoraY
        }
    }

    // ── mão aberta parada = mostra / esconde os cartões de ajuda ──
    private fun maoAberta(g: Analise, t: Double) {
        val ref = paradoRef
        if (paradoDesde == null || ref == null || hypot(g.ancoraX - ref[0], g.ancoraY - ref[1]) > Ajustes.PARADO) {
            paradoDesde = t; paradoRef = doubleArrayOf(g.ancoraX, g.ancoraY); painel.anel = 0f
            return
        }
        if (ajudaFeita) return
        val prog = (t - paradoDesde!!) / Ajustes.AJUDA_SEGURAR
        painel.anel = prog.toFloat().coerceIn(0f, 1f)
        painel.anelNome = if (painel.ajuda) "Esconder ajuda" else "Mostrar ajuda"
        if (prog >= 1) {
            painel.ajuda = !painel.ajuda
            ajudaFeita = true
            painel.anel = 0f
            acoes.som("confirma")
        }
    }

    // ── gestos de segurar (com anel de progresso) ──
    private fun acao(p: String, t: Double) {
        val (qual, nome) = ACOES_POSE[p] ?: return
        if (acaoPose != p) {
            acaoPose = p; acaoInicio = t; acaoFeita = false
        }
        if (acaoFeita) { painel.anel = 0f; return }
        if (t < esperaAte) { acaoInicio = t; painel.anel = 0f; return }
        val prog = (t - acaoInicio) / Ajustes.SEGURAR
        painel.anel = prog.toFloat().coerceIn(0f, 1f)
        painel.anelNome = nome
        if (prog >= 1) {
            acaoFeita = true
            esperaAte = t + Ajustes.ESPERA
            painel.anel = 0f
            if (qual == "pausar") alternarPausa(t)
            else {
                acoes.global(qual)
                acoes.som("confirma")
                avisar(nome, t)
            }
        }
    }
}
