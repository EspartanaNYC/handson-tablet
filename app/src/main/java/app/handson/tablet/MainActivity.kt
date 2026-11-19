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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
private val Vermelho = Color(0xFFE0262B)
private val Fundo = Color(0xFF120707)
private val Cartao = Color(0xFF1F0B0B)
private val Verde = Color(0xFF5ADC78)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ouro, onPrimary = Color(0xFF2A1400),
                    secondary = Vermelho, background = Fundo, surface = Cartao,
                    onBackground = Color.White, onSurface = Color.White
                )
            ) { Tela() }
        }
    }
}

fun acessibilidadeAtiva(c: Context): Boolean {
    val lista = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val comp = ComponentName(c, HandsOnService::class.java)
    return lista.split(':').any {
        it.equals(comp.flattenToString(), true) || it.equals(comp.flattenToShortString(), true)
    }
}

@Composable
fun Tela() {
    val ctx = LocalContext.current
    var camera by remember { mutableStateOf(false) }
    var acess by remember { mutableStateOf(false) }
    var ligado by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    // atualiza o estado a cada meio segundo (a pessoa volta das Configurações, liga/desliga pela notificação…)
    LaunchedEffect(Unit) {
        while (true) {
            camera = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            acess = acessibilidadeAtiva(ctx) && HandsOnService.instancia != null
            ligado = HandsOnService.ligado
            erro = HandsOnService.erro
            delay(500)
        }
    }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camera = it[Manifest.permission.CAMERA] == true
    }

    val largo = LocalConfiguration.current.screenWidthDp > 760
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF3A0C0C), Fundo), radius = 1400f)
        )
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
            Cabecalho(ligado)
            Spacer(Modifier.height(24.dp))
            if (largo) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(0.9f)) { Preparar(camera, acess, ligado, erro, { pedir.launch(it) }); Spacer(Modifier.height(20.dp)); Preferencias() }
                    Column(Modifier.weight(1.1f)) { Guia() }
                }
            } else {
                Preparar(camera, acess, ligado, erro, { pedir.launch(it) })
                Spacer(Modifier.height(20.dp)); Guia()
                Spacer(Modifier.height(20.dp)); Preferencias()
            }
            Spacer(Modifier.height(20.dp))
            Dicas()
        }
    }
}

@Composable
fun Cabecalho(ligado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).border(3.dp, Ouro, CircleShape),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(20.dp).clip(CircleShape).background(Vermelho)) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("HandsOn", color = Ouro, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Controle o tablet com gestos na frente da câmera", color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
        }
        Surface(shape = RoundedCornerShape(50), color = Cartao, border = BorderStroke(1.dp, if (ligado) Verde else Color.Gray)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (ligado) Verde else Color.Gray))
                Spacer(Modifier.width(8.dp))
                Text(if (ligado) "Lendo gestos" else "Desligado", fontSize = 15.sp)
            }
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
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (feito) Verde else Vermelho),
                contentAlignment = Alignment.Center
            ) { Text(if (feito) "✓" else "$numero", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, color = Ouro, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(texto, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                conteudo()
            }
        }
    }
}

