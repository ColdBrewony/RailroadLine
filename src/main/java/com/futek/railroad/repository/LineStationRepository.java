package com.futek.railroad.repository;

import com.futek.railroad.domain.LineStation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineStationRepository extends JpaRepository<LineStation, Long> {

    /** 노선 상세(F4): 노선 내 역 목록을 순서대로. */
    List<LineStation> findByLine_IdOrderBySequenceNoAsc(Long lineId);

    /** 역 상세(F3): 이 역이 속한 모든 노선-역 관계(환승/분기역이면 2개 이상). */
    List<LineStation> findByStation_IdOrderByLine_IdAsc(Long stationId);
}
