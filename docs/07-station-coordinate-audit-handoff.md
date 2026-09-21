# 07. 노선도 좌표 전수 검증 인수인계 문서

이 문서는 "전체 노선도(F1)에서 역 위치가 실제와 다르게 나오는 문제"를 노선별로 하나하나
검증해 고치는 작업을, 토큰 제약으로 이 세션에서 다 끝내지 못하고 다음 세션(다른 모델)에게
넘기기 위해 작성했다. 2026-09-21, `feature/full-diagram-view` 브랜치, 커밋 `f9d2b66` 기준.

## 1. 지금 상태 요약

이번 세션에서 이미 고친 것들(모두 `feature/full-diagram-view`에 커밋·push됨, PR #15):

1. 신경주 이름 오류 수정, 실측 닻 1개뿐인 노선의 좌표 붕괴/별표 패턴 버그 수정 (`70e7fa9`, `533bc89`)
2. 지역본부 중심을 실측 데이터 기반으로 계산 (`7ca5ff9`)
3. 삼척선(추암/삼척해변/삼척) 실측 좌표 보충 (`3033fc4`)
4. **DataSeeder를 "이미 있으면 건너뜀"에서 "시작할 때마다 전체 재적재"로 변경** (`1250a60`) — 아주 중요, 2장 참고
5. 테스트가 로컬 개발 DB 파일을 공유하지 않도록 인메모리 DB로 분리 (`f5b7ce8`)
6. 배경 지도를 원본 해상도 경계 데이터로 교체(해상도 개선) + 노선 목록에 관할 본부 표시 (`f9d2b66`)

**아직 안 한 것** — 이 문서의 본론: 823개 역(64~68개 노선)의 좌표가 실제 위치와 맞는지
노선별로 하나씩 검증하는 작업. 사용자가 "가야선부터 하나씩, 검색해서 확인하고 넘어가자"고
명시적으로 요청했다.

## 2. 작업 시작 전 반드시 알아야 할 것 (안 지키면 삽질함)

- **코드/데이터를 고치고 나면 반드시 앱을 완전히 정지 후 재시작해야 반영된다.** 브라우저
  캐시 삭제, `git pull`만으로는 절대 안 바뀐다. 이번 세션에서 실제로 이 문제 때문에
  "고쳤는데 그대로다"라는 리포트를 두 번 받았다 — 확인해보니 둘 다 예전 코드가 그대로
  떠 있는 프로세스였다. **어떤 수정을 했든, 사용자에게 "완전히 정지 후 재시작"을 먼저
  확인시키고 나서 문제를 더 찾아라.**
- `DataSeeder`(`src/main/java/com/futek/railroad/seed/DataSeeder.java`)는 이제 앱이
  시작할 때마다 Line/LineStation/Station을 전부 지우고 `data/raw/lines/*.json`에서 다시
  적재한다. 즉 **JSON/CSV 원본 파일만 고치면 재시작 시 자동으로 반영된다** — DB를 손으로
  건드릴 필요 없음.
- 테스트(`./gradlew test`)는 `src/test/resources/application.properties`의 인메모리
  H2를 쓴다. 로컬 개발 DB 파일(`data/railroad-db.*`, git 추적 안 됨)과 무관하니 안심하고
  돌려도 된다.
- **`GeoAnchorCalculator.COORDINATE_OVERRIDES`를 꼭 확인해라.** `data/raw/
  station-coordinates.csv`를 grep해서 나오는 값이 실제로 쓰이는 값이 아닐 수 있다. 예:
  `청량리`는 정부 원본 CSV에 `37.11298, 129.036482`(강원 동해안 좌표, 명백한 오류)로
  잘못 박혀 있는데, `GeoAnchorCalculator`가 코드에서 `37.5802, 127.0466`(서울)로
  덮어쓴다. CSV 파일 자체는 원본 그대로 보존하는 게 이 프로젝트 방침이라, **새로 발견하는
  오류도 CSV를 고치지 말고 이 오버라이드 맵에 추가하는 방식을 따를지, 아니면 CSV에
  아예 새 행을 추가(삼척선 때 한 방식)할지 상황에 따라 판단해야 한다** — 정부 원본에
  그 역 자체가 없으면(예: 삼척, 추암) CSV에 새 행 추가, 원본에 있는데 값이 틀리면(예:
  청량리) 오버라이드 맵 추가가 기존 관례.
- 배경 지도(`src/main/resources/static/data/korea-outline.json`)는
  `scripts/build-korea-outline.py`로 만든다. `GeoAnchorCalculator`의 `LAT_MIN`/`LAT_MAX`/
  `LNG_MIN`/`LNG_MAX`/`CANVAS_MARGIN_RATIO` 상수를 바꾸면 이 스크립트를 다시 돌려야
  지도와 역 좌표가 안 어긋난다.

## 3. 핵심 발견: "실측 닻 쏠림" 외삽 버그 (아직 코드 수정 안 함)

역 위치는 `GeoAnchorCalculator.compute()`가 계산한다. 대략:

1. 노선의 각 역 이름을 `station-coordinates.csv`(실측 좌표, 215개 정부 데이터 + 이번
   세션에 보충한 3개)에서 찾아 "실측 닻"으로 삼는다.
2. 실측 닻이 노선 전체에서 **몇 개 안 되거나 없으면** 그 노선이 지나는 지역본부 중심
   좌표를 "가짜 닻"으로 보강한다 — 단, 이 보강 로직은 **실측 닻이 2개 미만일 때만**
   작동한다(`GeoAnchorCalculator.compute()`의 `if (anchorPos.size() < 2 && ...)` 부분).
3. 실측 닻 사이(또는 양 끝 바깥)에 있는 역은 노선 순서(index) 기준으로 선형
   보간/외삽한다.

**문제**: 실측 닻이 2개 "이상"이기만 하면 이 보강 로직이 아예 작동을 안 한다. 그런데
그 2개(또는 몇 개)가 노선의 한쪽 끝(주로 서울 근교, 정부 데이터가 관리역 위주라 도심
역만 실측 좌표가 있는 경우가 많음)에 몰려 있으면, 그 좁은 구간의 기울기만으로 노선
전체를 외삽하게 된다 — 실제 노선이 그 방향·비율대로 쭉 이어진다는 보장이 전혀 없는데도.

### 구체적 사례: 경원선 → 의정부

`경원선`(용산~백마고지, 94.3km, 41개 역)의 실측 닻은 **`용산`(idx 0)과 `청량리`(idx 7)
딱 2개뿐**이고, 둘 다 노선 맨 앞 12.7km 안(서울 시내)에 있다. `의정부`(idx 21, 31.2km
지점)부터 종점 `백마고지`(idx 40, 94.3km)까지 33개 역 전부가 이 12.7km 구간의 기울기로
외삽된다.

직접 계산해보면(`interpolate()`의 외삽 공식 그대로):

```
용산  = (37.5296, 126.9639)  [실측]
청량리 = (37.5802, 127.0466)  [실측, COORDINATE_OVERRIDES 적용된 값]
의정부(idx 21): t = (21-0)/(7-0) = 3.0
  → lat = 37.5296 + 3.0*(37.5802-37.5296) = 37.6814
  → lng = 126.9639 + 3.0*(127.0466-126.9639) = 127.2120
  실제 의정부 좌표(위키백과): 약 37.7381, 127.0470
  → 오차: 남쪽으로 약 6km, 동쪽으로 약 15km

백마고지(idx 40): t = 40/7 = 5.71
  → lat = 37.8187, lng = 127.4365
  실제 백마고지 좌표: 약 38.2495, 127.0710 (철원 인근, DMZ 근처)
  → 오차: 북쪽으로 48km 부족, 동쪽으로 37km 과다
```

노선 뒤로 갈수록 오차가 기하급수로 커진다. "의정부만 갑자기 남쪽에 있다"는 사용자
리포트가 정확히 이 계산과 일치한다.

### 이 패턴이 의심되는 다른 노선들 (진단 스크립트로 전수 조사함)

아래 스크립트를 돌리면(`data/raw/lines/*.json` 전부와 `data/raw/station-coordinates.csv`
대조), 각 노선에서 "실측 닻 구간 밖으로 얼마나 멀리 외삽되는지"(overshoot, 1.0 = 닻
구간 길이의 100%만큼 바깥으로 나감)를 계산할 수 있다:

```python
# scripts/audit-line-anchors.py 로 저장해서 실행 (python 3.x, 표준 라이브러리만 사용)
import glob, json, os

COORD_CSV = "data/raw/station-coordinates.csv"
LINES_DIR = "data/raw/lines"

# GeoAnchorCalculator.COORDINATE_OVERRIDES와 반드시 일치시켜야 한다.
COORDINATE_OVERRIDES = {"청량리": (37.5802, 127.0466)}

REGION_BOUNDS = {
    "서울본부": (37.30, 37.80, 126.70, 127.30),
    "수도권서부본부": (37.20, 37.80, 126.30, 126.90),
    "수도권동부본부": (37.20, 38.35, 127.00, 127.60),
    "강원본부": (37.10, 38.60, 127.60, 129.40),
    "충북본부": (36.30, 37.20, 127.20, 128.20),
    "대전충남본부": (36.00, 37.10, 126.30, 127.60),
    "전북본부": (35.40, 36.20, 126.40, 127.60),
    "광주본부": (34.80, 35.40, 126.60, 127.20),
    "전남본부": (34.30, 35.40, 126.20, 127.60),
    "경북본부": (35.60, 37.10, 128.20, 129.50),
    "대구본부": (35.60, 36.20, 128.20, 128.90),
    "부산경남본부": (34.70, 35.70, 127.80, 129.40),
}

def load_coords():
    coords = {}
    with open(COORD_CSV, encoding="utf-8-sig") as f:
        for i, line in enumerate(f):
            if i == 0:
                continue
            line = line.strip().replace('"', "")
            if not line:
                continue
            parts = line.split(",")
            if len(parts) < 3:
                continue
            try:
                lat, lng = float(parts[1]), float(parts[2])
            except ValueError:
                continue
            name = parts[0]
            if name in COORDINATE_OVERRIDES:
                lat, lng = COORDINATE_OVERRIDES[name]
            coords[name] = (lat, lng)
    return coords

def is_plausible(lat, lng, regions):
    if not regions:
        return True
    for r in regions:
        b = REGION_BOUNDS.get(r)
        if b and b[0] <= lat <= b[1] and b[2] <= lng <= b[3]:
            return True
    return False

def analyze(path, coords):
    with open(path, encoding="utf-8-sig") as f:
        dto = json.load(f)
    names = [s["name"] for s in dto["stations"]]
    regions = dto.get("region") or []
    n = len(names)
    anchor_pos = [i for i, name in enumerate(names)
                  if (c := coords.get(name)) and is_plausible(c[0], c[1], regions)]
    if len(anchor_pos) < 2:
        return {"line": dto["lineName"], "segment": dto.get("segmentLabel"),
                "n": n, "anchors": len(anchor_pos), "max_overshoot": None}
    max_overshoot, worst_idx = 0.0, None
    for i in range(n):
        lo = hi = None
        for k, ap in enumerate(anchor_pos):
            if ap <= i: lo = k
            if ap >= i and hi is None: hi = k
        if lo is None: lo, hi = 0, 1
        elif hi is None: hi = len(anchor_pos) - 1; lo = hi - 1
        elif lo == hi: continue
        pos_lo, pos_hi = anchor_pos[lo], anchor_pos[hi]
        t = (i - pos_lo) / (pos_hi - pos_lo) if pos_hi != pos_lo else 0.5
        overshoot = max(0.0, -t, t - 1.0)
        if overshoot > max_overshoot:
            max_overshoot, worst_idx = overshoot, i
    return {"line": dto["lineName"], "segment": dto.get("segmentLabel"), "n": n,
            "anchors": len(anchor_pos), "max_overshoot": round(max_overshoot, 2),
            "worst_station": names[worst_idx] if worst_idx is not None else None}

if __name__ == "__main__":
    coords = load_coords()
    results = [analyze(p, coords) for p in sorted(glob.glob(os.path.join(LINES_DIR, "*.json")))]
    danger = sorted([r for r in results if r.get("max_overshoot")], key=lambda r: -r["max_overshoot"])
    for r in danger:
        if r["max_overshoot"] > 0.3:
            print(r["line"], r.get("segment"), "n=", r["n"], "anchors=", r["anchors"],
                  "max_overshoot=", r["max_overshoot"], "worst=", r.get("worst_station"))
```

**2026-09-21 기준 실행 결과(overshoot > 0.5, 심각한 순)** — 이 6개 노선을 최우선으로
확인할 것:

| 노선 | 역 수 | 실측 닻 | overshoot | 가장 의심되는 역 |
|---|---|---|---|---|
| 경북선 | 12 | 5 | 6.00 | 김천(idx 0) |
| 경원선 | 41 | 2 | 4.71 | 백마고지(idx 40) — 위에서 이미 확인함 |
| 경의선 | 25 | 2 | 2.43 | 도라산(idx 24) |
| 전라선 | 30 | 9 | 1.00 | 여수엑스포(idx 29) — 사용자가 "바다에 있다"고 지적한 역, 좌표 자체는 맞지만(4장 참고) 마지막 실측 닻보다 바깥에서 순수 외삽됨 |
| 태백선 | 19 | 7 | 1.00 | 백산(idx 18) |
| 중앙선 | 47 | 20 | 0.67 | 모량(idx 46) |

이 목록은 "이 노선들에 버그가 있다"가 아니라 "이 노선들의 이 역들은 외삽 구간이니
우선 검색해서 실제 좌표를 확인하라"는 뜻이다. `overshoot`가 0에 가까울수록(순수
보간, 외삽 아님) 안전하다.

