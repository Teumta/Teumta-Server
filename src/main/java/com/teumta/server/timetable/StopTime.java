package com.teumta.server.timetable;

import com.teumta.server.station.Station;
import jakarta.persistence.*;

// 열차가 서는 역 하나와 그 시각. 급행이 서지 않고 지나치는 역은 저장하지 않는다 (타고 내릴 수 없음)
@Entity
@Table(name = "stop_time")
public class StopTime {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    // 외래 키(train_id)를 가진 쪽이라 이 필드가 관계의 주인이다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id")
    private Train train;

    // 열차 안에서 몇 번째 정차역인지 (1부터)
    @Column(nullable = false)
    private int seq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id")
    private Station station;

    // 운행일 0시 기준 초. 자정이 넘으면 86400 이상이다 (예: 24:10 -> 87000)
    @Column(name = "arr_sec", nullable = false)
    private int arrSec;

    @Column(name = "dep_sec", nullable = false)
    private int depSec;

    // JPA가 DB에서 읽은 값을 채울 때 쓰는 빈 생성자. 코드에서는 쓰지 않는다
    protected StopTime() {}

    StopTime(Train train, int seq, Station station, int arrSec, int depSec) {
        this.train = train;
        this.seq = seq;
        this.station = station;
        this.arrSec = arrSec;
        this.depSec = depSec;
    }

    public int getSeq() {
        return seq;
    }

    public Station getStation() {
        return station;
    }

    public int getArrSec() {
        return arrSec;
    }

    public int getDepSec() {
        return depSec;
    }
}
