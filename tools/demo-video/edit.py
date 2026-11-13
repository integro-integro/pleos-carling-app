#!/usr/bin/env python3
"""마이카링 서비스 시연 영상 편집: 에뮬레이터 녹화 → 자막·제목·확대·탭 표시·TTS 내레이션 → mp4."""
import json, os, subprocess, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

V = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(V, 'out'); os.makedirs(OUT, exist_ok=True)
FONT = '/System/Library/Fonts/AppleSDGothicNeo.ttc'
W, H, FPS = 1920, 1080, 30
BG = (23, 21, 58)
# 11/13: Pleos 전체 화면(2560×1440)을 그대로 1920×1080으로 보여 준다
APP = (0, 0, 2560, 1440)
SCALE = W / APP[2]
APP_W, APP_H = W, H
APP_X, APP_Y = 0, 0
VOICE = os.environ.get('VOICE', 'com.apple.eloquence.ko-KR.Reed')
RATE = os.environ.get('RATE', '165')

def f(size, w=6):
    return ImageFont.truetype(FONT, size, index=w)

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(' '.join(cmd)); print(r.stderr[-2000:]); sys.exit(1)
    return r.stdout

def dur(path):
    return float(run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path]).strip())

def rounded(draw, box, r, fill):
    draw.rounded_rectangle(box, r, fill=fill)

def caption_png(path, text, tag=None, sub=None, zoom=False):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if text:
        ft = f(40, 6)
        fs = f(28, 4)
        tw = d.textlength(text, font=ft)
        sw = d.textlength(sub, font=fs) + 40 if sub else 0
        bw = tw + 70 + (sw + 18 if sub else 0); bh = 84
        x0 = (W - bw) / 2; y0 = H - bh - (24 if not zoom else 40)
        rounded(d, (x0, y0, x0 + bw, y0 + bh), 26, (20, 18, 48, 225))
        x = x0 + 35
        if sub:
            rounded(d, (x - 12, y0 + 18, x - 12 + sw, y0 + bh - 18), 20, (140, 124, 240, 255))
            d.text((x - 12 + sw / 2, y0 + bh / 2), sub, font=fs, fill='white', anchor='mm')
            x += sw + 6
        d.text((x, y0 + bh / 2), text, font=ft, fill=(255, 255, 255, 255), anchor='lm')
    if tag:
        ft = f(30, 6)
        tw = d.textlength(tag, font=ft) + 44
        rounded(d, (36, 30, 36 + tw, 86), 28, (47, 191, 143, 235))
        d.text((36 + tw / 2, 58), tag, font=ft, fill='white', anchor='mm')
    im.save(path)

