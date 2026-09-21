package com.futek.railroad.layout;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.Station;
import com.futek.railroad.repository.LineRepository;
import com.futek.railroad.repository.LineStationRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link DiagramLayoutRunner}이 사용할 역별 목표 위치(앵커)를, 가능한 한 실제 위경도에 가깝게
 * 계산한다.
 *
 * <p>data/raw/station-coordinates.csv(국가철도공단_철도역 정보, data.go.kr 공공데이터, 215개 관리역
 * 기준 실측 위경도)를 노선별 "닻(anchor)"으로 삼아, 같은 노선 위의 나머지 역들은 노선 내 순서를
 * 기준으로 그 닻들 사이를 선형 보간(사이 구간)·외삽(양 끝)해서 위경도를 추정한다. 실측 닻이 하나도
 * 없는 노선(주로 화물/단거리 지선)은 {@link #REGION_BOUNDS}에 정의된 코레일 지역본부의 대략적인
 * 중심 좌표를 "가짜 닻"으로 대신 써서 같은 보간 로직을 그대로 적용한다.
 *
 * <p>정부 데이터에도 오류가 섞여 있어(예: 특정 역의 좌표가 소속 지역과 전혀 다른 곳을 가리키는
 * 사례를 실제로 발견함), 실측 좌표를 쓰기 전에 그 역이 속한 노선들의 지역본부 범위
 * ({@link #REGION_BOUNDS}) 중 하나에라도 들어오는지 검증하고, 어디에도 들어오지 않으면 버린다.
 */
@Component
public class GeoAnchorCalculator {

    private static final Logger log = LoggerFactory.getLogger(GeoAnchorCalculator.class);

    private static final Path COORDINATES_FILE = Path.of("data", "raw", "station-coordinates.csv");
    private static final double CANVAS_MARGIN_RATIO = 0.08;

    /**
     * 대한민국(본토+제주, 울릉도/독도 제외) 전체를 담는 고정 위경도 범위. 역 좌표 투영뿐 아니라
     * frontend가 배경으로 까는 국토 윤곽선(static/data/korea-outline.json)도 반드시 이 값과
     * 똑같은 범위·공식으로 미리 투영해 뒀다 — 그래야 역 점과 배경 윤곽선이 같은 좌표계에서 정렬된다.
     * 값을 바꾸면 그 윤곽선도 다시 생성해야 한다(스크립트: 이 클래스의 커밋 로그 참고).
     */
    static final double LAT_MIN = 33.0;
    static final double LAT_MAX = 38.65;
    static final double LNG_MIN = 124.5;
    static final double LNG_MAX = 129.6;

    /** 대한민국 대략 중심(위경도 전부 못 구한 극단적인 경우의 최후 기본값). */
    private static final double DEFAULT_LAT = 36.5;
    private static final double DEFAULT_LNG = 127.8;

    /**
     * data/raw/station-coordinates.csv 원본에 있는 것으로 확인된 오류를 바로잡는다(원본 파일은
     * 그대로 두고 여기서만 교정 — station-coordinates.README.md 참고). 청량리역은 원본에
     * (37.11298, 129.036482)로 돼 있는데 이는 서울이 아니라 강원 동해안 좌표다. 지역본부
     * 범위 검증이 노선에 "강원본부"가 포함돼 있으면 이 값도 통과시켜 버려(그 지역 범위 자체가
     * 넓어서) 실제 서울 위치로 바로잡는다.
     */
    // 2026-09-21 검증 출처/원본과의 차이: data/raw/station-coordinate-audit.csv.
    private static final Map<String, double[]> COORDINATE_OVERRIDES = Map.ofEntries(
            Map.entry("청량리", new double[] {37.5802, 127.0466}),
            Map.entry("철암", new double[] {37.1128861, 129.0369556}),
            Map.entry("서원주", new double[] {37.3498, 127.837}),
            Map.entry("횡성", new double[] {37.48278, 128.01028}),
            Map.entry("둔내", new double[] {37.51000, 128.22111}),
            Map.entry("평창", new double[] {37.56222, 128.43000}),
            Map.entry("진부(오대산)", new double[] {37.64250, 128.57472}),
            Map.entry("여천", new double[] {34.77944, 127.66722}),
            Map.entry("여수엑스포", new double[] {34.75778, 127.74722}));

    /**
     * 코레일 지역본부 이름 -> 그 지역의 대략적인 위경도 범위(latMin, latMax, lngMin, lngMax).
     * 눈대중으로 잡은 근사치이며 두 가지 용도로 쓰인다: (1) 실측 좌표가 엉뚱한 값인지 검증,
     * (2) 실측 닻이 전혀 없는 노선에서 지역 중심점을 "가짜 닻"으로 대신 쓸 때의 좌표.
     */
    private static final Map<String, double[]> REGION_BOUNDS = Map.ofEntries(
            Map.entry("서울본부", new double[] {37.30, 37.80, 126.70, 127.30}),
            // 경의선 문산/도라산까지 포함한다(기존 위도 상한 37.80은 두 역을 제외함).
            Map.entry("수도권서부본부", new double[] {37.20, 37.95, 126.30, 126.90}),
            // 경원선(의정부~신탄리/백마고지, DMZ 인접)이 이 지역본부 소속이라 위도 상한을
            // 37.90에서 38.35까지 넓혔다 — 실제로 그 구간 역들이 이 범위 밖으로 검증에서
            // 걸러지는 문제를 발견해서 수정함.
            // 경강선 여주(127.62857E)도 포함한다.
            Map.entry("수도권동부본부", new double[] {37.20, 38.35, 127.00, 127.70}),
            Map.entry("강원본부", new double[] {37.10, 38.60, 127.60, 129.40}),
            Map.entry("충북본부", new double[] {36.30, 37.20, 127.20, 128.20}),
            Map.entry("대전충남본부", new double[] {36.00, 37.10, 126.30, 127.60}),
            Map.entry("전북본부", new double[] {35.40, 36.20, 126.40, 127.60}),
            Map.entry("광주본부", new double[] {34.80, 35.40, 126.60, 127.20}),
            // 전라선 여천/여수엑스포의 정상 좌표가 127.60E 밖이라 버려지던 문제 수정.
            Map.entry("전남본부", new double[] {34.30, 35.40, 126.20, 127.80}),
            // 경북선 김천/옥산/청리/상주/함창은 128.20E보다 서쪽에 있다.
            Map.entry("경북본부", new double[] {35.60, 37.10, 128.00, 129.50}),
            Map.entry("대구본부", new double[] {35.60, 36.20, 128.20, 128.90}),
            Map.entry("부산경남본부", new double[] {34.70, 35.70, 127.80, 129.40}));

    private final LineRepository lineRepository;
    private final LineStationRepository lineStationRepository;
    private final VerifiedStationCoordinates verifiedCoordinates;

    public GeoAnchorCalculator(LineRepository lineRepository, LineStationRepository lineStationRepository,
            VerifiedStationCoordinates verifiedCoordinates) {
        this.lineRepository = lineRepository;
        this.lineStationRepository = lineStationRepository;
        this.verifiedCoordinates = verifiedCoordinates;
    }

    /** 역 id -> 배열 인덱스(indexOf)를 기준으로, 캔버스 단위의 anchorX/Y 배열을 계산해 돌려준다. */
    public double[][] compute(Map<Long, Integer> indexOf, int n) {
        Map<String, double[]> realCoords = loadRealCoordinates();
        verifiedCoordinates.all().forEach((name, point) ->
                realCoords.put(name, new double[] {point.lat(), point.lng()}));
        Map<String, double[]> regionCenters = computeEmpiricalRegionCenters(realCoords);

        double[] sumLat = new double[n];
        double[] sumLng = new double[n];
        double[] weight = new double[n];
        double[][] acceptedRealCoords = new double[n][];

        for (Line line : lineRepository.findAll()) {
            List<LineStation> ordered = lineStationRepository.findByLine_IdOrderBySequenceNoAsc(line.getId());
            if (ordered.isEmpty()) {
                continue;
            }
            List<String> regions = parseRegions(line.getRegionNames());

            List<Integer> anchorPos = new ArrayList<>();
            List<double[]> anchorLatLng = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                double[] latLng = realCoords.get(ordered.get(i).getStation().getName());
                if (latLng != null && (verifiedCoordinates.contains(ordered.get(i).getStation().getName())
                        || isPlausible(latLng, regions))) {
                    anchorPos.add(i);
                    anchorLatLng.add(latLng);
                    Integer idx = indexOf.get(ordered.get(i).getStation().getId());
                    if (idx != null) {
                        acceptedRealCoords[idx] = latLng;
                    }
                }
            }
            // 이 노선이 확보한 실측 닻 개수를, 여러 노선이 같은 역을 공유할 때 최종 평균에서
            // 이 노선의 발언권으로 쓴다. 실측 닻이 많아 신뢰도가 높은 노선(예: 실측 앵커가
            // 15개인 영동선)이, 지역 중심으로만 대충 채운 짧은 지선(예: 실측 앵커 0~1개인
            // 삼척선)보다 공유역의 최종 위치를 더 많이 좌우하게 한다.
            double lineWeight = 1 + anchorPos.size();

            // 실측 닻이 2개 미만이면 보간할 "형태"가 안 나온다 — 0개는 위치 정보가 전혀 없는
            // 것이고, 1개뿐이면 노선 전체가 그 한 점 주변에 다 몰려버린다(경원선처럼 실측 닻이
            // 서울 쪽 역 1곳뿐인데 노선이 한참 북쪽까지 이어지는 경우 특히 심각함). 그래서 부족한
            // 만큼 지역본부 중심을 노선 길이에 맞춰 섞어 넣어 최소한의 퍼짐을 보장한다.
            if (anchorPos.size() < 2 && !regions.isEmpty()) {
                List<Integer> existingPos = new ArrayList<>(anchorPos);
                List<Integer> regionPos = new ArrayList<>();
                List<double[]> regionLatLng = new ArrayList<>();
                for (String region : regions) {
                    double[] center = regionCenters.get(region);
                    if (center == null) {
                        continue;
                    }
                    regionPos.add(0); // 아래에서 노선 길이에 맞춰 다시 분산시킬 임시값
                    regionLatLng.add(center);
                }
                for (int k = 0; k < regionPos.size(); k++) {
                    int pos;
                    if (regionPos.size() == 1 && !existingPos.isEmpty()) {
                        // 실측 닻이 이미 하나 있는데 채워 넣을 지역 닻이 1개뿐이면(가장 흔한 경우),
                        // 항상 index 0을 겨냥하는 일반 공식 대신 그 실측 닻에서 가장 먼 반대쪽
                        // 끝에 놓는다. 그래야 실측 닻이 하필 첫 역(흔히 기점역이 실측 닻인 경우가
                        // 많음)일 때도 바로 옆 칸에 다닥다닥 붙지 않고 노선 전체에 제대로 퍼진다.
                        int refPos = existingPos.get(0);
                        pos = (refPos < ordered.size() / 2) ? ordered.size() - 1 : 0;
                    } else {
                        pos = (int) ((long) k * (ordered.size() - 1) / Math.max(1, regionPos.size() - 1));
                    }
                    // 그래도 실측 닻과 정확히 같은 인덱스에 겹치면 보간이 "그 실측값과 반반
                    // 섞기"로 망가지므로, 겹치면 옆 칸으로 살짝 밀어낸다.
                    while (existingPos.contains(pos) || regionPos.subList(0, k).contains(pos)) {
                        pos = (pos < ordered.size() - 1) ? pos + 1 : pos - 1;
                    }
                    regionPos.set(k, pos);
                }
                anchorPos.addAll(regionPos);
                anchorLatLng.addAll(regionLatLng);
                sortAnchorsByPosition(anchorPos, anchorLatLng);
            }
            if (anchorPos.isEmpty()) {
                continue; // 지역 정보조차 없는 노선(사실상 없음): 기본값에 맡긴다.
            }

            // 지역본부가 1개뿐인 노선(그래서 위 병합 후에도 닻이 여전히 1개)은 아직 보간할
            // "형태"가 없다. 이때 노선 id로 고정된(재현 가능한, 그러나 노선마다 다른) 두 번째
            // 가상 지점을 같은 지역 범위 안에 하나 더 만들어 섞는다. 이렇게 하면 (a) 실측 닻이
            // 하나뿐이라고 노선 전체가 한 점에 뭉치지 않고 순서대로 쭉 이어지는 모양이 나오고,
            // (b) 같은 지역을 공유하는 서로 다른 노선끼리 완전히 같은 좌표로 겹치지 않는다
            // (예전엔 지역이 같으면 모든 노선이 "지역 중심점 + index 기반 나선"이라는 같은 공식을
            // 써서, 일산선·안산선·수인선처럼 노선은 다른데 좌표가 그대로 겹치는 버그가 있었다).
            if (anchorPos.size() == 1) {
                double[] box = combinedBounds(regions);
                java.util.Random rnd = new java.util.Random(line.getId());
                double[] synthetic = pickNearRealStation(box, realCoords, rnd);
                int realIdx = anchorPos.get(0);
                int syntheticIdx = (realIdx < ordered.size() / 2) ? ordered.size() - 1 : 0;
                if (syntheticIdx != realIdx) {
                    anchorPos.add(syntheticIdx);
                    anchorLatLng.add(synthetic);
                    sortAnchorsByPosition(anchorPos, anchorLatLng);
                }
            }

            for (int i = 0; i < ordered.size(); i++) {
                Integer idx = indexOf.get(ordered.get(i).getStation().getId());
                if (idx == null) {
                    continue;
                }
                double[] latLng = interpolate(i, anchorPos, anchorLatLng);
                sumLat[idx] += latLng[0] * lineWeight;
                sumLng[idx] += latLng[1] * lineWeight;
                weight[idx] += lineWeight;
            }
        }

        double[] lat = new double[n];
        double[] lng = new double[n];
        for (int i = 0; i < n; i++) {
            // 어느 소속 노선에서든 지역 검증을 통과한 실측값은 확정 위치다. 다른 노선의
            // 지역 경계 오판/가상 닻/외삽 결과를 평균해서 그 위치를 밀어내면 안 된다.
            if (acceptedRealCoords[i] != null) {
                lat[i] = acceptedRealCoords[i][0];
                lng[i] = acceptedRealCoords[i][1];
            } else if (weight[i] > 0) {
                lat[i] = sumLat[i] / weight[i];
                lng[i] = sumLng[i] / weight[i];
            } else {
                lat[i] = DEFAULT_LAT;
                lng[i] = DEFAULT_LNG;
            }
        }

        return projectToCanvas(lat, lng, n);
    }

    /**
     * 위경도를 평면에 투영한다. {@link #LAT_MIN}~{@link #LNG_MAX}로 정의된 고정 범위를 기준으로
     * 삼고(역 데이터가 아니라!), 경도 1도의 실제 거리가 위도 1도보다 짧다는 점(위도에 따른 보정,
     * cos)을 반영한 뒤 x/y에 같은 배율을 적용해서(따로따로 늘리지 않음) 실제 한국 지형의 가로세로
     * 비율이 그대로 유지되게 한다. 배경 윤곽선(static/data/korea-outline.json)도 똑같은 공식으로
     * 미리 투영해 뒀으므로, 이 메서드가 만든 좌표는 그 위에 그대로 겹쳐 그릴 수 있다.
     */
    private double[][] projectToCanvas(double[] lat, double[] lng, int n) {
        double meanLat = (LAT_MIN + LAT_MAX) / 2;
        double lngScale = Math.cos(Math.toRadians(meanLat));

        double widthDeg = (LNG_MAX - LNG_MIN) * lngScale;
        double heightDeg = LAT_MAX - LAT_MIN;

        double margin = DiagramLayoutRunner.AREA_WIDTH * CANVAS_MARGIN_RATIO;
        double targetW = DiagramLayoutRunner.AREA_WIDTH - margin * 2;
        double targetH = DiagramLayoutRunner.AREA_HEIGHT - margin * 2;

        double scale = Math.min(targetW / widthDeg, targetH / heightDeg);
        double offsetX = margin + (targetW - widthDeg * scale) / 2;
        double offsetY = margin + (targetH - heightDeg * scale) / 2;

        double[] anchorX = new double[n];
        double[] anchorY = new double[n];
        for (int i = 0; i < n; i++) {
            anchorX[i] = offsetX + (lng[i] - LNG_MIN) * lngScale * scale;
            anchorY[i] = offsetY + (LAT_MAX - lat[i]) * scale;
        }
        return new double[][] {anchorX, anchorY};
    }

    /** 실측 좌표가 그 노선이 지나는 지역본부들 중 하나의 대략적인 범위 안에 있는지 검증한다. */
    private boolean isPlausible(double[] latLng, List<String> regions) {
        if (regions.isEmpty()) {
            return true; // 대조할 지역 정보가 없으면 검증 없이 신뢰한다.
        }
        for (String region : regions) {
            double[] b = REGION_BOUNDS.get(region);
            if (b == null) {
                continue;
            }
            if (latLng[0] >= b[0] && latLng[0] <= b[1] && latLng[1] >= b[2] && latLng[1] <= b[3]) {
                return true;
            }
        }
        return false;
    }

    /** posIndex 위치의 역 위경도를, 노선 순서상 앞뒤로 가장 가까운 두 닻 사이에서 선형 보간(구간 밖은 외삽)한다. */
    private double[] interpolate(int posIndex, List<Integer> anchorPos, List<double[]> anchorLatLng) {
        if (anchorPos.size() == 1) {
            return anchorLatLng.get(0);
        }
        int lo = -1;
        int hi = -1;
        for (int k = 0; k < anchorPos.size(); k++) {
            if (anchorPos.get(k) <= posIndex) {
                lo = k;
            }
            if (anchorPos.get(k) >= posIndex && hi == -1) {
                hi = k;
            }
        }
        if (lo == -1) {
            lo = 0;
            hi = 1;
        } else if (hi == -1) {
            hi = anchorPos.size() - 1;
            lo = hi - 1;
        } else if (lo == hi) {
            return anchorLatLng.get(lo);
        }

        int posLo = anchorPos.get(lo);
        int posHi = anchorPos.get(hi);
        double t = (posHi == posLo) ? 0.5 : (double) (posIndex - posLo) / (posHi - posLo);
        double[] a = anchorLatLng.get(lo);
        double[] b = anchorLatLng.get(hi);
        return new double[] {a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])};
    }

    /**
     * 지역본부 중심을 손으로 그린 사각형의 기하학적 중점이 아니라, 그 사각형 안에 들어오는 실측
     * 역 좌표들의 평균으로 계산한다. 서해안처럼 해안선이 들쭉날쭉한 지역은 사각형의 기하학적
     * 중점이 바다 위로 떨어지는 경우가 실제로 있었다(예: 수도권서부본부 박스 중점이 바다였음).
     * 실측 역은 당연히 전부 육지에 있으므로, 그 평균은 항상 육지 위의 현실적인 지점이 된다.
     */
    private Map<String, double[]> computeEmpiricalRegionCenters(Map<String, double[]> realCoords) {
        Map<String, double[]> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (double[] latLng : realCoords.values()) {
            for (Map.Entry<String, double[]> entry : REGION_BOUNDS.entrySet()) {
                double[] b = entry.getValue();
                if (latLng[0] >= b[0] && latLng[0] <= b[1] && latLng[1] >= b[2] && latLng[1] <= b[3]) {
                    String region = entry.getKey();
                    double[] sum = sums.computeIfAbsent(region, k -> new double[2]);
                    sum[0] += latLng[0];
                    sum[1] += latLng[1];
                    counts.merge(region, 1, Integer::sum);
                }
            }
        }
        Map<String, double[]> centers = new HashMap<>();
        for (Map.Entry<String, double[]> entry : REGION_BOUNDS.entrySet()) {
            String region = entry.getKey();
            int count = counts.getOrDefault(region, 0);
            if (count > 0) {
                double[] sum = sums.get(region);
                centers.put(region, new double[] {sum[0] / count, sum[1] / count});
            } else {
                // 그 지역에 실측 역이 하나도 없으면(이론상 드묾) 기하학적 중점으로 대체한다.
                double[] b = entry.getValue();
                centers.put(region, new double[] {(b[0] + b[1]) / 2, (b[2] + b[3]) / 2});
            }
        }
        return centers;
    }

    /** 노선이 지나는 지역본부들의 범위를 하나로 합친다(없으면 대한민국 전체 범위로 대신한다). */
    private double[] combinedBounds(List<String> regions) {
        double latMin = Double.MAX_VALUE;
        double latMax = -Double.MAX_VALUE;
        double lngMin = Double.MAX_VALUE;
        double lngMax = -Double.MAX_VALUE;
        boolean found = false;
        for (String region : regions) {
            double[] b = REGION_BOUNDS.get(region);
            if (b == null) {
                continue;
            }
            found = true;
            latMin = Math.min(latMin, b[0]);
            latMax = Math.max(latMax, b[1]);
            lngMin = Math.min(lngMin, b[2]);
            lngMax = Math.max(lngMax, b[3]);
        }
        return found ? new double[] {latMin, latMax, lngMin, lngMax} : new double[] {LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX};
    }

    /**
     * box 범위 안에 있는 실측 역들 중 하나를(노선마다 고정된 rnd로) 골라 살짝 흩어서 돌려준다.
     * 순수 무작위 좌표는 서해안처럼 해안선이 복잡한 지역에서 바다 위에 떨어질 수 있는데, 실측
     * 역은 항상 육지 위에 있으므로 이 방식이 훨씬 안전하다. 그 범위에 실측 역이 하나도 없으면
     * (드묾) 박스 안 무작위 좌표로 대체한다.
     */
    private double[] pickNearRealStation(double[] box, Map<String, double[]> realCoords, java.util.Random rnd) {
        List<double[]> candidates = new ArrayList<>();
        for (double[] latLng : realCoords.values()) {
            if (latLng[0] >= box[0] && latLng[0] <= box[1] && latLng[1] >= box[2] && latLng[1] <= box[3]) {
                candidates.add(latLng);
            }
        }
        if (candidates.isEmpty()) {
            double lat = box[0] + rnd.nextDouble() * (box[1] - box[0]);
            double lng = box[2] + rnd.nextDouble() * (box[3] - box[2]);
            return new double[] {lat, lng};
        }
        double[] picked = candidates.get(rnd.nextInt(candidates.size()));
        // 여러 노선이 같은 실측 역을 고르더라도 완전히 같은 점이 되지 않게 아주 살짝(약 1~2km) 흩는다.
        double jitter = 0.015;
        return new double[] {picked[0] + (rnd.nextDouble() - 0.5) * jitter, picked[1] + (rnd.nextDouble() - 0.5) * jitter};
    }

    /** 실측 닻과 지역본부 "가짜 닻"을 섞은 뒤, interpolate()가 가정하는 대로 위치(index) 오름차순으로 정렬한다. */
    private void sortAnchorsByPosition(List<Integer> anchorPos, List<double[]> anchorLatLng) {
        Integer[] order = new Integer[anchorPos.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(anchorPos.get(a), anchorPos.get(b)));

        List<Integer> sortedPos = new ArrayList<>(anchorPos.size());
        List<double[]> sortedLatLng = new ArrayList<>(anchorLatLng.size());
        for (int idx : order) {
            sortedPos.add(anchorPos.get(idx));
            sortedLatLng.add(anchorLatLng.get(idx));
        }
        anchorPos.clear();
        anchorPos.addAll(sortedPos);
        anchorLatLng.clear();
        anchorLatLng.addAll(sortedLatLng);
    }

    private List<String> parseRegions(String regionNames) {
        if (regionNames == null || regionNames.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String r : regionNames.split(",")) {
            result.add(r.trim());
        }
        return result;
    }

    private Map<String, double[]> loadRealCoordinates() {
        Map<String, double[]> result = new HashMap<>();
        if (!Files.exists(COORDINATES_FILE)) {
            log.warn("역 실측 좌표 파일이 없어 지역본부 근사 배치만 사용합니다: {}", COORDINATES_FILE);
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(COORDINATES_FILE, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) { // 0번째는 헤더
                String line = lines.get(i).replace("﻿", "").replace("\"", "").trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    continue;
                }
                try {
                    double lat = Double.parseDouble(parts[1]);
                    double lng = Double.parseDouble(parts[2]);
                    double[] override = COORDINATE_OVERRIDES.get(parts[0]);
                    result.put(parts[0], override != null ? override : new double[] {lat, lng});
                } catch (NumberFormatException ignored) {
                    // 잘못된 행은 건너뛴다.
                }
            }
            log.info("역 실측 좌표 {}건을 불러왔습니다 ({})", result.size(), COORDINATES_FILE);
        } catch (IOException e) {
            log.warn("역 실측 좌표 파일을 읽지 못했습니다: {}", COORDINATES_FILE, e);
        }
        return result;
    }
}
