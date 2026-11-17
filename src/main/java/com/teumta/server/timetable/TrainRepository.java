package com.teumta.server.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TrainRepository extends JpaRepository<Train, Long> {
    // 열차와 정차역을 쿼리 한 번에 함께 읽는다(fetch join)
    // 그냥 findAll 하면 열차마다 정차역을 따로 읽어 쿼리가 1 + 열차 수만큼 나간다(N+1)
    @Query("select t from Train t join fetch t.stops where t.dayType = :dayType order by t.id")
    List<Train> findAllWithStops(DayType dayType);
}