### 권장 수정 방향 (아직 구현 안 함, 다음 세션에서 판단)

두 가지 선택지가 있다:

1. **가장 직접적**: 위 표에 나온 것처럼 외삽이 심한 역들의 실측 좌표를 하나씩
   찾아서(위키백과 등) `station-coordinates.csv`에 추가한다(삼척선 때 한 방식 그대로).
   실측 닻이 늘어나면 그 사이 역들의 보간 정확도도 같이 좋아진다.
2. **알고리즘 차원 보완**: `GeoAnchorCalculator`에서 "실측 닻이 2개 이상이어도, 그
   닻들이 노선 앞부분(또는 뒷부분) 일정 비율 안에만 몰려 있으면" 그 바깥 구간에는
   지역본부 보강 앵커를 추가로 섞는 조건을 넣는다(현재 `anchorPos.size() < 2`
   조건을 "커버 안 되는 구간이 있으면"으로 일반화). 이러면 새로 발견되는 노선까지
   한 번에 완화되지만, 회귀 테스트를 더 꼼꼼히 해야 한다.

사용자는 "1번(직접 실측 좌표 보충)을 먼저 하고, 그래도 이상해 보이는 노선만 추려서
검색 검증"하는 방향을 선호했다. 다음 세션은 이 방향으로 진행하되, 위 우선순위표부터
시작하면 된다.

