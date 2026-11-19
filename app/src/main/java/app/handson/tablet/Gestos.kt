package app.handson.tablet

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// O "cérebro" do HandsOn para tablet: lê os 21 pontos da mão e decide o que fazer.
// Pensado para a tela de toque: um DEDO VIRTUAL encosta, desliza e solta na tela
// acompanhando a mão em tempo real. Não depende do Android (é testado no computador).
//
//   ☝️  Apontar ............ move o cursor
//   🤏  Pinça ............... toque · 2 pinças = toque duplo · segurar = toque longo · pinça + mover = arrastar
//   ✌️  Dois dedos .......... rolar: a página acompanha a mão (para cima, para baixo e para os lados)
//   🤙  Só o mindinho ....... volume (subir/descer a mão)
//   👆  L (polegar+indicador) abra ou feche os dois dedos = zoom, como na tela
//   🖐  Mão aberta, deslize rápido:  ← → voltar · ↑ tela inicial · ↓ notificações (2x = ajustes rápidos)
//   🖐  Mão aberta parada 1 s = MENU (apps recentes, captura, ajustes, pausar, ajuda…)
//   ✊  Punho ............... segura o cursor parado (para reposicionar a mão)
//   🖖  Quatro dedos (polegar dobrado), segurando = pausar / retomar
// ─────────────────────────────────────────────────────────────────────────────

/** Ponto da mão (imagem: x, y de 0 a 1; mundo: em metros). */
data class P3(val x: Double, val y: Double, val z: Double)

/** O que o leitor pede para o tablet fazer. */
interface Acoes {
    fun dedoDescer(x: Float, y: Float)            // o dedo virtual encosta na tela
    fun dedoMover(x: Float, y: Float)             // ... desliza (segue a mão)
    fun dedoSubir()                               // ... solta
    fun toque(x: Float, y: Float)                 // um toque rapidinho (segundo toque do toque duplo)
    fun zoom(x: Float, y: Float, fator: Float)    // > 1 aproxima, < 1 afasta
    fun volume(degraus: Int)
    fun global(acao: String)                      // voltar, inicio, recentes, notificacoes, ajustes_rapidos, fechar_painel, captura
    fun comando(nome: String)                     // do próprio HandsOn: ajuda, janela
    fun som(nome: String)
}

object Ajustes {
    // área da imagem da câmera que cobre a tela inteira (fração da imagem)
    var larguraArea = 0.70
    var alturaArea = 0.62
    var ganhoBorda = 0.3
    var suaveMin = 0.6                 // menor = cursor mais firme
    var rolagemGanho = 2.4             // quanto a página anda para cada movimento da mão
    const val SUAVE_VEL = 0.03
    const val ZONA_MORTA = 2.0         // em "pixels virtuais" (tela de 1920 x 1080)
    const val ANTECIPA = 0.022         // s: o cursor "adianta" um pouquinho o movimento (menos atraso)

    const val PINCA_APERTA = 0.33
    const val PINCA_SOLTA = 0.48
    const val PINCA_FILTRO = 5.0
    const val PINCA_REACAO = 2.0
    const val PINCA_CONFIRMA = 2
    const val PINCA_REABRIR = 0.10
    const val PINCA_PISO = 0.01
    const val PINCA_FUNDO = 0.05
    const val FOLGA_TOQUE = 18f        // pixels: o dedo só começa a arrastar depois de andar isto
    const val TOQUE_LONGO = 0.5        // s
    const val DUPLO_JANELA = 0.45      // s para a 2ª pinça virar toque duplo
    const val DUPLO_RAIO = 70f

    const val DEDO_ENTRA = 60.0
    const val DEDO_SAI = 95.0
    const val DEDO_BASE_ENTRA = 55.0
    const val DEDO_BASE_SAI = 78.0
    const val POLEGAR_DOBRADO = 1.0
    const val L_ANGULO = 60.0
    const val POLEGAR_DENTRO = 0.35    // polegar recolhido para dentro da palma (sai do zoom)

    const val VOLUME_PASSO = 0.045
    const val ZOOM_PASSO = 0.2         // |ln(abertura/abertura_anterior)| para 1 passo de zoom
    const val DESLIZE_DIST = 0.12      // deslize da mão aberta: anda isto (fração da imagem)...
    const val DESLIZE_TEMPO = 0.30     // ... em até este tempo
    const val DESLIZE_PAUSA = 0.75     // s sem outro deslize depois de um
    const val NOTIF_REPETE = 6.0       // s: 2º deslize para baixo = ajustes rápidos
    const val MENU_SEGURAR = 0.9       // s com a mão aberta parada = menu
    const val MENU_PARADO = 0.03
    const val MENU_ESCOLHA = 0.045     // o quanto mover a mão para escolher um item
    const val MENU_ESPERA = 1.1        // s em cima de um item = abre
    const val MENU_TEMPO = 8.0
    const val SEGURAR = 0.75           // s segurando os gestos com anel
    const val ESPERA = 0.45
}

