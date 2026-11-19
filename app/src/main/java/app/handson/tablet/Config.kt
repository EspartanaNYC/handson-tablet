package app.handson.tablet

import android.content.Context
import android.content.SharedPreferences

/** Ajustes que a pessoa escolhe no app (guardados no tablet). */
object Config {
    const val ARQUIVO = "handson"
    const val ALCANCE = "alcance"        // 0 = mão anda mais, 1 = mão anda menos
    const val FIRMEZA = "firmeza"        // 0 = cursor mais solto, 1 = mais firme
    const val ROLAGEM = "rolagem"        // 0 = rola devagar, 1 = rola rápido
    const val SONS = "sons"
    const val GUIA_VISTO = "guia_visto"
    const val JANELA_TAM = "janela_tamanho"
    const val JANELA_X = "janela_x"
    const val JANELA_Y = "janela_y"
    const val TUTORIAL_FEITO = "tutorial_feito"

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    /** Copia os ajustes para o leitor de gestos. */
    fun aplicar(p: SharedPreferences) {
        val a = p.getFloat(ALCANCE, 0.5f).toDouble()
        Ajustes.larguraArea = 0.85 - 0.30 * a
        Ajustes.alturaArea = 0.75 - 0.26 * a
        val f = p.getFloat(FIRMEZA, 0.5f).toDouble()
        Ajustes.suaveMin = 0.95 - 0.7 * f
        val r = p.getFloat(ROLAGEM, 0.5f).toDouble()
        Ajustes.rolagemGanho = 1.6 + 1.6 * r
    }
}

/** Os gestos, do jeito que aparecem nos cartões (demo = animação em DemoView; feito = contador do tutorial). */
data class CartaoGesto(val gesto: String, val faz: String, val dica: String, val demo: String, val feito: String, val meta: Int = 1)

val GUIA_GESTOS = listOf(
    CartaoGesto("Indicador apontando", "Mover o cursor", "Mexa a mão inteira, sem pressa", "mover", "mover_px", 1500),
    CartaoGesto("Pinça e solta", "Tocar", "Encoste o polegar no indicador e solte", "toque", "toque"),
    CartaoGesto("2 pinças rápidas", "Toque duplo", "Bem rapidinho, no mesmo lugar", "duplo", "duplo"),
    CartaoGesto("Pinça parada", "Toque longo", "Segure a pinça meio segundo", "longo", "longo"),
    CartaoGesto("Pinça + mover", "Arrastar", "Feche, mova a mão e solte", "arrastar", "arrastar"),
    CartaoGesto("Dois dedos", "Rolar", "A página acompanha a mão", "rolar", "rolar"),
    CartaoGesto("Só o mindinho", "Volume", "Suba a mão = mais alto", "volume", "volume", 2),
    CartaoGesto("L: abra ou feche", "Zoom", "Como na tela, com polegar e indicador", "zoom", "zoom"),
    CartaoGesto("Mão aberta para o lado", "Voltar", "Deslize rápido para a esquerda ou direita", "voltar", "voltar"),
    CartaoGesto("Mão aberta para cima", "Tela inicial", "Um deslize rápido para cima", "inicio", "inicio"),
    CartaoGesto("Mão aberta para baixo", "Notificações", "De novo para baixo = ajustes rápidos", "notificacoes", "notificacoes"),
    CartaoGesto("Mão aberta parada", "Menu", "Apps recentes, captura, ajustes, ajuda…", "menu", "menu"),
    CartaoGesto("Punho fechado", "Segurar o cursor", "Reposicione a mão sem mexer o cursor", "punho", "punho"),
    CartaoGesto("Quatro dedos", "Pausar / retomar", "Polegar dobrado na palma, segure (2x: pausa e volta)", "pausar", "pausar", 2),
)
