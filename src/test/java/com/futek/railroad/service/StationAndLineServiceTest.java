package com.futek.railroad.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.futek.railroad.service.dto.LineDetail;
import com.futek.railroad.service.dto.LineStationItem;
import com.futek.railroad.service.dto.LineSummary;
import com.futek.railroad.service.dto.StationDetail;
import com.futek.railroad.service.dto.StationLineInfo;
import com.futek.railroad.service.dto.StationSearchItem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 시딩된 데이터(영주역 = 경북선/영동선/중앙선 3개 노선)를 기준으로
 * 서비스 계층이 F2~F4 요구사항대로 동작하는지 확인한다.
 */
@SpringBootTest
class StationAndLineServiceTest {

    @Autowired
    private StationSearchService stationSearchService;

    @Autowired
    private StationDetailService stationDetailService;

    @Autowired
    private LineService lineService;

    @Test
    void 영주역을_검색하면_소속된_3개_노선이_보인다() {
        List<StationSearchItem> results = stationSearchService.search("영주", 20);

        StationSearchItem yeongju = results.stream()
                .filter(item -> item.name().equals("영주"))
                .findFirst()
                .orElseThrow();

        assertThat(yeongju.lineNames()).containsExactlyInAnyOrder("경북선", "영동선", "중앙선");
    }

    @Test
    void 영주역_상세에서_영동선은_기점이라_이전역이_없다() {
        Long stationId = stationSearchService.search("영주", 20).stream()
                .filter(item -> item.name().equals("영주"))
                .findFirst()
                .orElseThrow()
                .stationId();

        Optional<StationDetail> detail = stationDetailService.getDetail(stationId);
        assertThat(detail).isPresent();

        StationLineInfo yeongdongLine = detail.get().lines().stream()
                .filter(l -> l.lineName().equals("영동선"))
                .findFirst()
                .orElseThrow();

        assertThat(yeongdongLine.sequenceNo()).isEqualTo(1);
        assertThat(yeongdongLine.prevStation()).isNull();
        assertThat(yeongdongLine.nextStation()).isNotNull();
        assertThat(yeongdongLine.nextStation().name()).isEqualTo("북영주");
    }

    @Test
    void 영동선_노선_상세는_188_9km_종점까지_37개_역이다() {
        LineSummary yeongdong = lineService.list("영동선", false).stream()
                .findFirst()
                .orElseThrow();

        Optional<LineDetail> detail = lineService.getDetail(yeongdong.lineId());
        assertThat(detail).isPresent();
        assertThat(detail.get().stations()).hasSize(37);
        assertThat(detail.get().stations().get(0).name()).isEqualTo("영주");
        assertThat(detail.get().stations().get(0).transfer()).isTrue();

        LineStationItem terminus = detail.get().stations().get(detail.get().stations().size() - 1);
        assertThat(terminus.name()).isEqualTo("청량신호소");
    }
}