val POSE_TEMPO = mapOf(
    "mouse" to 0.04, "punho" to 0.08, "dois" to 0.09, "mao" to 0.09, "quatro" to 0.12,
    "mindinho" to 0.12, "zoom" to 0.18
)

val NOMES_POSE = mapOf(
    "mouse" to "Cursor", "punho" to "Cursor parado", "dois" to "Rolando", "mao" to "Mão aberta",
    "quatro" to "Pausar / retomar", "mindinho" to "Volume", "zoom" to "Zoom"
)

/** Itens do menu (sentido horário, começando em cima): (comando, nome). */
val MENU_ITENS = listOf(
    "inicio" to "Tela inicial", "recentes" to "Apps recentes", "captura" to "Captura de tela",
    "ajustes_rapidos" to "Ajustes rápidos", "notificacoes" to "Notificações", "pausar" to "Pausar",
    "ajuda" to "Ajuda", "janela" to "Janela HandsOn"
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
    var pinI: Double,                // distância polegar–indicador (x palma)
    val pinBruto: Double,
    val polegarAberto: Boolean,
    val angPolInd: Double,
    val polegarRazao: Double,
    val polegarLat: Double,          // > 0.35: polegar recolhido para dentro da palma; < 0: para fora
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
    val polegarAberto = dobra(2, 3, 4) < 40 && d3(q[4], q[9]) / palma > 0.75
    val u = doubleArrayOf(q[4].x - q[2].x, q[4].y - q[2].y, q[4].z - q[2].z)
    val v = doubleArrayOf(q[8].x - q[5].x, q[8].y - q[5].y, q[8].z - q[5].z)
    val nu = sqrt(u.sumOf { it * it }) * sqrt(v.sumOf { it * it })
    val ang = if (nu < 1e-9) 0.0 else Math.toDegrees(acos(limitar((u[0] * v[0] + u[1] * v[1] + u[2] * v[2]) / nu, -1.0, 1.0)))
    val larguraPalma = d3(q[5], q[17]).let { if (it < 1e-6) 1e-6 else it }
    // posição do polegar ao longo da palma (do nó do indicador para o do mindinho)
    val lx = q[17].x - q[5].x; val ly = q[17].y - q[5].y; val lz = q[17].z - q[5].z
    val lat = ((q[4].x - q[5].x) * lx + (q[4].y - q[5].y) * ly + (q[4].z - q[5].z) * lz) / (larguraPalma * larguraPalma)
    val ax = intArrayOf(0, 5, 9, 13, 17).sumOf { img[it].x } / 5.0
    val ay = intArrayOf(0, 5, 9, 13, 17).sumOf { img[it].y } / 5.0
    val tam = hypot((img[0].x - img[9].x) * aspecto, img[0].y - img[9].y) / max(aspecto, 1.0)
    val pin = d3(q[4], q[8]) / palma
    return Analise(
        img, ax, ay, curvas, bases, fechados, abertos[0], pin, pin,
        polegarAberto, ang, d3(q[4], q[17]) / larguraPalma, lat, tam
    )
}

class OneEuro(var minCorte: Double, var beta: Double, private val dCorte: Double = 1.0) {
    private var xAnt: Double? = null
    private var dxAnt = 0.0
    private var tAnt = 0.0
    var velocidade = 0.0
        private set
    private fun alfa(corte: Double, dt: Double): Double {
        val tau = 1.0 / (2 * Math.PI * corte)
        return 1.0 / (1.0 + tau / dt)
    }
    fun reset() { xAnt = null; velocidade = 0.0; dxAnt = 0.0 }
    fun filtrar(x: Double, t: Double): Double {
        val ant = xAnt
        if (ant == null) { xAnt = x; tAnt = t; return x }
        val dt = max(t - tAnt, 1e-3)
        val dx = (x - ant) / dt
        val dxF = dxAnt + alfa(dCorte, dt) * (dx - dxAnt)
        val corte = minCorte + beta * abs(dxF)
        val xF = ant + alfa(corte, dt) * (x - ant)
        xAnt = xF; dxAnt = dxF; tAnt = t
        velocidade = dxF
        return xF
    }
}

