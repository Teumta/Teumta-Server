package com.teumta.server.timetable;

import com.teumta.server.station.Station;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

// 열차 한 편. 정차역(StopTime)을 순서대로 가진다 (열차 1 : 정차역 N)
@Entity
// 요일 종류로 열차를 찾으므로 그 열에 색인을 둔다
@Table(name = "train", indexes = @Index(name = "idx_train_day_type", columnList = "day_type"))
public class Train {
    // SEQUENCE: 번호를 DB에 미리 여러 개 받아 두고 쓴다. 그래야 저장을 묶어서(batch) 보낼 수 있다
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    // 이름(문자열)으로 저장한다. 숫자(순서)로 저장하면 enum 순서를 바꿨을 때 값이 뒤섞인다
    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Direction direction;

    // 행선지 역 이름 (화면의 "OO행")
    @Column(nullable = false, length = 20)
    private String headsign;

    // mappedBy: 관계의 주인은 StopTime.train(외래 키를 가진 쪽)이다
    // PERSIST: 열차를 저장하면 정차역도 함께 저장한다
    @OneToMany(mappedBy = "train", cascade = CascadeType.PERSIST)
    @OrderBy("seq")
    private List<StopTime> stops = new ArrayList<>();

    // JPA가 DB에서 읽은 값을 채울 때 쓰는 빈 생성자. 코드에서는 쓰지 않는다
    protected Train() {}

    public Train(DayType dayType, Direction direction, String headsign) {
        this.dayType = dayType;
        this.direction = direction;
        this.headsign = headsign;
    }

    // 정차역을 다음 순서로 붙인다. 양쪽(열차 -> 정차역, 정차역 -> 열차)을 함께 맞춘다
    public void addStop(Station station, int arrSec, int depSec) {
        stops.add(new StopTime(this, stops.size() + 1, station, arrSec, depSec));
    }

    public Long getId() {
        return id;
    }

    public DayType getDayType() {
        return dayType;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getHeadsign() {
        return headsign;
    }

    public List<StopTime> getStops() {
        return stops;
    }
}
