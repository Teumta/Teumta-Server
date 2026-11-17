package com.teumta.server.timetable.dto;

import com.teumta.server.timetable.Direction;
import com.teumta.server.timetable.StopTime;
import com.teumta.server.timetable.Train;

import java.util.List;

public record TrainResponse(Long id, Direction direction, String headsign, List<StopResponse> stops) {
    // 시각은 운행일 0시 기준 초
    public record StopResponse(String stationId, int arr, int dep) {
        static StopResponse from(StopTime stop) {
            // station은 LAZY 프록시지만 ID만 읽으므로 역 테이블을 따로 조회하지 않는다
            return new StopResponse(stop.getStation().getId(), stop.getArrSec(), stop.getDepSec());
        }
    }

    public static TrainResponse from(Train train) {
        List<StopResponse> stops = train.getStops().stream().map(StopResponse::from).toList();
        return new TrainResponse(train.getId(), train.getDirection(), train.getHeadsign(), stops);
    }
}
