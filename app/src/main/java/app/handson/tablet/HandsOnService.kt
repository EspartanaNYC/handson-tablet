package app.handson.tablet

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * O coração do HandsOn no tablet:
 *  - liga a câmera frontal e lê a mão com o MediaPipe (tudo no próprio tablet);
 *  - mostra a janela do HandsOn (com botões de tamanho, minimizar, pausar e fechar)
 *    e desenha o cursor e os avisos por cima de qualquer app;
 *  - um DEDO VIRTUAL toca, arrasta e rola a tela acompanhando a mão (serviço de acessibilidade);
 *  - atalhos de teclado em qualquer app: Ctrl + Alt + tecla.
 */
class HandsOnService : AccessibilityService(), LifecycleOwner, Acoes,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val LIGAR = "app.handson.tablet.LIGAR"
        const val DESLIGAR = "app.handson.tablet.DESLIGAR"
        const val ALTERNAR = "app.handson.tablet.ALTERNAR"
        const val JANELA = "app.handson.tablet.JANELA"
        const val TESTE_DEDO = "app.handson.tablet.TESTE_DEDO"
        private const val CANAL = "handson"
        private const val TAG = "HandsOn"

        @Volatile var instancia: HandsOnService? = null
            private set
        @Volatile var ligado = false
            private set
        @Volatile var erro: String? = null
            private set

        /** Estado ao vivo (para o tutorial do app). */
        val painelAtual: Painel? get() = instancia?.leitor?.painel

        /** Modo treino: o tutorial confere os gestos sem mexer no tablet. */
        fun treino(ativo: Boolean) { instancia?.leitor?.treino = ativo }

        fun pedir(ctx: Context, acao: String) {
            try { ctx.startService(Intent(ctx, HandsOnService::class.java).setAction(acao)) } catch (e: Exception) {
                Log.w(TAG, "pedir $acao", e)
            }
        }
    }

    private val registro = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registro

    private val principal = Handler(Looper.getMainLooper())
    private lateinit var janelas: WindowManager
    private lateinit var prefs: SharedPreferences
    private var camada: OverlayView? = null
    private var janela: JanelaHandsOn? = null
    private var leitor: Leitor? = null
    private var detector: HandLandmarker? = null
    private var analise: ImageAnalysis? = null
    private var executor: ExecutorService? = null
    private var tom: ToneGenerator? = null
    private var quadros = 0
    private var fpsDesde = 0L
    private var giro: Bitmap? = null
    private val matriz = Matrix()
    private val pinturaQuadro = Paint(Paint.FILTER_BITMAP_FLAG)
    private var ultimaMiniatura = 0L
    private val dedo = DedoVirtual()

    // ───────────────────────── ciclo de vida ─────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
        janelas = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Config.prefs(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        Config.aplicar(prefs)
        registro.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            LIGAR -> ligar()
            DESLIGAR -> desligar()
            ALTERNAR -> if (ligado) desligar() else ligar()
            JANELA -> janela?.alternarVisivel()
            TESTE_DEDO -> testeDedo()
        }
        return START_NOT_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        desligar()
        instancia = null
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        desligar()
        instancia = null
        if (registro.currentState.isAtLeast(Lifecycle.State.CREATED)) registro.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, chave: String?) {
        Config.aplicar(p)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        atualizarTela()
        janela?.manterNaTela()
    }

    // ───────────────────────── teclado: Ctrl + Alt + tecla em qualquer app ─────────────────────────

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!(event.isCtrlPressed && event.isAltPressed)) return false
        val letra = Atalhos.letra(event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) principal.post { atalho(letra) }
        return true
    }

    /** Executa um atalho (também chamado pelo app quando ele está aberto). */
    fun atalho(letra: Char): Boolean {
        val agora = SystemClock.uptimeMillis() / 1000.0
        when (letra) {
            's' -> {
                val novo = !prefs.getBoolean(Config.SONS, true)
                prefs.edit().putBoolean(Config.SONS, novo).apply()
                leitor?.painel?.let { it.aviso = if (novo) "Som ligado" else "Mudo"; it.avisoAte = agora + 1.2 }
            }
            'p' -> noLeitor { it.alternarPausa(agora) }
            'h' -> comando("ajuda")
            'j' -> janela?.alternarTamanho()
            'm' -> janela?.alternarMinimizar()
            'l' -> if (ligado) desligar() else ligar()
            'q' -> desligar()
            'i' -> global("inicio")
            'n' -> global("notificacoes")
            'v' -> global("voltar")
            't' -> startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("aba", "tutorial"))
            else -> return false
        }
        camada?.postInvalidate()
        return true
    }

    /** Mexe no leitor na mesma linha de execução da câmera (sem atropelar um quadro). */
    private fun noLeitor(f: (Leitor) -> Unit) {
        val l = leitor ?: return
        val ex = executor
        if (ex != null && !ex.isShutdown) ex.execute { f(l) } else f(l)
    }

    // ───────────────────────── ligar / desligar ─────────────────────────

    fun ligar() {
        if (ligado) return
        erro = null
        if (!::janelas.isInitialized) {
            erro = "Ative o HandsOn em Acessibilidade primeiro"
            return
        }
        try {
            iniciarPrimeiroPlano()
        } catch (e: Exception) {
            erro = "Não consegui ligar a câmera: ${e.message}"
            Log.e(TAG, "startForeground", e)
            return
        }
        ligado = true
        val tela = tamanhoTela()
        val l = Leitor(this, tela.x, tela.y)
        leitor = l
        criarCamada(l)
        janela = JanelaHandsOn(this, janelas, prefs, l.painel,
            aoFechar = { desligar() },
            aoPausar = { noLeitor { it.alternarPausa(SystemClock.uptimeMillis() / 1000.0) } }).also { it.mostrar() }
        tom = try { ToneGenerator(AudioManager.STREAM_SYSTEM, 35) } catch (_: Exception) { null }
        executor = Executors.newSingleThreadExecutor()
        executor?.execute { criarDetector() }
        registro.currentState = Lifecycle.State.STARTED
        ligarCamera()
        som("inicio")
    }

    fun desligar() {
        if (!ligado) return
        ligado = false
        dedo.cancelar()
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        if (registro.currentState.isAtLeast(Lifecycle.State.STARTED)) registro.currentState = Lifecycle.State.CREATED
        analise?.clearAnalyzer(); analise = null
        val ex = executor; executor = null
        val d = detector; detector = null
        ex?.execute { try { d?.close() } catch (_: Throwable) {} }
        ex?.shutdown()
        camada?.let { try { janelas.removeView(it) } catch (_: Exception) {} }
        camada = null
        janela?.remover(); janela = null
        leitor = null
        tom?.release(); tom = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun iniciarPrimeiroPlano() {
        val gerente = getSystemService(NotificationManager::class.java)
        gerente.createNotificationChannel(NotificationChannel(CANAL, "HandsOn ligado", NotificationManager.IMPORTANCE_LOW))
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val parar = PendingIntent.getService(this, 1, Intent(this, HandsOnService::class.java).setAction(DESLIGAR), PendingIntent.FLAG_IMMUTABLE)
        val mostrar = PendingIntent.getService(this, 2, Intent(this, HandsOnService::class.java).setAction(JANELA), PendingIntent.FLAG_IMMUTABLE)
        val aviso = Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_frente)
            .setContentTitle("HandsOn está lendo seus gestos")
            .setContentText("Mão aberta parada = menu · quatro dedos = pausar")
            .setContentIntent(abrir)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Janela", mostrar).build())
            .addAction(Notification.Action.Builder(null, "Desligar", parar).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(7, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(7, aviso)
    }

    // ───────────────────────── camada de desenho ─────────────────────────

    private fun criarCamada(l: Leitor) {
        val v = OverlayView(this)
        v.painel = l.painel
        if (!prefs.getBoolean(Config.GUIA_VISTO, false)) {
            l.painel.ajuda = true
            prefs.edit().putBoolean(Config.GUIA_VISTO, true).apply()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        janelas.addView(v, lp)
        camada = v
    }

    @Suppress("DEPRECATION")
    private fun tamanhoTela(): Point = Point().also { janelas.defaultDisplay.getRealSize(it) }

    @Suppress("DEPRECATION")
    private fun rotacaoTela(): Int = janelas.defaultDisplay.rotation

    private fun atualizarTela() {
        val t = tamanhoTela()
        leitor?.larguraTela = t.x
        leitor?.alturaTela = t.y
        analise?.targetRotation = rotacaoTela()
    }

    // ───────────────────────── câmera + MediaPipe ─────────────────────────

    private fun criarDetector() {
        fun opcoes(d: Delegate) = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(d).build())
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.4f)       // segue a mão sem ficar "procurando" de novo
            .setRunningMode(RunningMode.VIDEO)
            .build()
        val novo = try {
            HandLandmarker.createFromOptions(this, opcoes(Delegate.GPU))
        } catch (e: Throwable) {
            Log.w(TAG, "GPU indisponível, usando CPU", e)
            try { HandLandmarker.createFromOptions(this, opcoes(Delegate.CPU)) } catch (e2: Throwable) {
                erro = "Não consegui carregar o leitor da mão: ${e2.message}"
                Log.e(TAG, "modelo", e2)
                null
            }
        }
        if (ligado) detector = novo else novo?.close()
    }

    private fun ligarCamera() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            if (!ligado) return@addListener
            try {
                val provedor = futuro.get()
                val a = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(480, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(rotacaoTela())
                    .build()
                a.setAnalyzer(executor!!) { img -> analisarQuadro(img) }
                provedor.unbindAll()
                provedor.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, a)
                analise = a
            } catch (e: Exception) {
                erro = "Não consegui abrir a câmera frontal: ${e.message}"
                Log.e(TAG, "camera", e)
                desligar()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analisarQuadro(img: ImageProxy) {
        try {
            val det = detector ?: return
            val l = leitor ?: return
            val bruto = img.toBitmap()
            val rot = img.imageInfo.rotationDegrees
            val w = if (rot % 180 == 0) bruto.width else bruto.height
            val h = if (rot % 180 == 0) bruto.height else bruto.width
            var g = giro
            if (g == null || g.width != w || g.height != h) { g = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); giro = g }
            // gira para ficar "em pé" e espelha (a câmera frontal funciona como um espelho), sem criar imagem nova
            matriz.reset()
            matriz.postTranslate(-bruto.width / 2f, -bruto.height / 2f)
            matriz.postRotate(rot.toFloat())
            matriz.postScale(-1f, 1f)
            matriz.postTranslate(w / 2f, h / 2f)
            Canvas(g!!).drawBitmap(bruto, matriz, pinturaQuadro)
            val agoraMs = SystemClock.uptimeMillis()
            val r = det.detectForVideo(BitmapImageBuilder(g).build(), agoraMs)
            val t = agoraMs / 1000.0
            if (r.landmarks().isEmpty() || r.worldLandmarks().isEmpty()) {
                l.semMao(t)
            } else {
                val im = r.landmarks()[0].map { P3(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }
                val mu = r.worldLandmarks()[0].map { P3(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }
                l.quadro(analisar(im, mu, w.toDouble() / h), t)
            }
            quadros++
            if (agoraMs - fpsDesde > 1000) {
                l.painel.fps = quadros * 1000f / (agoraMs - fpsDesde)
                quadros = 0; fpsDesde = agoraMs
            }
            val jan = janela
            if (jan != null && jan.querQuadro() && agoraMs - ultimaMiniatura > 60) {
                ultimaMiniatura = agoraMs
                jan.novoQuadro(Bitmap.createScaledBitmap(g, 192, (192f * h / w).toInt().coerceAtLeast(1), true))
            }
            camada?.postInvalidateOnAnimation()
            jan?.atualizar()
        } catch (e: Throwable) {
            Log.w(TAG, "quadro", e)
        } finally {
            img.close()
        }
    }

    // ───────────────────────── dedo virtual: encosta, desliza e solta seguindo a mão ─────────────────────────

    private inner class DedoVirtual {
        private var traco: GestureDescription.StrokeDescription? = null
        private var ultimo = PointF()
        private var alvo: PointF? = null
        private var soltar = false
        private var enviando = false
        private var ativo = false
        private var inicioMs = 0L
        private val caminho = ArrayList<PointF>()       // para o plano B (se o sistema não aceitar o modo contínuo)
        var semContinuo = false

        fun descer(x: Float, y: Float) {
            if (ativo) subir()
            ativo = true; soltar = false; alvo = null
            ultimo = PointF(x, y); inicioMs = SystemClock.uptimeMillis()
            caminho.clear(); caminho.add(PointF(x, y))
            if (semContinuo) return
            val p = Path().apply { moveTo(x, y) }
            val s = GestureDescription.StrokeDescription(p, 0, 25, true)
            traco = s
            enviar(s)
        }

        fun mover(x: Float, y: Float) {
            if (!ativo) return
            alvo = PointF(x, y)
            caminho.add(PointF(x, y))
            if (!enviando && !semContinuo) proximo()
        }

        fun subir() {
            if (!ativo) return
            soltar = true
            if (semContinuo) { planoB(); ativo = false; return }
            if (!enviando) proximo()
        }

        fun cancelar() { ativo = false; soltar = false; traco = null; alvo = null }

        private fun enviar(s: GestureDescription.StrokeDescription) {
            enviando = true
            val ok = dispatchGesture(GestureDescription.Builder().addStroke(s).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { enviando = false; proximo() }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        enviando = false
                        if (ativo && !semContinuo) {
                            Log.w(TAG, "toque contínuo cancelado: usando o modo simples")
                            semContinuo = true
                            if (soltar) { planoB(); ativo = false }
                        }
                    }
                }, principal)
            if (!ok) { enviando = false; semContinuo = true }
        }

        private fun proximo() {
            val s = traco ?: return
            if (!ativo) return
            val destino = alvo ?: ultimo
            alvo = null
            val p = Path().apply { moveTo(ultimo.x, ultimo.y); lineTo(destino.x + 0.01f, destino.y) }
            val fim = soltar
            val novo = try { s.continueStroke(p, 0, if (fim) 20 else 30, !fim) } catch (e: Exception) {
                Log.w(TAG, "continueStroke", e); semContinuo = true; if (fim) { planoB(); ativo = false }; return
            }
            ultimo = PointF(destino.x + 0.01f, destino.y)
            if (fim) { ativo = false; traco = null } else traco = novo
            enviar(novo)
        }

        /** Plano B: repete o movimento inteiro de uma vez ao soltar (toque, toque longo ou arrasto). */
        private fun planoB() {
            if (caminho.isEmpty()) return
            val p = Path()
            caminho.forEachIndexed { i, q -> if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
            val dur = (SystemClock.uptimeMillis() - inicioMs).coerceIn(40L, 3000L)
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build(), null, principal)
            caminho.clear()
        }
    }

    /** Teste automático: o dedo virtual arrasta a tela para cima (usado no teste do tablet virtual). */
    private fun testeDedo() {
        val t = tamanhoTela()
        val x = t.x / 2f; val y0 = t.y * 0.75f
        dedo.descer(x, y0)
        for (i in 1..20) principal.postDelayed({ dedo.mover(x, y0 - i * t.y * 0.025f) }, i * 35L)
        principal.postDelayed({ dedo.subir() }, 800)
    }

    // ───────────────────────── ações (Acoes) ─────────────────────────

    override fun dedoDescer(x: Float, y: Float) { principal.post { dedo.descer(x, y) } }
    override fun dedoMover(x: Float, y: Float) { principal.post { dedo.mover(x, y) } }
    override fun dedoSubir() { principal.post { dedo.subir() } }

    override fun toque(x: Float, y: Float) {
        principal.post {
            val p = Path().apply { moveTo(x, y) }
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 35)).build(), null, principal)
        }
    }

    override fun zoom(x: Float, y: Float, fator: Float) {
        principal.post {
            // "pinça de zoom" com dois dedos na tela, como faria com a mão
            val l = leitor ?: return@post
            val w = l.larguraTela.toFloat(); val h = l.alturaTela.toFloat()
            val cx = x.coerceIn(w * 0.25f, w * 0.75f); val cy = y.coerceIn(h * 0.25f, h * 0.75f)
            val d0 = 160f
            val d1 = (d0 * fator).coerceIn(50f, min(w, h) * 0.45f)
            val a = Path().apply { moveTo(cx - d0, cy); lineTo(cx - d1, cy) }
            val b = Path().apply { moveTo(cx + d0, cy); lineTo(cx + d1, cy) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(a, 0, 180))
                .addStroke(GestureDescription.StrokeDescription(b, 0, 180))
                .build()
            dispatchGesture(g, null, principal)
        }
    }

    override fun volume(degraus: Int) {
        principal.post {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val dir = if (degraus > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(min(abs(degraus), 5)) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
        }
    }

    override fun global(acao: String) {
        principal.post {
            val g = when (acao) {
                "voltar" -> GLOBAL_ACTION_BACK
                "inicio" -> GLOBAL_ACTION_HOME
                "recentes" -> GLOBAL_ACTION_RECENTS
                "notificacoes" -> GLOBAL_ACTION_NOTIFICATIONS
                "ajustes_rapidos" -> GLOBAL_ACTION_QUICK_SETTINGS
                "captura" -> GLOBAL_ACTION_TAKE_SCREENSHOT
                "fechar_painel" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
                                   else GLOBAL_ACTION_BACK
                else -> null
            }
            if (g != null) {
                if (acao == "captura") {
                    camada?.esconderPor(900); janela?.esconderPor(900)      // o cursor e a janela não saem na foto
                    principal.postDelayed({ performGlobalAction(g) }, 150)
                } else performGlobalAction(g)
            }
        }
    }

    override fun comando(nome: String) {
        principal.post {
            when (nome) {
                "ajuda" -> leitor?.painel?.let { it.ajuda = !it.ajuda }
                "janela" -> janela?.alternarVisivel()
            }
            camada?.postInvalidate()
        }
    }

    override fun som(nome: String) {
        if (!prefs.getBoolean(Config.SONS, true)) return
        val t = tom ?: return
        try {
            when (nome) {
                "clique" -> t.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                "tique" -> t.startTone(ToneGenerator.TONE_CDMA_PIP, 20)
                "deslize" -> t.startTone(ToneGenerator.TONE_PROP_ACK, 60)
                "menu" -> t.startTone(ToneGenerator.TONE_PROP_BEEP2, 80)
                "confirma" -> t.startTone(ToneGenerator.TONE_PROP_ACK, 90)
                "fechar" -> t.startTone(ToneGenerator.TONE_PROP_NACK, 60)
                "pausa" -> t.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                "volta", "inicio" -> t.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            }
        } catch (_: Exception) {}
    }
}

