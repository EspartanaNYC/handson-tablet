package app.handson.tablet

import android.content.Context
import android.content.SharedPreferences

/** Ajustes que a pessoa escolhe no app (guardados no tablet). */
object Config {
    const val ARQUIVO = "handson"
    const val ALCANCE = "alcance"        // 0 = mão anda mais, 1 = mão anda menos
    const val FIRMEZA = "firmeza"        // 0 = cursor mais solto, 1 = mais firme
    const val SONS = "sons"
    const val MINI_CAMERA = "mini_camera"
    const val GUIA_VISTO = "guia_visto"

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    /** Copia os ajustes para o leitor de gestos. */
    fun aplicar(p: SharedPreferences) {
        val a = p.getFloat(ALCANCE, 0.5f).toDouble()
        Ajustes.larguraArea = 0.85 - 0.30 * a
        Ajustes.alturaArea = 0.75 - 0.26 * a
        val f = p.getFloat(FIRMEZA, 0.5f).toDouble()
        Ajustes.suaveMin = 0.95 - 0.7 * f
    }
}

/** Os gestos, do jeito que aparecem nos cartões de ajuda. */
data class CartaoGesto(val gesto: String, val faz: String, val dica: String)

val GUIA_GESTOS = listOf(
    CartaoGesto("Indicador apontando", "Move o cursor", "Mexa a mão inteira, sem pressa"),
    CartaoGesto("Pinça", "Toque", "Encoste o polegar no indicador e solte"),
    CartaoGesto("2 pinças rápidas", "Toque duplo", "Bem rapidinho, no mesmo lugar"),
    CartaoGesto("Pinça parada", "Toque longo", "Segure a pinça meio segundo"),
    CartaoGesto("Pinça + mover", "Arrastar / deslizar", "Feche, mova e solte"),
    CartaoGesto("Dois dedos juntos", "Rolar a página", "A página acompanha a mão"),
    CartaoGesto("Só o mindinho", "Volume e zoom", "Sobe/desce = volume · lados = zoom"),
    CartaoGesto("L (polegar + indicador)", "Voltar", "Segure até o anel fechar"),
    CartaoGesto("V aberto", "Tela inicial", "Indicador e médio separados"),
    CartaoGesto("Polegar para o lado", "Apps recentes", "Mão fechada, polegar apontando"),
    CartaoGesto("Joinha de costas", "Notificações", "Costas da mão para a câmera"),
    CartaoGesto("Polegar para baixo", "Fechar painel", "Também vale o chifrinho"),
    CartaoGesto("Hang loose", "Captura de tela", "Polegar e mindinho abertos"),
    CartaoGesto("Três dedos", "Ajustes rápidos", "Wi-Fi, brilho, Bluetooth"),
    CartaoGesto("Quatro dedos", "Pausar / retomar", "Polegar dobrado na palma"),
    CartaoGesto("Mão aberta parada", "Mostrar / esconder ajuda", "Fique parada 1 segundo"),
)
