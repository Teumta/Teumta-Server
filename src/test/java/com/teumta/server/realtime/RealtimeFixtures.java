package com.teumta.server.realtime;

import com.teumta.server.station.StationResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// 테스트 자료: 11/21(휴일 시간표) 19:28·19:32에 실제 인증키로 받은 1호선 열차 위치(인증키는 들어 있지 않다)와 시드 역 102개
final class RealtimeFixtures {
    static final ObjectMapper JSON = JsonMapper.builder().build();
    // 위치를 받은 시각. 시계를 여기에 맞춘다
    static final Instant AT_1928 = OffsetDateTime.parse("2026-11-21T19:28:10+09:00").toInstant();
    static final Instant AT_1932 = OffsetDateTime.parse("2026-11-21T19:32:12+09:00").toInstant();

    static final String SEOUL = "MTRS11150";        // 서울역
    static final String GURO = "MTRKR1141";         // 구로
    static final String GUIL = "MTRKR1142";         // 구일 (경인선 쪽 다음 역)
    static final String SINDORIM = "MTRKR1140";     // 신도림 (연천 쪽 다음 역)
    static final String GASAN = "MTRKR1P142";       // 가산디지털단지 (경부선 쪽 다음 역)
    static final String DOKSAN = "MTRKR1P143";      // 독산 (급행이 서지 않는다)
    static final String GEUMCHEON = "MTRKR1P144";   // 금천구청
    static final String GWANGMYEONG = "MTRKR1P144-1"; // 광명 (셔틀 끝 역)
    static final String BYEONGJEOM = "MTRKR1P157";  // 병점
    static final String SEODONGTAN = "MTRKR1P157-1"; // 서동탄 (끝 역)
    static final String INCHEON = "MTRKR1161";      // 인천

    private RealtimeFixtures() {
    }

    static String text(String name) {
        try (InputStream in = RealtimeFixtures.class.getResourceAsStream("/realtime/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<SeoulTrainPosition> positions(String name) {
        JsonNode list = JSON.readTree(text(name)).path("realtimePositionList");
        return List.of(JSON.treeToValue(list, SeoulTrainPosition[].class));
    }

    static List<StationResponse> stations() {
        List<StationResponse> result = new ArrayList<>();
        for (JsonNode s : JSON.readTree(text("../seed/stations.json"))) {
            result.add(new StationResponse(s.path("id").asString(), s.path("name").asString(), s.path("branch").asString(),
                    s.path("order").asInt(), s.path("parentId").isNull() ? null : s.path("parentId").asString(),
                    s.path("expressStop").asBoolean(), null));
        }
        return result;
    }

    // 손으로 넘기는 시계. "90초 뒤", "다음 날"을 만들어 본다
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
