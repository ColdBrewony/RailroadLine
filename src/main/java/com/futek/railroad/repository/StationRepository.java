package com.futek.railroad.repository;

import com.futek.railroad.domain.Station;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, Long> {

    /** 시더가 역 중복 생성을 막기 위해 이름으로 기존 역을 찾을 때 사용. */
    Optional<Station> findByName(String name);

    /** 역 검색 API(F2)용 부분 일치 검색. */
    List<Station> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
