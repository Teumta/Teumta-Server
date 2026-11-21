package com.teumta.server.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static com.teumta.server.realtime.RealtimeFixtures.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 Security 필터와 시드 역으로 주소·응답 모양을 확인한다. 서울시 API와 시계만 가짜로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
class RealtimeArrivalApiTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PositionSource source;

    // 받은 지 5분 넘은 위치는 버리므로, 시계를 위치를 받은 시각에 맞춘다
    @MockitoBean
    Clock clock;

    @BeforeEach
    void fixClock() {
        when(clock.instant()).thenReturn(AT_1928);
    }

    @Test
    void 로그인_없이_내릴_역까지_가는_열차를_돌려준다() throws Exception {
        when(source.isEnabled()).thenReturn(true);
        when(source.fetchLine1()).thenReturn(positions("positions-1928.json"));
        mockMvc.perform(get("/api/realtime/arrivals").param("stationId", GWANGMYEONG).param("towardStationId", GEUMCHEON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(GWANGMYEONG))
                .andExpect(jsonPath("$.fetchedAt").value("2026-11-21T19:28:10+09:00"))
                .andExpect(jsonPath("$.arrivals[0].trainNo").value("7031"))
                .andExpect(jsonPath("$.arrivals[0].direction").value("DOWN"))
                .andExpect(jsonPath("$.arrivals[0].terminal").value("광명"))
                .andExpect(jsonPath("$.arrivals[0].currentStation").value("금천구청"))
                .andExpect(jsonPath("$.arrivals[0].currentStatus").value("ARRIVED"))
                .andExpect(jsonPath("$.arrivals[0].stationsAway").value(1))
                .andExpect(jsonPath("$.arrivals[0].turnsBack").value(true))
                .andExpect(jsonPath("$.arrivals[0].express").value(false))
                .andExpect(jsonPath("$.arrivals[0].receivedAt").value("2026-11-21T19:27:33+09:00"));
    }

    @Test
    void 인증키가_없으면_503_REALTIME_UNAVAILABLE() throws Exception {
        when(source.isEnabled()).thenReturn(false);
        mockMvc.perform(get("/api/realtime/arrivals").param("stationId", GURO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REALTIME_UNAVAILABLE"));
    }

    @Test
    void 없는_역은_400_빠진_값도_400() throws Exception {
        mockMvc.perform(get("/api/realtime/arrivals").param("stationId", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_STATION"));
        mockMvc.perform(get("/api/realtime/arrivals"))
                .andExpect(status().isBadRequest());
    }
}
