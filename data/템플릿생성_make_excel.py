# -*- coding: utf-8 -*-
"""주간 취합용 Excel 템플릿 + 샘플 데이터 생성"""
import datetime as dt
import random
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

random.seed(7)
HDR_FILL = PatternFill("solid", fgColor="0B2A55")
HDR_FONT = Font(bold=True, color="FFFFFF", size=10)
NOTE_FONT = Font(italic=True, color="666666", size=9)
THIN = Side(style="thin", color="BFC7D5")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)

SHEETS = {
    "프로젝트개요": [
        ("프로젝트ID", 12, "고유 ID (예: P01, T01). 다른 시트의 프로젝트ID와 동일하게 입력"),
        ("프로젝트명", 28, ""),
        ("구분", 10, "PJT지원 / 수행과제 / 기타업무"),
        ("발주처", 14, ""),
        ("위치(주소)", 26, "지도 표시용 주소"),
        ("위도", 10, "소수점 좌표 (예: 37.3861)"),
        ("경도", 10, "소수점 좌표 (예: 126.6390)"),
        ("규모", 24, "연면적, 층수, 세대수 등"),
        ("착공(시작)", 12, "YYYY-MM-DD"),
        ("준공(종료)", 12, "YYYY-MM-DD"),
        ("BIM적용범위", 26, ""),
        ("현장담당(E&C)", 14, "현장 담당 엔지니어 (쉼표로 여러 명)"),
        ("BIM섹션담당(E&C)", 16, "건축BIM섹션 담당자 (쉼표로 여러 명)"),
        ("작업담당(A&C)", 14, "POSCO A&C 작업 담당자 (쉼표로 여러 명)"),
        ("IFC경로", 34, "IFC 파일이 있는 폴더 경로 (공유폴더 가능, 예: \\\\server\\BIM\\P01)"),
        ("IFC파일명", 24, "파일명 (여러 개면 쉼표 구분, 첫 파일이 기본)"),
        ("프로젝트개요", 50, "팝업 개요 문단"),
    ],
    "업무현황": [
        ("주차", 12, "해당 주 월요일 날짜 (YYYY-MM-DD)"),
        ("프로젝트ID", 12, "프로젝트개요 시트의 ID"),
        ("프로젝트명", 26, "자동 표시 (프로젝트ID 입력 시) – 수정 불필요"),
        ("업무항목", 26, "세부 수행 업무명 (동일 업무는 매주 같은 이름 유지)"),
        ("세부내용", 44, "금주 수행 내용"),
        ("시작일", 12, "YYYY-MM-DD"),
        ("종료일", 12, "YYYY-MM-DD"),
        ("진행률(%)", 10, "0~100"),
        ("현장담당(E&C)", 14, "현장 담당 (쉼표로 여러 명)"),
        ("BIM섹션담당(E&C)", 16, "건축BIM섹션 담당 (쉼표로 여러 명)"),
        ("작업담당(A&C)", 14, "A&C 작업 담당 (쉼표로 여러 명)"),
        ("투입MD", 9, "금주 투입 Man-day (BIM섹션+A&C 담당 합계)"),
        ("상태", 9, "예정 / 진행 / 완료 / 지연"),
        ("이슈사항", 34, "금주 이슈/요청/지연사유 → 대쉬보드 '주요 이슈'에 표시"),
    ],
    "인원": [
        ("이름", 10, "기본 인원 (대쉬보드 인력현황 순서)"),
        ("소속", 12, "POSCO E&C / POSCO A&C"),
        ("직급", 10, ""),
        ("비고", 30, ""),
    ],
    "인력현황": [
        ("날짜", 12, "이벤트 발생 날짜 (YYYY-MM-DD) – 요일·주차는 대쉬보드가 자동 계산"),
        ("이름", 10, "인원 시트의 이름"),
        ("소속", 12, "자동 (인원 시트)"),
        ("직급", 10, "자동 (인원 시트)"),
        ("출장", 8, "해당하면 1 (없으면 비움=0)"),
        ("야근", 8, "해당하면 1"),
        ("주말근무", 9, "해당하면 1"),
        ("연차", 8, "1 (반차 0.5)"),
        ("비고", 30, "출장지, 특이사항 등"),
        ("담당프로젝트", 22, "(선택) 프로젝트ID 쉼표 구분 – 비우면 업무현황에서 자동 집계"),
    ],
}

