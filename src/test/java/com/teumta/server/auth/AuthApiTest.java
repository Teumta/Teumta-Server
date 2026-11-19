package com.teumta.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 가입 → 로그인 → 내 정보 → 갱신 → 로그아웃 → 탈퇴를 실제 Security 필터와 함께 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // 테스트끼리 같은 DB를 쓰므로 이메일이 겹치지 않게 한다
    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }

    private JsonNode signup(String email) throws Exception {
        return postJson("/api/auth/signup", "{\"email\":\"" + email + "\",\"password\":\"teumta1234\"}", 201);
    }

    @Test
    void 가입하면_토큰을_주고_그_토큰으로_내_정보를_본다() throws Exception {
        String email = newEmail();
        JsonNode tokens = signup(email);

        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + tokens.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void 같은_이메일은_대소문자가_달라도_다시_가입할_수_없다() throws Exception {
        String email = newEmail();
        signup(email);

        JsonNode error = postJson("/api/auth/signup", "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"teumta1234\"}", 409);
        assertThat(error.get("code").asString()).isEqualTo("EMAIL_ALREADY_USED");
    }

    @Test
    void 형식이_틀리면_칸마다_이유를_준다() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-email\",\"password\":\"짧음\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void 비밀번호가_틀리거나_없는_이메일이면_같은_오류() throws Exception {
        String email = newEmail();
        signup(email);

        JsonNode wrongPassword = postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}", 401);
        JsonNode noEmail = postJson("/api/auth/login", "{\"email\":\"" + newEmail() + "\",\"password\":\"teumta1234\"}", 401);
        assertThat(wrongPassword.get("code").asString()).isEqualTo("LOGIN_FAILED");
        assertThat(noEmail.get("code").asString()).isEqualTo("LOGIN_FAILED");
    }

    @Test
    void 토큰이_없거나_가짜면_401과_같은_오류_모양() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 갱신하면_새_토큰을_주고_쓴_refresh_token은_다시_못_쓴다() throws Exception {
        String oldRefresh = signup(newEmail()).get("refreshToken").asString();

        JsonNode renewed = postJson("/api/auth/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}", 200);
        assertThat(renewed.get("refreshToken").asString()).isNotEqualTo(oldRefresh);

        JsonNode reused = postJson("/api/auth/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}", 401);
        assertThat(reused.get("code").asString()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void 로그아웃하면_그_refresh_token으로_갱신할_수_없다() throws Exception {
        String refresh = signup(newEmail()).get("refreshToken").asString();

        postJson("/api/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}", 204);
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", 401);
    }

    @Test
    void 탈퇴하면_다시_로그인할_수_없고_같은_이메일로_새로_가입할_수_있다() throws Exception {
        String email = newEmail();
        JsonNode tokens = signup(email);
        String accessToken = tokens.get("accessToken").asString();

        mockMvc.perform(delete("/api/members/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"teumta1234\"}", 401);
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + tokens.get("refreshToken").asString() + "\"}", 401);
        // 남은 access token(최대 30분)으로 들어와도 회원이 없으니 401
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        signup(email);
    }

    @Test
    void 시간표는_로그인_없이_본다() throws Exception {
        mockMvc.perform(get("/api/stations")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
