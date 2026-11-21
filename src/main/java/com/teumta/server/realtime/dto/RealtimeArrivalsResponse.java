package com.teumta.server.realtime.dto;

import java.time.OffsetDateTime;
import java.util.List;

// 한 역으로 오는 열차 목록. fetchedAt은 서버가 서울시에서 받아 온 시각(최대 90초 전 것을 함께 쓴다)
public record RealtimeArrivalsResponse(String stationId, OffsetDateTime fetchedAt, List<RealtimeArrival> arrivals) {
}
