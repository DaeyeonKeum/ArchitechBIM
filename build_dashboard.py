# -*- coding: utf-8 -*-
"""
data/ 폴더의 주간 취합 Excel(*.xlsx)들을 읽어 data/data.js 로 변환합니다. (헤더명 기준 – 열 순서/양식 버전 무관)
  실행:  python build_dashboard.py            (data/ 폴더의 모든 xlsx 병합)
         python build_dashboard.py 파일1.xlsx 파일2.xlsx ...
대쉬보드(dashboard.html)는 data/data.js 를 읽어 표시하므로, 실행 후 HTML을 새로고침하면 반영됩니다.
"""
import sys, os, glob, json, re, datetime as dt
from openpyxl import load_workbook

HERE = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(HERE, "data")
OUT = {"프로젝트개요": "projects", "업무현황": "tasks", "인력현황": "staff", "인원": "members"}
HEADMAP = {
    "프로젝트개요": [("프로젝트ID", "id"), ("프로젝트명", "name"), ("구분", "category"), ("발주처", "client"), ("위치", "address"), ("위도", "lat"), ("경도", "lng"),
              ("규모", "scale"), ("착공", "start"), ("시작", "start"), ("준공", "end"), ("종료", "end"), ("BIM적용", "scope"), ("BIM섹션", "enc"), ("건축BIM", "enc"),
              ("현장담당", "field"), ("A&C", "anc"), ("IFC경로", "ifcPath"), ("IFC파일", "ifc"), ("프로젝트개요", "overview"), ("개요", "overview"), ("이슈", "issue")],
    "업무현황": [("주차", "week"), ("프로젝트ID", "pid"), ("업무항목", "task"), ("세부내용", "detail"), ("시작", "start"), ("종료", "end"), ("진행률", "progress"),
             ("BIM섹션", "enc"), ("건축BIM", "enc"), ("담당자(E&C)", "enc"), ("현장담당", "field"), ("A&C", "anc"), ("MD", "md"), ("상태", "status"), ("이슈", "issue"), ("비고", "issue")],
    "인원": [("이름", "name"), ("성명", "name"), ("소속", "org"), ("구분", "org"), ("회사", "org"), ("직급", "rank"), ("직위", "rank"), ("비고", "note")],
    "인력현황": [("주차", "week"), ("날짜", "week"), ("이름", "name"), ("소속", "org"), ("직급", "rank"), ("담당프로젝트", "projects"), ("출장", "trip"), ("야근", "overtime"),
             ("주말", "weekend"), ("연차", "leave"), ("비고", "note")],
}
DEFAULT_MEMBERS = [("금대연", "POSCO E&C", "리더"), ("여민규", "POSCO E&C", "과장"), ("강보희", "POSCO E&C", "과장"), ("김재순", "POSCO E&C", "대리"), ("조범현", "POSCO A&C", "차장"), ("문우빈", "POSCO A&C", "대리"), ("김두하", "POSCO A&C", "대리"), ("신동환", "POSCO A&C", "대리"), ("백지환", "POSCO A&C", "대리"), ("강진훈", "POSCO A&C", "사원"), ("권은주", "POSCO A&C", "사원")]
DAYN = ["월", "화", "수", "목", "금", "토", "일"]; DAYK = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
NUM = {"lat", "lng", "progress", "md", "trip", "overtime", "weekend", "leave"}
FLAG = {"trip", "overtime", "weekend", "leave"}
DATE = {"week", "start", "end"}


def header_keys(sheet, header):
    has_section = any(re.search(r"BIM섹션|건축BIM", str(h or "")) for h in header)
    keys = []
    for h in header:
        h = re.sub(r"\s", "", str(h or "")); key = None
        if sheet == "인력현황" and re.sub(r"\(|\)|요일", "", h) in DAYN:
            keys.append(DAYK[DAYN.index(re.sub(r"\(|\)|요일", "", h))]); continue
        if h:
            for pat, k in HEADMAP[sheet]:
                if pat in h:
                    key = "enc" if (k == "field" and not has_section) else k; break
        keys.append(key)
    return keys


def norm(v, key):
    if v is None or v == "" or v == "-":
        return None
    if key in DATE:
        if isinstance(v, (dt.datetime, dt.date)):
            return v.strftime("%Y-%m-%d")
        s = str(v).strip().replace(".", "-").replace("/", "-")
        return s[:10] if s else None
    if key in NUM:
        try:
            return float(v)
        except (TypeError, ValueError):
            s = str(v).strip()
            return 1.0 if (key in FLAG and re.fullmatch(r"[oOvVxX○●√✓]|예|유", s)) else None
    return str(v).strip()


