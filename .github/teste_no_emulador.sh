#!/bin/bash
# Teste automático: instala o app num tablet virtual, passeia pelas telas, liga o HandsOn
# e confere se o dedo virtual consegue rolar a tela de outro app.
set -x
PKG=app.handson.tablet
mkdir -p telas
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.CAMERA
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell am start -n $PKG/.MainActivity
sleep 10
adb exec-out screencap -p > telas/01_inicio.png

toca_texto() {   # toca no elemento da tela que tem esse texto
  adb shell uiautomator dump /sdcard/tela.xml >/dev/null 2>&1
  adb pull /sdcard/tela.xml tela.xml >/dev/null 2>&1
  B=$(python3 - "$1" <<'PY'
import re, sys
xml = open("tela.xml", encoding="utf-8").read()
alvo = sys.argv[1]
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    if t and t.group(1) == alvo:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x0, y0, x1, y1 = map(int, b.groups())
        print((x0 + x1) // 2, (y0 + y1) // 2)
        break
PY
)
  echo "tocar '$1' em: $B"
  [ -n "$B" ] && adb shell input tap $B
  [ -n "$B" ]
}

toca_texto "Gestos"; sleep 3
adb exec-out screencap -p > telas/02_gestos.png
toca_texto "Toque duplo"; sleep 3
adb exec-out screencap -p > telas/03_demo.png
adb shell input keyevent KEYCODE_BACK; sleep 2
toca_texto "Teclas"; sleep 3
adb exec-out screencap -p > telas/04_teclas.png
toca_texto "Ajustes"; sleep 2
adb exec-out screencap -p > telas/05_ajustes.png

# ativa o serviço de acessibilidade e liga
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.HandsOnService
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell am start -n $PKG/.MainActivity
sleep 3
toca_texto "Início"; sleep 2
toca_texto "Ligar o HandsOn" || adb shell am startservice -n $PKG/.HandsOnService -a $PKG.LIGAR
sleep 14
adb exec-out screencap -p > telas/06_ligado.png
toca_texto "Tutorial"; sleep 4
adb exec-out screencap -p > telas/07_tutorial.png

# outro app: o dedo virtual rola a tela de Configurações
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -a android.settings.SETTINGS; sleep 6
adb exec-out screencap -p > telas/08_config_antes.png
adb shell uiautomator dump /sdcard/a.xml >/dev/null 2>&1; adb pull /sdcard/a.xml antes.xml >/dev/null 2>&1
adb shell am startservice -n $PKG/.HandsOnService -a $PKG.TESTE_DEDO
sleep 3
adb exec-out screencap -p > telas/09_config_depois.png
adb shell uiautomator dump /sdcard/b.xml >/dev/null 2>&1; adb pull /sdcard/b.xml depois.xml >/dev/null 2>&1
python3 - <<'PY' > telas/rolagem.txt
import re
def textos(f):
    x = open(f, encoding="utf-8").read()
    return [t for t in re.findall(r' text="([^"]+)"', x)]
a, b = textos("antes.xml"), textos("depois.xml")
print("antes:", a[:12]); print("depois:", b[:12])
print("ROLOU" if a[:12] != b[:12] else "NAO ROLOU")
PY
cat telas/rolagem.txt
adb shell dumpsys activity services $PKG | grep -E "isForeground|foregroundId" > telas/primeiro_plano.txt
adb logcat -d > telas/logcat.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|HandsOn" telas/logcat.txt | head -200 > telas/resumo_log.txt
if grep -q "FATAL EXCEPTION" telas/logcat.txt; then echo "O APP FECHOU SOZINHO (crash)"; exit 1; fi
echo "Sem crash"
