package com.futek.railroad.repository;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineRepository extends JpaRepository<Line, Long> {

    /** 시더가 (노선명, 구간라벨) 기준으로 기존 노선을 찾아 재적재를 막을 때 사용. */
    Optional<Line> findByNameAndSegmentLabel(String name, String segmentLabel);

    List<Line> findByStatusOrderByNameAsc(LineStatus status);

    List<Line> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
