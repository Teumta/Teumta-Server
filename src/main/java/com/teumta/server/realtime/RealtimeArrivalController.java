package com.teumta.server.realtime;

import com.teumta.server.realtime.dto.RealtimeArrivalsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeArrivalController {
    private final RealtimeArrivalService realtimeArrivalService;

    public RealtimeArrivalController(RealtimeArrivalService realtimeArrivalService) {
        this.realtimeArrivalService = realtimeArrivalService;
    }

    // 탈 역(stationId)으로 오는 1호선 열차. towardStationId(내릴 역)를 주면 그 역까지 가는 열차만.
    // 인증키가 없거나 서울시 API가 실패하거나 하루 한도에 닿으면 503 REALTIME_UNAVAILABLE(앱은 실시간 줄을 숨긴다)
    @GetMapping("/arrivals")
    public RealtimeArrivalsResponse getArrivals(@RequestParam String stationId,
                                                @RequestParam(required = false) String towardStationId) {
        return realtimeArrivalService.getArrivals(stationId, towardStationId);
    }
}
