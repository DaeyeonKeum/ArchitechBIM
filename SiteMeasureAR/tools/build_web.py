"""웹 시제품(web/photo-qto.html)을 앱 화면(app/src/main/assets/web/index.html)으로 감싼다.
웹 시제품을 고친 뒤 이 스크립트를 돌리고 커밋하면 다음 APK 빌드에 반영된다.
    python3 tools/build_web.py
"""
import os, shutil
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(root, 'web')
dst = os.path.join(root, 'app', 'src', 'main', 'assets', 'web')
page = open(os.path.join(src, 'photo-qto.html'), encoding='utf-8').read()
head = ('<!doctype html><html lang="ko"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
        '<style>:root{color-scheme:light}body{margin:0;font:14px system-ui}img{max-width:100%}[hidden]{display:none!important}</style>'
        '<link rel="stylesheet" href="app.css"><script src="app-shim.js"></script></head><body>')
open(os.path.join(dst, 'index.html'), 'w', encoding='utf-8').write(head + page + '<script src="app-ui.js"></script></body></html>')
for d in ('samples', 'brand'):
    if os.path.isdir(os.path.join(src, d)):
        shutil.copytree(os.path.join(src, d), os.path.join(dst, d), dirs_exist_ok=True)
print('ok', len(page))