## 4. 여수엑스포 케이스 — 별도 유형의 문제

`전라선`의 `여수엑스포`역은 위키백과 좌표(34.75778, 127.74722)와 우리 CSV 값
(34.748164, 127.745746)이 거의 일치한다(1km 미만 차이) — **좌표 자체는 정확**하다.
그런데도 화면에서 바다에 있는 것처럼 보이는 건 두 가지 이유가 겹쳐서다:

1. 위 3장 표에서 보듯 이 역은 실측 닻이 아니라(마지막 실측 닻보다 뒤에 있어서)
   외삽으로 계산된다 — 실측 좌표가 있는데도 `isPlausible()` 지역 범위 검사를
   통과했다면 실측값을 써야 하는데, 혹시 이 역이 실측 닻 목록에서 빠진 이유(지역
   범위 밖으로 판정됐는지)를 먼저 확인할 것. `station-coordinates.csv`에 값이
   있다고 무조건 닻으로 쓰이는 게 아니라 `isPlausible(latLng, regions)` 검증을
   통과해야 한다.
2. 설령 실측 닻으로 정상적으로 쓰이더라도, 이 역은 해안선에서 1km도 안 되는
   매립지/항구 근처라 배경 지도(행정구역 경계 데이터, 실측 역 좌표와는 출처가
   다름)와 실측 GPS 좌표가 서브킬로미터 단위로 안 맞을 수 있다 — 계산해보면 새
   배경지도 기준으로 해안선에서 3.4px(약 0.57km) 안쪽에 있다. 완전히 바다 밖은
   아니지만 화면에 그려지는 역 표시 원(반지름)이 해안선에 걸쳐 보일 수 있다.

