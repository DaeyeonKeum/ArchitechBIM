# -*- coding: utf-8 -*-
"""dashboard.html + lib/ + data/data.js 를 하나의 파일(dashboard_single.html)로 합칩니다.
   NAS 웹 미리보기, 메일 첨부, 외부(사외) 공유 등 lib/ 폴더를 함께 둘 수 없는 상황용. (약 9MB)

   ⚠ 외부 공유용 조회 전용 파일이므로 '데이터 입력'(편집) 탭은 자동으로 제외됩니다.
   ⚠ data/data.js 를 그 시점 그대로 내장하는 "스냅샷"입니다. 데이터를 갱신한 뒤에는
      반드시 다시 실행해야 최신 정보가 반영됩니다 — 이 파일을 build_dashboard.py 맨 끝에서
      자동으로 호출하도록 연결해두었으니, 평소에는 build_dashboard.py 한 번만 실행하면 됩니다.
   실행: python build_single.py   (단독 실행 시 data/data.js 를 현재 상태 그대로 내장)
"""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
rd = lambda p: open(os.path.join(HERE, p), encoding='utf-8').read()
safe = lambda js: js.replace('</script', '<\\/script')  # 인라인 스크립트 종료 방지

# 외부 공유용에서 제외할 '데이터 입력' 탭 버튼과 그 화면 섹션
EDIT_TAB = '<div class="tab" data-v="edit">데이터 입력</div>'
EDIT_SECTION_START = '<section class="view" id="v-edit">'


def build():
    html = rd('dashboard.html')
    html = html.replace('<link rel="stylesheet" href="lib/leaflet.css">', '<style>' + rd('lib/leaflet.css') + '</style>')
    for lib in ['xlsx.full.min.js', 'chart.umd.min.js', 'leaflet.js', 'three.min.js']:
        html = html.replace(f'<script src="lib/{lib}"></script>', f'<script>/* {lib} */\n' + safe(rd('lib/' + lib)) + '\n</script>')
    data = rd('data/data.js') if os.path.exists(os.path.join(HERE, 'data/data.js')) else 'window.DASH_DATA=null;'
    html = html.replace('<script src="data/data.js"></script>', '<script>/* data.js */\n' + safe(data) + '\n</script>')

    # 외부 공유용: '데이터 입력' 탭(편집 화면)은 조회 전용 단일 파일에서 제외
    # (dashboard.html 쪽 스크립트는 이 탭의 요소가 없어도 안전하도록 on() 헬퍼로 방어되어 있음)
    removed_edit = False
    if EDIT_TAB in html:
        html = html.replace(EDIT_TAB, ''); removed_edit = True
    sec_start = html.find(EDIT_SECTION_START)
    if sec_start != -1:
        sec_end = html.index('</section>', sec_start) + len('</section>')
        html = html[:sec_start] + html[sec_end:]; removed_edit = True

    # web-ifc 는 지연 로드용 text/plain 으로 내장 (필요할 때만 실행)
    emb = ''.join(f'<script type="text/plain" data-lib="{lib}">' + safe(rd('lib/' + lib)) + '</script>\n' for lib in ['web-ifc-api-iife.js', 'web-ifc-wasm.js'])
    # 주의: xlsx.full.min.js 자체 소스 안에 문자열로 '</body>' 가 들어있어 html.replace()를 그냥 쓰면
    # (기본으로 전부 치환되므로) 실제 </body> 태그뿐 아니라 그 안에도 한 번 더 끼워 넣어져 파일이 깨진다.
    # → 실제 문서 끝의 마지막 </body> 위치에만 삽입한다.
    body_idx = html.rindex('</body>')
    html = html[:body_idx] + emb + html[body_idx:]
    out = os.path.join(HERE, 'dashboard_single.html')
    open(out, 'w', encoding='utf-8').write(html)
    print(f"완료: {out} ({os.path.getsize(out)/1e6:.1f} MB) — data/data.js 최신 내용 반영"
          + (", '데이터 입력' 탭 제외(조회 전용)" if removed_edit else ", ⚠ '데이터 입력' 탭을 찾지 못해 제외하지 못했습니다"))
    return out


if __name__ == '__main__':
    build()
