"""웹 시제품(photo-qto.html)으로 아이패드 홈 화면 앱(ai-qto/)을 만든다.

    python3 ai-qto/_build/build_ipad.py <photo-qto.html 경로>

photo-qto.html 옆의 brand/, samples/ 폴더도 함께 복사한다.
만든 뒤 main 브랜치에 커밋하면 GitHub Pages(…/ArchitechBIM/ai-qto/)에 반영된다.
"""
import os, re, shutil, sys, time

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.dirname(here)
src = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else sys.exit(__doc__)
page = open(src, encoding='utf-8').read()

# 사진 높이: 아이패드 가로 2단에서는 화면을 더 크게 쓴다 (ipad.js의 __qtoPhotoH)
old = '(window.innerHeight||720)*0.48'
if old not in page:
    sys.exit('사진 높이 계산식을 찾지 못했습니다: ' + old)
page = page.replace(old, '(window.innerHeight||720)*(window.__qtoPhotoH?window.__qtoPhotoH():0.48)')

build = time.strftime('%Y%m%d%H%M')
head = ('<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover,user-scalable=no">'
        '<meta name="apple-mobile-web-app-capable" content="yes">'
        '<meta name="mobile-web-app-capable" content="yes">'
        '<meta name="apple-mobile-web-app-title" content="AI 물량">'
        '<meta name="apple-mobile-web-app-status-bar-style" content="default">'
        '<meta name="theme-color" content="#2f62f5">'
        '<meta name="format-detection" content="telephone=no">'
        '<link rel="manifest" href="manifest.webmanifest">'
        '<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">'
        '<link rel="icon" type="image/png" sizes="192x192" href="icons/icon-192.png">'
        '<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}'
        'body{margin:0;font:14px system-ui}img{max-width:100%}[hidden]{display:none!important}</style>'
        '<link rel="stylesheet" href="ipad.css?v=' + build + '"><script src="ipad.js?v=' + build + '"></script></head><body>')
open(os.path.join(out, 'index.html'), 'w', encoding='utf-8').write(head + page + '</body></html>')

sw = open(os.path.join(here, 'sw.template.js'), encoding='utf-8').read().replace('__BUILD__', build)
open(os.path.join(out, 'sw.js'), 'w', encoding='utf-8').write(sw)

for d in ('brand', 'samples'):
    s = os.path.join(os.path.dirname(src), d)
    if os.path.isdir(s):
        shutil.copytree(s, os.path.join(out, d), dirs_exist_ok=True)
print('ok', build, len(page))