**다음 세션 확인 사항**: 여수엑스포가 실측 닻으로 쓰이는지 먼저 확인(안 쓰이면 왜
`isPlausible()`에서 걸렀는지 확인 — 전라선의 `region` 필드와 좌표가 정말 안
맞는지, 아니면 로직 버그인지). 실측 닻으로 정상적으로 쓰이는데도 시각적으로
바다에 걸쳐 보이면, 이건 좌표 오류가 아니라 "배경 지도 해상도 한계"이니 수정
불가/우선순위 낮음으로 분류하고 넘어가도 된다.

## 5. 실측 닻 0~1개인 49개 노선 (기존 안전장치는 있지만 개별 확인 필요)

이 노선들은 이미 "실측 닻 <2개면 지역본부로 보강" 로직이 적용되지만, 삼척선처럼
그 보강만으로는 부족한 경우가 있었다(지역본부가 너무 넓어서 중심점이 엉뚱한 데
찍히는 경우). 아래는 전수 조사 결과 목록(역 수 오름차순 아님, 파일명 순):

가야선(1), 강경선(0), 경강선[판교-여주](0), 경인선(0), 경춘선(0), 과천선(0),
광양제철선(1), 광양항선(0), 괴동선(0), 교외선(0), 군산항선(1), 군산화물선(0),
대구선(1), 대불선(1), 덕산선(1), 동해북부선(0), 목포보성선(1), 문경선(1),
병점기지선(0), 부강화물선(1), 부산신항선(1), 부전선(1), 북전주선(0), 북평선(1),
분당선(0), 서해선[서화성-홍성](1), 서해선[대곡-원시](0), 수인선(0), 신광양항선(0),
신동화물선(0), 신항남선(0), 신항북선(0), 안산선(0), 양산화물선(1), 여천선(0),
영일만항선(0), 옥구선(0), 온산선(1), 우암선(0), 울산신항선(0), 울산항선(1),
익산삼각선(0), 일산선(0), 장성화물선(0), 정선선(1), 중부내륙선(1), 진해선(1),
평택선(1), 함백선(1)

