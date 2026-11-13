A=~/Library/Android/sdk/platform-tools/adb
V=/private/tmp/claude-501/-Users-pablokim-project-pleos-carling-app/cbd52007-ac30-461b-91e4-4d456a83101e/scratchpad/video
dev(){ local c=$1; shift; $A shell am broadcast --user 10 -a work.integro.carling.DEV --es cmd $c "$@" >/dev/null; }
gear(){ curl -s -o /dev/null -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=$1"; }
# 탭: 화면 표시 좌표(2000 기준) → 실제 2560 좌표, 녹화 시작 기준 시각을 기록
T0=0
tap(){ local x=$(python3 -c "print(round($1*1.28))") y=$(python3 -c "print(round($2*1.28))"); [ "$T0" != 0 ] && echo "$(python3 -c "import time;print(round(time.time()-$T0,2))") $x $y" >> $V/$SCENE.taps; $A shell input tap $x $y; }
rec(){ SCENE=$1; dev hideDev --ez on true; sleep 0.3; rm -f $V/$SCENE.taps $V/$SCENE.webm; $A emu screenrecord start --fps 15 --bit-rate 6000000 $V/$SCENE.webm >/dev/null; sleep 0.6; T0=$(python3 -c "import time;print(time.time())"); }
stop(){ $A emu screenrecord stop >/dev/null; sleep 1.5; ffmpeg -v error -y -i $V/$SCENE.webm -c:v libx264 -preset fast -crf 16 -pix_fmt yuv420p $V/$SCENE.mp4; T0=0; echo "saved $SCENE $(ffprobe -v error -show_entries format=duration -of csv=p=0 $V/$SCENE.mp4)"; }
shot(){ $A exec-out screencap -p > $V/$1.png; }