/** Cursor firme e rápido: filtro adaptativo, zona morta que se ajusta ao tremor e uma leve antecipação. */
class Estabilizador {
    private val fx = OneEuro(Ajustes.suaveMin, Ajustes.SUAVE_VEL)
    private val fy = OneEuro(Ajustes.suaveMin, Ajustes.SUAVE_VEL)
    private var pos: DoubleArray? = null
    private var ruido = Ajustes.ZONA_MORTA / 1.2
    private var vel = 0.0
    private var tAnt = 0.0
    fun reset() { fx.reset(); fy.reset(); pos = null }
    /** x, y em pixels virtuais (1920 x 1080). firme = mais suave (durante a pinça). */
    fun filtrar(x: Double, y: Double, t: Double, firme: Boolean = false): DoubleArray {
        val k = if (firme) 0.45 else 1.0
        fx.minCorte = Ajustes.suaveMin * k; fy.minCorte = Ajustes.suaveMin * k
        val a = fx.filtrar(x, t); val b = fy.filtrar(y, t)
        val p = pos
        if (p == null) { pos = doubleArrayOf(a, b); tAnt = t; return pos!!.copyOf() }
        if (vel < 120) ruido += 0.04 * (hypot(x - a, y - b) - ruido)
        val zona = limitar(1.2 * ruido, Ajustes.ZONA_MORTA, 7.0) * (if (firme) 1.6 else 1.0)
        val dx = a - p[0]; val dy = b - p[1]; val d = hypot(dx, dy)
        val dt = max(t - tAnt, 1e-3); tAnt = t
        if (d > zona) {
            val f = (d - zona) / d
            p[0] += dx * f; p[1] += dy * f
            vel = d * f / dt
        } else vel = 0.0
        // antecipação: só quando a mão está andando rápido (não treme parada)
        val lead = if (vel > 250 && !firme) Ajustes.ANTECIPA else 0.0
        return doubleArrayOf(p[0] + fx.velocidade * lead, p[1] + fy.velocidade * lead)
    }
}

/** Posição na área (0..1) com mais alcance perto das bordas; o meio continua preciso. */
fun borda(u: Double): Double {
    val d = u - 0.5
    return limitar(0.5 + d * (1 + Ajustes.ganhoBorda * (2 * abs(d)) * (2 * abs(d))), 0.0, 1.0)
}

/** Estado para o desenho na tela (lido pela camada de desenho, pela janela e pelo tutorial). */
class Painel {
    @Volatile var cursorX = -1f
    @Volatile var cursorY = -1f
    @Volatile var maoVista = false
    @Volatile var pose: String? = null
    @Volatile var apertado = false
    @Volatile var pinca = 0f                   // 0 = aberta, 1 = fechada
    @Volatile var anel = 0f                    // progresso de um gesto de segurar
    @Volatile var anelNome = ""
    @Volatile var aviso = ""
    @Volatile var avisoAte = 0.0
    @Volatile var pausado = false
    @Volatile var ajuda = false
    @Volatile var dica = ""
    @Volatile var pontos: List<P3> = emptyList()
    @Volatile var fps = 0f
    @Volatile var dedoX = -1f                  // dedo virtual encostado na tela
    @Volatile var dedoY = -1f
    @Volatile var dedoAtivo = false
    @Volatile var zoomAtivo = false
    @Volatile var zoomAte = 0.0
    @Volatile var zoomDir = 0
    @Volatile var deslize = ""                 // último deslize (para a seta animada)
    @Volatile var deslizeEm = -10.0
    @Volatile var menuAberto = false
    @Volatile var menuSel = -1
    @Volatile var menuProg = 0f
    @Volatile var volumeEm = -10.0
    @Volatile var volumeDir = 0
    @Volatile var agora = 0.0
    val feitos = ConcurrentHashMap<String, Int>()   // contagem de gestos (para o tutorial)
}

class Leitor(private val acoesReais: Acoes, var larguraTela: Int, var alturaTela: Int) {
    val painel = Painel()
    /** Modo treino (tutorial): reconhece e conta os gestos, mas não mexe no tablet. */
    @Volatile var treino = false
    private val treinoAcoes = object : Acoes {
        override fun dedoDescer(x: Float, y: Float) {}
        override fun dedoMover(x: Float, y: Float) {}
        override fun dedoSubir() {}
        override fun toque(x: Float, y: Float) {}
        override fun zoom(x: Float, y: Float, fator: Float) {}
        override fun volume(degraus: Int) {}
        override fun global(acao: String) {}
        override fun comando(nome: String) {}
        override fun som(nome: String) = acoesReais.som(nome)
    }
    private val acoes: Acoes get() = if (treino) treinoAcoes else acoesReais