(괄호 안 숫자 = 실측 닻 개수)

이 중 **닻이 0개인 노선**(강경선, 경인선, 경춘선, 과천선, 광양항선, 괴동선, 교외선,
군산화물선, 동해북부선, 병점기지선, 북전주선, 분당선, 서해선[대곡-원시], 수인선,
신광양항선, 신동화물선, 신항남선, 신항북선, 안산선, 여천선, 영일만항선, 옥구선,
우암선, 울산신항선, 익산삼각선, 일산선, 장성화물선)이 지역본부 중심에만 의존하므로
가장 부정확할 가능성이 높다. 특히 **경인선·경춘선·분당선·수인선·안산선·일산선**처럼
역이 많은(20개 이상) 수도권 노선이 닻 0개인 건 의외로 심각할 수 있다 — 실측 좌표
215개 안에 왜 하나도 안 걸렸는지 먼저 확인해볼 가치가 있다(역 이름 표기 차이로
매칭이 안 되는 것일 수도 있음, 예: "경인선"의 역들이 "경부선"이나 "1호선" 쪽
이름으로만 실측 CSV에 들어있고 정작 경인선 자체 역 이름과 안 맞는 경우 등).

## 6. 전체 68개 노선 목록 (사용자 요청: 가야선부터 하나씩)

