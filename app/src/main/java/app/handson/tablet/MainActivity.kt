package app.handson.tablet

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val Ouro = Color(0xFFE8B04A)
private val OuroClaro = Color(0xFFFFDE96)
private val Vermelho = Color(0xFFE0262B)
private val Fundo = Color(0xFF120707)
private val Cartao = Color(0xFF1F0B0B)
private val Verde = Color(0xFF5ADC78)

class MainActivity : ComponentActivity() {
    private val aba = mutableStateOf("inicio")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extras(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ouro, onPrimary = Color(0xFF2A1400),
                    secondary = Vermelho, background = Fundo, surface = Cartao,
                    onBackground = Color.White, onSurface = Color.White
                )
            ) { App(aba) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extras(intent)
    }

    private fun extras(i: Intent?) {
        i?.getStringExtra("aba")?.let { aba.value = it }
        when (i?.getStringExtra("teste")) {          // usados pelo teste automático
            "ligar" -> HandsOnService.pedir(this, HandsOnService.LIGAR)
            "dedo" -> HandsOnService.instancia?.testeDedo(5000)
        }
    }

    /** Atalhos de teclado com o app aberto: a tecla sozinha (em outros apps: Ctrl + Alt + tecla). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCtrlPressed == true || event?.isAltPressed == true) return super.onKeyDown(keyCode, event)
        val letra = Atalhos.letra(keyCode) ?: return super.onKeyDown(keyCode, event)
        when (letra) {
            't' -> aba.value = "tutorial"
            'l' -> HandsOnService.pedir(this, HandsOnService.ALTERNAR)
            's' -> {
                val s = HandsOnService.instancia
                if (s != null) s.atalho('s') else {
                    val p = Config.prefs(this); p.edit().putBoolean(Config.SONS, !p.getBoolean(Config.SONS, true)).apply()
                }
            }
            else -> HandsOnService.instancia?.atalho(letra)
        }
        return true
    }
}

fun acessibilidadeAtiva(c: Context): Boolean {
    val lista = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val comp = ComponentName(c, HandsOnService::class.java)
    return lista.split(':').any { it.equals(comp.flattenToString(), true) || it.equals(comp.flattenToShortString(), true) }
}

/** Estado geral (atualizado a cada meio segundo). */
class Estado {
    var camera by mutableStateOf(false)
    var acess by mutableStateOf(false)
    var ligado by mutableStateOf(false)
    var erro by mutableStateOf<String?>(null)
}

@Composable
fun App(aba: MutableState<String>) {
    val ctx = LocalContext.current
    val estado = remember { Estado() }
    LaunchedEffect(Unit) {
        while (true) {
            estado.camera = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            estado.acess = acessibilidadeAtiva(ctx) && HandsOnService.instancia != null
            estado.ligado = HandsOnService.ligado
            estado.erro = HandsOnService.erro
            delay(400)
        }
    }
    Row(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF3A0C0C), Fundo), radius = 1600f))) {
        BarraLateral(aba.value, estado.ligado) { aba.value = it }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (aba.value) {
                "tutorial" -> Tutorial(estado) { aba.value = it }
                "gestos" -> Gestos()
                "teclas" -> Teclas()
                "ajustes" -> AjustesTela()
                else -> Inicio(estado) { aba.value = it }
            }
        }
    }
}

// ───────────────────────── barra lateral ─────────────────────────