    var pose: String? = null
        private set
    private var candidata: String? = null
    private var candidataDesde = 0.0
    private val dedosEst = BooleanArray(4)
    private val pinFiltro = OneEuro(Ajustes.PINCA_FILTRO, Ajustes.PINCA_REACAO)
    private val estab = Estabilizador()
    private var cursor = floatArrayOf(-1f, -1f)
    private val historico = ArrayDeque<Pair<Double, FloatArray>>()
    private var ultimoT = 0.0
    private var ultimaVista = -10.0
    var pausado = false
        private set

    // pinça / dedo virtual
    private var apertado = false
    private var armado = true
    private var quadrosFechada = 0
    private var pinMin: Double? = null
    private var tApertou = 0.0
    private var pontoApertou = floatArrayOf(0f, 0f)
    private var dedoNaTela = false
    private var arrastou = false
    private var duploNestaPinca = false
    private var ultimoToqueT = -10.0
    private var ultimoToque = floatArrayOf(0f, 0f)

    // rolagem (dedo virtual)
    private val rolX = OneEuro(3.0, 0.5)
    private val rolY = OneEuro(3.0, 0.5)
    private var rolRef: DoubleArray? = null
    private var rolInicio = floatArrayOf(0f, 0f)
    private var rolando = false

    // volume / zoom
    private var volY: Double? = null
    private var zoomRef: Double? = null
    private var zoomUltimo = 0.0
    private var zoomCentro = floatArrayOf(0f, 0f)

    // mão aberta: deslizes e menu
    private val rastro = ArrayDeque<DoubleArray>()      // (t, x, y)
    private var deslizeAte = 0.0
    private var notifEm = -100.0
    private var paradoDesde: Double? = null
    private var paradoRef: DoubleArray? = null
    private var menuAberto = false
    private var menuRef = doubleArrayOf(0.0, 0.0)
    private var menuDesde = 0.0
    private var menuSel = -1
    private var menuSelDesde = 0.0
    private var punhoDesde: Double? = null

    // gestos de segurar (quatro dedos)
    private var acaoInicio = 0.0
    private var acaoFeita = false
    private var acaoAtiva = false
    private var esperaAte = 0.0

    private fun avisar(texto: String, t: Double, dur: Double = 0.9) {
        painel.aviso = texto; painel.avisoAte = t + dur
    }

    private fun contar(nome: String) { painel.feitos[nome] = (painel.feitos[nome] ?: 0) + 1 }

    fun alternarPausa(t: Double) {
        pausado = !pausado
        painel.pausado = pausado
        soltarTudo()
        acoes.som(if (pausado) "pausa" else "volta")
        avisar(if (pausado) "Leitura pausada" else "Leitura retomada", t, 1.4)
        contar("pausar")
    }

    private fun soltarTudo() {
        if (dedoNaTela || rolando) acoes.dedoSubir()
        dedoNaTela = false; rolando = false
        apertado = false; quadrosFechada = 0; pinMin = null
        painel.apertado = false; painel.dedoAtivo = false
        fecharMenu()
    }

    // ── quadro sem mão ──
    fun semMao(t: Double) {
        painel.agora = t
        if (t - ultimaVista < 0.25) return       // a câmera perdeu a mão por um instante
        if (apertado) soltarPinca(t)
        if (rolando) pararRolagem()
        painel.maoVista = false
        painel.pontos = emptyList()
        painel.anel = 0f
        painel.dica = ""
        painel.zoomAtivo = false
        acaoAtiva = false
        pose = null; candidata = null
        volY = null; zoomRef = null; paradoDesde = null
        rastro.clear()
        if (menuAberto && t - menuDesde > 2.0) fecharMenu()
        estab.reset(); pinFiltro.reset()
    }