`data/raw/lines/` 디렉터리 파일명 기준 오름차순(사용자가 "가야선부터"라고 했으니 이
순서 그대로 진행하면 된다):

```
가야선, 강경선, 강릉선, 경강선, 경부고속선, 경부선, 경북선, 경원선, 경의선, 경인선,
경전선, 경춘선, 과천선, 광양제철선, 광양항선, 광주선, 괴동선, 교외선, 군산항선,
군산화물선, 대구선, 대불선, 덕산선, 동해북부선, 동해선, 목포보성선, 묵호항선, 문경선,
병점기지선, 부강화물선, 부산신항선, 부전선, 북전주선, 북평선, 분당선, 삼척선,
서해선-홍성서화성, 서해선, 수인선, 신광양항선, 신동화물선, 신항남선, 신항북선, 안산선,
양산화물선, 여천선, 영동선, 영일만항선, 옥구선, 온산선, 우암선, 울산신항선, 울산항선,
익산삼각선, 일산선, 장성화물선, 장항선, 전라선, 정선선, 중부내륙선, 중앙선, 진해선,
충북선, 태백선, 평택선, 함백선, 호남고속선, 호남선
```

(삼척선·경부고속선은 이번 세션에 이미 검증·수정 완료했으니 건너뛰어도 된다.)

## 7. 노선 하나 검증하는 절차 (권장 방법론)

1. `data/raw/lines/<노선>.json`을 읽고 역 목록·순서·region을 확인한다.
2. 3장의 진단 스크립트(또는 아래 간단 bash 버전)로 이 노선의 실측 닻 개수·위치를
   확인한다:
   ```bash
   grep -oP '"name":\s*"\K[^"]+' data/raw/lines/<노선>.json | while read -r name; do
     grep -qF "\"$name\"," data/raw/station-coordinates.csv && echo "REAL $name" || echo "-    $name"
   done
   ```
3. 닻이 부족하거나 클러스터링(3장)이 의심되면, 외삽 구간에 속하는 역들의 실제
   좌표를 위키백과(`ko.wikipedia.org/wiki/<역이름>역`)에서 찾는다(WebFetch로
   인포박스의 좌표 확인 — 이번 세션에서 삼척선 할 때 쓴 방법 그대로).
4. 위키백과 좌표와 우리 CSV의 기존 좌표(있다면)를 비교해서 크게 다르면(1~2km
   이상) `station-coordinates.csv`에 반영한다:
   - CSV에 그 역이 아예 없으면(정부 데이터셋 커버리지 밖) 파일 끝에 새 행 추가.
   - CSV에 있는데 값이 명백히 틀리면(정부 원본 자체 오류) `GeoAnchorCalculator.
     COORDINATE_OVERRIDES`에 추가(청량리 사례처럼) — CSV 원본은 그대로 보존.
5. `data/raw/lines/README.md`나 `station-coordinates.README.md`에 무엇을 왜
   고쳤는지 한두 문단 기록(이 프로젝트의 기존 관례).