@Composable
fun BarraLateral(atual: String, ligado: Boolean, ir: (String) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(112.dp).background(Color(0xCC0C0405)).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reator(Modifier.size(64.dp), ligado)
        Text("HandsOn", color = Ouro, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(22.dp))
        for ((id, nome, simbolo) in listOf(
            Triple("inicio", "Início", "◉"), Triple("tutorial", "Tutorial", "▶"), Triple("gestos", "Gestos", "✋"),
            Triple("teclas", "Teclas", "⌨"), Triple("ajustes", "Ajustes", "⚙")
        )) {
            val sel = atual == id
            Column(
                Modifier.padding(vertical = 5.dp).width(92.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Vermelho.copy(alpha = 0.25f) else Color.Transparent)
                    .border(1.dp, if (sel) Ouro else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { ir(id) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(simbolo, fontSize = 24.sp, color = if (sel) OuroClaro else Ouro)
                Text(nome, fontSize = 13.sp, color = if (sel) Color.White else Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun Reator(modifier: Modifier, ativo: Boolean) {
    val giro = rememberInfiniteTransition(label = "reator")
    val a by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(if (ativo) 2500 else 9000, easing = LinearEasing)), label = "a")
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Ouro, r - 2.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        val r2 = r * 0.72f
        for (k in 0 until 8) drawArc(Ouro, a + k * 45f, 26f, false, Offset(c.x - r2, c.y - r2), Size(r2 * 2, r2 * 2), style = Stroke(3.dp.toPx()))
        val r3 = r * 0.92f
        for (k in 0 until 3) drawArc(OuroClaro, -a * 1.5f + k * 120f, 40f, false, Offset(c.x - r3, c.y - r3), Size(r3 * 2, r3 * 2), style = Stroke(1.dp.toPx()))
        drawCircle(if (ativo) Vermelho else Color.Gray, r * 0.28f, c)
        drawCircle(OuroClaro, r * 0.4f, c, style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun Titulo(texto: String, sub: String? = null) {
    Text(texto, color = Ouro, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
    Spacer(Modifier.height(18.dp))
}

@Composable
fun Bloco(modifier: Modifier = Modifier, borda: Color = Ouro.copy(alpha = 0.3f), conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Cartao, border = BorderStroke(1.dp, borda), modifier = modifier) {
        Column(Modifier.padding(18.dp), content = conteudo)
    }
}

// ───────────────────────── Início ─────────────────────────

@Composable
fun Inicio(estado: Estado, ir: (String) -> Unit) {
    val ctx = LocalContext.current
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val largo = LocalConfiguration.current.screenWidthDp > 900
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HandsOn", color = Ouro, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Controle o tablet com gestos na frente da câmera", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
            }
            Surface(shape = RoundedCornerShape(50), color = Cartao, border = BorderStroke(1.dp, if (estado.ligado) Verde else Color.Gray)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (estado.ligado) Verde else Color.Gray))
                    Spacer(Modifier.width(8.dp))
                    Text(if (estado.ligado) "Lendo gestos" else "Desligado", fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        val pronto = estado.camera && estado.acess
        val esquerda: @Composable ColumnScope.() -> Unit = {
            Passo(1, "Permitir a câmera", "A imagem é lida só aqui no tablet. Nada é gravado nem enviado.", estado.camera) {
                if (!estado.camera) TextButton(onClick = {
                    val lista = mutableListOf(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT >= 33) lista += Manifest.permission.POST_NOTIFICATIONS
                    pedir.launch(lista.toTypedArray())
                }) { Text("Permitir") }
            }
            Passo(2, "Ativar o HandsOn em Acessibilidade",
                "É o que deixa os gestos tocarem na tela. Acessibilidade → Apps instalados → HandsOn → Ativar.", estado.acess) {
                if (!estado.acess) {
                    Row {
                        TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Abrir Acessibilidade") }
                        TextButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                        }) { Text("Está bloqueado?") }
                    }
                    Text("Se aparecer \"Configuração restrita\": toque em \"Está bloqueado?\", nos 3 pontinhos (⋮) lá em cima → \"Permitir configurações restritas\" e tente de novo.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            Button(
                onClick = { HandsOnService.pedir(ctx, if (estado.ligado) HandsOnService.DESLIGAR else HandsOnService.LIGAR) },
                enabled = pronto,
                colors = ButtonDefaults.buttonColors(containerColor = if (estado.ligado) Vermelho else Ouro,
                    contentColor = if (estado.ligado) Color.White else Color(0xFF2A1400)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(76.dp).padding(top = 6.dp)
            ) {
                Text(when { !pronto -> "Faça os passos 1 e 2"; estado.ligado -> "Desligar o HandsOn"; else -> "Ligar o HandsOn" },
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (estado.ligado) Text("Pronto! Pode sair do app: a janela do HandsOn fica por cima de tudo.",
                color = Verde, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            estado.erro?.let { Text(it, color = Vermelho, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        val direita: @Composable ColumnScope.() -> Unit = {
            Bloco(Modifier.fillMaxWidth(), borda = Vermelho.copy(alpha = 0.5f)) {
                Text("Primeira vez?", color = Ouro, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("O tutorial mostra cada gesto animado e confere, ao vivo, se você acertou. Leva uns 5 minutos.",
                    fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { ir("tutorial") }, colors = ButtonDefaults.buttonColors(containerColor = Vermelho, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp)) { Text("Começar o tutorial  ▶", fontSize = 16.sp) }
            }
            Spacer(Modifier.height(14.dp))
            Bloco(Modifier.fillMaxWidth()) {
                Text("A janela do HandsOn", color = Ouro, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                for ((s, t) in listOf("⏸" to "pausa ou retoma a leitura", "⤢" to "muda o tamanho (pequena, média, grande)",
                    "—" to "minimiza: vira uma bolinha (toque nela para voltar)", "✕" to "fecha a janela e desliga o HandsOn",
                    "✥" to "arraste pela barra de cima para mudar de lugar")) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF2A120C)).border(1.dp, Ouro, CircleShape),
                            contentAlignment = Alignment.Center) { Text(s, color = OuroClaro, fontSize = 14.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(t, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Bloco(Modifier.fillMaxWidth()) {
                Text("Para funcionar bem", color = Ouro, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                listOf(
                    "Tablet de pé num suporte, de preferência deitado (paisagem).",
                    "Fique a uns 40–70 cm, com a mão na altura do peito.",
                    "Luz de frente para você; evite janela clara atrás.",
                    "Gestos rápidos (deslizes) com a mão aberta; o resto, com calma.",
                ).forEach { Text("•  $it", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        if (largo) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), content = esquerda)
                Column(Modifier.weight(1f), content = direita)
            }
        } else {
            Column(content = esquerda); Spacer(Modifier.height(20.dp)); Column(content = direita)
        }
    }
}

@Composable
fun Passo(numero: Int, titulo: String, texto: String, feito: Boolean, conteudo: @Composable () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = Cartao,
        border = BorderStroke(1.dp, if (feito) Verde.copy(alpha = 0.6f) else Ouro.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (feito) Verde else Vermelho), contentAlignment = Alignment.Center) {
                Text(if (feito) "✓" else "$numero", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, color = Ouro, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(texto, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                conteudo()
            }
        }
    }
}

// ───────────────────────── Tutorial interativo ─────────────────────────

@Composable
fun Tutorial(estado: Estado, ir: (String) -> Unit) {
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(0) }
    var base by remember { mutableIntStateOf(-1) }
    var acertou by remember { mutableStateOf(false) }
    var maoVista by remember { mutableStateOf(false) }
    var poseAgora by remember { mutableStateOf<String?>(null) }
    val fim = idx >= GUIA_GESTOS.size

    // modo treino: o HandsOn reconhece os gestos sem mexer no tablet enquanto o tutorial está aberto
    DisposableEffect(Unit) { onDispose { HandsOnService.treino(false) } }
    LaunchedEffect(idx) {
        acertou = false; base = -1
        while (!fim) {
            HandsOnService.treino(true)
            val p = HandsOnService.painelAtual
            maoVista = p?.maoVista == true
            poseAgora = p?.pose
            val g = GUIA_GESTOS[idx]
            val n = p?.feitos?.get(g.feito) ?: 0
            if (p != null && base < 0) base = n
            if (base >= 0 && n - base >= g.meta && !acertou) {
                acertou = true
                delay(1400)
                idx += 1
                if (idx >= GUIA_GESTOS.size) Config.prefs(ctx).edit().putBoolean(Config.TUTORIAL_FEITO, true).apply()
                break
            }
            delay(120)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Titulo("Tutorial", "Veja o gesto animado e faça na frente da câmera: o HandsOn confere na hora.")
        if (!estado.ligado) {
            Bloco(Modifier.fillMaxWidth(), borda = Vermelho.copy(alpha = 0.6f)) {
                Text("Ligue o HandsOn para treinar", color = Ouro, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Durante o tutorial, os gestos são só treino: nada acontece no tablet.", fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { HandsOnService.pedir(ctx, HandsOnService.LIGAR) }, enabled = estado.camera && estado.acess,
                    shape = RoundedCornerShape(16.dp)) {
                    Text(if (estado.camera && estado.acess) "Ligar o HandsOn" else "Faça os passos da aba Início primeiro", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (fim) {
            Bloco(Modifier.fillMaxWidth(), borda = Verde) {
                Text("Tutorial concluído! 🎉", color = Verde, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Agora é só usar. A ajuda de gestos está no menu (mão aberta parada) e na tecla H.", fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { HandsOnService.treino(false); ir("inicio") }, shape = RoundedCornerShape(16.dp)) { Text("Começar a usar") }
                    OutlinedButton(onClick = { idx = 0 }, shape = RoundedCornerShape(16.dp)) { Text("Refazer") }
                }
            }
            return@Column
        }
        val g = GUIA_GESTOS[idx]
        // progresso
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            for (i in GUIA_GESTOS.indices) {
                Box(Modifier.height(8.dp).weight(1f).clip(RoundedCornerShape(4.dp))
                    .background(when { i < idx -> Ouro; i == idx -> Vermelho; else -> Color.White.copy(alpha = 0.15f) }))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF140809), border = BorderStroke(1.5.dp, Ouro.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1.4f)) {
                AndroidView(factory = { DemoView(it).apply { demo = g.demo } }, update = { if (it.demo != g.demo) it.demo = g.demo },
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.15f).padding(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Lição ${idx + 1} de ${GUIA_GESTOS.size}", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Text(g.faz, color = Ouro, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(g.gesto, fontSize = 18.sp, color = Color.White)
                Text(g.dica, fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))
                val (cor, msg) = when {
                    acertou -> Verde to "✓  Muito bem!"
                    !estado.ligado -> Color.Gray to "Ligue o HandsOn para treinar"
                    !maoVista -> Ouro to "Mostre a mão para a câmera…"
                    else -> OuroClaro to "Tente agora…  (vendo: ${NOMES_POSE[poseAgora] ?: "mão"})"
                }
                Surface(shape = RoundedCornerShape(16.dp), color = cor.copy(alpha = 0.15f), border = BorderStroke(1.5.dp, cor)) {
                    Text(msg, color = cor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                if (g.meta > 1 && !acertou) Text("Faça ${g.meta} vezes", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { if (idx > 0) idx -= 1 }, enabled = idx > 0, shape = RoundedCornerShape(14.dp)) { Text("◀ Anterior") }
                    OutlinedButton(onClick = { idx += 1 }, shape = RoundedCornerShape(14.dp)) { Text("Pular ▶") }
                }
            }
        }
    }
}

// ───────────────────────── Gestos (com demonstração animada) ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gestos() {
    var aberto by remember { mutableStateOf<CartaoGesto?>(null) }
    val colunas = if (LocalConfiguration.current.screenWidthDp > 1000) 3 else 2
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Titulo("Gestos", "Toque num gesto para ver a animação de como se faz.")
        GUIA_GESTOS.chunked(colunas).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                linha.forEach { g ->
                    Surface(onClick = { aberto = g }, shape = RoundedCornerShape(16.dp), color = Cartao,
                        border = BorderStroke(1.dp, Ouro.copy(alpha = 0.3f)), modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF100709)).border(1.dp, Ouro.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                AndroidView(factory = { DemoView(it).apply { miniatura = true; demo = g.demo } },
                                    update = { it.demo = g.demo }, modifier = Modifier.size(58.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.faz, color = Ouro, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(g.gesto, fontSize = 14.sp)
                                Text(g.dica, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            Text("▶", color = Vermelho, fontSize = 16.sp)
                        }
                    }
                }
                repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    aberto?.let { g -> Demonstracao(g) { aberto = null } }
}

@Composable
fun Demonstracao(g: CartaoGesto, fechar: () -> Unit) {
    Dialog(onDismissRequest = fechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF140809), border = BorderStroke(1.5.dp, Ouro.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(0.86f).widthIn(max = 900.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(Vermelho))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.faz, color = Ouro, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(g.gesto + "  ·  " + g.dica, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                    }
                    TextButton(onClick = fechar) { Text("Fechar", fontSize = 16.sp) }
                }
                Spacer(Modifier.height(12.dp))
                AndroidView(factory = { DemoView(it).apply { demo = g.demo } }, update = { if (it.demo != g.demo) it.demo = g.demo },
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.15f))
            }
        }
    }
}

// ───────────────────────── Teclas ─────────────────────────

@Composable
fun Teclas() {
    val colunas = if (LocalConfiguration.current.screenWidthDp > 1000) 3 else 2
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Titulo("Teclas de atalho", "Com um teclado ligado ao tablet (como a capa-teclado do Galaxy Tab).")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 18.dp)) {
            Bloco(Modifier.weight(1f)) {
                Text("Com o app HandsOn aberto", color = Ouro, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Tecla("S"); Spacer(Modifier.width(10.dp)); Text("só a tecla", fontSize = 15.sp)
                }
            }
            Bloco(Modifier.weight(1f)) {
                Text("Em qualquer outro app", color = Ouro, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Tecla("Ctrl", 64); Text("  +  ", fontSize = 18.sp); Tecla("Alt", 56); Text("  +  ", fontSize = 18.sp); Tecla("S")
                }
            }
        }
        Atalhos.LISTA.chunked(colunas).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                linha.forEach { (letra, nome, desc) ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Cartao, border = BorderStroke(1.dp, Ouro.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tecla(letra.toString())
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(nome, color = Ouro, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(desc, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
                repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Sem teclado? Tudo isso também está nos gestos e nos botões da janela do HandsOn (⏸ ⤢ — ✕).",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun Tecla(t: String, largura: Int = 48) {
    Box(
        Modifier.width(largura.dp).height(48.dp).clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A1A12), Color(0xFF1A0A08))))
            .border(1.5.dp, Ouro, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) { Text(t, color = OuroClaro, fontSize = if (t.length > 1) 15.sp else 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

// ───────────────────────── Ajustes ─────────────────────────

@Composable
fun AjustesTela() {
    val ctx = LocalContext.current
    val p = remember { Config.prefs(ctx) }
    var alcance by remember { mutableFloatStateOf(p.getFloat(Config.ALCANCE, 0.5f)) }
    var firmeza by remember { mutableFloatStateOf(p.getFloat(Config.FIRMEZA, 0.5f)) }
    var rolagem by remember { mutableFloatStateOf(p.getFloat(Config.ROLAGEM, 0.5f)) }
    var sons by remember { mutableStateOf(p.getBoolean(Config.SONS, true)) }
    var tam by remember { mutableIntStateOf(p.getInt(Config.JANELA_TAM, 1)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Titulo("Ajustes")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Bloco(Modifier.weight(1f)) {
                Deslizante("Alcance da mão", alcance, "mexo mais a mão", "mexo menos a mão") { alcance = it; p.edit().putFloat(Config.ALCANCE, it).apply() }
                Deslizante("Firmeza do cursor", firmeza, "mais rápido", "mais firme") { firmeza = it; p.edit().putFloat(Config.FIRMEZA, it).apply() }
                Deslizante("Velocidade da rolagem", rolagem, "devagar", "rápida") { rolagem = it; p.edit().putFloat(Config.ROLAGEM, it).apply() }
            }
            Bloco(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sons de confirmação (tecla S = mudo)", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = sons, onCheckedChange = { sons = it; p.edit().putBoolean(Config.SONS, it).apply() })
                }
                Spacer(Modifier.height(12.dp))
                Text("Tamanho da janela do HandsOn", color = Ouro, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("Pequena", "Média", "Grande").forEachIndexed { i, nome ->
                        val sel = tam == i
                        OutlinedButton(onClick = { tam = i; p.edit().putInt(Config.JANELA_TAM, i).apply() },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) Vermelho.copy(alpha = 0.3f) else Color.Transparent),
                            border = BorderStroke(1.dp, if (sel) Ouro else Ouro.copy(alpha = 0.4f)), shape = RoundedCornerShape(12.dp)) { Text(nome) }
                    }
                }
                Text("Vale na próxima vez que ligar (ou use o botão ⤢ da janela).", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { p.edit().putBoolean(Config.GUIA_VISTO, false).apply() }) {
                    Text("Mostrar a ajuda de gestos na próxima vez que ligar")
                }
            }
        }
    }
}

@Composable
fun Deslizante(titulo: String, valor: Float, esq: String, dir: String, mudar: (Float) -> Unit) {
    Text(titulo, color = Ouro, fontSize = 16.sp)
    Slider(value = valor, onValueChange = mudar)
    Row {
        Text(esq, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(dir, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(12.dp))
}
