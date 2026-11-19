# HandsOn para tablet Android

Controle o tablet (feito pensando no Samsung Galaxy Tab S7 FE) com gestos na frente da câmera frontal.
Um **dedo virtual** toca, arrasta e rola a tela seguindo a sua mão, por cima de qualquer app.
A imagem da câmera é analisada só no próprio tablet: nada é gravado nem enviado.

## Instalar

Baixe no tablet: **https://github.com/EspartanaNYC/handson-tablet/releases/download/ultima/HandsOn.apk**

1. Abra o arquivo e toque em **Instalar** (permita "instalar apps desta fonte"; no aviso do Play Protect, *Mais detalhes → Instalar mesmo assim*).
2. Abra o HandsOn: **câmera → Acessibilidade → Ligar**.
   Se aparecer "Configuração restrita": no app, toque em "Está bloqueado?" → **⋮ → Permitir configurações restritas**.
3. Faça o **Tutorial** (aba Tutorial): ele mostra cada gesto animado e confere ao vivo se você acertou.

## Gestos

| Gesto | O que faz |
|---|---|
| ☝️ Indicador apontando | Move o cursor |
| 🤏 Pinça e solta | Toque |
| 🤏🤏 Duas pinças rápidas | Toque duplo |
| 🤏 Pinça parada | Toque longo |
| 🤏 Pinça + mover | Arrastar (o item vai junto com a mão) |
| ✌️ Dois dedos | Rolar: a página acompanha a mão (para cima, para baixo e para os lados) |
| 🤙 Só o mindinho | Volume: suba a mão = mais alto |
| L com polegar e indicador | Zoom: abra os dedos = aproxima, feche = afasta |
| 🖐 Mão aberta, deslize rápido para o lado | Voltar |
| 🖐 Mão aberta, deslize rápido para cima | Tela inicial |
| 🖐 Mão aberta, deslize rápido para baixo | Notificações (de novo = ajustes rápidos) |
| 🖐 Mão aberta parada 1 s | **Menu**: apps recentes, captura de tela, ajustes rápidos, notificações, pausar, ajuda, janela |
| ✊ Punho | Segura o cursor parado (para reposicionar a mão) |
| Quatro dedos (polegar dobrado), segurando | Pausar / retomar |

No menu: mova a mão até o item e faça a pinça (ou espere o anel). Punho fecha o menu.

## A janela do HandsOn

Fica por cima dos apps e mostra a câmera com o desenho da sua mão.
**⏸** pausar · **⤢** tamanho (pequena, média, grande) · **—** minimizar (vira uma bolinha) · **✕** fechar e desligar.
Arraste pela barra de cima para mudar de lugar.

## Teclas (com teclado ligado ao tablet)

Com o app aberto, só a tecla. Em qualquer outro app: **Ctrl + Alt + tecla**.

| Tecla | Faz |
|---|---|
| S | Mudo (liga/desliga os sons do HandsOn) |
| P | Pausar / retomar |
| H | Ajuda (cartões de gestos na tela) |
| J | Tamanho da janela |
| M | Minimizar a janela |
| L | Ligar / desligar |
| Q | Desligar |
| V | Voltar |
| I | Tela inicial |
| N | Notificações |
| T | Tutorial |

## Para quem mexe no código

Projeto Android (Kotlin). O GitHub monta o APK sozinho a cada mudança (aba *Actions*) e publica no link acima.
Também roda um teste automático num tablet virtual: abre o app, liga o HandsOn e confere se o dedo virtual rola a tela de outro app.
