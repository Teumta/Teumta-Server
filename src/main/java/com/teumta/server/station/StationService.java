package com.teumta.server.station;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StationService {
    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    // 1호선 102개 역을 목록 순서(연천 1 -> 서동탄 102)로 돌려준다
    @Transactional(readOnly = true)
    public List<StationResponse> getStations() {
        return stationRepository.findAllByOrderBySortOrder().stream()
                .map(StationResponse::from)
                .toList();
    }
}
