# 04. 백엔드 API 설계

전제: `02-data-model.md`의 엔티티(Region/Line/Station/LineStation)와 `01-requirements.md`의 기능 요구사항(F1~F6)을 기준으로 설계한다. 내부 도구이므로 인증/속도제한 등은 최소화하고, 조회(read) 위주 API에 집중한다.

## 1. 기술 스택 결정 (제안)

기존 프로젝트에는 `spring-boot-starter-webmvc`만 있고 영속성 계층이 없다. 다음을 제안한다.

- **영속성**: Spring Data JPA + **H2(파일 모드)**. 내부 도구이고 데이터가 정적/읽기 위주이므로 별도 DB 서버 운영 부담 없이 애플리케이션과 같이 뜨는 파일 기반 DB로 충분하다. 추후 운영 규모가 커지면 PostgreSQL 등으로 교체 가능하도록 Repository는 Spring Data JPA 인터페이스로 추상화한다.
- **API 스타일**: REST + JSON, 요청/응답 필드는 camelCase.
- **DB 시딩**: `03-data-source.md`에서 만든 `data/raw/lines/*.json`을 애플리케이션 시작 시(또는 별도 CLI 배치) 읽어 DB에 적재하는 `DataSeeder` 컴포넌트를 둔다(Phase 3 구현 시 상세화).

> **확인 필요**: H2(파일 모드) 채택에 이견 없는지. 내부 도구라도 다중 사용자가 동시에 접근할 가능성이 크면 PostgreSQL 등을 처음부터 쓰는 것도 고려 가능.

## 2. 패키지 구조 (제안)

```
com.futek.railroad
 ├─ domain          // JPA 엔티티: Region, Line, Station, LineStation, enum들
 ├─ repository       // Spring Data JPA Repository 인터페이스
 ├─ service          // 검색/조회 비즈니스 로직
 ├─ web              // @RestController + DTO
 │   └─ dto
 ├─ seed             // 초기 데이터 적재(DataSeeder)
 └─ RailroadApplication
```

## 3. 공통 규약

- 기본 경로 prefix: `/api`
- 응답 형식: JSON, 성공 시 데이터 그대로 반환(리스트는 배열 또는 `{ items: [...], total: N }` 래핑 — 검색류는 total 포함 권장)
- 에러 응답 공통 포맷:

```json
{
  "code": "STATION_NOT_FOUND",
  "message": "해당 역을 찾을 수 없습니다.",
  "status": 404
}
```

- 정렬: 노선 내 역 목록은 항상 `sequence_no` 오름차순.
- 인증: 1차 버전은 인증 없음(내부망 접근 전제). 필요 시 이후 API Key 헤더(`X-Internal-Token`) 방식으로 최소 인증 추가 예정.

## 4. 엔드포인트 목록

### 4.1 역 검색 — `GET /api/stations/search`

F2(역 검색) 대응.

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `q` | Y | 역 이름 부분 문자열 (한글) |
| `limit` | N | 최대 결과 수, 기본 20 |

응답 예:

```json
{
  "total": 2,
  "items": [
    { "stationId": 101, "name": "동대구", "regionName": "대구본부", "lineNames": ["경부선", "경부고속선", "대구선"] },
    { "stationId": 205, "name": "동대전", "regionName": "대전충남본부", "lineNames": ["경부선"] }
  ]
}
```

### 4.2 역 상세 — `GET /api/stations/{stationId}`

F3(역 상세 정보) 대응. 환승역이면 `lines` 배열에 노선별 항목이 여러 개 들어간다.

응답 예:

```json
{
  "stationId": 101,
  "name": "동대구",
  "regionName": "대구본부",
  "stationType": "MANAGED",
  "isKtxStop": true,
  "diagramX": 812.0,
  "diagramY": 430.5,
  "lines": [
    {
      "lineId": 3,
      "lineName": "경부선",
      "sequenceNo": 42,
      "cumulativeKm": 288.5,
      "prevStation": { "stationId": 100, "name": "대구", "distanceKm": 4.0 },
      "nextStation": { "stationId": 102, "name": "지천", "distanceKm": 9.1 }
    },
    {
      "lineId": 7,
      "lineName": "대구선",
      "sequenceNo": 1,
      "cumulativeKm": 0.0,
      "prevStation": null,
      "nextStation": { "stationId": 340, "name": "동촌", "distanceKm": 5.3 }
    }
  ]
}
```

### 4.3 노선 목록 — `GET /api/lines`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `category` | N | `LineCategory` 필터 (고속철도/일반철도/광역전철/화물전용선) |
| `region` | N | 해당 본부를 지나는 노선만 필터 |

응답 예:

```json
{
  "total": 107,
  "items": [
    { "lineId": 3, "name": "경부선", "category": "CONVENTIONAL_RAIL", "colorHex": "#0052A4", "totalDistanceKm": 441.7, "stationCount": 58 }
  ]
}
```

### 4.4 노선 상세 — `GET /api/lines/{lineId}`

F4(노선 상세 정보) 대응. 소속 역을 순서대로 반환.

```json
{
  "lineId": 3,
  "name": "경부선",
  "category": "CONVENTIONAL_RAIL",
  "totalDistanceKm": 441.7,
  "stations": [
    { "stationId": 98, "name": "서울", "sequenceNo": 1, "cumulativeKm": 0.0, "isTransfer": true },
    { "stationId": 99, "name": "영등포", "sequenceNo": 2, "cumulativeKm": 8.0, "isTransfer": false }
  ]
}
```

### 4.5 노선도 렌더링 데이터 — `GET /api/diagram`

F1(노선도 시각화) 대응. 프론트엔드가 전체 노선도를 그리기 위한 원스톱 API. 데이터 양이 많으므로 `lines` 파라미터로 부분 요청도 지원.

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `lineIds` | N | 콤마 구분 노선 ID 목록. 생략 시 전체 |

응답 예:

```json
{
  "lines": [
    {
      "lineId": 3,
      "name": "경부선",
      "colorHex": "#0052A4",
      "stations": [
        { "stationId": 98, "name": "서울", "sequenceNo": 1, "x": 420.0, "y": 120.0 },
        { "stationId": 99, "name": "영등포", "sequenceNo": 2, "x": 400.0, "y": 140.0 }
      ]
    }
  ],
  "transferStationIds": [98, 101, 205]
}
```

### 4.6 본부 목록 — `GET /api/regions`

필터 UI용 보조 API.

```json
{ "items": [ { "regionId": 1, "name": "서울본부", "hqStationName": "서울" } ] }
```

## 5. 서비스 계층 설계 메모

- `StationSearchService`: 부분 일치 검색 — 1차는 `LIKE '%q%'` 기반 JPA 쿼리로 충분(데이터 규모가 크지 않음, 전체 역 수는 지도 기준 수천 개 이하로 추정). 이후 성능 이슈 시 인덱스/전문검색(예: DB LIKE → 정규화된 검색 컬럼) 도입 검토.
- `StationDetailService`: 특정 역의 모든 `LineStation` 행을 조회 후 노선별로 그룹핑, 각 그룹에서 이전/다음 역을 `sequence_no ± 1`로 조회.
- `DiagramService`: `lineIds` 파라미터 유무에 따라 전체/부분 조회, `diagram_x/y`가 아직 없는 역(Phase 4 이전)은 null로 반환 — 프론트엔드가 좌표 없는 경우를 처리할 수 있어야 함(개발 중간 단계 대응).

## 6. 다음 단계

`docs/05-frontend-design.md`(프론트엔드 노선도 시각화 설계)로 진행한다.
