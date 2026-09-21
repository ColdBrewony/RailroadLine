# 철도 노선도 (RailroadLine)

KORAIL 전국 철도노선도(2026.01.01 기준)를 실제 지리 좌표 위에 **지하철 노선도 스타일로 도식화한 다이어그램** 한 장으로 보여주고, 역 이름을 검색하거나 지도에서 역·노선을 클릭하면 해당 역/노선의 상세 정보(소속 노선, 인접역, 구간거리, 역 종류 등)를 보여주는 웹 서비스입니다.

## 진행 상황

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 기획 및 요구사항 정리 | ✅ 완료 |
| 1 | 데이터 모델 설계 | ✅ 완료 |
| 2 | 원본 데이터 구축 | ✅ 완료 (68개 노선, 839개 역-노선 데이터, 위키백과 전수 검증) |
| 3 | 백엔드 API 개발 | ✅ 완료 (역 검색/상세, 노선 목록/상세, 전체 노선도 API) |
| 4 | 프론트엔드 노선도 시각화 | ✅ 완료 (전체 노선도 한 화면 통합 표시, pan/zoom, 실측 좌표 기반 배치) |
| 5 | 검색/상세 정보 UI | ✅ 완료 (역 검색, 역/노선 클릭 시 상세 패널) |
| 6 | 테스트 및 배포 | 🔶 부분 완료 (단위/회귀 테스트만 있고 배포 환경은 아직 없음) |

## 프로젝트 구조

```
railroad/
├─ docs/                  # 단계별 기획 문서
│   ├─ 00-roadmap.md         # 전체 작업 로드맵
│   ├─ 01-requirements.md     # 요구사항 정의
│   ├─ 02-data-model.md       # 데이터 모델 설계 (엔티티, ERD)
│   ├─ 03-data-source.md      # 원본 데이터 추출 규칙
│   ├─ 04-api-design.md       # 백엔드 API 설계
│   ├─ 05-frontend-design.md  # 프론트엔드 노선도 시각화 설계
│   ├─ 06-search-ui.md        # 검색/상세 정보 UI 설계
│   ├─ 08-station-coordinate-audit-progress.md  # 역 좌표 전수 검증 진행 기록
│   └─ assets/               # 원본 노선도 이미지
├─ data/raw/lines/        # 노선별 원시 데이터 (JSON)
│   └─ README.md             # 데이터 수집 현황 및 검토 가이드
├─ data/raw/station-coordinates*.csv  # 역 실측 좌표 및 검증 기록 (station-coordinates.README.md 참고)
├─ scripts/               # 좌표 감사/배경 지도 생성용 보조 스크립트 (Python)
├─ src/main/              # Spring Boot 애플리케이션 소스 + 정적 프론트엔드(static/)
├─ src/test/              # 단위/회귀 테스트
└─ build.gradle
```

## 기술 스택

- **백엔드**: Spring Boot 4.1.1 (Java 17), Gradle
- **프론트엔드**: 순수 HTML/CSS/바닐라 JavaScript (별도 빌드 툴체인 없음, 자세한 내용은 [`docs/05-frontend-design.md`](docs/05-frontend-design.md) 참고)

## 주요 기능

- **전체 노선도**: `/api/diagram` 응답 하나로 정상 운행 중인 모든 노선/역을 한 화면에 그립니다. 여러 노선이 겹치는 구간은 한 번만 그리고, 노선 목록에서 노선을 선택하면 그 노선의 전체 경로만 강조합니다.
- **역 검색(F2)**: 상단 검색창에 역 이름을 입력하면 소속 노선 배지와 함께 자동완성 결과를 보여줍니다.
- **역/노선 상세(F3·F4)**: 지도에서 역이나 노선의 선을 클릭하면 오른쪽(모바일은 하단) 패널에 상세 정보가 뜹니다 — 역은 소속 노선별 이전/다음역·구간거리, 노선은 전체 역 목록·거리·소속 지역본부를 보여줍니다.
- **노선 목록(F4)**: 왼쪽 사이드바에서 노선명으로 필터링하고 폐선/중지 노선 포함 여부를 토글할 수 있습니다.
- **좌표 검증**: 726개 역 중 724개의 위경도 좌표를 위키백과·Rail.Blue·OpenStreetMap 등 외부 출처와 대조해 실제 지리적 위치에 가깝게 배치합니다. 진행 현황은 [`docs/08-station-coordinate-audit-progress.md`](docs/08-station-coordinate-audit-progress.md) 참고.

## 데이터 현황

`data/raw/lines/`에 노선별 JSON 파일 68개, 총 839개 역-노선 데이터(고유 역 726개)가 있습니다. 노선 구성/순서는 한국어 위키백과(ko.wikipedia.org) 노선별 문서와 대조해 검증했고, 각 역의 실측 좌표는 별도로 [`data/raw/station-coordinates.README.md`](data/raw/station-coordinates.README.md)에 정리된 절차로 검증합니다. 자세한 노선 데이터 현황과 검토 가이드는 [`data/raw/lines/README.md`](data/raw/lines/README.md)를 참고하세요.

## 빌드 및 실행

```bash
./gradlew build
./gradlew bootRun
./gradlew test
```

## 다음 단계

- 야음(울산항선)·군산화물선분기(군산화물선) 2개 역은 신뢰할 수 있는 좌표 출처를 아직 찾지 못해 추정 배치 상태입니다.
- 배포 환경(CI/CD, 운영 DB 등)은 아직 구성하지 않았습니다.
