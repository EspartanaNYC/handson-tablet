# HandsOn para tablet Android

Esta é a versão do HandsOn para tablet (feita pensando no Samsung Galaxy Tab S7 FE, mas funciona em qualquer tablet ou celular com **Android 9 ou mais novo**).

O app usa a **câmera frontal** para ler a sua mão e transforma os gestos em **toques na tela**, rolagem, volume e nos botões do sistema (Voltar, Início, Notificações…). Ele funciona **por cima de qualquer app**: YouTube, Netflix, receitas, leitura, fotos…

> A imagem da câmera é analisada só no próprio tablet. Nada é gravado e nada vai para a internet.

---

## 1. Gerar o app no Mac (uma vez só)

O Android não instala programas em Python. Por isso esta versão é um app Android de verdade e precisa ser "montado" no **Android Studio**, que é gratuito.

1. Baixe e instale o **Android Studio**: https://developer.android.com/studio
   Na primeira abertura, aceite tudo no assistente ("Standard").
2. Descompacte o `HandsOnTablet.zip` (por exemplo, na pasta Documentos).
3. No Android Studio: **File → Open…** → escolha a pasta **HandsOnTablet** → **Open** → "Trust project".
4. Espere a barra de baixo terminar o **Gradle sync**. Na primeira vez, isso baixa as bibliotecas e o modelo da mão do Google e pode levar de 5 a 15 minutos.

## 2. Preparar o tablet (uma vez só)

1. No tablet: **Configurações → Sobre o tablet → Informações do software**.
2. Toque **7 vezes** em **Número de compilação** até aparecer "Modo de desenvolvedor ativado".
3. Volte em **Configurações → Opções do desenvolvedor** e ligue **Depuração USB**.
4. Ligue o tablet no Mac com o cabo USB‑C. No tablet, toque em **Permitir** na pergunta "Permitir depuração USB?".

## 3. Instalar

No Android Studio, o nome do tablet aparece lá em cima, ao lado do botão verde **▶ (Run)**. Clique no ▶. O HandsOn é instalado e abre sozinho no tablet.

*Sem cabo?* Use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`. Mande para o tablet (Google Drive, e-mail ou Quick Share) e toque nele para instalar. O tablet vai pedir para "permitir instalar apps desta fonte".

## 4. Primeira vez no tablet

O próprio app guia você em 3 passos:

1. **Permitir a câmera.**
2. **Ativar o HandsOn em Acessibilidade.** Toque em "Abrir Acessibilidade" → **Apps instalados** → **HandsOn** → ligue a chave → **Permitir**.
   * Se aparecer **"Configuração restrita"** (o Android faz isso com apps instalados fora da Play Store), faça assim:
     toque em **"Está bloqueado?"** no app (ou vá em Configurações → Apps → HandsOn) → toque nos **3 pontinhos ⋮** no canto de cima → **Permitir configurações restritas** → confirme com a senha/digital. Depois volte e ative de novo.
3. Toque em **Ligar o HandsOn**.

Pronto: pode sair do app. Um cursor dourado aparece na tela quando você mostra a mão. Na primeira vez, a tela de ajuda com todos os gestos aparece sozinha. Para fechá-la, deixe a **mão aberta parada por 1 segundo**.

---

## Gestos

| Gesto | O que faz |
|---|---|
| ☝️ Indicador apontando | Move o cursor |
| 🤏 Pinça (polegar + indicador) e soltar | **Toque** |
| 🤏🤏 Duas pinças rápidas | **Toque duplo** (por exemplo, dar zoom em foto) |
| 🤏 Pinça parada ½ segundo | **Toque longo** |
| 🤏 Pinça + mover + soltar | **Arrastar / deslizar** (passar fotos, mover ícones) |
| ✌️ Dois dedos juntos | **Rolar**: a página acompanha a mão |
| Só o mindinho em pé | **Volume**: subir a mão aumenta, descer diminui |
| L (polegar + indicador) | Voltar ⭕ |
| ✌️ V aberto (dedos separados) | Tela inicial ⭕ |
| 👊 Mão fechada, polegar para o lado | Apps recentes ⭕ |
| 👍 **Joinha de costas** (costas da mão para a câmera) | Abre as notificações ⭕ |
| 👎 Polegar para baixo (ou 🤘 chifrinho) | Fecha o painel de notificações ⭕ |
| 🤙 Hang loose (polegar e mindinho abertos) | Captura de tela ⭕ |
| 3 dedos (polegar, indicador e médio) | Ajustes rápidos (Wi‑Fi, brilho…) ⭕ |
| 4 dedos com o polegar dobrado | **Pausar / retomar** a leitura ⭕ |
| 🖐 Mão aberta parada 1 s | Mostra / esconde a ajuda de gestos |

⭕ = gesto de "segurar": mantenha até o **anel dourado** em volta do cursor se fechar (¾ de segundo). Isso evita acionar sem querer.

### Diferenças em relação à versão do Mac
* O "clique" virou **toque** (e o arrastar do mouse virou **deslizar**).
* No tablet não existe "tela cheia" nem "abas" do sistema. Por isso o **joinha de costas abre as notificações**, o polegar para o lado abre os **apps recentes**, o V aberto vai para a **tela inicial** e os três dedos abrem os **ajustes rápidos**.
* O volume com o mindinho e a pausa com quatro dedos funcionam igual ao Mac.

---

## Dicas para funcionar bem
* Deixe o tablet **de pé num suporte**, de preferência **deitado (paisagem)**.
* Fique a uns **40–70 cm** da tela, com a mão mais ou menos na altura do peito.
* **Luz de frente** para você ajuda muito. Evite uma janela clara atrás de você.
* Em **Ajustes** no app:
  * **Alcance da mão**: puxe para "mexo menos a mão" se precisar esticar demais o braço para chegar nas bordas.
  * **Firmeza do cursor**: aumente se o cursor tremer.
  * Também dá para ligar ou desligar os sons e a mini câmera do canto.
* Para **desligar**, use o botão **Desligar** na notificação do HandsOn ou abra o app.
* A tela não apaga sozinha enquanto o HandsOn está ligado. Lembre de desligar quando terminar, para poupar bateria.

## Se algo não funcionar
* **O botão "Ligar" está apagado**: falta o passo 1 ou o 2. Se ativou a Acessibilidade e mesmo assim não liberou, desative e ative de novo.
* **O cursor aparece, mas o toque não acontece**: confira se o HandsOn continua ativo em Acessibilidade. Às vezes o sistema desliga o serviço depois de uma atualização.
* **A Samsung desliga o app depois de um tempo**: vá em Configurações → Bateria → Limites de uso em segundo plano e coloque o HandsOn em **"Apps que nunca suspendem"**.
* **O Android Studio dá erro no "sync" falando de versão**: aceite a sugestão de atualizar (*Upgrade*) que ele mesmo oferece.