GUIDE = [
    "건축BIM섹션 주간 업무현황 취합 Excel – 작성 안내",
    "",
    "1. 배포/취합 주기 : 매주 금요일 각 담당자가 본인 행을 작성하여 제출 → 담당자(취합)가 data 폴더에 모아 build_dashboard.py 실행 (또는 대쉬보드에 파일 드래그)",
    "2. 프로젝트개요 : 프로젝트 1건당 1행. 새 프로젝트/과제가 생기면 행 추가 (ID는 중복 금지). 대쉬보드에 자동 반영됩니다.",
    "3. 업무현황 : 프로젝트별 세부 업무 1건당 1행, 매주 새 행으로 추가 (진행률·투입MD 갱신). 같은 업무는 '업무항목' 명칭을 동일하게 유지하면 간트차트가 최신 상태로 갱신됩니다.",
    "4. 인원 : 기본 인원 명단 (E&C 4명, A&C 7명). 인원 변동 시 이 시트만 수정하면 대쉬보드 인력현황에 반영됩니다.",
    "4-1. 인력현황 : 출장/야근/주말근무/연차가 발생한 날짜마다 1행 (날짜·이름 입력 → 소속/직급 자동). 해당 칸에 1 입력(연차 반차는 0.5), 비어 있으면 0. 대쉬보드가 날짜의 요일과 주차를 자동 계산해 월~일 표에 표시하고 일수를 집계합니다.",
    "5. 주차 : 해당 주 월요일 날짜를 YYYY-MM-DD 로 입력 (예: 2026-08-31). 대쉬보드 상단 주차 선택 및 추이 그래프 기준.",
    "6. 담당자 이름은 인력현황 시트의 이름과 동일하게 입력 (인원별 담당 프로젝트 자동 집계). 업무현황의 프로젝트명 열은 프로젝트ID를 입력하면 자동 표시됩니다.",
    "7. IFC경로/IFC파일명 : 프로젝트별 IFC 폴더 경로와 파일명. 대쉬보드에서 해당 폴더를 한 번 연결(또는 파일 지정)하면 이후 자동으로 열립니다. 여러 파일은 쉼표 구분.",
    "7-1. 담당 구분 : 현장담당(E&C 현장 엔지니어) / BIM섹션담당(건축BIM섹션 E&C) / 작업담당(POSCO A&C). 투입MD는 BIM섹션담당+작업담당에게 배분됩니다.",
    "7-2. 이슈사항 : 업무현황의 이슈사항 열이 대쉬보드 '금주 주요 이슈'와 프로젝트 팝업에 표시됩니다.",
    "8. 여러 담당자 파일을 취합할 때 프로젝트개요는 ID 기준, 업무현황은 주차+프로젝트ID+업무항목 기준, 인력현황은 주차+이름 기준으로 병합(뒤에 읽은 파일 우선)됩니다.",
]


def write_sheet(ws, cols, rows):
    for c, (name, width, note) in enumerate(cols, 1):
        cell = ws.cell(row=1, column=c, value=name)
        cell.fill, cell.font = HDR_FILL, HDR_FONT
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = BORDER
        n = ws.cell(row=2, column=c, value=note)
        n.font = NOTE_FONT
        n.alignment = Alignment(wrap_text=True, vertical="top")
        ws.column_dimensions[get_column_letter(c)].width = width
    ws.row_dimensions[1].height = 24
    ws.row_dimensions[2].height = 30
    for r, row in enumerate(rows, 3):
        for c, v in enumerate(row, 1):
            cell = ws.cell(row=r, column=c, value=v)
            cell.border = BORDER
            cell.alignment = Alignment(vertical="top", wrap_text=isinstance(v, str) and len(v) > 20)
            if isinstance(v, dt.date):
                cell.number_format = "yyyy-mm-dd"
    ws.freeze_panes = "A3"
    ws.auto_filter.ref = f"A1:{get_column_letter(len(cols))}{max(3, len(rows) + 2)}"


