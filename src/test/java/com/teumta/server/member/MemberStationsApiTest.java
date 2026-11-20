package com.teumta.server.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 자주 쓰는 두 역 저장을 실제 Security 필터와 시드 역(102개)으로 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class MemberStationsApiTest {
    private static final String SEOUL = "MTRS11150";   // 서울역
    private static final String GURO = "MTRKR1141";    // 구로

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // 새 회원으로 가입하고 access token을 받는다(테스트끼리 이메일이 겹치지 않게)
    private String signupAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"teumta1234\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asString();
    }

    private static String newEmail() {
        return "stations-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions putStations(String token, String json) throws Exception {
        return mockMvc.perform(put("/api/members/me/stations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getMe(String token) throws Exception {
        return mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token));
    }

    @Test
    void 처음에는_저장한_두_역이_없다() throws Exception {
        getMe(signupAndGetToken(newEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originStationId").doesNotExist())
                .andExpect(jsonPath("$.destinationStationId").doesNotExist());
    }

    @Test
    void 저장하면_내_정보에_보인다() throws Exception {
        String token = signupAndGetToken(newEmail());

        putStations(token, "{\"originStationId\":\"" + GURO + "\",\"destinationStationId\":\"" + SEOUL + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originStationId").value(GURO))
                .andExpect(jsonPath("$.destinationStationId").value(SEOUL));
        getMe(token)
                .andExpect(jsonPath("$.originStationId").value(GURO))
                .andExpect(jsonPath("$.destinationStationId").value(SEOUL));
    }

    @Test
    void null로_보내면_그_역만_지운다() throws Exception {
        String token = signupAndGetToken(newEmail());
        putStations(token, "{\"originStationId\":\"" + GURO + "\",\"destinationStationId\":\"" + SEOUL + "\"}");

        putStations(token, "{\"originStationId\":\"" + GURO + "\",\"destinationStationId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originStationId").value(GURO))
                .andExpect(jsonPath("$.destinationStationId").doesNotExist());
    }

    @Test
    void 없는_역이면_400이고_저장하지_않는다() throws Exception {
        String token = signupAndGetToken(newEmail());

        putStations(token, "{\"originStationId\":\"" + GURO + "\",\"destinationStationId\":\"NO_SUCH\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_STATION"));
        getMe(token).andExpect(jsonPath("$.originStationId").doesNotExist());
    }

    @Test
    void 두_역이_같으면_400() throws Exception {
        putStations(signupAndGetToken(newEmail()), "{\"originStationId\":\"" + GURO + "\",\"destinationStationId\":\"" + GURO + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_STATIONS"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(put("/api/members/me/stations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originStationId\":\"" + GURO + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 탈퇴하면_저장한_두_역도_지워진다() throws Exception {
        String email = newEmail();
        String token = signupAndGetToken(email);
        putStations(token, "{\"originStationId\":\"" + GURO + "\",\"destinationStationId\":\"" + SEOUL + "\"}");

        mockMvc.perform(delete("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 같은 이메일로 다시 가입하면 새 회원이라 비어 있다
        getMe(signupAndGetToken(email))
                .andExpect(jsonPath("$.originStationId").doesNotExist())
                .andExpect(jsonPath("$.destinationStationId").doesNotExist());
    }
}
