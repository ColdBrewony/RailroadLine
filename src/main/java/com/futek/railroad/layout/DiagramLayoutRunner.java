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
    private static final double GRAVITY_STRENGTH = 0.02;
    private static final double CANVAS_PADDING = 80;

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

        double[] x = new double[n];
        double[] y = new double[n];
        initializePositions(x, y, n);

        runForceDirectedLayout(x, y, n, edges);

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

    /** 초기 배치를 원형으로 흩뿌려, 전부 같은 점에서 시작해 힘이 0이 되는 퇴화 상태를 피한다. */
    private void initializePositions(double[] x, double[] y, int n) {
        double radius = Math.min(AREA_WIDTH, AREA_HEIGHT) * 0.35;
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            x[i] = AREA_WIDTH / 2 + radius * Math.cos(angle);
            y[i] = AREA_HEIGHT / 2 + radius * Math.sin(angle);
        }
    }

    private void runForceDirectedLayout(double[] x, double[] y, int n, List<int[]> edges) {
        double area = AREA_WIDTH * AREA_HEIGHT;
        double k = Math.sqrt(area / n);
        double temperature = AREA_WIDTH / 10;
        double centerX = AREA_WIDTH / 2;
        double centerY = AREA_HEIGHT / 2;

        double[] dispX = new double[n];
        double[] dispY = new double[n];

        for (int iter = 0; iter < ITERATIONS; iter++) {
            Arrays.fill(dispX, 0);
            Arrays.fill(dispY, 0);

            applyRepulsiveForces(x, y, n, k, dispX, dispY);
            applyAttractiveForces(x, y, edges, k, dispX, dispY);
            applyGravity(x, y, n, centerX, centerY, dispX, dispY);
            applyDisplacement(x, y, n, temperature, dispX, dispY);

            temperature *= COOLING_FACTOR;
        }

        // 시뮬레이션 중에는 좌표를 경계로 강제하지 않고(clamp 시 무관한 노드들이 같은
        // 모서리에 뭉치는 문제가 있었음), 끝난 뒤 실제 좌표 범위를 캔버스 크기로 한 번만 맞춘다.
        normalizeToCanvas(x, y, n);
    }

    /** 서로 연결되지 않은 컴포넌트(별개 노선망)가 무한히 멀어지지 않도록 중심으로 약하게 당긴다. */
    private void applyGravity(double[] x, double[] y, int n, double centerX, double centerY, double[] dispX, double[] dispY) {
        for (int i = 0; i < n; i++) {
            dispX[i] += (centerX - x[i]) * GRAVITY_STRENGTH;
            dispY[i] += (centerY - y[i]) * GRAVITY_STRENGTH;
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
