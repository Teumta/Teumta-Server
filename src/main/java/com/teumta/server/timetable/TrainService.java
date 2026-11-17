package com.teumta.server.timetable;

import com.teumta.server.timetable.dto.TrainResponse;
import com.teumta.server.timetable.dto.TrainsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TrainService {
    private final TrainRepository trainRepository;
    private final LocalDate baseDate;

    public TrainService(TrainRepository trainRepository, @Value("${teumta.timetable.base-date}") LocalDate baseDate) {
        this.trainRepository = trainRepository;
        this.baseDate = baseDate;
    }

    // readOnly: 읽기만 해서 변경 감지(스냅샷 비교)를 건너뛴다. 응답(DTO)은 이 안에서 다 만든다(open-in-view 꺼 둠)
    @Transactional(readOnly = true)
    public TrainsResponse getTrains(DayType dayType) {
        List<TrainResponse> trains = trainRepository.findAllWithStops(dayType).stream()
                .map(TrainResponse::from)
                .toList();
        return new TrainsResponse(baseDate, dayType, trains);
    }
}