def add_validations(wb):
    dv = DataValidation(type="list", formula1='"PJT지원,수행과제,기타업무"', allow_blank=True)
    wb["프로젝트개요"].add_data_validation(dv); dv.add("C3:C500")
    dv2 = DataValidation(type="list", formula1='"예정,진행,완료,지연"', allow_blank=True)
    wb["업무현황"].add_data_validation(dv2); dv2.add("M3:M2000")
    dv4 = DataValidation(type="list", formula1='"1,0.5"', allow_blank=True, showErrorMessage=False)
    wb["인력현황"].add_data_validation(dv4); dv4.add("E3:H2000")
    dv5 = DataValidation(type="list", formula1="=인원!$A$3:$A$50", allow_blank=True, showErrorMessage=False)
    wb["인력현황"].add_data_validation(dv5); dv5.add("B3:B2000")
    dv3 = DataValidation(type="list", formula1='"POSCO E&C,POSCO A&C"', allow_blank=True)
    wb["인력현황"].add_data_validation(dv3); dv3.add("C3:C2000")


def build(path, data):
    wb = Workbook()
    ws = wb.active
    ws.title = "사용안내"
    for i, line in enumerate(GUIDE, 1):
        c = ws.cell(row=i, column=1, value=line)
        if i == 1:
            c.font = Font(bold=True, size=14, color="0B2A55")
    ws.column_dimensions["A"].width = 150
    for name, cols in SHEETS.items():
        write_sheet(wb.create_sheet(name), cols, data.get(name, []))
    ws = wb["인력현황"]
    for r in range(3, 1001):  # 소속/직급 자동
        for col, idx in ((3, 2), (4, 3)):
            c = ws.cell(row=r, column=col, value=f'=IF(B{r}="","",IFERROR(VLOOKUP(B{r},인원!$A:$C,{idx},FALSE),""))'); c.font = Font(color="1F4E79"); c.border = BORDER
    ws = wb["업무현황"]
    for r in range(3, 1001):  # 프로젝트명 자동 표시
        c = ws.cell(row=r, column=3, value=f'=IF(B{r}="","",IFERROR(VLOOKUP(B{r},프로젝트개요!$A:$B,2,FALSE),"ID 없음"))')
        c.font = Font(color="1F4E79"); c.border = BORDER
    add_validations(wb)
    wb.save(path)


# ---------------------------------------------------------------- 샘플 데이터
WEEKS = [dt.date(2026, 7, 27) + dt.timedelta(days=7 * i) for i in range(6)]  # 07-27 ~ 08-31
D = dt.date

