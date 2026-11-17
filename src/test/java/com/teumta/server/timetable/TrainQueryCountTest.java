package com.teumta.server.timetable;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// 시간표를 읽을 때 쿼리가 몇 번 나가는지 센다. 열차마다 정차역을 따로 읽는 N+1이 다시 생기면 이 테스트가 깨진다
// generate_statistics: Hibernate가 보낸 쿼리 수를 세게 한다 (테스트에서만 켠다)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TrainQueryCountTest {

    @Autowired
    TrainService trainService;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void 열차_843편과_정차역을_쿼리_한_번에_읽는다() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        trainService.getTrains(DayType.WEEKDAY);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}