package com.teumta.server.realtime;

import java.util.List;

// 1호선 열차 위치를 받아 오는 곳. 실제로는 서울시 API(SeoulSubwayClient)이고,
// 테스트에서는 가짜로 바꿔 끼워 바깥 API를 부르지 않는다
public interface PositionSource {
    // 인증키가 있어야 켜진다. 키가 없으면 서버는 실시간 없이 시간표만 준다
    boolean isEnabled();

    // 지금 다니는 1호선 열차 전체(80편 안팎). 데이터가 없으면 빈 목록
    List<SeoulTrainPosition> fetchLine1();
}
