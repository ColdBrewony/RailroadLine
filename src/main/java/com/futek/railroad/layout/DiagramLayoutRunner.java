package com.futek.railroad.layout;

import com.futek.railroad.domain.Station;
import com.futek.railroad.repository.StationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 모든 역의 배치 좌표({@link Station#getDiagramX()}/{@code Y})를 {@link GeoAnchorCalculator}가
 * 계산한 실제(또는 보간된) 위경도 투영 좌표 그대로 저장한다.
 *
 * <p>처음에는 Fruchterman-Reingold force-directed 알고리즘으로 역들을 서로 안 겹치게 자동
 * 재배치했지만, "한국 지도를 배경으로 깔고 그 위에 역을 정확한 좌표로 배치해 달라"는 요청에 따라
 * 좌표를 임의로 밀어내지 않고 GeoAnchorCalculator의 결과를 그대로 쓰는 것으로 바꿨다. 그 결과 서울
 * 등 역이 밀집한 구간은 점/선이 겹칠 수 있다(사용자가 감수하기로 한 트레이드오프).
 *
 * <p>{@link com.futek.railroad.seed.DataSeeder} 다음에 실행되어야 하고, 이미 좌표가 계산돼
 * 있으면(모든 역의 diagramX가 not null) 다시 계산하지 않는다(재시작 시 매번 다시 도는 것 방지).
 */
@Component
@Order(2)
public class DiagramLayoutRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DiagramLayoutRunner.class);

    static final double AREA_WIDTH = 4000;
    static final double AREA_HEIGHT = 4000;

    private final StationRepository stationRepository;
    private final GeoAnchorCalculator geoAnchorCalculator;

    public DiagramLayoutRunner(StationRepository stationRepository, GeoAnchorCalculator geoAnchorCalculator) {
        this.stationRepository = stationRepository;
        this.geoAnchorCalculator = geoAnchorCalculator;
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

        double[][] anchors = geoAnchorCalculator.compute(indexOf, n);
        double[] x = anchors[0];
        double[] y = anchors[1];

        for (int i = 0; i < n; i++) {
            Station station = stations.get(i);
            station.setDiagramX(round(x[i]));
            station.setDiagramY(round(y[i]));
        }
        stationRepository.saveAll(stations);

        log.info("노선도 레이아웃 계산 완료(실좌표 기반, 자동 디클러터링 없음): 역 {}개", n);
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
