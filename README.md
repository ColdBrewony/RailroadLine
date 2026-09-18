# 철도 노선도 (RailroadLine)

KORAIL 전국 철도노선도(2026.01.01 기준)를 실제 지도가 아니라 **지하철 노선도 스타일의 도식화된 다이어그램**으로 재구성하고, 역 이름을 검색하면 해당 역이 속한 노선과 관련 정보(인접역, 거리, 역 종류 등)를 보여주는 웹 서비스입니다.

## 진행 상황

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | 기획 및 요구사항 정리 | ✅ 완료 |
| 1 | 데이터 모델 설계 | ✅ 완료 |
| 2 | 원본 데이터 구축 | ✅ 완료 (68개 노선, 839개 역-노선 데이터, 위키백과 전수 검증) |
| 3 | 백엔드 API 개발 | ⏳ 예정 |
| 4 | 프론트엔드 노선도 시각화 | ⏳ 예정 |
| 5 | 검색/상세 정보 UI | ⏳ 예정 |
| 6 | 테스트 및 배포 | ⏳ 예정 |

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
│   └─ assets/               # 원본 노선도 이미지
├─ data/raw/lines/        # 노선별 원시 데이터 (JSON)
│   └─ README.md             # 데이터 수집 현황 및 검토 가이드
├─ src/                   # Spring Boot 애플리케이션 소스
└─ build.gradle
```

## 기술 스택

- **백엔드**: Spring Boot 4.1.1 (Java 17), Gradle
- **프론트엔드**: 순수 HTML/CSS/바닐라 JavaScript (별도 빌드 툴체인 없음, 자세한 내용은 [`docs/05-frontend-design.md`](docs/05-frontend-design.md) 참고)

## 데이터 현황

`data/raw/lines/`에 노선별 JSON 파일 68개, 총 839개 역-노선 데이터가 있습니다. 모든 데이터는 한국어 위키백과(ko.wikipedia.org) 노선별 문서와 대조해 검증했습니다. 자세한 현황과 검토 가이드는 [`data/raw/lines/README.md`](data/raw/lines/README.md)를 참고하세요.

## 빌드 및 실행

```bash
./gradlew build
./gradlew bootRun
```

## 다음 단계

`docs/04-api-design.md` 스펙에 따라 JPA 엔티티, `data/raw/lines/*.json`을 읽어 DB에 적재하는 시더, 역 검색/노선 조회 REST API를 구현하는 **Phase 3(백엔드 구현)**이 다음 작업입니다.
