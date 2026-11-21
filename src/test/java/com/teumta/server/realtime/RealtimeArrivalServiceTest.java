package com.teumta.server.realtime;

import com.teumta.server.common.error.BusinessException;
import com.teumta.server.common.error.ErrorCode;
import com.teumta.server.realtime.RealtimeFixtures.MovableClock;
import com.teumta.server.realtime.dto.RealtimeArrival;
import com.teumta.server.station.StationService;
import com.teumta.server.timetable.Direction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.teumta.server.realtime.RealtimeFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeArrivalServiceTest {
    private MovableClock clock = new MovableClock(AT_1928);
    private final AtomicInteger calls = new AtomicInteger();

    // 고정된 위치를 돌려주는 가짜 서울시. 몇 번 불렸는지 센다
    private RealtimeArrivalService service(int dailyLimit, boolean enabled, Supplier<List<SeoulTrainPosition>> answer) {
        StationService stationService = mock(StationService.class);
        when(stationService.getStations()).thenReturn(stations());
        PositionSource source = new PositionSource() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public List<SeoulTrainPosition> fetchLine1() {
                calls.incrementAndGet();
                return answer.get();
            }
        };
        return new RealtimeArrivalService(source, stationService, clock, dailyLimit);
    }

    private RealtimeArrivalService at(Instant time, String fixture) {
        clock = new MovableClock(time);
        return service(950, true, () -> positions(fixture));
    }

    private static List<String> trainNos(List<RealtimeArrival> arrivals) {
        return arrivals.stream().map(RealtimeArrival::trainNo).toList();
    }

    @Test
    void 구로에서_구일_쪽은_인천_방면_하행만_가까운_순서로() {
        var arrivals = at(AT_1928, "positions-1928.json").getArrivals(GURO, GUIL).arrivals();
        // 0165 인천행은 신도림에 들어가는 중(1역 전), 1119 동인천 급행은 노량진(5역 전)
        assertThat(trainNos(arrivals)).startsWith("0165", "1119");
        var first = arrivals.getFirst();
        assertThat(first.direction()).isEqualTo(Direction.DOWN);
        assertThat(first.terminal()).isEqualTo("인천");
        assertThat(first.currentStation()).isEqualTo("신도림");
        assertThat(first.currentStatus()).isEqualTo(TrainStatus.ENTERING);
        assertThat(first.stationsAway()).isEqualTo(1);
        assertThat(arrivals.get(1).express()).isTrue();
        assertThat(arrivals).allMatch(a -> a.direction() == Direction.DOWN && !a.turnsBack());
    }

    @Test
    void 구로에서_가산디지털단지_쪽은_경부선으로_가는_열차만() {
        var arrivals = at(AT_1928, "positions-1928.json").getArrivals(GURO, GASAN).arrivals();
        // 구로에 선 서동탄행, 영등포의 광명 셔틀(2역 전), 대방의 천안 급행(4역 전), 노량진의 신창행(5역 전). 인천행은 빠진다
        assertThat(trainNos(arrivals)).startsWith("0485", "7033", "1949", "0703");
        assertThat(arrivals).noneMatch(a -> a.terminal().equals("인천") || a.terminal().equals("동인천"));
    }

    @Test
    void 급행이_서지_않는_역으로_가면_급행은_빠진다() {
        var arrivals = at(AT_1928, "positions-1928.json").getArrivals(GURO, DOKSAN).arrivals();
        assertThat(trainNos(arrivals)).startsWith("0485", "7033", "0703").doesNotContain("1949");
    }

    @Test
    void 상행은_행선지가_내릴_역까지_가는_열차만() {
        var service = at(AT_1928, "positions-1928.json");
        // 구로에 선 광운대행, 가산디지털단지의 광운대행(1역 전), 독산의 청량리행(2역 전), 개봉에 들어가는 동두천행(2역 전)
        assertThat(trainNos(service.getArrivals(GURO, SINDORIM).arrivals())).startsWith("0680", "0476", "0682", "0162");
        // 영등포 셔틀(7030)은 영등포에서 끝나서 구로 -> 신도림 목록에 없다
        assertThat(trainNos(service.getArrivals(GURO, SINDORIM).arrivals())).doesNotContain("7030");
    }

    @Test
    void 광명은_돌아_나갈_셔틀이_금천구청까지_온_것을_보여_준다() {
        var service = at(AT_1928, "positions-1928.json");
        var arrivals = service.getArrivals(GWANGMYEONG, GEUMCHEON).arrivals();
        // 7031은 금천구청(1역 전), 7033은 영등포(6역 전). 둘 다 광명에서 끝나고 돌아 나간다
        assertThat(trainNos(arrivals)).containsExactly("7031", "7033");
        assertThat(arrivals).allMatch(RealtimeArrival::turnsBack);
        assertThat(arrivals.getFirst().stationsAway()).isEqualTo(1);
        assertThat(arrivals.getFirst().currentStation()).isEqualTo("금천구청");
        assertThat(arrivals.get(1).stationsAway()).isEqualTo(6);
    }

    @Test
    void 서동탄은_서_있는_열차를_먼저_돌아_나갈_열차를_뒤에() {
        var arrivals = at(AT_1932, "positions-1932.json").getArrivals(SEODONGTAN, BYEONGJEOM).arrivals();
        // 0484 광운대행은 서동탄에 서 있다. 0479는 방금 서동탄에 도착해 돌아 나가고, 0481·0483은 서동탄으로 오는 중
        assertThat(trainNos(arrivals)).startsWith("0484", "0479", "0481", "0483");
        assertThat(arrivals.getFirst().turnsBack()).isFalse();
        assertThat(arrivals.getFirst().terminal()).isEqualTo("광운대");
        assertThat(arrivals.subList(1, 4)).allMatch(RealtimeArrival::turnsBack);
    }

    @Test
    void 중간_역에서_끝나는_열차는_그_역_목록에서_뺀다() {
        // 7030 셔틀은 영등포에서 끝나지만 영등포는 끝 역이 아니라 어디로 돌아 나갈지 모른다
        var arrivals = at(AT_1928, "positions-1928.json").getArrivals("MTRKR1139", null).arrivals();
        assertThat(trainNos(arrivals)).doesNotContain("7030");
    }

    @Test
    void 서울시_이름_서울_지제_쌍용을_앱_이름으로_바꾼다() {
        // 서울역에 선 지제행 하행, 쌍용(나사렛대)을 떠난 서울행 상행
        var rows = List.of(
                new SeoulTrainPosition("1001", "0151", "서울", "지제", "1", "1", "0", "2026-11-21 19:28:00"),
                new SeoulTrainPosition("1001", "1940", "쌍용(나사렛대)", "서울", "0", "2", "1", "2026-11-21 19:28:00"));
        var service = service(950, true, () -> rows);
        // 용산에는 두 편 다 온다(하행 2역 전, 상행 36역 전)
        var toYongsan = service.getArrivals("MTRKR1135", null).arrivals();
        assertThat(trainNos(toYongsan)).containsExactly("0151", "1940");
        assertThat(toYongsan.getFirst()).satisfies(a -> {
            assertThat(a.currentStation()).isEqualTo("서울역");
            assertThat(a.terminal()).isEqualTo("평택지제");
            assertThat(a.stationsAway()).isEqualTo(2);
        });
        var toCheonan = service.getArrivals("MTRKR1P169", null).arrivals();
        assertThat(toCheonan).singleElement().satisfies(a -> {
            assertThat(a.currentStation()).isEqualTo("쌍용");
            assertThat(a.terminal()).isEqualTo("서울역");
            assertThat(a.express()).isTrue();
        });
    }

    @Test
    void 같은_열차가_두_줄이면_나중_것만_쓰고_5분_넘은_위치는_버린다() {
        // 0487은 시청 도착(19:24:49)과 시청 출발(19:27:37) 두 줄로 온다
        var service = at(AT_1928, "positions-1928.json");
        var train0487 = service.getArrivals(GURO, GASAN).arrivals().stream().filter(a -> a.trainNo().equals("0487")).toList();
        assertThat(train0487).hasSize(1);
        assertThat(train0487.getFirst().currentStatus()).isEqualTo(TrainStatus.DEPARTED);
        // 4분 뒤(19:32:10)에는 19:27:10 이전 위치가 버려진다. 7031(19:27:33)은 남고 0479(19:23:35)는 빠진다
        clock.advance(Duration.ofMinutes(4));
        var later = service.getArrivals(GWANGMYEONG, null).arrivals();
        assertThat(calls).hasValue(2);
        assertThat(trainNos(later)).contains("7031");
        assertThat(trainNos(service.getArrivals(SEODONGTAN, null).arrivals())).doesNotContain("0479");
    }

    @Test
    void 위치는_90초_동안_모든_역이_같이_쓴다() {
        var service = at(AT_1928, "positions-1928.json");
        service.getArrivals(GURO, GUIL);
        service.getArrivals(GWANGMYEONG, null);
        clock.advance(Duration.ofSeconds(89));
        service.getArrivals(SEODONGTAN, null);
        assertThat(calls).hasValue(1);
        clock.advance(Duration.ofSeconds(2));
        service.getArrivals(GURO, GUIL);
        assertThat(calls).hasValue(2);
    }

    @Test
    void 하루_한도에_닿으면_503이고_다음_날_다시_묻는다() {
        var service = service(1, true, () -> positions("positions-1928.json"));
        service.getArrivals(GURO, null);
        clock.advance(Duration.ofSeconds(91));
        assertUnavailable(() -> service.getArrivals(GURO, null));
        clock.advance(Duration.ofHours(5)); // 19:28 + 5시간 = 다음 날 00:29
        assertThat(service.getArrivals(INCHEON, null)).isNotNull();
        assertThat(calls).hasValue(2);
    }

    @Test
    void 서울시가_실패하면_503이고_90초_동안은_다시_묻지_않는다() {
        var service = service(950, true, () -> {
            throw new SeoulSubwayClient.SeoulApiException("ERROR-337");
        });
        assertUnavailable(() -> service.getArrivals(GURO, null));
        assertUnavailable(() -> service.getArrivals(INCHEON, null));
        assertThat(calls).hasValue(1);
    }

    @Test
    void 인증키가_없으면_묻지_않고_503() {
        var service = service(950, false, () -> positions("positions-1928.json"));
        assertUnavailable(() -> service.getArrivals(GURO, null));
        assertThat(calls).hasValue(0);
    }

    @Test
    void 없는_역이나_같은_역은_400() {
        var service = at(AT_1928, "positions-1928.json");
        assertThatThrownBy(() -> service.getArrivals("NOPE", null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNKNOWN_STATION));
        assertThatThrownBy(() -> service.getArrivals(GURO, GURO))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SAME_STATIONS));
        assertThat(calls).hasValue(0);
    }

    private static void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REALTIME_UNAVAILABLE));
    }
}
