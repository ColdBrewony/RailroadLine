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

    /** 대한민국 대략 중심(위경도 전부 못 구한 극단적인 경우의 최후 기본값). */
    private static final double DEFAULT_LAT = 36.5;
    private static final double DEFAULT_LNG = 127.8;

    /**
     * 코레일 지역본부 이름 -> 그 지역의 대략적인 위경도 범위(latMin, latMax, lngMin, lngMax).
     * 눈대중으로 잡은 근사치이며 두 가지 용도로 쓰인다: (1) 실측 좌표가 엉뚱한 값인지 검증,
     * (2) 실측 닻이 전혀 없는 노선에서 지역 중심점을 "가짜 닻"으로 대신 쓸 때의 좌표.
     */
    private static final Map<String, double[]> REGION_BOUNDS = Map.ofEntries(
            Map.entry("서울본부", new double[] {37.30, 37.80, 126.70, 127.30}),
            Map.entry("수도권서부본부", new double[] {37.20, 37.80, 126.30, 126.90}),
            Map.entry("수도권동부본부", new double[] {37.20, 37.90, 127.00, 127.60}),
            Map.entry("강원본부", new double[] {37.10, 38.60, 127.60, 129.40}),
            Map.entry("충북본부", new double[] {36.30, 37.20, 127.20, 128.20}),
            Map.entry("대전충남본부", new double[] {36.00, 37.10, 126.30, 127.60}),
            Map.entry("전북본부", new double[] {35.40, 36.20, 126.40, 127.60}),
            Map.entry("광주본부", new double[] {34.80, 35.40, 126.60, 127.20}),
            Map.entry("전남본부", new double[] {34.30, 35.40, 126.20, 127.60}),
            Map.entry("경북본부", new double[] {35.60, 37.10, 128.20, 129.50}),
            Map.entry("대구본부", new double[] {35.60, 36.20, 128.20, 128.90}),
            Map.entry("부산경남본부", new double[] {34.70, 35.70, 127.80, 129.40}));

    private final LineRepository lineRepository;
    private final LineStationRepository lineStationRepository;

    public GeoAnchorCalculator(LineRepository lineRepository, LineStationRepository lineStationRepository) {
        this.lineRepository = lineRepository;
        this.lineStationRepository = lineStationRepository;
    }

    /** 역 id -> 배열 인덱스(indexOf)를 기준으로, 캔버스 단위의 anchorX/Y 배열을 계산해 돌려준다. */
    public double[][] compute(Map<Long, Integer> indexOf, int n) {
        Map<String, double[]> realCoords = loadRealCoordinates();

        double[] sumLat = new double[n];
        double[] sumLng = new double[n];
        int[] count = new int[n];

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
                if (latLng != null && isPlausible(latLng, regions)) {
                    anchorPos.add(i);
                    anchorLatLng.add(latLng);
                }
            }

            if (anchorPos.isEmpty()) {
                // 이 노선엔 실측 닻이 하나도 없다: 지역본부 중심들을 순서대로 "가짜 닻"으로 대신 쓴다.
                for (String region : regions) {
                    double[] bounds = REGION_BOUNDS.get(region);
                    if (bounds == null) {
                        continue;
                    }
                    anchorPos.add(anchorPos.isEmpty() ? 0 : anchorPos.get(anchorPos.size() - 1));
                    anchorLatLng.add(new double[] {(bounds[0] + bounds[1]) / 2, (bounds[2] + bounds[3]) / 2});
                }
                // 지역본부들을 노선 길이에 맞춰 고르게 흩어 놓는다(전부 같은 index면 보간이 안 되므로).
                for (int k = 0; k < anchorPos.size(); k++) {
                    anchorPos.set(k, (int) ((long) k * (ordered.size() - 1) / Math.max(1, anchorPos.size() - 1)));
                }
            }
            if (anchorPos.isEmpty()) {
                continue; // 지역 정보조차 없는 노선(사실상 없음): 기본값에 맡긴다.
            }

            for (int i = 0; i < ordered.size(); i++) {
                Integer idx = indexOf.get(ordered.get(i).getStation().getId());
                if (idx == null) {
                    continue;
                }
                double[] latLng = interpolate(i, anchorPos, anchorLatLng);
                sumLat[idx] += latLng[0];
                sumLng[idx] += latLng[1];
                count[idx]++;
            }
        }

        double[] lat = new double[n];
        double[] lng = new double[n];
        for (int i = 0; i < n; i++) {
            if (count[i] > 0) {
                lat[i] = sumLat[i] / count[i];
                lng[i] = sumLng[i] / count[i];
            } else {
                lat[i] = DEFAULT_LAT;
                lng[i] = DEFAULT_LNG;
            }
        }

        return projectToCanvas(lat, lng, n);
    }

    /** 위경도를 평면(x=경도, y=-위도, 위도에 따른 경도 간격 보정 포함)에 투영하고, 캔버스 크기로 정규화한다. */
    private double[][] projectToCanvas(double[] lat, double[] lng, int n) {
        double meanLat = 0;
        for (double v : lat) {
            meanLat += v;
        }
        meanLat = (n > 0) ? meanLat / n : DEFAULT_LAT;
        double lngScale = Math.cos(Math.toRadians(meanLat));

        double[] px = new double[n];
        double[] py = new double[n];
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            px[i] = lng[i] * lngScale;
            py[i] = -lat[i];
            minX = Math.min(minX, px[i]);
            maxX = Math.max(maxX, px[i]);
            minY = Math.min(minY, py[i]);
            maxY = Math.max(maxY, py[i]);
        }

        double rangeX = Math.max(1e-6, maxX - minX);
        double rangeY = Math.max(1e-6, maxY - minY);
        double margin = DiagramLayoutRunner.AREA_WIDTH * CANVAS_MARGIN_RATIO;
        double targetW = DiagramLayoutRunner.AREA_WIDTH - margin * 2;
        double targetH = DiagramLayoutRunner.AREA_HEIGHT - margin * 2;

        double[] anchorX = new double[n];
        double[] anchorY = new double[n];
        for (int i = 0; i < n; i++) {
            anchorX[i] = margin + (px[i] - minX) / rangeX * targetW;
            anchorY[i] = margin + (py[i] - minY) / rangeY * targetH;
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
                    result.put(parts[0], new double[] {lat, lng});
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
