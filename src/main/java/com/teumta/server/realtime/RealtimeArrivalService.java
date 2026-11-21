package com.teumta.server.realtime;

import com.teumta.server.common.error.BusinessException;
import com.teumta.server.common.error.ErrorCode;
import com.teumta.server.realtime.SeoulSubwayClient.SeoulApiException;
import com.teumta.server.realtime.dto.RealtimeArrival;
import com.teumta.server.realtime.dto.RealtimeArrivalsResponse;
import com.teumta.server.station.StationService;
import com.teumta.server.timetable.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 탈 역으로 오는 1호선 열차. 서울시 열차 위치(1호선 전체)를 한 번 받아 모든 역의 답을 만든다.
// 서울시 API는 하루 1,000번까지라
//  - 받은 위치는 90초 동안 모든 역·모든 사람이 같이 쓴다(실패도 90초 동안 다시 묻지 않는다)
//  - 하루 호출 수가 한도(기본 950)에 닿으면 그날은 더 부르지 않고 "지금은 볼 수 없음"으로 답한다
@Service
public class RealtimeArrivalService {
    static final Duration CACHE_TTL = Duration.ofSeconds(90);
    // 받은 지 5분이 넘은 위치는 버린다. 서울시가 오래된 위치를 섞어 줄 때가 있다
    static final Duration STALE_AFTER = Duration.ofMinutes(5);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 앱(시간표) 이름과 서울시 이름이 다른 역. 11/21에 역 102개를 하나씩 물어 찾았다
    private static final Map<String, String> APP_NAMES = Map.of(
            "서울", "서울역",
            "지제", "평택지제",
            "쌍용(나사렛대)", "쌍용");
    private static final DateTimeFormatter RECEIVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LINE_1 = "1001";

    private static final Logger log = LoggerFactory.getLogger(RealtimeArrivalService.class);

    private final PositionSource source;
    private final StationService stationService;
    private final Clock clock;
    private final int dailyLimit;

    private volatile RouteTree tree;
    private volatile Cached cached;
    private final Object fetchLock = new Object();
    private LocalDate callDate;
    private int callsToday;

    // trains가 null이면 받기에 실패한 것
    private record Cached(Instant fetchedAt, List<Train> trains) {
    }

    // 서울시 한 줄을 앱 이름과 나무 모양의 역으로 바꾼 것
    private record Train(String trainNo, Direction direction, RouteTree.Node current, RouteTree.Node terminal,
                         boolean express, TrainStatus status, Instant receivedAt) {
    }