PROJECTS = [['P01',
  '더샵 송도 마리나베이 신축공사',
  'PJT지원',
  '포스코이앤씨(자체)',
  '인천 연수구 송도동',
  37.3861,
  126.639,
  '지하2층~지상49층, 3개동 1,100세대, 연면적 182,000㎡',
  D(2025, 3, 1),
  D(2028, 6, 30),
  '골조/마감 BIM, 간섭검토, 4D 공정, 물량산출',
  '정우성,한상민',
  '금대연,여민규',
  '조범현,신동환',
  '\\\\posco-fs\\BIM\\P01_송도마리나베이',
  'P01_송도마리나베이.ifc',
  '송도 국제도시 워터프론트 초고층 주상복합. 커튼월·PC공법 병행 구간의 간섭검토와 4D 공정시뮬레이션을 중점 지원하며, 시공 BIM을 현장 엔지니어가 직접 갱신하는 시범 운영 현장.'],
 ['P02',
  '광양 제철소 후판공장 증축',
  'PJT지원',
  '포스코',
  '전남 광양시 금호동',
  34.927,
  127.759,
  '지상4층 철골조, 연면적 46,000㎡',
  D(2025, 9, 1),
  D(2027, 2, 28),
  '철골 BIM, 설비 간섭검토, 시공 시뮬레이션',
  '오경석',
  '여민규',
  '문우빈,김두하',
  '\\\\posco-fs\\BIM\\P02_광양후판',
  'P02_광양후판.ifc',
  '제철 설비 반입 동선과 대형 철골 부재 양중계획을 BIM 기반으로 검증. 플랜트 설비 모델(공급사)과의 통합 간섭검토 수행.'],
 ['P03',
  '판교 테크노밸리 R&D센터',
  'PJT지원',
  '민간 발주',
  '경기 성남시 분당구 삼평동',
  37.402,
  127.108,
  '지하5층~지상15층, 연면적 78,000㎡',
  D(2026, 1, 15),
  D(2028, 3, 31),
  '설계검토 BIM, 흙막이/역타 시공 BIM, 물량 검증',
  '배준호',
  '강보희',
  '조범현,백지환',
  '\\\\posco-fs\\BIM\\P03_판교RD',
  'P03_판교RD.ifc',
  '역타공법 적용 현장. 지하 구조체 시공단계 모델링과 물량 검증, 설계 변경 대응 모델 갱신 지원.'],
 ['P04',
  '부산 에코델타시티 공동주택 A2블록',
  'PJT지원',
  '포스코이앤씨(자체)',
  '부산 강서구 명지동',
  35.105,
  128.922,
  '지하2층~지상35층, 6개동 980세대',
  D(2026, 4, 1),
  D(2029, 1, 31),
  '골조 BIM, 세대 마감 BIM, 4D 공정',
  '임재현,조승우',
  '김재순',
  '신동환,권은주',
  '\\\\posco-fs\\BIM\\P04_에코델타',
  'P04_에코델타.ifc',
  '착공 초기 현장. 골조 표준 세대 모델 구축 및 타워크레인 배치 검토 지원.'],
 ['P05',
  '서울 강동 지식산업센터',
  'PJT지원',
  '민간 발주',
  '서울 강동구 고덕동',
  37.556,
  127.165,
  '지하4층~지상20층, 연면적 120,000㎡',
  D(2025, 11, 1),
  D(2027, 12, 31),
  '설비/전기 BIM 통합, 간섭검토',
  '신동엽',
  '강보희',
  '김두하,권은주',
  '\\\\posco-fs\\BIM\\P05_강동지산',
  'P05_강동지산.ifc',
  'MEP 밀집 구간 간섭검토 및 천장 내 설비 배치 조정 지원. 협력사 BIM 산출물 검수 체계 시범 적용.'],
 ['P06',
  '세종 행복도시 복합커뮤니티센터',
  'PJT지원',
  'LH',
  '세종특별자치시 반곡동',
  36.496,
  127.298,
  '지하1층~지상5층, 연면적 21,000㎡',
  D(2026, 6, 1),
  D(2027, 11, 30),
  '설계 BIM 검토, 물량산출, 도면 정합성',
  '강민수',
  '김재순',
  '백지환',
  '\\\\posco-fs\\BIM\\P06_세종복컴',
  'P06_세종복컴.ifc',
  '공공 BIM 발주 현장. 설계 BIM 납품 모델의 시공성 검토와 도면-모델 정합성 점검 지원.'],
 ['T01',
  '건축 BIM 관리체계 표준화 및 통합 활용체계 구축',
  '수행과제',
  '건축사업본부',
  '본사(송도)',
  37.39,
  126.64,
  '용역 (2026.07~2027.01)',
  D(2026, 7, 1),
  D(2027, 1, 31),
  'BIM 관리규정, 데이터 표준, CDE 연계',
  '',
  '금대연',
  '조범현,문우빈',
  '',
  '',
  '건축 BIM 관리체계 표준화 용역. 관리규정·데이터 체계·통합 활용 프로세스 수립 및 P-DeFiNE/CDE 연계 방안 도출.'],
 ['T02',
  'BIM Mass Model 기반 사업성 검토 자동화',
  '수행과제',
  '건축사업본부',
  '본사(송도)',
  37.39,
  126.64,
  'R&D (2026.05~2026.12)',
  D(2026, 5, 1),
  D(2026, 12, 31),
  'Mass 모델 자동생성, 물량/원가 연동',
  '',
  '금대연,여민규',
  '권은주,권은주',
  '',
  '',
  '수주 초기 단계 Mass Model 자동 생성 및 개략 물량·원가 산출 자동화 도구 개발.'],
 ['T03',
  '간섭검토 성과 대쉬보드 고도화',
  '수행과제',
  '건축BIM섹션',
  '본사(송도)',
  37.39,
  126.64,
  '내부 개발 (2026.06~2026.10)',
  D(2026, 6, 1),
  D(2026, 10, 31),
  '간섭검토 데이터 집계, 시각화',
  '',
  '강보희',
  '김두하',
  '',
  '',
  '현장별 간섭검토 결과를 집계·시각화하는 성과 대쉬보드 고도화.'],
 ['E01',
  'BIM 실무교육 (현장 엔지니어 대상)',
  '기타업무',
  '건축BIM섹션',
  '본사/현장',
  37.39,
  126.64,
  '월 1회, 8회차',
  D(2026, 3, 1),
  D(2026, 11, 30),
  '교육 과정 운영',
  '',
  '금대연',
  '백지환,신동환',
  '',
  '',
  '현장 엔지니어 대상 BIM 실무교육 운영 (모델 검토, 간섭검토 실습, 4D 활용).'],
 ['E02',
  '임원보고 및 대외 협력',
  '기타업무',
  '건축사업본부',
  '본사(송도)',
  37.39,
  126.64,
  '수시',
  D(2026, 1, 1),
  D(2026, 12, 31),
  '보고자료, 대외 세미나, 학회',
  '',
  '금대연,김재순',
  '',
  '',
  '',
  '월간 임원보고, 대외 세미나 발표, 관련 기관 협력.']]

