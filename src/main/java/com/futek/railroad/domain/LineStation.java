package com.futek.railroad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * 노선-역 매핑. 역과 노선의 다대다 관계이자, 노선 내 순서·누적거리를 담는 핵심 테이블.
 * 환승/분기역은 동일 station_id가 서로 다른 line_id로 2개 이상의 행을 갖는 것으로 표현한다.
 */
@Entity
@Table(
        name = "line_station",
        uniqueConstraints = @UniqueConstraint(columnNames = {"line_id", "sequence_no"})
)
public class LineStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = false)
    private Line line;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    /** 노선 기점으로부터의 누적 거리(km). 미개통 역 등은 null 가능(예: 수인선 학익역). */
    private BigDecimal cumulativeKm;

    @Column(length = 1000)
    private String remarks;

    protected LineStation() {
        // JPA
    }

    public LineStation(Station station, int sequenceNo, BigDecimal cumulativeKm) {
        this.station = station;
        this.sequenceNo = sequenceNo;
        this.cumulativeKm = cumulativeKm;
    }

    public Long getId() {
        return id;
    }

    public Line getLine() {
        return line;
    }

    public void setLine(Line line) {
        this.line = line;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public BigDecimal getCumulativeKm() {
        return cumulativeKm;
    }

    public void setCumulativeKm(BigDecimal cumulativeKm) {
        this.cumulativeKm = cumulativeKm;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
