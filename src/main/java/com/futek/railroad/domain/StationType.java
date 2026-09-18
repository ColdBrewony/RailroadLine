package com.futek.railroad.domain;

/**
 * 원본 KORAIL 노선도 범례의 역 종류 9종.
 * "고속열차 정차역"은 별도 분류가 아니라 {@link Station#isKtxStop} 플래그로 표현한다.
 */
public enum StationType {
    MANAGED,            // 관리역
    STAFFED,            // 직원배치역
    ENTRUSTED,          // 위탁역
    FREIGHT,            // 화물취급역
    UNMANNED,           // 무인역
    SIGNAL_YARD,        // 신호장
    SIGNAL_STATION,     // 신호소
    TEMPORARY_PLATFORM  // 임시승강장
}