    // ── quadro com mão ──
    fun quadro(g: Analise, t: Double) {
        ultimaVista = t; ultimoT = t
        painel.agora = t
        painel.maoVista = true
        painel.pontos = g.pontos
        g.pinI = pinFiltro.filtrar(g.pinI, t)
        dedos(g)
        painel.pinca = limitar((0.9 - g.pinI) / (0.9 - Ajustes.PINCA_APERTA), 0.0, 1.0).toFloat()
        painel.dica = when {
            g.tamanho > 0.45 -> "Mão perto demais: afaste um pouco"
            g.tamanho < 0.09 -> "Mão longe: chegue mais perto da câmera"
            else -> ""
        }
        val antes = pose
        estabilizar(classificar(g), t)
        if (pose != antes) mudouPose(antes, t)
        painel.pose = pose
        painel.zoomAtivo = pose == "zoom"

        if (pausado) {
            if (pose == "quatro") segurarPausa(t) else { acaoAtiva = false; painel.anel = 0f }
            moverCursor(g, t, mover = false)
            return
        }
        if (menuAberto) { menu(g, t); return }
        if (pose != "quatro") { acaoAtiva = false }
        if (pose != "mao") { paradoDesde = null }
        if (pose == "punho") {
            val d = punhoDesde ?: t.also { punhoDesde = it }
            if (t - d > 0.3) painel.anel = 0f
        } else punhoDesde = null
        when (pose) {
            "mouse" -> { moverCursor(g, t, mover = true); pinca(g, t) }
            "punho" -> moverCursor(g, t, mover = false)
            "dois" -> { moverCursor(g, t, mover = false); rolar(g, t) }
            "mindinho" -> { moverCursor(g, t, mover = false); volume(g, t) }
            "zoom" -> { moverCursor(g, t, mover = false); zoom(g, t) }
            "mao" -> { moverCursor(g, t, mover = false); maoAberta(g, t) }
            "quatro" -> { moverCursor(g, t, mover = false); segurarPausa(t) }
        }
        if (pose != "mao" && pose != "quatro" && !menuAberto) painel.anel = 0f
    }

    private fun mudouPose(antes: String?, t: Double) {
        if (antes == "mouse" && apertado && pose != "mouse") soltarPinca(t)
        if (antes == "dois" && rolando) pararRolagem()
        if (antes == "zoom") { zoomRef = null; painel.zoomAtivo = false }
        when (pose) {
            "mindinho" -> volY = null
            "zoom" -> { zoomRef = null; contar("zoom_pose") }
            "mao" -> { rastro.clear(); paradoDesde = null }
            "dois" -> rolRef = null
            "punho" -> contar("punho")
        }
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
        val e = g.esticados
        if (apertado && g.pinI < limiteSoltar()) return "mouse"
        if (pose == "zoom") {
            // no zoom, juntar o polegar com o indicador continua sendo zoom (não vira toque)
            val outros = e[1] || e[2] || e[3]
            if (!outros && g.polegarLat < Ajustes.POLEGAR_DENTRO && (e[0] || g.pinI < 0.7)) return "zoom"
        }
        if (g.pinI < Ajustes.PINCA_APERTA && g.indAberto) return "mouse"
        if (!e[1] && !e[2]) {
            if (!e[0] && e[3]) return "mindinho"
            if (e[0] && !e[3] && g.polegarAberto && g.angPolInd > Ajustes.L_ANGULO && g.polegarLat < 0.05) return "zoom"
        }
        if (e.all { it }) {
            val lim = Ajustes.POLEGAR_DOBRADO * (if (pose == "quatro") 1.15 else 1.0)
            return if (g.polegarRazao < lim) "quatro" else "mao"
        }
        if (e[0] && e[1] && !e[3]) return "dois"
        if (g.fechados.all { it } && !g.indAberto) return "punho"
        return "mouse"
    }

    private fun estabilizar(nova: String, t: Double) {
        if (nova != candidata) { candidata = nova; candidataDesde = t }
        val imediato = nova == "mouse" && apertado
        if (nova != pose && (imediato || t - candidataDesde >= (POSE_TEMPO[nova] ?: 0.1))) pose = nova
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
        val v = estab.filtrar(ux * 1920, uy * 1080, t, firme = apertado)
        val x = (limitar(v[0], 0.0, 1920.0) / 1920 * (larguraTela - 1)).toFloat()
        val y = (limitar(v[1], 0.0, 1080.0) / 1080 * (alturaTela - 1)).toFloat()
        if (cursor[0] >= 0) painel.feitos["mover_px"] = (painel.feitos["mover_px"] ?: 0) +
            hypot((x - cursor[0]).toDouble(), (y - cursor[1]).toDouble()).toInt()
        cursor[0] = x; cursor[1] = y
        historico.addLast(t to floatArrayOf(x, y))
        while (historico.isNotEmpty() && t - historico.first().first > 0.3) historico.removeFirst()
        painel.cursorX = x; painel.cursorY = y
        if (painel.dica.isEmpty() && (bx < -0.05 || bx > 1.05 || by < -0.05 || by > 1.05))
            painel.dica = "Traga a mão um pouco mais para o centro"
    }

