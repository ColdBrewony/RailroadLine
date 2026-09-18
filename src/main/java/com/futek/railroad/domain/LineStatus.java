package com.futek.railroad.domain;

/** 노선의 현재 운행 상태. */
public enum LineStatus {
    OPERATING,  // 정상 운행
    CLOSED,     // 완전 폐선
    SUSPENDED,  // 여객 영업 중지 (선로는 존재)
    UNBUILT     // 아직 준공/미개통
}
