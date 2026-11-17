package com.teumta.server.station;

import jakarta.persistence.*;

// 1호선 역. 1호선은 한 줄이 아니라 구로·금천구청·병점에서 갈라지는 나무 모양이라
// 역마다 연천 쪽 이웃 역(parent)을 가리켜 노선 모양을 나타낸다
@Entity
@Table(name = "station")
public class Station {
    // 시간표 출처(TAGO)의 역 ID를 그대로 쓴다. 예) 구로 MTRKR1141
    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    // 경원, 도심, 본선, 경인, 경부, 장항, 광명, 서동탄
    @Column(nullable = false, length = 10)
    private String branch;

    // 목록에 보여 줄 순서 (연천 1 ~ 102)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 연천 쪽 이웃 역. 연천은 없고(null), 갈림길 첫 역은 갈라지는 역을 가리킨다 (광명 -> 금천구청).
    // LAZY: 역을 읽을 때 이웃 역까지 줄줄이 읽지 않는다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Station parent;

    // 급행이 한 편이라도 서는 역
    @Column(nullable = false)
    private boolean expressStop;

    // 이 역에서 다른 열차로 갈아탈 때 걸리는 시간(초). 없으면(null) 앱이 기본 2분으로 본다
    private Integer transferSec;

    // JPA가 DB에서 읽은 값을 채울 때 쓰는 빈 생성자. 코드에서는 쓰지 않는다
    protected Station() {}

    public Station(String id, String name, String branch, int sortOrder, Station parent, boolean expressStop, Integer transferSec) {
        this.id = id;
        this.name = name;
        this.branch = branch;
        this.sortOrder = sortOrder;
        this.parent = parent;
        this.expressStop = expressStop;
        this.transferSec = transferSec;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBranch() {
        return branch;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Station getParent() {
        return parent;
    }

    public boolean isExpressStop() {
        return expressStop;
    }

    public Integer getTransferSec() {
        return transferSec;
    }
}