    /** Posição de ~80 ms atrás (o gesto de fechar os dedos mexe um pouquinho a mão). */
    private fun posicaoAntes(t: Double): FloatArray {
        for ((tt, p) in historico) if (t - tt <= 0.08) return p.copyOf()
        return cursor.copyOf()
    }

    // ── pinça: o dedo virtual encosta, segue a mão e solta ──
    private fun pinca(g: Analise, t: Double) {
        val pin = g.pinI
        if (!apertado) {
            if (pin > Ajustes.PINCA_SOLTA) armado = true
            val fechada = pin < Ajustes.PINCA_APERTA && g.indAberto
            quadrosFechada = if (fechada) quadrosFechada + 1 else 0
            val confirmada = quadrosFechada >= Ajustes.PINCA_CONFIRMA + 1 ||
                (quadrosFechada >= Ajustes.PINCA_CONFIRMA && pin < Ajustes.PINCA_APERTA - Ajustes.PINCA_FUNDO)
            if (armado && confirmada && t >= esperaAte) {
                apertado = true; armado = false
                painel.apertado = true
                tApertou = t; pinMin = pin
                pontoApertou = posicaoAntes(t)
                arrastou = false
                acoes.som("clique")
                duploNestaPinca = t - ultimoToqueT < Ajustes.DUPLO_JANELA &&
                    hypot((pontoApertou[0] - ultimoToque[0]).toDouble(), (pontoApertou[1] - ultimoToque[1]).toDouble()) < Ajustes.DUPLO_RAIO
                if (duploNestaPinca) {
                    acoes.toque(ultimoToque[0], ultimoToque[1])     // o segundo toque, na hora
                    avisar("Toque duplo", t)
                    contar("duplo")
                    ultimoToqueT = -10.0
                    cursor[0] = ultimoToque[0]; cursor[1] = ultimoToque[1]
                } else {
                    acoes.dedoDescer(pontoApertou[0], pontoApertou[1])
                    dedoNaTela = true
                    painel.dedoAtivo = true; painel.dedoX = pontoApertou[0]; painel.dedoY = pontoApertou[1]
                }
            }
        } else {
            pinMin = min(pinMin ?: pin, pin)
            if (dedoNaTela) {
                val d = hypot((cursor[0] - pontoApertou[0]).toDouble(), (cursor[1] - pontoApertou[1]).toDouble())
                if (!arrastou && d > Ajustes.FOLGA_TOQUE) arrastou = true
                if (arrastou) {
                    acoes.dedoMover(cursor[0], cursor[1])
                    painel.dedoX = cursor[0]; painel.dedoY = cursor[1]
                } else if (t - tApertou > Ajustes.TOQUE_LONGO && painel.aviso != "Toque longo") {
                    avisar("Toque longo", t); contar("longo")
                }
            }
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
        if (duploNestaPinca) { duploNestaPinca = false; return }
        if (dedoNaTela) {
            acoes.dedoSubir()
            dedoNaTela = false
            painel.dedoAtivo = false
        }
        val dur = t - tApertou
        when {
            arrastou -> { avisar("Arrastar", t, 0.6); contar("arrastar") }
            dur < Ajustes.TOQUE_LONGO -> {
                ultimoToqueT = t; ultimoToque = pontoApertou.copyOf()
                avisar("Toque", t, 0.5); contar("toque")
            }
        }
    }

    // ── rolagem: dois dedos; o dedo virtual arrasta a página junto com a mão ──
    private fun rolar(g: Analise, t: Double) {
        val x = rolX.filtrar(g.ancoraX, t); val y = rolY.filtrar(g.ancoraY, t)
        val ref = rolRef
        if (ref == null) {
            rolX.reset(); rolY.reset(); rolX.filtrar(g.ancoraX, t); rolY.filtrar(g.ancoraY, t)
            rolRef = doubleArrayOf(g.ancoraX, g.ancoraY)
            val cx = (if (cursor[0] >= 0) cursor[0] else larguraTela / 2f).coerceIn(larguraTela * 0.2f, larguraTela * 0.8f)
            val cy = (if (cursor[0] >= 0) cursor[1] else alturaTela / 2f).coerceIn(alturaTela * 0.25f, alturaTela * 0.75f)
            rolInicio = floatArrayOf(cx, cy)
            return
        }
        val dx = ((x - ref[0]) * Ajustes.rolagemGanho * larguraTela).toFloat()
        val dy = ((y - ref[1]) * Ajustes.rolagemGanho * alturaTela).toFloat()
        if (!rolando) {
            if (hypot(dx.toDouble(), dy.toDouble()) < 10) return
            acoes.dedoDescer(rolInicio[0], rolInicio[1])
            rolando = true
            painel.dedoAtivo = true
        }
        var fx = rolInicio[0] + dx; var fy = rolInicio[1] + dy
        val fora = fx < larguraTela * 0.03f || fx > larguraTela * 0.97f || fy < alturaTela * 0.04f || fy > alturaTela * 0.96f
        fx = fx.coerceIn(2f, larguraTela - 3f); fy = fy.coerceIn(2f, alturaTela - 3f)
        acoes.dedoMover(fx, fy)
        painel.dedoX = fx; painel.dedoY = fy
        if (fora) {                     // chegou na borda: levanta o dedo e "pega" a página de novo no meio
            pararRolagem()
            contar("rolar")
            rolRef = null
        }
    }

    private fun pararRolagem() {
        if (rolando) {
            acoes.dedoSubir()
            val d = hypot((painel.dedoX - rolInicio[0]).toDouble(), (painel.dedoY - rolInicio[1]).toDouble())
            if (d > 60) contar("rolar")
        }
        rolando = false
        painel.dedoAtivo = false
    }

    // ── volume: só o mindinho, subindo ou descendo a mão ──
    private fun volume(g: Analise, t: Double) {
        val y0 = volY ?: run { volY = g.ancoraY; return }
        val degraus = ((y0 - g.ancoraY) / Ajustes.VOLUME_PASSO).toInt()
        if (degraus != 0) {
            acoes.volume(degraus)
            acoes.som("tique")
            volY = y0 - degraus * Ajustes.VOLUME_PASSO
            painel.volumeEm = t; painel.volumeDir = if (degraus > 0) 1 else -1
            contar("volume")
        }
    }

    // ── zoom: L com polegar e indicador; abrir = aproxima, fechar = afasta ──
    private fun zoom(g: Analise, t: Double) {
        if (candidata != "zoom") return          // a mão já está mudando de gesto: não dá zoom no caminho
        val gap = max(g.pinBruto, 0.08)
        val ref = zoomRef
        if (ref == null) {
            zoomRef = gap
            zoomCentro = if (cursor[0] >= 0) cursor.copyOf() else floatArrayOf(larguraTela / 2f, alturaTela / 2f)
            avisar("Zoom: abra ou feche os dedos", t, 1.2)
            return
        }
        val passo = ln(gap / ref)
        if (abs(passo) >= Ajustes.ZOOM_PASSO && t - zoomUltimo > 0.2) {
            val fator = (gap / ref).coerceIn(0.5, 2.0).toFloat()
            acoes.zoom(zoomCentro[0], zoomCentro[1], fator)
            acoes.som("tique")
            zoomRef = gap; zoomUltimo = t
            painel.zoomDir = if (fator > 1) 1 else -1; painel.zoomAte = t + 0.6
            contar("zoom")
        }
    }

    // ── mão aberta: deslizes rápidos e menu (parada) ──
    private fun maoAberta(g: Analise, t: Double) {
        rastro.addLast(doubleArrayOf(t, g.ancoraX, g.ancoraY))
        while (rastro.isNotEmpty() && t - rastro.first()[0] > Ajustes.DESLIZE_TEMPO) rastro.removeFirst()
        if (t >= deslizeAte && rastro.size >= 3) {
            val o = rastro.first()
            val dx = g.ancoraX - o[1]; val dy = g.ancoraY - o[2]
            val ax = abs(dx); val ay = abs(dy)
            val dir = when {
                ax > Ajustes.DESLIZE_DIST && ax > 1.4 * ay -> if (dx > 0) "direita" else "esquerda"
                ay > Ajustes.DESLIZE_DIST * 0.9 && ay > 1.4 * ax -> if (dy > 0) "baixo" else "cima"
                else -> null
            }
            if (dir != null) {
                deslizar(dir, t)
                deslizeAte = t + Ajustes.DESLIZE_PAUSA
                rastro.clear(); paradoDesde = null; painel.anel = 0f
                return
            }
        }
        // parada 1 s = menu
        val ref = paradoRef
        if (paradoDesde == null || ref == null || hypot(g.ancoraX - ref[0], g.ancoraY - ref[1]) > Ajustes.MENU_PARADO) {
            paradoDesde = t; paradoRef = doubleArrayOf(g.ancoraX, g.ancoraY); painel.anel = 0f
            return
        }
        if (t < deslizeAte) { paradoDesde = t; return }
        val prog = (t - paradoDesde!!) / Ajustes.MENU_SEGURAR
        if (prog > 0.25) {
            painel.anel = ((prog - 0.25) / 0.75).toFloat().coerceIn(0f, 1f)
            painel.anelNome = "Menu"
        }
        if (prog >= 1) abrirMenu(g, t)
    }

    private fun deslizar(dir: String, t: Double) {
        val acao = when (dir) {
            "esquerda", "direita" -> "voltar"
            "cima" -> if (t - notifEm < 15.0) "fechar_painel" else "inicio"
            else -> if (t - notifEm < Ajustes.NOTIF_REPETE) "ajustes_rapidos" else "notificacoes"
        }
        notifEm = if (acao == "notificacoes" || acao == "ajustes_rapidos") t else -100.0
        acoes.global(acao)
        acoes.som("deslize")
        painel.deslize = dir; painel.deslizeEm = t
        avisar(NOME_ACAO[acao] ?: acao, t)
        contar(acao)
    }

    // ── menu radial ──
    private fun abrirMenu(g: Analise, t: Double) {
        menuAberto = true; menuDesde = t
        menuRef = doubleArrayOf(g.ancoraX, g.ancoraY)
        menuSel = -1; menuSelDesde = t
        painel.menuAberto = true; painel.menuSel = -1; painel.menuProg = 0f
        painel.anel = 0f
        paradoDesde = null
        acoes.som("menu")
        contar("menu")
    }

    private fun fecharMenu() {
        menuAberto = false
        painel.menuAberto = false; painel.menuSel = -1; painel.menuProg = 0f
    }

    private fun menu(g: Analise, t: Double) {
        moverCursor(g, t, mover = false)
        if (t - menuDesde > Ajustes.MENU_TEMPO || pose == "punho") {
            fecharMenu(); acoes.som("fechar"); esperaAte = t + 0.4; return
        }
        val dx = g.ancoraX - menuRef[0]; val dy = g.ancoraY - menuRef[1]
        val sel = if (hypot(dx, dy) < Ajustes.MENU_ESCOLHA) -1 else {
            val ang = Math.toDegrees(atan2(dy, dx))            // 0 = direita, 90 = baixo
            (((ang + 90) / 45.0).roundToInt() % 8 + 8) % 8
        }
        if (sel != menuSel) { menuSel = sel; menuSelDesde = t; if (sel >= 0) acoes.som("tique") }
        painel.menuSel = sel
        val prog = if (sel >= 0) ((t - menuSelDesde) / Ajustes.MENU_ESPERA) else 0.0
        painel.menuProg = prog.toFloat().coerceIn(0f, 1f)
        val pincou = g.pinI < Ajustes.PINCA_APERTA && g.indAberto && t - menuDesde > 0.3
        if (sel >= 0 && (prog >= 1.0 || pincou)) {
            val (cmd, nome) = MENU_ITENS[sel]
            fecharMenu()
            esperaAte = t + 0.5; armado = false
            when (cmd) {
                "pausar" -> alternarPausa(t)
                "ajuda", "janela" -> acoes.comando(cmd)
                else -> acoes.global(cmd)
            }
            acoes.som("confirma")
            avisar(nome, t)
            contar("menu_" + cmd)
        }
    }

    // ── quatro dedos segurando: pausar / retomar ──
    private fun segurarPausa(t: Double) {
        if (!acaoAtiva) { acaoAtiva = true; acaoInicio = t; acaoFeita = false }
        if (acaoFeita) { painel.anel = 0f; return }
        if (t < esperaAte) { acaoInicio = t; painel.anel = 0f; return }
        val prog = (t - acaoInicio) / Ajustes.SEGURAR
        painel.anel = prog.toFloat().coerceIn(0f, 1f)
        painel.anelNome = if (pausado) "Retomar" else "Pausar"
        if (prog >= 1) {
            acaoFeita = true
            esperaAte = t + Ajustes.ESPERA
            painel.anel = 0f
            alternarPausa(t)
        }
    }

    companion object {
        val NOME_ACAO = mapOf(
            "voltar" to "Voltar", "inicio" to "Tela inicial", "notificacoes" to "Notificações",
            "ajustes_rapidos" to "Ajustes rápidos", "fechar_painel" to "Fechar painel",
            "recentes" to "Apps recentes", "captura" to "Captura de tela"
        )
    }
}
