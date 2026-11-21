package com.teumta.server.realtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 서울 열린데이터광장 실시간 열차 위치(realtimePosition)의 한 줄 = 열차 한 편이 지금 있는 곳. 쓰는 값만 받는다.
// 값은 모두 글자로 온다. 예) updnLine "1", trainSttus "0", recptnDt "2026-11-21 19:27:33"
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulTrainPosition(
        String subwayId,    // 노선. 1호선은 1001
        String trainNo,     // 열차 번호. 1000번대는 급행, 7000번대는 광명 셔틀
        String statnNm,     // 열차가 지금 있는 역(trainSttus가 이 역 기준)
        String statnTnm,    // 행선지 역
        String updnLine,    // 0 상행(연천 쪽), 1 하행
        String trainSttus,  // 0 진입, 1 도착, 2 출발, 3 전역 출발
        String directAt,    // 1이면 급행
        String recptnDt     // 이 위치를 받은 시각(한국 시간)
) {
}
