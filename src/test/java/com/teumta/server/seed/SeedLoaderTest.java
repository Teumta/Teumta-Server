package com.teumta.server.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void 시각을_운행일_0시_기준_초로_바꾼다() {
        assertThat(SeedLoader.toSec("05:27")).isEqualTo(19620);
        assertThat(SeedLoader.toSec("05:34:30")).isEqualTo(20070);
    }

    @Test
    void 자정을_넘긴_시각은_24시_이후로_센다() {
        // 24:10 = 다음 날 00:10이지만 같은 운행일로 본다
        assertThat(SeedLoader.toSec("24:10")).isEqualTo(87000);
    }
}