package com.teumta.server.timetable;

import com.teumta.server.timetable.dto.TrainsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trains")
public class TrainController {
    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    // 요일 종류(WEEKDAY, HOLIDAY)의 모든 열차와 정차역. 없는 값을 넣으면 400
    @GetMapping
    public TrainsResponse getTrains(@RequestParam DayType dayType) {
        return trainService.getTrains(dayType);
    }
}
