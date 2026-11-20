package com.teumta.server.member;

import com.teumta.server.station.Station;
import jakarta.persistence.*;

import java.time.Instant;

// 회원. 이메일과 비밀번호 해시만 저장한다(비밀번호 원문은 어디에도 남기지 않는다)
@Entity
@Table(name = "member")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디. 소문자로 바꿔 저장해서 대소문자만 다른 중복 가입을 막는다
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    // BCrypt 해시(60자). 같은 비밀번호도 매번 다른 값이 된다
    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    // 자주 쓰는 두 역(출발역·도착역). 고르지 않았으면 null. 역 표를 외래 키로 가리켜서 없는 역은 저장되지 않는다.
    // LAZY: 회원을 읽을 때 역까지 읽지 않는다(응답에는 역 ID만 쓴다)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_station_id")
    private Station originStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_station_id")
    private Station destinationStation;

    protected Member() {}

    public Member(String email, String passwordHash, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Station getOriginStation() {
        return originStation;
    }

    public Station getDestinationStation() {
        return destinationStation;
    }

    // 두 역을 함께 바꾼다. 트랜잭션 안에서 부르면 끝날 때 UPDATE가 나간다(변경 감지)
    public void changeStations(Station originStation, Station destinationStation) {
        this.originStation = originStation;
        this.destinationStation = destinationStation;
    }
}
