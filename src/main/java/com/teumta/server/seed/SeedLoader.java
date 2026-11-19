package com.teumta.server.seed;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teumta.server.station.Station;
import com.teumta.server.station.StationRepository;
import com.teumta.server.timetable.DayType;
import com.teumta.server.timetable.Direction;
import com.teumta.server.timetable.Train;
import com.teumta.server.timetable.TrainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 서버가 켜질 때 DB가 비어 있으면 resources/seed의 역·시간표 파일을 한 번 넣는다
// 배포 DB에는 처음 한 번만 들어가고, 개발(H2 메모리)은 켤 때마다 새로 들어간다
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final ObjectMapper objectMapper;
    private final Resource stationsFile;
    private final Resource timetableFile;

    public SeedLoader(StationRepository stationRepository, TrainRepository trainRepository, ObjectMapper objectMapper,
                      @Value("${teumta.seed.stations}") Resource stationsFile,
                      @Value("${teumta.seed.timetable}") Resource timetableFile
    ) {
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.objectMapper = objectMapper;
        this.stationsFile = stationsFile;
        this.timetableFile = timetableFile;
    }

    // 역과 시간표를 한 묶음으로 넣는다. 중간에 실패하면 모두 되돌려서 반쯤 들어간 상태로 남지 않는다
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (stationRepository.count() > 0) {
            return;
        }
        Map<String, Station> stationsByName = loadStations();
        int trains = loadTrains(stationsByName);
        log.info("시드를 넣었어요: 역 {}개, 열차 {}편", stationsByName.size(), trains);
    }

    private Map<String, Station> loadStations() throws IOException {
        List<StationSeed> seeds = read(stationsFile, new TypeReference<>() {});
        Map<String, Station> byId = new HashMap<>();
        List<Station> stations = new ArrayList<>();
        // 파일은 연천부터 순서대로라 이웃 역(parent)이 항상 먼저 만들어져 있다
        for (StationSeed seed : seeds) {
            Station parent = seed.parentId() == null ? null : byId.get(seed.parentId());
            Station station = new Station(seed.id(), seed.name(), seed.branch(), seed.order(), parent, seed.expressStop(), seed.transferSec());
            byId.put(station.getId(), station);
            stations.add(station);
        }
        // ID를 직접 정한 엔티티라 saveAll은 넘긴 객체가 아니라 JPA가 관리하는 복사본을 돌려준다. 열차는 그 복사본을 가리켜야 한다
        Map<String, Station> byName = new HashMap<>();
        for (Station saved : stationRepository.saveAll(stations)) {
            byName.put(saved.getName(), saved);
        }
        return byName;
    }

    private int loadTrains(Map<String, Station> stationsByName) throws IOException {
        List<TimetableSeed> groups = read(timetableFile, new TypeReference<>() {});
        List<Train> trains = new ArrayList<>();
        for (TimetableSeed group : groups) {
            DayType dayType = group.day().equals("01") ? DayType.WEEKDAY : DayType.HOLIDAY;
            Direction direction = group.orient().equals("S") ? Direction.DOWN : Direction.UP;
            for (TripSeed trip : group.trips()) {
                Train train = new Train(dayType, direction, trip.end());
                for (StopSeed stop : trip.stops()) {
                    // 급행이 지나치기만 하는 역은 타고 내릴 수 없어서 넣지 않는다
                    if (stop.pass() == 1) {
                        continue;
                    }
                    train.addStop(stationsByName.get(stop.station()), toSec(stop.arr()), toSec(stop.dep()));
                }
                trains.add(train);
            }
        }
        // 정차역은 cascade(PERSIST)로 함께 저장된다
        trainRepository.saveAll(trains);
        return trains.size();
    }

    private <T> T read(Resource resource, TypeReference<T> type) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    // "05:34:30"이나 "24:10"(자정 넘김)을 운행일 0시 기준 초로 바꾼다
    static int toSec(String time) {
        String[] parts = time.split(":");
        int sec = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
        return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + sec;
    }

    // 시드 파일 모양. stations.json의 lat, lng처럼 여기 없는 값은 읽지 않고 넘어간다
    record StationSeed(String id, String name, String branch, int order, String parentId, boolean expressStop, Integer transferSec) {
    }

    // day: 01 평일, 03 휴일 / orient: S 연천에서 멀어지는 방향, N 반대
    record TimetableSeed(String day, String orient, List<TripSeed> trips) {
    }

    record TripSeed(String end, List<StopSeed> stops) {
    }

    // 파일에는 ["연천", "05:27", "05:27", 0]처럼 배열로 들어 있어서, 순서대로 칸에 넣는다
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    record StopSeed(String station, String arr, String dep, int pass) {
    }
}