@Composable
fun Preparar(camera: Boolean, acess: Boolean, ligado: Boolean, erro: String?, pedir: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    Text("Preparar", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Passo(1, "Permitir a câmera", "A imagem é lida só aqui no tablet. Nada é gravado nem enviado.", camera) {
        if (!camera) TextButton(onClick = {
            val lista = mutableListOf(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) lista += Manifest.permission.POST_NOTIFICATIONS
            pedir(lista.toTypedArray())
        }) { Text("Permitir") }
    }
    Passo(2, "Ativar o HandsOn em Acessibilidade",
        "É o que deixa os gestos tocarem na tela. Em Acessibilidade → Apps instalados → HandsOn → Ativar.", acess) {
        if (!acess) Row {
            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Abrir Acessibilidade") }
            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            }) { Text("Está bloqueado?") }
        }
        if (!acess) Text(
            "Se aparecer \"Configuração restrita\": toque em \"Está bloqueado?\", depois nos 3 pontinhos (⋮) lá em cima → \"Permitir configurações restritas\" e tente de novo.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
        )
    }
    Spacer(Modifier.height(6.dp))
    val pronto = camera && acess
    Button(
        onClick = {
            val acao = if (ligado) HandsOnService.DESLIGAR else HandsOnService.LIGAR
            ctx.startService(Intent(ctx, HandsOnService::class.java).setAction(acao))
        },
        enabled = pronto,
        colors = ButtonDefaults.buttonColors(containerColor = if (ligado) Vermelho else Ouro, contentColor = if (ligado) Color.White else Color(0xFF2A1400)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(68.dp)
    ) {
        Text(
            when {
                !pronto -> "Faça os passos 1 e 2"
                ligado -> "Desligar o HandsOn"
                else -> "Ligar o HandsOn"
            }, fontSize = 21.sp, fontWeight = FontWeight.Bold
        )
    }
    if (ligado) Text(
        "Pronto! Pode sair do app: o HandsOn continua funcionando por cima de tudo. Para desligar, use a notificação ou volte aqui.",
        color = Verde, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)
    )
    erro?.let { Text(it, color = Vermelho, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Guia() {
    var aberto by remember { mutableStateOf<CartaoGesto?>(null) }
    Text("Gestos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Text("Toque num gesto para ver como se faz", color = Ouro.copy(alpha = 0.8f), fontSize = 14.sp)
    Spacer(Modifier.height(10.dp))
    val colunas = 2
    GUIA_GESTOS.chunked(colunas).forEach { linha ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            linha.forEach { g ->
                Surface(
                    onClick = { aberto = g },
                    shape = RoundedCornerShape(14.dp), color = Cartao,
                    border = BorderStroke(1.dp, Ouro.copy(alpha = 0.25f)), modifier = Modifier.weight(1f)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF100709))
                                .border(1.dp, Ouro.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { DemoView(it).apply { miniatura = true; demo = g.demo } },
                                update = { it.demo = g.demo },
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.faz, color = Ouro, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(g.gesto, fontSize = 14.sp)
                            Text(g.dica, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                        Text("▶", color = Vermelho, fontSize = 14.sp)
                    }
                }
            }
            if (linha.size < colunas) Spacer(Modifier.weight(1f))
        }
    }
    aberto?.let { g -> Demonstracao(g) { aberto = null } }
}

@Composable
fun Demonstracao(g: CartaoGesto, fechar: () -> Unit) {
    Dialog(onDismissRequest = fechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(22.dp), color = Color(0xFF140809),
            border = BorderStroke(1.5.dp, Ouro.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(0.86f).widthIn(max = 900.dp)
        ) {
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
                AndroidView(
                    factory = { DemoView(it).apply { demo = g.demo } },
                    update = { if (it.demo != g.demo) it.demo = g.demo },
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.15f)
                )
                Text("Toque fora para fechar", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
            }
        }
    }
}

@Composable
fun Preferencias() {
    val ctx = LocalContext.current
    val p = remember { Config.prefs(ctx) }
    var alcance by remember { mutableFloatStateOf(p.getFloat(Config.ALCANCE, 0.5f)) }
    var firmeza by remember { mutableFloatStateOf(p.getFloat(Config.FIRMEZA, 0.5f)) }
    var sons by remember { mutableStateOf(p.getBoolean(Config.SONS, true)) }
    var mini by remember { mutableStateOf(p.getBoolean(Config.MINI_CAMERA, true)) }
    Text("Ajustes", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Surface(shape = RoundedCornerShape(18.dp), color = Cartao, border = BorderStroke(1.dp, Ouro.copy(alpha = 0.25f))) {
        Column(Modifier.padding(18.dp)) {
            Text("Alcance da mão", color = Ouro, fontSize = 16.sp)
            Slider(value = alcance, onValueChange = { alcance = it; p.edit().putFloat(Config.ALCANCE, it).apply() })
            Row { Text("mexo mais a mão", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text("mexo menos a mão", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.End) }
            Spacer(Modifier.height(12.dp))
            Text("Firmeza do cursor", color = Ouro, fontSize = 16.sp)
            Slider(value = firmeza, onValueChange = { firmeza = it; p.edit().putFloat(Config.FIRMEZA, it).apply() })
            Row { Text("mais rápido", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text("mais firme", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.End) }
            Spacer(Modifier.height(8.dp))
            Linha("Sons de confirmação", sons) { sons = it; p.edit().putBoolean(Config.SONS, it).apply() }
            Linha("Mini câmera no canto da tela", mini) { mini = it; p.edit().putBoolean(Config.MINI_CAMERA, it).apply() }
            TextButton(onClick = { p.edit().putBoolean(Config.GUIA_VISTO, false).apply() }) {
                Text("Mostrar a ajuda de gestos na próxima vez que ligar")
            }
        }
    }
}

@Composable
fun Linha(texto: String, valor: Boolean, mudar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(texto, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = mudar)
    }
}

@Composable
fun Dicas() {
    Surface(shape = RoundedCornerShape(18.dp), color = Cartao.copy(alpha = 0.7f), border = BorderStroke(1.dp, Vermelho.copy(alpha = 0.4f))) {
        Column(Modifier.padding(18.dp).fillMaxWidth()) {
            Text("Para funcionar bem", color = Ouro, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Deixe o tablet de pé, num suporte, de preferência deitado (paisagem).",
                "Fique a uns 40–70 cm da tela, com a mão na altura do peito.",
                "Luz de frente para você ajuda muito; evite janela clara atrás.",
                "Gestos com anel (Voltar, Início…): segure até o anel dourado fechar.",
                "Quatro dedos (polegar dobrado) pausa e retoma a leitura a qualquer momento.",
            ).forEach { Text("•  $it", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}