def read_file(path, out):
    wb = load_workbook(path, data_only=True)
    for sheet in OUT:
        alias = {"인원": ["인원", "명단", "기본인원", "인원리스트", "인력리스트"]}.get(sheet, [sheet])
        name = next((n for n in wb.sheetnames if n.replace(" ", "") == sheet), None) or next((n for n in wb.sheetnames if n.replace(" ", "") != "인력현황" and any(a in n.replace(" ", "") for a in alias)), None)
        if not name:
            continue
        rows = list(wb[name].iter_rows(values_only=True))
        if not rows:
            continue
        keys = header_keys(sheet, rows[0])
        for row in rows[1:]:
            s0 = str(row[0] or "")
            if any(k in s0 for k in ("YYYY", "고유", "해당 주", "기본 인원", "인원 시트")):
                continue
            rec = {}
            for j, k in enumerate(keys):
                if k and rec.get(k) is None:
                    rec[k] = norm(row[j] if j < len(row) else None, k)
            if sheet == "프로젝트개요":
                if not rec.get("name") and not rec.get("id"):
                    continue
                rec.setdefault("id", None); rec["id"] = rec["id"] or rec["name"]
                rec["category"] = rec.get("category") or "PJT지원"
            elif sheet == "업무현황":
                if not rec.get("week") or not rec.get("task"):
                    continue
                rec["pid"] = rec.get("pid") or "(미지정)"
            elif sheet == "인원":
                if not rec.get("name"):
                    continue
            elif not rec.get("week") or not rec.get("name"):
                continue
            else:
                days = [str(rec.get(k) or "").strip() for k in DAYK]
                if any(days):  # (구) 요일 코드 입력형
                    cnt = lambda c: sum(1 for d in days if c in d)
                    rec["trip"], rec["overtime"], rec["weekend"], rec["leave"] = cnt("출"), cnt("야"), cnt("주"), cnt("연") + cnt("반") * 0.5
                else:  # 이벤트(날짜)형: 날짜의 요일 위치에 코드 기록
                    d = dt.date.fromisoformat(rec["week"]); wd = d.weekday(); rec["date"] = rec["week"]
                    codes = []
                    for k, c in (("trip", "출장"), ("overtime", "야근"), ("weekend", "주말"), ("leave", "연차")):
                        rec[k] = rec.get(k) or 0
                        if rec[k]: codes.append("반차" if k == "leave" and rec[k] == 0.5 else c)
                    days = [""] * 7; days[wd] = ",".join(codes)
                    rec["week"] = (d - dt.timedelta(days=wd)).strftime("%Y-%m-%d")
                rec["days"] = days
            out[OUT[sheet]].append(rec)


def merge(recs, keyfn):
    d = {}
    for r in recs:
        d[keyfn(r)] = r
    return list(d.values())


def warn_duplicate_project_ids(projects):
    """프로젝트ID가 같은 행이 여러 개면 merge()가 마지막 값만 남기고 나머지는 조용히
    사라진다 (프로젝트명이 뒤바뀌거나 업무현황이 엉뚱한 프로젝트에 붙는 사고의 근본 원인).
    데이터 병합 직전에 충돌을 콘솔에 크게 경고해 사람이 엑셀에서 바로 고칠 수 있게 한다."""
    by_id = {}
    for p in projects:
        by_id.setdefault(p.get("id"), []).append(p)
    dups = {pid: ps for pid, ps in by_id.items() if len(ps) > 1}
    if not dups:
        return
    print("=" * 60)
    print(f"⚠ 경고: 프로젝트ID가 중복된 행이 {len(dups)}건 있습니다 — 마지막 행만 반영되고 나머지는 사라집니다!")
    for pid, ps in dups.items():
        names = " / ".join(str(p.get("name") or "(이름없음)") for p in ps)
        print(f"   - ID '{pid}' ({len(ps)}건): {names}  → 최종 반영: '{ps[-1].get('name')}'")
    print("   → data/*.xlsx 의 '프로젝트개요' 시트에서 프로젝트ID를 서로 다르게 지정해 주세요.")
    print("=" * 60)


