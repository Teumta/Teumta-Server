package com.teumta.server.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// 지금 시각을 Clock으로 받아 쓴다. 테스트는 시각을 고정한 Clock을 넣어 "1분 뒤"를 만들어 본다
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
