package com.teumta.server.member;

import com.teumta.server.station.Station;

import java.time.Instant;

// 내 정보 응답. 비밀번호 해시는 내보내지 않는다.
// 집역·회사역은 역 ID만 준다(앱은 역 목록을 이미 갖고 있어서 이름은 앱에서 찾는다). 고르지 않았으면 null
public record MemberResponse(Long id, String email, Instant createdAt, String homeStationId, String workStationId) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getCreatedAt(),
                idOf(member.getHomeStation()), idOf(member.getWorkStation()));
    }

    // LAZY 프록시(빈 껍데기)여도 ID는 들어 있어서 역 표를 다시 읽지 않는다
    private static String idOf(Station station) {
        return station == null ? null : station.getId();
    }
}
