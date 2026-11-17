package com.teumta.server.timetable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 서버가 켜질 때 넣은 시드(역 102개, 평일 843편, 휴일 731편)를 API로 읽어 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class TimetableApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 역_102개를_연천부터_순서대로_돌려준다() throws Exception {
        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(102))
                .andExpect(jsonPath("$[0].name").value("연천"))
                .andExpect(jsonPath("$[0].parentId").doesNotExist());
    }

    @Test
    void 갈림길_첫_역은_갈라지는_역을_가리키고_갈아타기_시간이_있다() throws Exception {
        mockMvc.perform(get("/api/stations"))
                // 광명(MTRKR1P144-1)은 금천구청(MTRKR1P144)에서 갈라진다
                .andExpect(jsonPath("$[?(@.name == '광명')].parentId").value("MTRKR1P144"))
                .andExpect(jsonPath("$[?(@.name == '구로')].transferSec").value(240));
    }

    @Test
    void 평일_열차_843편과_정차역을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/trains").param("dayType", "WEEKDAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDate").value("2026-11-06"))
                .andExpect(jsonPath("$.dayType").value("WEEKDAY"))
                .andExpect(jsonPath("$.trains.length()").value(843))
                // 첫 열차: 연천 05:27(19620초) 출발 인천행
                .andExpect(jsonPath("$.trains[0].headsign").value("인천"))
                .andExpect(jsonPath("$.trains[0].stops[0].stationId").value("MTRKR1100-3"))
                .andExpect(jsonPath("$.trains[0].stops[0].dep").value(19620));
    }

    @Test
    void 휴일_열차는_731편() throws Exception {
        mockMvc.perform(get("/api/trains").param("dayType", "HOLIDAY"))
                .andExpect(jsonPath("$.trains.length()").value(731));
    }

    @Test
    void 없는_요일_종류나_빠진_값은_400() throws Exception {
        mockMvc.perform(get("/api/trains").param("dayType", "SUNDAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(get("/api/trains"))
                .andExpect(status().isBadRequest());
    }
}