def build(files):
    out = {"projects": [], "tasks": [], "staff": [], "members": []}
    for f in files:
        print("read:", os.path.basename(f))
        read_file(f, out)
    warn_duplicate_project_ids(out["projects"])
    out["projects"] = merge(out["projects"], lambda r: r["id"])
    # '수행과제'/'기타업무'처럼 업무항목 값이 같은 값을 재사용하고 세부내용이 비어있거나 겹치는 경우가 있어
    # (주차,프로젝트,업무항목,세부내용)만으로는 서로 다른 두 업무가 같은 키로 충돌해 하나가 덮어써져 사라질 수 있다.
    # 일정/담당자/진행률/MD/상태까지 키에 포함해 서로 다른 업무는 절대 같은 키가 되지 않도록 한다.
    out["tasks"] = merge(out["tasks"], lambda r: (r["week"], r["pid"], r["task"], r.get("detail") or "", r.get("start") or "", r.get("end") or "", r.get("enc") or "", r.get("anc") or "", r.get("progress"), r.get("md"), r.get("status") or ""))
    out["staff"] = merge(out["staff"], lambda r: (r.get("date") or r["week"], r["name"], r.get("trip"), r.get("overtime"), r.get("weekend"), r.get("leave"), r.get("note")))
    agg = {}
    for r in out["staff"]:  # 같은 주·같은 사람 행 합치기 (날짜별 이벤트 행 → 주 단위)
        k = (r["week"], r["name"]); a = agg.get(k)
        if not a:
            agg[k] = dict(r); continue
        a["days"] = [",".join(x for x in (p, q) if x) for p, q in zip(a["days"], r["days"])]
        for f in FLAG: a[f] = (a.get(f) or 0) + (r.get(f) or 0)
        if r.get("note") and r["note"] not in (a.get("note") or ""): a["note"] = ((a.get("note") or "") + " / " + r["note"]).strip(" /")
        for f in ("org", "rank", "projects"):
            if not a.get(f) and r.get(f): a[f] = r[f]
    out["staff"] = list(agg.values())
    out["members"] = merge(out["members"], lambda r: r["name"])
    if not out["members"]:  # 인원 시트 없으면 인력현황에 입력된 인원(등장 순), 그것도 없으면 내장 기본
        seen = {}
        for s in out["staff"]:
            if s["name"] not in seen: seen[s["name"]] = dict(name=s["name"], org=s.get("org"), rank=s.get("rank"))
        out["members"] = list(seen.values()) or [dict(name=n, org=o, rank=k) for n, o, k in DEFAULT_MEMBERS]
    mem = {m["name"]: m for m in out["members"]}
    for s in out["staff"]:  # 소속/직급 자동 (수식 셀은 값이 없을 수 있음)
        m = mem.get(s["name"])
        if m:
            if not s.get("org") or str(s.get("org")).startswith("="): s["org"] = m.get("org")
            if not s.get("rank") or str(s.get("rank")).startswith("="): s["rank"] = m.get("rank")
    ids = {p["id"] for p in out["projects"]}
    by_name = {re.sub(r"\s", "", str(p.get("name") or "")): p["id"] for p in out["projects"]}
    for t in out["tasks"]:
        if t["pid"] not in ids:  # 프로젝트명으로 입력된 ID 보정
            nid = by_name.get(re.sub(r"\s", "", str(t["pid"])))
            if nid:
                t["pid"] = nid
            else:
                cat = "수행과제" if re.search(r"수행과제|기타", str(t.get("task") or "")) else "PJT지원"
                out["projects"].append({"id": t["pid"], "name": t["pid"], "category": cat}); ids.add(t["pid"]); by_name[re.sub(r"\s", "", t["pid"])] = t["pid"]
    out["generated"] = dt.datetime.now().strftime("%Y-%m-%d %H:%M")
    out["sources"] = [os.path.basename(f) for f in files]
    return out


if __name__ == "__main__":
    files = sys.argv[1:] or sorted(glob.glob(os.path.join(DATA_DIR, "*.xlsx")))
    files = [f for f in files if "템플릿" not in os.path.basename(f) and not os.path.basename(f).startswith("~$")]
    if not files:
        sys.exit("xlsx 파일이 없습니다: " + DATA_DIR)
    data = build(files)
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(os.path.join(DATA_DIR, "data.js"), "w", encoding="utf-8") as fp:
        fp.write("window.DASH_DATA = " + json.dumps(data, ensure_ascii=False) + ";\n")
    print(f"완료: projects {len(data['projects'])}, tasks {len(data['tasks'])}, staff {len(data['staff'])} → data/data.js")

    # data.js 를 갱신할 때마다 공유용 단일 파일(dashboard_single.html)도 함께 최신화한다.
    # → dashboard_single.html 을 이메일/NAS 등으로 "따로" 보내도 옛날 정보가 아니라
    #   방금 취합한 최신 정보가 반영된 상태로 나가도록, 두 단계를 한 번에 묶어둔 것.
    try:
        import build_single
        build_single.build()
    except Exception as e:
        print(f"⚠ dashboard_single.html 자동 갱신 실패({e}) — 별도로 `python build_single.py` 를 실행해 주세요.")