def title_png(path, lines, small=None):
    im = Image.new('RGB', (W, H), BG)
    # 은은한 라벤더 빛
    glow = Image.new('L', (W, H), 0)
    ImageDraw.Draw(glow).ellipse((W * 0.18, H * 0.05, W * 0.82, H * 0.95), fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    im = Image.composite(Image.new('RGB', (W, H), (92, 78, 190)), im, glow)
    d = ImageDraw.Draw(im)
    total = len(lines)
    for i, (txt, size, weight, color) in enumerate(lines):
        y = H / 2 + (i - (total - 1) / 2) * (size * 1.55)
        d.text((W / 2, y), txt, font=f(size, weight), fill=color, anchor='mm')
    if small:
        d.text((W / 2, H - 70), small, font=f(26, 2), fill=(200, 196, 235), anchor='mm')
    im.save(path)

def ring_png(path):
    s = 120
    im = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((10, 10, s - 10, s - 10), outline=(255, 255, 255, 240), width=8)
    d.ellipse((22, 22, s - 22, s - 22), fill=(140, 124, 240, 120))
    im.save(path)

NARR_DIR = os.environ.get('NARR_DIR')

def tts(text, path, idx=None):
    # 미리 만든 AI 성우 음성(narr/n{번호}.wav)이 있으면 그것을 쓴다
    src = next((os.path.join(NARR_DIR, f'n{idx}.{e}') for e in ('wav', 'm4a') if NARR_DIR and idx is not None and os.path.exists(os.path.join(NARR_DIR, f'n{idx}.{e}'))), None)
    if src:
        run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-af', 'silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,loudnorm=I=-16:TP=-1.5', '-ar', '48000', '-ac', '2', path])
        return dur(path)
    aiff = path.replace('.wav', '.aiff')
    run(['say', '-v', VOICE, '-r', RATE, '-o', aiff, text])
    run(['ffmpeg', '-v', 'error', '-y', '-i', aiff, '-ar', '48000', '-ac', '2', path])
    return dur(path)

def taps_of(src):
    p = os.path.join(V, src.replace('.mp4', '.taps'))
    if not os.path.exists(p): return []
    out = []
    for line in open(p):
        t, x, y = line.split(); out.append((float(t), int(x), int(y)))
    return out

RING = os.path.join(OUT, 'ring.png')
ring_png(RING)

def render(i, seg):
    name = os.path.join(OUT, f'seg{i:02d}.mp4')
    narr = seg.get('narr', '')
    wav = os.path.join(OUT, f'n{i:02d}.wav')
    nd = tts(narr, wav, i) if narr else 0.0
    T = max(nd + 0.7, seg.get('min', 0))
    cap = os.path.join(OUT, f'c{i:02d}.png')
    caption_png(cap, seg.get('cap'), seg.get('tag'), seg.get('sub'), bool(seg.get('zoom')))
    audio = ['-i', wav] if narr else ['-f', 'lavfi', '-t', str(T), '-i', 'anullsrc=r=48000:cl=stereo']
    if seg['kind'] == 'title':
        png = os.path.join(OUT, f't{i:02d}.png'); title_png(png, seg['lines'], seg.get('small'))
        vf = f"[0:v]fps={FPS},format=yuv420p,fade=t=in:st=0:d=0.4,fade=t=out:st={T-0.4:.2f}:d=0.4[v]"
        cmd = ['ffmpeg', '-v', 'error', '-y', '-loop', '1', '-t', f'{T:.2f}', '-i', png] + audio + [
            '-filter_complex', vf + f";[1:a]adelay=300|300,apad,atrim=0:{T:.2f}[a]", '-map', '[v]', '-map', '[a]']
    else:
        src = os.path.join(V, seg['src']); ss, to = seg['ss'], seg['to']
        L = to - ss
        speed = L / T if L > T else 1.0
        pad = max(0.0, T - L) if L <= T else 0.0
        chain = f"[0:v]fps={FPS},setpts=PTS-STARTPTS"
        if speed != 1.0: chain += f",setpts=PTS/{speed:.4f}"
        if pad > 0: chain += f",tpad=stop_mode=clone:stop_duration={pad:.2f}"
        z = seg.get('zoom')
        if z:
            x, y, w, h = z
            chain += f",crop={w}:{h}:{x}:{y},scale={W}:{H}:flags=lanczos[base]"
        else:
            x, y, w, h = APP
            chain += f",crop={w}:{h}:{x}:{y},scale={APP_W}:{APP_H}:flags=lanczos[app];color=c=0x{BG[0]:02x}{BG[1]:02x}{BG[2]:02x}:s={W}x{H}:r={FPS}:d={T:.2f}[bg];[bg][app]overlay={APP_X}:{APP_Y}:shortest=1[base]"
        chain += ";[base][2:v]overlay=0:0[v1]"
        last = 'v1'; extra = []
        # 탭 표시: 원래 속도로 쓰는 전체 화면 장면만
        if not z and speed == 1.0 and seg.get('taps', True):
            n = 3
            for (t, tx, ty) in taps_of(seg['src']):
                tt = t - ss - 0.1
                if tt < 0 or tt > T: continue
                ox = APP_X + (tx - APP[0]) * SCALE - 60; oy = APP_Y + (ty - APP[1]) * SCALE - 60
                extra += ['-loop', '1', '-t', f'{T:.2f}', '-i', RING]
                chain += f";[{last}][{n}:v]overlay={ox:.0f}:{oy:.0f}:enable='between(t,{tt:.2f},{tt+0.55:.2f})'[r{n}]"
                last = f'r{n}'; n += 1
        chain += f";[{last}]format=yuv420p[v];[1:a]adelay=300|300,apad,atrim=0:{T:.2f}[a]"
        cmd = ['ffmpeg', '-v', 'error', '-y', '-ss', f'{ss:.2f}', '-t', f'{L:.2f}', '-i', src] + audio + ['-loop', '1', '-t', f'{T:.2f}', '-i', cap] + extra + [
            '-filter_complex', chain, '-map', '[v]', '-map', '[a]']
    if seg['kind'] == 'title':
        # 제목 카드에는 자막 PNG가 필요 없다
        pass
    cmd += ['-t', f'{T:.2f}', '-r', str(FPS), '-c:v', 'libx264', '-preset', 'medium', '-crf', '19', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k', '-ar', '48000', name]
    run(cmd)
    print(f'seg{i:02d} {T:5.1f}s  {seg.get("cap") or seg.get("kind")}', flush=True)
    return name, T

if __name__ == '__main__':
    segs = json.load(open(os.path.join(V, 'segments.json')))
    only = set(int(a) for a in sys.argv[1:])
    files = []; total = 0
    for i, s in enumerate(segs):
        name = os.path.join(OUT, f'seg{i:02d}.mp4')
        if only and i not in only and os.path.exists(name):
            files.append(name); total += dur(name); continue
        n, T = render(i, s); files.append(n); total += T
    lst = os.path.join(OUT, 'list.txt')
    open(lst, 'w').write(''.join(f"file '{p}'\n" for p in files))
    final = os.path.join(V, 'mycarling-demo.mp4')
    run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', lst, '-c', 'copy', '-movflags', '+faststart', final])
    print(f'완성: {final}  {dur(final):.1f}s')
