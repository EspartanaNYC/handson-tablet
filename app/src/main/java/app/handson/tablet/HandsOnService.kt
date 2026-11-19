package app.handson.tablet

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
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
import kotlin.math.max
import kotlin.math.min

/**
 * O coração do HandsOn no tablet:
 *  - liga a câmera frontal e lê a mão com o MediaPipe (tudo no próprio tablet);
 *  - desenha o cursor e os avisos por cima de qualquer app;
 *  - transforma os gestos em toques, rolagens e botões do sistema (serviço de acessibilidade).
 */
class HandsOnService : AccessibilityService(), LifecycleOwner, Acoes,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val LIGAR = "app.handson.tablet.LIGAR"
        const val DESLIGAR = "app.handson.tablet.DESLIGAR"
        private const val CANAL = "handson"
        private const val TAG = "HandsOn"

        @Volatile var instancia: HandsOnService? = null
            private set
        @Volatile var ligado = false
            private set
        @Volatile var erro: String? = null
            private set
    }

    private val registro = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registro

    private val principal = Handler(Looper.getMainLooper())
    private lateinit var janelas: WindowManager
    private lateinit var prefs: SharedPreferences
    private var camada: OverlayView? = null
    private var leitor: Leitor? = null
    private var detector: HandLandmarker? = null
    private var analise: ImageAnalysis? = null
    private var executor: ExecutorService? = null
    private var tom: ToneGenerator? = null
    private var ultimoToqueMs = 0L
    private var quadros = 0
    private var fpsDesde = 0L

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
        camada?.miniCamera = p.getBoolean(Config.MINI_CAMERA, true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        atualizarTela()
    }

    // ───────────────────────── ligar / desligar ─────────────────────────

    fun ligar() {
        if (ligado) return
        erro = null
        if (!::janelas.isInitialized) {
            erro = "Ative o HandsOn em Acessibilidade primeiro"
            stopSelf()
            return
        }
        try {
            iniciarPrimeiroPlano()
        } catch (e: Exception) {
            erro = "Não consegui ligar a câmera em segundo plano: ${e.message}"
            Log.e(TAG, "startForeground", e)
            return
        }
        ligado = true
        val tela = tamanhoTela()
        leitor = Leitor(this, tela.x, tela.y)
        criarCamada()
        tom = try { ToneGenerator(AudioManager.STREAM_SYSTEM, 35) } catch (_: Exception) { null }
        executor = Executors.newSingleThreadExecutor()
        executor?.execute { criarDetector() }
        registro.currentState = Lifecycle.State.STARTED
        ligarCamera()
    }

    fun desligar() {
        if (!ligado) return
        ligado = false
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        if (registro.currentState.isAtLeast(Lifecycle.State.STARTED)) registro.currentState = Lifecycle.State.CREATED
        analise?.clearAnalyzer(); analise = null
        val ex = executor; executor = null
        val d = detector; detector = null
        ex?.execute { try { d?.close() } catch (_: Exception) {} }
        ex?.shutdown()
        camada?.let { try { janelas.removeView(it) } catch (_: Exception) {} }
        camada = null
        leitor = null
        tom?.release(); tom = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun iniciarPrimeiroPlano() {
        val gerente = getSystemService(NotificationManager::class.java)
        gerente.createNotificationChannel(
            NotificationChannel(CANAL, "HandsOn ligado", NotificationManager.IMPORTANCE_LOW)
        )
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val parar = PendingIntent.getService(
            this, 1, Intent(this, HandsOnService::class.java).setAction(DESLIGAR), PendingIntent.FLAG_IMMUTABLE
        )
        val aviso = Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_frente)
            .setContentTitle("HandsOn está lendo seus gestos")
            .setContentText("Quatro dedos = pausar · toque aqui para abrir")
            .setContentIntent(abrir)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Desligar", parar).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(7, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(7, aviso)
        }
    }

    // ───────────────────────── camada de desenho ─────────────────────────

    private fun criarCamada() {
        val v = OverlayView(this)
        v.miniCamera = prefs.getBoolean(Config.MINI_CAMERA, true)
        v.painel = leitor!!.painel
        if (!prefs.getBoolean(Config.GUIA_VISTO, false)) {
            leitor!!.painel.ajuda = true
            prefs.edit().putBoolean(Config.GUIA_VISTO, true).apply()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        janelas.addView(v, lp)
        camada = v
    }

    // tela inteira, em pixels (é nesse espaço que os toques são feitos)
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
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.VIDEO)
            .build()
        val novo = try {
            HandLandmarker.createFromOptions(this, opcoes(Delegate.GPU))
        } catch (e: Exception) {
            Log.w(TAG, "GPU indisponível, usando CPU", e)
            try { HandLandmarker.createFromOptions(this, opcoes(Delegate.CPU)) } catch (e2: Exception) {
                erro = "Não consegui carregar o modelo da mão: ${e2.message}"
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
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
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
            // gira para ficar "em pé" e espelha (a câmera frontal funciona como um espelho)
            val m = Matrix().apply {
                postRotate(img.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }
            val quadro = Bitmap.createBitmap(bruto, 0, 0, bruto.width, bruto.height, m, true)
            val agoraMs = SystemClock.uptimeMillis()
            val r = det.detectForVideo(BitmapImageBuilder(quadro).build(), agoraMs)
            val t = agoraMs / 1000.0
            if (r.landmarks().isEmpty() || r.worldLandmarks().isEmpty()) {
                l.semMao(t)
            } else {
                val im = r.landmarks()[0].map { P3(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }
                val mu = r.worldLandmarks()[0].map { P3(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }
                l.quadro(analisar(im, mu, quadro.width.toDouble() / quadro.height), t)
            }
            quadros++
            if (agoraMs - fpsDesde > 1000) {
                l.painel.fps = quadros * 1000f / (agoraMs - fpsDesde)
                quadros = 0; fpsDesde = agoraMs
            }
            camada?.postInvalidateOnAnimation()
        } catch (e: Exception) {
            Log.w(TAG, "quadro", e)
        } finally {
            img.close()
        }
    }

    // ───────────────────────── ações (Acoes) ─────────────────────────

    private fun gesto(caminho: Path, inicio: Long, duracao: Long, depois: (() -> Unit)? = null) {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(caminho, inicio, max(1L, duracao)))
            .build()
        dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { depois?.invoke() }
        }, principal)
    }

    private fun ponto(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    override fun toque(x: Float, y: Float) {
        principal.post {
            ultimoToqueMs = SystemClock.uptimeMillis()
            gesto(ponto(x, y), 0, 40)
        }
    }

    override fun toqueDuplo(x: Float, y: Float) {
        principal.post {
            // se o 1º toque foi há pouco, um toque a mais já forma o toque duplo para o Android;
            // se não, manda um toque duplo completo.
            val desde = SystemClock.uptimeMillis() - ultimoToqueMs
            if (desde < 260) gesto(ponto(x, y), 0, 40)
            else gesto(ponto(x, y), 0, 40) { principal.postDelayed({ gesto(ponto(x, y), 0, 40) }, 70) }
            ultimoToqueMs = 0
        }
    }

    override fun toqueLongo(x: Float, y: Float, ms: Long) {
        principal.post {
            gesto(ponto(x, y), 0, ms)
        }
    }

    override fun arrastar(caminho: List<FloatArray>, ms: Long) {
        principal.post {
            val p = Path()
            caminho.forEachIndexed { i, q -> if (i == 0) p.moveTo(q[0], q[1]) else p.lineTo(q[0], q[1]) }
            gesto(p, 0, ms)
        }
    }

    override fun deslizar(x: Float, y: Float, dx: Float, dy: Float) {
        principal.post {
            val l = leitor ?: return@post
            val w = l.larguraTela.toFloat(); val h = l.alturaTela.toFloat()
            val ddx = dx.coerceIn(-w * 0.4f, w * 0.4f)
            val ddy = dy.coerceIn(-h * 0.4f, h * 0.4f)
            val x0 = (x - ddx / 2).coerceIn(w * 0.1f, w * 0.9f)
            val y0 = (y - ddy / 2).coerceIn(h * 0.15f, h * 0.85f)
            val p = Path().apply { moveTo(x0, y0); lineTo((x0 + ddx).coerceIn(1f, w - 1), (y0 + ddy).coerceIn(1f, h - 1)) }
            gesto(p, 0, 220)
        }
    }

    override fun zoom(x: Float, y: Float, degraus: Int) {
        principal.post {
            // "pinça de zoom" com dois dedos na tela, como faria com a mão
            val l = leitor ?: return@post
            val w = l.larguraTela.toFloat(); val h = l.alturaTela.toFloat()
            val cx = x.coerceIn(w * 0.2f, w * 0.8f); val cy = y.coerceIn(h * 0.2f, h * 0.8f)
            val perto = 40f; val longe = 40f + 140f * min(abs(degraus), 3)
            val (d0, d1) = if (degraus > 0) perto to longe else longe to perto
            val a = Path().apply { moveTo(cx - d0, cy); lineTo(cx - d1, cy) }
            val b = Path().apply { moveTo(cx + d0, cy); lineTo(cx + d1, cy) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(a, 0, 260))
                .addStroke(GestureDescription.StrokeDescription(b, 0, 260))
                .build()
            dispatchGesture(g, null, principal)
        }
    }

    override fun volume(degraus: Int) {
        principal.post {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val dir = if (degraus > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(min(abs(degraus), 5)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            }
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
                "captura" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else null
                "fechar_painel" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
                                   else GLOBAL_ACTION_BACK
                else -> null
            }
            if (g != null) {
                if (acao == "captura") {
                    // esconde o cursor para ele não sair na foto
                    camada?.esconderPor(900)
                    principal.postDelayed({ performGlobalAction(g) }, 150)
                } else performGlobalAction(g)
            }
        }
    }

    override fun som(nome: String) {
        if (!prefs.getBoolean(Config.SONS, true)) return
        val t = tom ?: return
        try {
            when (nome) {
                "clique" -> t.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                "tique" -> t.startTone(ToneGenerator.TONE_CDMA_PIP, 25)
                "confirma" -> t.startTone(ToneGenerator.TONE_PROP_ACK, 90)
                "pausa" -> t.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                "volta" -> t.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            }
        } catch (_: Exception) {}
    }
}