STAFF = [
    ("금대연", "POSCO E&C", "리더"),
    ("여민규", "POSCO E&C", "과장"),
    ("강보희", "POSCO E&C", "과장"),
    ("김재순", "POSCO E&C", "대리"),
    ("조범현", "POSCO A&C", "차장"),
    ("문우빈", "POSCO A&C", "대리"),
    ("신동환", "POSCO A&C", "대리"),
    ("김두하", "POSCO A&C", "대리"),
    ("백지환", "POSCO A&C", "대리"),
    ("권은주", "POSCO A&C", "사원"),
]

# (프로젝트ID, 업무항목, 시작, 종료, E&C, A&C, 주당MD 범위, 첫주 진행률)
TASKS = [
    ("P01", "커튼월 앵커 간섭검토", D(2026, 7, 20), D(2026, 9, 4), "여민규", "조범현", (3, 5), 20),
    ("P01", "4D 공정 시뮬레이션 (골조 20~35F)", D(2026, 8, 3), D(2026, 9, 25), "금대연", "신동환", (2, 4), 5),
    ("P01", "PC 부재 물량산출 검증", D(2026, 8, 17), D(2026, 9, 11), "여민규", "조범현,신동환", (2, 3), 0),
    ("P02", "철골 BIM 모델 갱신(2차)", D(2026, 7, 27), D(2026, 8, 28), "여민규", "문우빈", (3, 5), 15),
    ("P02", "설비-철골 통합 간섭검토", D(2026, 8, 10), D(2026, 9, 18), "여민규", "김두하", (2, 4), 0),
    ("P03", "지하3층 설계변경 모델 갱신", D(2026, 8, 3), D(2026, 8, 28), "강보희", "조범현", (2, 3), 10),
    ("P03", "역타 시공단계 시뮬레이션", D(2026, 8, 17), D(2026, 10, 2), "강보희", "백지환", (2, 4), 0),
    ("P04", "표준 세대 골조 모델 구축", D(2026, 7, 27), D(2026, 9, 4), "김재순", "신동환,권은주", (3, 5), 10),
    ("P04", "타워크레인 배치 검토", D(2026, 8, 24), D(2026, 9, 11), "김재순", "권은주", (1, 3), 0),
    ("P05", "MEP 간섭검토 (지하4~2층)", D(2026, 7, 27), D(2026, 9, 11), "강보희", "김두하,권은주", (3, 5), 25),
    ("P05", "협력사 모델 검수", D(2026, 8, 10), D(2026, 8, 28), "강보희", "권은주", (1, 2), 0),
    ("P06", "설계 BIM 시공성 검토", D(2026, 8, 3), D(2026, 9, 18), "김재순", "백지환", (2, 3), 0),
    ("T01", "관리규정 초안 작성", D(2026, 7, 6), D(2026, 8, 28), "금대연", "조범현", (2, 3), 40),
    ("T01", "데이터 표준(분류체계) 정의", D(2026, 8, 3), D(2026, 10, 16), "금대연", "문우빈", (2, 4), 5),
    ("T01", "1차 중간보고 자료", D(2026, 8, 24), D(2026, 9, 15), "금대연", "조범현,문우빈", (1, 3), 0),
    ("T02", "Mass 자동생성 스크립트 개발", D(2026, 6, 1), D(2026, 9, 30), "여민규", "권은주,권은주", (2, 4), 45),
    ("T02", "개략 물량-원가 연동 검증", D(2026, 8, 17), D(2026, 11, 13), "금대연", "권은주", (1, 3), 0),
    ("T03", "간섭검토 집계 자동화", D(2026, 7, 13), D(2026, 9, 4), "강보희", "김두하", (1, 3), 35),
    ("E01", "7회차 교육 (간섭검토 실습)", D(2026, 8, 17), D(2026, 8, 21), "금대연", "백지환,신동환", (1, 2), 0),
    ("E01", "8회차 교육 자료 (4D 활용)", D(2026, 8, 31), D(2026, 9, 18), "금대연", "백지환", (1, 2), 0),
    ("E02", "9월 임원보고 자료", D(2026, 8, 24), D(2026, 9, 8), "금대연,김재순", "", (1, 2), 0),
]