/** Atalhos de teclado (iguais no app e em qualquer app com Ctrl + Alt). */
object Atalhos {
    val LISTA = listOf(
        Triple('S', "Mudo", "Liga/desliga os sons do HandsOn"),
        Triple('P', "Pausar", "Pausa ou retoma a leitura dos gestos"),
        Triple('H', "Ajuda", "Mostra/esconde os cartões de gestos na tela"),
        Triple('J', "Tamanho", "Muda o tamanho da janela do HandsOn"),
        Triple('M', "Minimizar", "Minimiza a janela (ou volta ao tamanho)"),
        Triple('L', "Ligar", "Liga ou desliga o HandsOn"),
        Triple('Q', "Desligar", "Desliga o HandsOn e fecha a janela"),
        Triple('V', "Voltar", "Volta uma tela"),
        Triple('I', "Início", "Vai para a tela inicial"),
        Triple('N', "Notificações", "Abre as notificações"),
        Triple('T', "Tutorial", "Abre o tutorial do HandsOn"),
    )

    fun letra(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_S -> 's'; KeyEvent.KEYCODE_P -> 'p'; KeyEvent.KEYCODE_H -> 'h'
        KeyEvent.KEYCODE_J -> 'j'; KeyEvent.KEYCODE_M -> 'm'; KeyEvent.KEYCODE_L -> 'l'
        KeyEvent.KEYCODE_Q -> 'q'; KeyEvent.KEYCODE_V -> 'v'; KeyEvent.KEYCODE_I -> 'i'
        KeyEvent.KEYCODE_N -> 'n'; KeyEvent.KEYCODE_T -> 't'
        else -> null
    }
}
