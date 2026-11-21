package com.teumta.server.realtime.dto;

import com.teumta.server.realtime.TrainStatus;
import com.teumta.server.timetable.Direction;

import java.time.OffsetDateTime;

// 탈 역으로 오는 열차 한 편. 역 이름은 앱과 같은 이름으로 바꿔 둔다(서울 -> 서울역)
public record RealtimeArrival(
        String trainNo,
        Direction direction,           // 지금 달리는 방향
        String terminal,               // 행선지("인천행"의 인천)
        boolean express,
        String currentStation,         // 열차가 지금 있는 역
        TrainStatus currentStatus,     // currentStation에서의 상태
        int stationsAway,              // currentStation에서 탈 역까지 몇 역(탈 역에 있으면 0)
        boolean turnsBack,             // 탈 역이 이 열차의 종점(끝 역)이라, 도착한 뒤 반대로 돌아 나간다
        OffsetDateTime receivedAt      // 서울시가 이 위치를 받은 시각. 길게는 5분 전 것까지 온다
) {
}
