package com.futek.railroad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * 역/시설. 이름 기준으로 여러 노선에서 공유되는 물리적 지점 하나를 나타낸다.
 * 역 단위 관할 본부(Region) 정보는 수집하지 못했으므로 이 엔티티에는 없다
 * (노선이 지나는 본부는 {@link Line#getRegionNames()} 참고).
 */
@Entity
@Table(name = "station", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "name"))
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    private StationType stationType;

    @Column(nullable = false)
    private boolean ktxStop = false;

    private BigDecimal diagramX;

    private BigDecimal diagramY;

    private BigDecimal lat;

    private BigDecimal lng;

    @Column(length = 1000)
    private String remarks;

    protected Station() {
        // JPA
    }

    public Station(String name) {
        this.name = name;
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

    public StationType getStationType() {
        return stationType;
    }

    public void setStationType(StationType stationType) {
        this.stationType = stationType;
    }

    public boolean isKtxStop() {
        return ktxStop;
    }

    public void setKtxStop(boolean ktxStop) {
        this.ktxStop = ktxStop;
    }

    public BigDecimal getDiagramX() {
        return diagramX;
    }

    public void setDiagramX(BigDecimal diagramX) {
        this.diagramX = diagramX;
    }

    public BigDecimal getDiagramY() {
        return diagramY;
    }

    public void setDiagramY(BigDecimal diagramY) {
        this.diagramY = diagramY;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
