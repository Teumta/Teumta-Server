package com.teumta.server.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

// 서울 열린데이터광장 실시간 열차 위치 API로 1호선 열차 전체의 위치를 받는다.
// 역마다 묻는 도착정보 API는 광명·서동탄에 답하지 않아서, 한 번에 모든 역을 알 수 있는 위치 API를 쓴다.
// 인증키는 서버 .env(SEOUL_SUBWAY_API_KEY)에만 둔다. 앱에 넣으면 누구나 꺼내 쓸 수 있다
@Component
public class SeoulSubwayClient implements PositionSource {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public SeoulSubwayClient(ObjectMapper objectMapper,
                             @Value("${teumta.realtime.base-url}") String baseUrl,
                             @Value("${teumta.realtime.api-key:}") String apiKey) {
        // 서울시 API가 느리면 앱 요청도 같이 붙잡힌다. 오래 기다리지 않고 실시간 없이 넘어간다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    @Override
    public List<SeoulTrainPosition> fetchLine1() {
        // 주소 틀의 {값}은 RestClient가 알맞게 바꿔 넣는다(한글 노선 이름도). 1호선은 100편 안쪽이라 300줄이면 넉넉하다
        String body = restClient.get()
                .uri("/{key}/json/realtimePosition/0/300/{line}", apiKey, "1호선")
                .retrieve()
                .body(String.class);
        return parse(body);
    }

    // 정상이면 errorMessage.code가 INFO-000, 데이터가 없으면 맨 위 code가 INFO-200으로 온다.
    // 그 밖의 코드(ERROR-337 하루 한도 넘음 등)는 실패로 본다. 응답 모양이 제각각이라 글자 그대로 받아서 읽는다
    List<SeoulTrainPosition> parse(String body) {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        String code = root.path("errorMessage").path("code").asString(root.path("code").asString(""));
        if ("INFO-200".equals(code)) {
            return List.of();
        }
        if (!"INFO-000".equals(code)) {
            throw new SeoulApiException(code.isEmpty() ? "응답 코드 없음" : code);
        }
        JsonNode rows = root.path("realtimePositionList");
        return rows.isArray() ? List.of(objectMapper.treeToValue(rows, SeoulTrainPosition[].class)) : List.of();
    }

    // 서울시 API가 실패를 알려 왔다. 메시지에는 코드만 담는다(주소에 든 인증키가 로그에 남지 않게)
    public static class SeoulApiException extends RuntimeException {
        public SeoulApiException(String code) {
            super(code);
        }
    }
}
