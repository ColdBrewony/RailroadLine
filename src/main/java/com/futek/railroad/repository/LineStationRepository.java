package com.futek.railroad.repository;

import com.futek.railroad.domain.LineStation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineStationRepository extends JpaRepository<LineStation, Long> {

    /** 노선 상세(F4): 노선 내 역 목록을 순서대로. */
    List<LineStation> findByLine_IdOrderBySequenceNoAsc(Long lineId);

    /**
     * 관제 콘솔 시더(F3과 무관)용: {@link com.futek.railroad.domain.Station}을 즉시 로딩해서 반환한다.
     * 시더는 트랜잭션 경계 밖에서 역 이름을 읽으므로, LAZY 프록시를 그대로 넘기면
     * {@code LazyInitializationException}이 난다.
     */
    @Query("SELECT ls FROM LineStation ls JOIN FETCH ls.station WHERE ls.line.id = :lineId ORDER BY ls.sequenceNo ASC")
    List<LineStation> findByLine_IdWithStationOrderBySequenceNoAsc(@Param("lineId") Long lineId);

    /** 역 상세(F3): 이 역이 속한 모든 노선-역 관계(환승/분기역이면 2개 이상). */
    List<LineStation> findByStation_IdOrderByLine_IdAsc(Long stationId);

    /** 노선 목록(F4)의 stationCount 표시용. */
    long countByLine_Id(Long lineId);

    /** 환승역 판별(둘 이상이면 환승/분기역)용. */
    long countByStation_Id(Long stationId);
}
