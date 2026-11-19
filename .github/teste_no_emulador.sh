#!/bin/bash
# Teste automático: instala o app num tablet virtual, abre, mostra uma demonstração e liga o HandsOn.
set -x
PKG=app.handson.tablet
mkdir -p telas
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.CAMERA
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell am start -n $PKG/.MainActivity
sleep 10
adb exec-out screencap -p > telas/1_app.png

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
    if t and alvo in t.group(1):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x0, y0, x1, y1 = map(int, b.groups())
        print((x0 + x1) // 2, (y0 + y1) // 2)
        break
PY
)
  echo "tocar '$1' em: $B"
  [ -n "$B" ] && adb shell input tap $B
}

# rola até os gestos e abre a demonstração do toque duplo
for i in 1 2 3; do toca_texto "Toque duplo" && break; adb shell input swipe 600 1200 600 500 400; sleep 2; done
sleep 3
adb exec-out screencap -p > telas/2_demo.png
sleep 1.3
adb exec-out screencap -p > telas/3_demo_depois.png
adb shell input keyevent KEYCODE_BACK
sleep 2

# ativa o serviço de acessibilidade e liga
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.HandsOnService
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell am start -n $PKG/.MainActivity
sleep 3
adb shell input swipe 600 400 600 1400 300
sleep 2
adb exec-out screencap -p > telas/4_pronto.png
toca_texto "Ligar o HandsOn"
sleep 15
adb exec-out screencap -p > telas/5_ligado.png
adb shell input keyevent KEYCODE_HOME
sleep 6
adb exec-out screencap -p > telas/6_por_cima.png
adb shell dumpsys activity services $PKG | head -40 > telas/servico.txt
adb logcat -d > telas/logcat.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|HandsOn|mediapipe|tflite" telas/logcat.txt | head -200 > telas/resumo_log.txt
if grep -q "FATAL EXCEPTION" telas/logcat.txt; then echo "O APP FECHOU SOZINHO (crash)"; exit 1; fi
echo "Sem crash"