6. `./gradlew compileJava test`로 회귀 확인.
7. 다음 노선으로 넘어가기 전에, 정말 앱을 재시작해서 눈으로 확인하고 싶으면
   2장의 "재시작 필수" 유의사항을 사용자에게 다시 상기시킬 것.

## 8. 보류된 UI/UX 결정 사항 (사용자가 이 문서에 남겨달라고 요청함)

사용자가 요청했지만 이번 세션에서 결정하지 않고 다음 세션·사용자 판단으로 넘긴
사항들 (`src/main/resources/static/js/diagram.js` 관련):

1. **더 깊은 확대**: 현재 `MAX_SCALE = 80`(diagram.js 19번째 줄 근처). 사용자가
   "서울처럼 밀집한 구간을 더 가깝게 확대할 수 있으면 좋겠다"고 했다 — 값을 더
   올리는 건 간단하지만, `updateStationVisualSizes()`의 `MAX_CONTENT_RADIUS`/
   `MAX_CONTENT_FONT` 클램프 값도 같이 재검토해야 화면 픽셀 크기가 이상해지지
   않는다. 실제로 몇 배까지 확대할 수 있어야 서울 밀집 구간의 역들이 서로 안
   겹치는지 직접 확대해보며 값을 잡아야 한다.
2. **축소했을 때도 몇 개 역 이름만 항상 보이게**: 현재는 `LABEL_SCALE_THRESHOLD =
   2.2`보다 확대해야만 라벨이 전부 나타나는 이진(on/off) 방식이다(`applyTransform()`의
   `show-labels` 클래스 토글). 사용자는 가장 축소된 상태에서도 "소수의 주요 역"만은
   항상 라벨이 보이길 원한다. **어떤 역을 "항상 보이는 소수"로 뽑을지 기준이
   정해지지 않았다** — 후보로 논의된 것:
   - 관리역(`stationType == MANAGED`)만 — 전국에 약 100여 개
   - KTX 정차역(`isKtxStop == true`)만 — 약 50여 개
   - 환승역(2개 이상 노선이 지나는 역)만
   - 혹은 이 셋의 조합(예: 관리역이면서 동시에 인구 밀집 지역 등)
   사용자에게 직접 물어보고 정한 뒤, `diagram.js`의 `stationsGroup` 렌더링과
   `applyTransform()`의 라벨 표시 로직에 "항상 보이는 라벨" 별도 클래스/조건을
   추가하면 된다(현재 라벨은 전부 `show-labels` 클래스 하나로 일괄 제어되므로,
   최소 하나의 축소 단계를 더 만들어야 함).
3. 위 두 가지는 서로 연결돼 있다 — 더 깊이 확대할 수 있게 되면 3단계 정도의
   확대 구간(전체 축소: 소수 라벨만 / 중간: 라벨 없음 또는 일부 / 깊은 확대:
   전체 라벨, 글자 크게)으로 나누는 게 자연스러울 수 있다. 사용자와 실제 화면을
   보며 조율하는 게 좋다.

## 9. 참고 파일 경로

- 좌표 계산: `src/main/java/com/futek/railroad/layout/GeoAnchorCalculator.java`
- 좌표 반영(레이아웃 실행): `src/main/java/com/futek/railroad/layout/DiagramLayoutRunner.java`
- 데이터 시딩: `src/main/java/com/futek/railroad/seed/DataSeeder.java`
- 노선 원본 데이터: `data/raw/lines/*.json`
- 실측 좌표: `data/raw/station-coordinates.csv` (+ `station-coordinates.README.md`)
- 배경 지도: `src/main/resources/static/data/korea-outline.json` (+ 생성 스크립트
  `scripts/build-korea-outline.py`)
- 프론트 렌더링/줌: `src/main/resources/static/js/diagram.js`
- PR: https://github.com/ColdBrewony/RailroadLine/pull/15
