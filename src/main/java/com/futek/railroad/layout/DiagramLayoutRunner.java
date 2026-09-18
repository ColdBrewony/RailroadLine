package com.futek.railroad.layout;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.Station;
import com.futek.railroad.repository.LineRepository;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.repository.StationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전체 노선망(모든 노선-역 연결)을 하나의 그래프로 보고, Fruchterman-Reingold 방식의
 * force-directed 알고리즘으로 겹치지 않는 배치 좌표({@link Station#getDiagramX()}/{@code Y})를
 * 계산해 저장한다.
 *
 * <p>사용자가 요청한 "지하철 노선도처럼 서로 연결된 통합 노선도"를 위한 1차 자동 배치이며,
 * 직선/45도로 딱 떨어지는 정갈한 옥틸리니어 배치는 아니다(그런 배치는 전용 알고리즘이나
 * 수작업 다듬기가 추가로 필요함, docs/05-frontend-design.md 참고).
 *
 * <p>실제 위경도 좌표는 수집하지 않았으므로(Station.lat/lng 없음), 대신 각 노선이 수집 당시
 * 기록해 둔 {@link Line#getRegionNames()}(코레일 지역본부 목록, 노선 순서대로)를 실제 한국
 * 지형에 대략 대응하는 좌표({@link #REGION_ANCHORS})로 매핑해 "약한 중력"으로 끌어당긴다.
 * 완전히 정확한 지리 배치는 아니지만, 서로 무관한 노선들이 전혀 다른 지역인데도 화면 중앙
 * 근처에서 뒤엉키는 것을 줄이고 전체적으로 한국 지형과 비슷한 윤곽을 갖게 한다.
 *
 * <p>{@link com.futek.railroad.seed.DataSeeder} 다음에 실행되어야 하고, 이미 좌표가 계산돼
 * 있으면(모든 역의 diagramX가 not null) 다시 계산하지 않는다(재시작 시 매번 다시 도는 것 방지).
 */
@Component
@Order(2)
public class DiagramLayoutRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DiagramLayoutRunner.class);

    private static final int ITERATIONS = 300;
    private static final double AREA_WIDTH = 4000;
    private static final double AREA_HEIGHT = 4000;
    private static final double COOLING_FACTOR = 0.97;
    private static final double REGION_GRAVITY_STRENGTH = 0.05;
    private static final double CANVAS_PADDING = 80;
    private static final double ANCHOR_MARGIN_RATIO = 0.12;

    /**
     * 코레일 지역본부 이름 -> 한국 지형에서 대략 그 지역이 위치한 정규화 좌표(0~1, x=서→동, y=북→남).
     * 실측 위경도가 아니라 눈대중으로 잡은 근사치이며, 노선이 지나는 지역본부 순서를 이용해
     * 노선 위 각 역을 이 좌표들 사이로 보간 배치하기 위한 용도다.
     */
    private static final Map<String, double[]> REGION_ANCHORS = Map.ofEntries(
            Map.entry("서울본부", new double[] {0.38, 0.10}),
            Map.entry("수도권서부본부", new double[] {0.22, 0.13}),
            Map.entry("수도권동부본부", new double[] {0.50, 0.15}),
            Map.entry("강원본부", new double[] {0.75, 0.18}),
            Map.entry("충북본부", new double[] {0.50, 0.33}),
            Map.entry("대전충남본부", new double[] {0.30, 0.37}),
            Map.entry("전북본부", new double[] {0.28, 0.52}),
            Map.entry("광주본부", new double[] {0.22, 0.62}),
            Map.entry("전남본부", new double[] {0.20, 0.74}),
            Map.entry("경북본부", new double[] {0.60, 0.48}),
            Map.entry("대구본부", new double[] {0.58, 0.62}),
            Map.entry("부산경남본부", new double[] {0.64, 0.80}));

    private final StationRepository stationRepository;
    private final LineRepository lineRepository;
    private final LineStationRepository lineStationRepository;

    public DiagramLayoutRunner(
            StationRepository stationRepository,
            LineRepository lineRepository,
            LineStationRepository lineStationRepository) {
        this.stationRepository = stationRepository;
        this.lineRepository = lineRepository;
        this.lineStationRepository = lineStationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Station> stations = stationRepository.findAll();
        if (stations.isEmpty()) {
            return;
        }
        boolean alreadyComputed = stations.stream().allMatch(s -> s.getDiagramX() != null);
        if (alreadyComputed) {
            log.info("노선도 레이아웃: 이미 계산되어 있어 건너뜀 (역 {}개)", stations.size());
            return;
        }

        int n = stations.size();
        Map<Long, Integer> indexOf = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            indexOf.put(stations.get(i).getId(), i);
        }

        List<int[]> edges = buildEdges(indexOf);

        double[] anchorX = new double[n];
        double[] anchorY = new double[n];
        computeRegionAnchors(indexOf, anchorX, anchorY);

        double[] x = new double[n];
        double[] y = new double[n];
        initializePositions(x, y, anchorX, anchorY, n);

        runForceDirectedLayout(x, y, n, edges, anchorX, anchorY);

        for (int i = 0; i < n; i++) {
            Station station = stations.get(i);
            station.setDiagramX(round(x[i]));
            station.setDiagramY(round(y[i]));
        }
        stationRepository.saveAll(stations);

        log.info("노선도 레이아웃 계산 완료: 역 {}개, 연결 {}개, {}회 반복", n, edges.size(), ITERATIONS);
    }

    private List<int[]> buildEdges(Map<Long, Integer> indexOf) {
        List<int[]> edges = new ArrayList<>();
        for (Line line : lineRepository.findAll()) {
            List<LineStation> ordered = lineStationRepository.findByLine_IdOrderBySequenceNoAsc(line.getId());
            for (int i = 0; i < ordered.size() - 1; i++) {
                Integer a = indexOf.get(ordered.get(i).getStation().getId());
                Integer b = indexOf.get(ordered.get(i + 1).getStation().getId());
                if (a != null && b != null) {
                    edges.add(new int[] {a, b});
                }
            }
        }
        return edges;
    }

    /**
     * 각 노선이 지나는 지역본부 순서({@link Line#getRegionNames()})를 이용해, 노선 위 역들을
     * 그 지역본부들 사이로 순서대로 보간해 역별 목표 앵커 좌표(anchorX/Y, 캔버스 단위)를 구한다.
     * 한 역이 여러 노선에 걸치면(환승역) 노선별로 나온 앵커의 평균을 쓴다. 지역 정보가 전혀
     * 없는 역은 캔버스 중앙을 기본값으로 쓴다.
     */
    private void computeRegionAnchors(Map<Long, Integer> indexOf, double[] anchorX, double[] anchorY) {
        int n = anchorX.length;
        double[] sumX = new double[n];
        double[] sumY = new double[n];
        int[] count = new int[n];

        for (Line line : lineRepository.findAll()) {
            if (line.getRegionNames() == null || line.getRegionNames().isBlank()) {
                continue;
            }
            String[] regionNames = line.getRegionNames().split(",");
            List<double[]> anchors = new ArrayList<>(regionNames.length);
            for (String regionName : regionNames) {
                double[] anchor = REGION_ANCHORS.get(regionName.trim());
                if (anchor != null) {
                    anchors.add(anchor);
                }
            }
            if (anchors.isEmpty()) {
                continue;
            }

            List<LineStation> ordered = lineStationRepository.findByLine_IdOrderBySequenceNoAsc(line.getId());
            int stationCount = ordered.size();
            for (int i = 0; i < stationCount; i++) {
                Integer idx = indexOf.get(ordered.get(i).getStation().getId());
                if (idx == null) {
                    continue;
                }
                int regionIdx = (int) Math.min(anchors.size() - 1, (long) i * anchors.size() / Math.max(1, stationCount));
                double[] anchor = anchors.get(regionIdx);
                sumX[idx] += toCanvasX(anchor[0]);
                sumY[idx] += toCanvasY(anchor[1]);
                count[idx]++;
            }
        }

        for (int i = 0; i < n; i++) {
            if (count[i] > 0) {
                anchorX[i] = sumX[i] / count[i];
                anchorY[i] = sumY[i] / count[i];
            } else {
                anchorX[i] = AREA_WIDTH / 2;
                anchorY[i] = AREA_HEIGHT / 2;
            }
        }
    }

    private double toCanvasX(double normalizedX) {
        double margin = AREA_WIDTH * ANCHOR_MARGIN_RATIO;
        return margin + normalizedX * (AREA_WIDTH - margin * 2);
    }

    private double toCanvasY(double normalizedY) {
        double margin = AREA_HEIGHT * ANCHOR_MARGIN_RATIO;
        return margin + normalizedY * (AREA_HEIGHT - margin * 2);
    }

    /** 초기 배치를 각자의 지역 앵커 근처에 약간씩 흩뿌려서 시작한다(전부 같은 점이면 힘이 0인 퇴화 상태). */
    private void initializePositions(double[] x, double[] y, double[] anchorX, double[] anchorY, int n) {
        double jitter = Math.min(AREA_WIDTH, AREA_HEIGHT) * 0.03;
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            x[i] = anchorX[i] + jitter * Math.cos(angle);
            y[i] = anchorY[i] + jitter * Math.sin(angle);
        }
    }

    private void runForceDirectedLayout(
            double[] x, double[] y, int n, List<int[]> edges, double[] anchorX, double[] anchorY) {
        double area = AREA_WIDTH * AREA_HEIGHT;
        double k = Math.sqrt(area / n);
        double temperature = AREA_WIDTH / 10;

        double[] dispX = new double[n];
        double[] dispY = new double[n];

        for (int iter = 0; iter < ITERATIONS; iter++) {
            Arrays.fill(dispX, 0);
            Arrays.fill(dispY, 0);

            applyRepulsiveForces(x, y, n, k, dispX, dispY);
            applyAttractiveForces(x, y, edges, k, dispX, dispY);
            applyGravity(x, y, n, anchorX, anchorY, dispX, dispY);
            applyDisplacement(x, y, n, temperature, dispX, dispY);

            temperature *= COOLING_FACTOR;
        }

        // 시뮬레이션 중에는 좌표를 경계로 강제하지 않고(clamp 시 무관한 노드들이 같은
        // 모서리에 뭉치는 문제가 있었음), 끝난 뒤 실제 좌표 범위를 캔버스 크기로 한 번만 맞춘다.
        normalizeToCanvas(x, y, n);
    }

    /** 각 역을 자신의 지역 앵커 쪽으로 약하게 당겨서, 무관한 지역의 노선끼리 뒤엉키는 것을 줄인다. */
    private void applyGravity(double[] x, double[] y, int n, double[] anchorX, double[] anchorY, double[] dispX, double[] dispY) {
        for (int i = 0; i < n; i++) {
            dispX[i] += (anchorX[i] - x[i]) * REGION_GRAVITY_STRENGTH;
            dispY[i] += (anchorY[i] - y[i]) * REGION_GRAVITY_STRENGTH;
        }
    }

    /** 최종 좌표의 실제 bounding box를 캔버스(여백 포함)에 맞춰 선형으로 재조정한다. */
    private void normalizeToCanvas(double[] x, double[] y, int n) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, x[i]);
            maxX = Math.max(maxX, x[i]);
            minY = Math.min(minY, y[i]);
            maxY = Math.max(maxY, y[i]);
        }
        double rangeX = Math.max(1.0, maxX - minX);
        double rangeY = Math.max(1.0, maxY - minY);
        double targetW = AREA_WIDTH - CANVAS_PADDING * 2;
        double targetH = AREA_HEIGHT - CANVAS_PADDING * 2;

        for (int i = 0; i < n; i++) {
            x[i] = CANVAS_PADDING + (x[i] - minX) / rangeX * targetW;
            y[i] = CANVAS_PADDING + (y[i] - minY) / rangeY * targetH;
        }
    }

    private void applyRepulsiveForces(double[] x, double[] y, int n, double k, double[] dispX, double[] dispY) {
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = x[i] - x[j];
                double dy = y[i] - y[j];
                double dist = Math.max(0.01, Math.sqrt(dx * dx + dy * dy));
                double force = (k * k) / dist;
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;
                dispX[i] += fx;
                dispY[i] += fy;
                dispX[j] -= fx;
                dispY[j] -= fy;
            }
        }
    }

    private void applyAttractiveForces(double[] x, double[] y, List<int[]> edges, double k, double[] dispX, double[] dispY) {
        for (int[] edge : edges) {
            int a = edge[0];
            int b = edge[1];
            double dx = x[a] - x[b];
            double dy = y[a] - y[b];
            double dist = Math.max(0.01, Math.sqrt(dx * dx + dy * dy));
            double force = (dist * dist) / k;
            double fx = (dx / dist) * force;
            double fy = (dy / dist) * force;
            dispX[a] -= fx;
            dispY[a] -= fy;
            dispX[b] += fx;
            dispY[b] += fy;
        }
    }

    private void applyDisplacement(double[] x, double[] y, int n, double temperature, double[] dispX, double[] dispY) {
        for (int i = 0; i < n; i++) {
            double dist = Math.max(0.01, Math.sqrt(dispX[i] * dispX[i] + dispY[i] * dispY[i]));
            double limited = Math.min(dist, temperature);
            x[i] = x[i] + (dispX[i] / dist) * limited;
            y[i] = y[i] + (dispY[i] / dist) * limited;
        }
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