    public RealtimeArrivalService(PositionSource source, StationService stationService, Clock clock,
                                  @Value("${teumta.realtime.daily-limit:950}") int dailyLimit) {
        this.source = source;
        this.stationService = stationService;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    // towardStationId(내릴 역)를 주면 그 역까지 가는 열차만 남긴다(방향, 행선지, 급행이 서는지)
    public RealtimeArrivalsResponse getArrivals(String stationId, String towardStationId) {
        RouteTree routes = routes();
        RouteTree.Node station = find(routes, stationId);
        RouteTree.Node toward = towardStationId == null ? null : find(routes, towardStationId);
        if (station.equals(toward)) {
            throw new BusinessException(ErrorCode.SAME_STATIONS);
        }
        if (!source.isEnabled()) {
            throw new BusinessException(ErrorCode.REALTIME_UNAVAILABLE);
        }
        Cached current = load(routes);
        if (current.trains() == null) {
            throw new BusinessException(ErrorCode.REALTIME_UNAVAILABLE);
        }

        Instant oldest = clock.instant().minus(STALE_AFTER);
        Direction wanted = toward == null ? null : routes.direction(station, toward);
        List<RealtimeArrival> arrivals = new ArrayList<>();
        for (Train train : current.trains()) {
            if (train.receivedAt().isBefore(oldest)) {
                continue;
            }
            RealtimeArrival arrival = approach(routes, train, station, toward, wanted);
            if (arrival != null) {
                arrivals.add(arrival);
            }
        }
        // 가까운 순서. 같은 거리면 돌아 나가는 열차보다 바로 타는 열차를 앞에
        arrivals.sort(Comparator.comparingInt((RealtimeArrival a) -> a.stationsAway() * 4 + a.currentStatus().offset())
                .thenComparing(RealtimeArrival::turnsBack)
                .thenComparing(RealtimeArrival::trainNo));
        return new RealtimeArrivalsResponse(station.id(), OffsetDateTime.ofInstant(current.fetchedAt(), SEOUL), arrivals);
    }

    // 이 열차가 station에 오는가. 오면 몇 역 남았는지를 담아 돌려주고, 아니면 null
    private static RealtimeArrival approach(RouteTree routes, Train train, RouteTree.Node station,
                                            RouteTree.Node toward, Direction wanted) {
        Direction direction = train.direction();
        int stationsAway = routes.stepsAlong(direction, train.current(), station);
        int beyond = routes.stepsAlong(direction, station, train.terminal());
        // station이 열차의 지금 역과 행선지 사이에 있어야 한다. 이미 떠났으면 못 탄다
        if (stationsAway < 0 || beyond < 0 || (stationsAway == 0 && train.status() == TrainStatus.DEPARTED)) {
            return null;
        }
        boolean turnsBack = beyond == 0;
        if (turnsBack && !routes.isEnd(station, direction)) {
            // 중간 역(구로·용산 등)에서 끝나는 열차는 어디로 돌아 나갈지 몰라 뺀다
            return null;
        }
        if (toward != null && !goesTo(routes, train, station, toward, wanted, turnsBack)) {
            return null;
        }
        return new RealtimeArrival(train.trainNo(), direction, train.terminal().name(), train.express(),
                train.current().name(), train.status(), stationsAway, turnsBack,
                OffsetDateTime.ofInstant(train.receivedAt(), SEOUL));
    }

    private static boolean goesTo(RouteTree routes, Train train, RouteTree.Node station, RouteTree.Node toward,
                                  Direction wanted, boolean turnsBack) {
        if (wanted == null) {
            return false;
        }
        // 끝 역에 들어와 돌아 나가는 열차는 반대 방향으로 간다. 돌아 나간 뒤의 행선지는 알 수 없다
        if (turnsBack) {
            return wanted != train.direction();
        }
        if (train.direction() != wanted || routes.stepsAlong(wanted, toward, train.terminal()) < 0) {
            return false;
        }
        // 급행은 급행이 서는 역에서만 타고 내릴 수 있다
        return !train.express() || (station.expressStop() && toward.expressStop());
    }

    private static RouteTree.Node find(RouteTree routes, String stationId) {
        RouteTree.Node node = routes.byId(stationId);
        if (node == null) {
            throw new BusinessException(ErrorCode.UNKNOWN_STATION);
        }
        return node;
    }

    // 역 102개는 바뀌지 않으므로 처음 한 번만 읽어 둔다
    private RouteTree routes() {
        RouteTree current = tree;
        if (current == null) {
            current = new RouteTree(stationService.getStations());
            tree = current;
        }
        return current;
    }

    private Cached load(RouteTree routes) {
        Cached current = cached;
        if (isFresh(current)) {
            return current;
        }
        // 여러 사람이 동시에 물어도 서울시에는 한 번만 묻는다
        synchronized (fetchLock) {
            current = cached;
            if (isFresh(current)) {
                return current;
            }
            Instant now = clock.instant();
            if (!useOneCall(LocalDate.ofInstant(now, SEOUL))) {
                throw new BusinessException(ErrorCode.REALTIME_UNAVAILABLE);
            }
            List<Train> trains;
            try {
                trains = toTrains(routes, source.fetchLine1());
            } catch (RuntimeException e) {
                // 예외 메시지에는 요청 주소(인증키 포함)가 들어 있을 수 있어서 종류만 남긴다
                String reason = e instanceof SeoulApiException ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("실시간 열차 위치를 받지 못했어요 ({})", reason);
                trains = null;
            }
            current = new Cached(now, trains);
            cached = current;
            return current;
        }
    }

    private boolean isFresh(Cached current) {
        return current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(clock.instant());
    }

    // 하루 호출 수를 센다. 날짜(한국 시간)가 바뀌면 0부터 다시
    private synchronized boolean useOneCall(LocalDate today) {
        if (!today.equals(callDate)) {
            callDate = today;
            callsToday = 0;
        }
        if (callsToday >= dailyLimit) {
            return false;
        }
        callsToday++;
        return true;
    }

    // 1호선만, 읽을 수 있는 줄만(우리 역·방향·상태·시각), 같은 열차가 두 줄이면 나중에 받은 것만
    private static List<Train> toTrains(RouteTree routes, List<SeoulTrainPosition> rows) {
        Map<String, Train> byTrainNo = new LinkedHashMap<>();
        for (SeoulTrainPosition row : rows) {
            Train train = toTrain(routes, row);
            if (train != null) {
                byTrainNo.merge(train.trainNo(), train, (a, b) -> b.receivedAt().isAfter(a.receivedAt()) ? b : a);
            }
        }
        return List.copyOf(byTrainNo.values());
    }

    private static Train toTrain(RouteTree routes, SeoulTrainPosition row) {
        if (!LINE_1.equals(row.subwayId()) || row.trainNo() == null) {
            return null;
        }
        Direction direction = switch (row.updnLine() == null ? "" : row.updnLine()) {
            case "0" -> Direction.UP;
            case "1" -> Direction.DOWN;
            default -> null;
        };
        RouteTree.Node current = routes.byName(appName(row.statnNm()));
        RouteTree.Node terminal = routes.byName(appName(row.statnTnm()));
        TrainStatus status = TrainStatus.fromSeoulCode(row.trainSttus());
        Instant receivedAt = parseReceivedAt(row.recptnDt());
        if (direction == null || current == null || terminal == null || status == null || receivedAt == null) {
            return null;
        }
        return new Train(row.trainNo(), direction, current, terminal, isExpress(row), status, receivedAt);
    }

    // 서울교통공사 구간에서는 급행도 directAt이 0으로 올 때가 있어, 1000번대 열차 번호도 급행으로 본다
    private static boolean isExpress(SeoulTrainPosition row) {
        return "1".equals(row.directAt()) || (row.trainNo().length() == 4 && row.trainNo().startsWith("1"));
    }

    private static String appName(String seoulName) {
        return seoulName == null ? null : APP_NAMES.getOrDefault(seoulName, seoulName);
    }

    private static Instant parseReceivedAt(String value) {
        try {
            return value == null ? null : LocalDateTime.parse(value, RECEIVED_AT).atZone(SEOUL).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
