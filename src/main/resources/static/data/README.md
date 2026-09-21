# korea-outline.json

배경 지도로 까는 대한민국(본토+제주, 울릉도/독도 제외) 윤곽선입니다. 역 좌표와 똑같은 좌표계로
미리 투영해 둔 정적 자산이라 서버 계산 없이 프론트에서 바로 배경으로 그릴 수 있습니다.

## 출처

[southkorea/southkorea-maps](https://github.com/southkorea/southkorea-maps) 저장소의
`kostat/2013/json/skorea_provinces_geo_simple.json`(통계청 2013년 행정구역 경계, 단순화된
GeoJSON). 이 저장소는 라이선스를 명시하지 않았습니다 — 내부 도구용 배경 참고 이미지로만 쓰고,
공개 배포 시에는 라이선스가 명확한 다른 출처(예: Natural Earth, 공공데이터포털의 행정구역
경계)로 교체하는 걸 권장합니다.

## 생성 방법

위 GeoJSON의 각 폴리곤 고리(ring)를, `GeoAnchorCalculator`가 역 위경도를 투영할 때 쓰는 것과
**완전히 동일한 고정 범위·공식**(위도 33.0~38.65, 경도 124.5~129.6, cos 보정 후 종횡비를
유지한 채 캔버스에 맞춤)으로 미리 투영해서 `[[ [x,y], ... ], ...]` 형태의 좌표 배열로 저장했습니다.
본토·제주 범위(경도 129.6) 밖에 있는 울릉도 등은 제외했습니다. 이 고정 범위 상수를
`GeoAnchorCalculator`에서 바꾸면 이 파일도 반드시 다시 생성해야 정렬이 어긋나지 않습니다.
