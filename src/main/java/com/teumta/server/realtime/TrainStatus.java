package com.teumta.server.realtime;

// 열차가 지금 있는 역(currentStation)에서 어떤 상태인지. 서울시 trainSttus 그대로다
public enum TrainStatus {
    ENTERING("0"),       // 그 역에 들어가는 중
    ARRIVED("1"),        // 그 역에 서 있다
    DEPARTED("2"),       // 그 역을 막 떠났다
    PREV_DEPARTED("3");  // 그 역의 전 역을 떠나 그 역으로 가는 중

    private final String seoulCode;

    TrainStatus(String seoulCode) {
        this.seoulCode = seoulCode;
    }

    // 모르는 코드는 null
    static TrainStatus fromSeoulCode(String code) {
        for (TrainStatus status : values()) {
            if (status.seoulCode.equals(code)) {
                return status;
            }
        }
        return null;
    }

    // 가까운 순서로 줄 세울 때 쓰는 보정(역 하나 = 4). 떠났으면 반 역 더 가깝고, 들어가는 중이면 조금 멀다
    int offset() {
        return switch (this) {
            case DEPARTED -> -2;
            case ARRIVED -> 0;
            case ENTERING -> 1;
            case PREV_DEPARTED -> 2;
        };
    }
}
