package com.teumta.server.realtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 서울시 응답을 읽는 부분만 확인한다(바깥 API는 부르지 않는다)
class SeoulSubwayClientTest {
    private final SeoulSubwayClient client = new SeoulSubwayClient(RealtimeFixtures.JSON, "http://localhost", "");

    @Test
    void 정상_응답의_열차_위치를_읽는다() {
        var rows = client.parse(RealtimeFixtures.text("positions-1928.json"));
        assertThat(rows).hasSize(81);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.trainNo()).isEqualTo("7031");
            assertThat(r.statnNm()).isEqualTo("금천구청");
            assertThat(r.statnTnm()).isEqualTo("광명");
            assertThat(r.updnLine()).isEqualTo("1");
        });
    }

    @Test
    void 데이터가_없으면_빈_목록() {
        assertThat(client.parse(RealtimeFixtures.text("no-data.json"))).isEmpty();
    }

    @Test
    void 한도_초과_같은_오류_코드는_실패로_보고_코드만_담는다() {
        assertThatThrownBy(() -> client.parse(RealtimeFixtures.text("over-limit.json")))
                .isInstanceOf(SeoulSubwayClient.SeoulApiException.class)
                .hasMessage("ERROR-337");
    }

    @Test
    void 인증키가_없으면_꺼져_있다() {
        assertThat(client.isEnabled()).isFalse();
        assertThat(new SeoulSubwayClient(RealtimeFixtures.JSON, "http://localhost", " key ").isEnabled()).isTrue();
    }
}
