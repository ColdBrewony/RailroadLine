package com.futek.railroad.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 노선. {@code data/raw/lines/*.json} 파일 하나(또는 segmentLabel로 구분되는 구간 하나)에 대응한다.
 */
@Entity
@Table(name = "line")
public class Line {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String segmentLabel;

    private String originStationName;

    private String terminusStationName;

    private BigDecimal totalDistanceKmOfficial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineStatus status = LineStatus.OPERATING;

    /** 이 노선이 지나는 관할 본부 이름들을 콤마로 이어붙인 텍스트. 역 단위 소속이 아니라 노선 단위 정보다. */
    @Column(length = 500)
    private String regionNames;

    @Column(length = 1000)
    private String remarks;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<LineStation> lineStations = new ArrayList<>();

    protected Line() {
        // JPA
    }

    public Line(String name, String segmentLabel) {
        this.name = name;
        this.segmentLabel = segmentLabel;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSegmentLabel() {
        return segmentLabel;
    }

    public void setSegmentLabel(String segmentLabel) {
        this.segmentLabel = segmentLabel;
    }

    public String getOriginStationName() {
        return originStationName;
    }

    public void setOriginStationName(String originStationName) {
        this.originStationName = originStationName;
    }

    public String getTerminusStationName() {
        return terminusStationName;
    }

    public void setTerminusStationName(String terminusStationName) {
        this.terminusStationName = terminusStationName;
    }

    public BigDecimal getTotalDistanceKmOfficial() {
        return totalDistanceKmOfficial;
    }

    public void setTotalDistanceKmOfficial(BigDecimal totalDistanceKmOfficial) {
        this.totalDistanceKmOfficial = totalDistanceKmOfficial;
    }

    public LineStatus getStatus() {
        return status;
    }

    public void setStatus(LineStatus status) {
        this.status = status;
    }

    public String getRegionNames() {
        return regionNames;
    }

    public void setRegionNames(String regionNames) {
        this.regionNames = regionNames;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<LineStation> getLineStations() {
        return lineStations;
    }

    public void addLineStation(LineStation lineStation) {
        lineStations.add(lineStation);
        lineStation.setLine(this);
    }
}