ISSUE_POOL = ["", "", "", "협력사 모델 납품 지연으로 일정 1주 순연 검토", "발주처 검토회의 결과 반영 필요", "설계변경 도면 미수령", "LOD 미달 모델 재제출 요청", "현장 인원 부족으로 야근 발생"]
ISSUES = {("P01", "커튼월 앵커 간섭검토"): "커튼월 앵커 간섭 47건 조치 중, 9/4 발주처 BIM 검토회의", ("P02", "철골 BIM 모델 갱신(2차)"): "설비 공급사 모델 납품 2주 지연", ("P03", "지하3층 설계변경 모델 갱신"): "설계변경 도면 9/2 접수, 모델 갱신 중", ("P05", "협력사 모델 검수"): "협력사 모델 LOD 미달 – 재제출 요청", ("T01", "1차 중간보고 자료"): "1차 중간보고 9/15 예정", ("P04", "타워크레인 배치 검토"): "크레인 사양 확정 대기"}
DETAILS = ["모델 검토 및 이슈 정리", "현장 협의 및 반영", "모델 갱신 및 검토 보고서 작성", "발주처/협력사 회의 및 조치 확인", "검토 결과 정리 및 다음 단계 준비", "최종 검토 및 마무리"]


def sample_rows():
    tasks = []
    for pid, name, s, e, enc, anc, mdr, p0 in TASKS:
        prog = p0
        total_w = max(1, (e - s).days / 7)
        for w in WEEKS:
            wend = w + dt.timedelta(days=6)
            if wend < s or w > e:
                continue
            step = 100 / total_w
            prog = min(100, round(prog + step * random.uniform(0.7, 1.2)))
            if w == WEEKS[-1] and wend >= e and random.random() < 0.7:
                prog = 100
            md = round(random.uniform(*mdr) * (2 if "," in anc else 1) * 0.8, 1)
            status = "완료" if prog >= 100 else ("지연" if (wend > e - dt.timedelta(days=7) and prog < 80) else "진행")
            field = next((p[11] for p in PROJECTS if p[0] == pid), "")
            issue = ISSUES.get((pid, name), "") if w == WEEKS[-1] else (random.choice(ISSUE_POOL) if status == "지연" or random.random() < 0.15 else "")
            tasks.append([w, pid, None, name, random.choice(DETAILS), s, e, prog, field, enc, anc, md, status, issue])
    staff = []
    for w in WEEKS:
        for nm, org, rank in STAFF:
            for d in range(7):
                ev = [0, 0, 0, 0]
                if d < 5 and random.random() < 0.18: ev[0] = 1
                if d < 5 and random.random() < 0.28: ev[1] = 1
                if d >= 5 and random.random() < 0.06: ev[2] = 1
                if d < 5 and random.random() < 0.05: ev[3] = random.choice([1, 0.5])
                if any(ev):
                    staff.append([w + dt.timedelta(days=d), nm, None, None] + [v or None for v in ev] + [random.choice(["", "", "광양 현장 출장", "송도 현장 지원", "발주처 회의"]), ""])
    return {"프로젝트개요": PROJECTS, "업무현황": tasks, "인력현황": staff, "인원": [[n, o, r, ""] for n, o, r in STAFF]}


if __name__ == "__main__":
    import os, sys
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    build(os.path.join(out, "주간업무현황_템플릿.xlsx"), {"인원": [[n, o, r, ""] for n, o, r in STAFF], "인력현황": []})
    build(os.path.join(out, "주간업무현황_2026-08-31_샘플.xlsx"), sample_rows())
    print("ok")
