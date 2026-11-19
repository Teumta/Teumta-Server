package com.teumta.server.member;

import java.time.Instant;

// 내 정보 응답. 비밀번호 해시는 내보내지 않는다
public record MemberResponse(Long id, String email, Instant createdAt) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getCreatedAt());
    }
}
