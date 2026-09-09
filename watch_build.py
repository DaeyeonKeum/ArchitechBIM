# -*- coding: utf-8 -*-
"""
dashboard.html(메인 파일) / data/data.js / lib/* 중 하나라도 바뀌면 dashboard_single.html(배포용 단일파일)을
자동으로 다시 만들어주는 감시 스크립트입니다.

무엇을 위한 것인가
-------------------------------------------------------------------------------
dashboard.html 을 직접 고치는 개발/유지보수 작업을 할 때, 고칠 때마다 매번 손으로
`python build_single.py` 를 실행해 배포용 단일파일을 다시 만들어야 했습니다(안 하면
dashboard_single.html이 예전 코드로 남아 있는 상태가 됩니다). 이 스크립트를 켜두면
dashboard.html을 저장하는 즉시(약 1~2초 이내) dashboard_single.html이 그 내용 그대로
자동으로 다시 만들어집니다 — build_single.py를 따로 실행할 필요가 없습니다.
(엑셀 데이터 갱신 시 자동 반영은 이미 build_dashboard.py가 끝에서 build_single을 호출해
처리하고 있습니다 — 이 스크립트는 '코드(dashboard.html) 수정' 쪽을 자동화합니다.)

실행 방법
-------------------------------------------------------------------------------
  방법 1) 이 폴더의 `자동빌드_시작.bat` 더블클릭 (Windows, 가장 쉬움)
  방법 2) 콘솔에서 `python watch_build.py` 직접 실행

실행하면 창이 하나 뜨고 계속 켜져 있는 상태로 감시합니다 — 그 창을 닫거나 Ctrl+C를
누르기 전까지 계속 동작합니다. dashboard.html을 고치고 저장할 때마다 이 창에
"변경 감지 → 재생성 완료" 로그가 뜨는지 확인하면 됩니다. 평소 데이터 취합·조회
작업에는 필요 없고, dashboard.html 코드를 직접 고치는 작업을 할 때만 켜두면 됩니다.
"""
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_single  # noqa: E402  (HERE를 sys.path에 넣은 뒤에 임포트)

POLL_SEC = 1.5     # 몇 초마다 변경 여부를 확인할지
DEBOUNCE_SEC = 0.6  # 변경 감지 후 파일 저장이 끝날 때까지 살짝 기다리는 시간(저장 중간에 읽어 깨지는 것 방지)


def watched_files():
    files = [os.path.join(HERE, 'dashboard.html'), os.path.join(HERE, 'data', 'data.js')]
    lib_dir = os.path.join(HERE, 'lib')
    if os.path.isdir(lib_dir):
        files += [os.path.join(lib_dir, f) for f in os.listdir(lib_dir)]
    return files


def snapshot():
    snap = {}
    for f in watched_files():
        try:
            snap[f] = os.path.getmtime(f)
        except OSError:
            pass  # 파일이 아직 없거나 잠깐 사라진 경우(저장 중) — 다음 주기에 다시 확인
    return snap


def rebuild(reason):
    print(f"[{time.strftime('%H:%M:%S')}] 변경 감지({reason}) → dashboard_single.html 재생성 중…")
    try:
        build_single.build()
    except Exception as e:
        print(f"⚠ 재생성 실패: {e} — dashboard.html 저장이 끝난 뒤 자동으로 다시 시도됩니다.")


def main():
    print("=" * 70)
    print(" BIM 대쉬보드 — 배포용 단일파일(dashboard_single.html) 자동 갱신 감시")
    print(" 감시 대상: dashboard.html, data/data.js, lib/*")
    print(" 끄려면 이 창을 닫거나 Ctrl+C 를 누르세요.")
    print("=" * 70)
    rebuild("시작 시 최신화")
    last = snapshot()
    try:
        while True:
            time.sleep(POLL_SEC)
            cur = snapshot()
            if cur != last:
                time.sleep(DEBOUNCE_SEC)  # 저장이 완전히 끝나길 잠깐 기다렸다가
                cur = snapshot()          # 한 번 더 확인
                changed = sorted({os.path.basename(f) for f in set(cur) | set(last) if cur.get(f) != last.get(f)})
                rebuild(', '.join(changed) or '파일 목록 변경')
                last = cur
    except KeyboardInterrupt:
        print("\n감시를 종료합니다.")


if __name__ == '__main__':
    main()
