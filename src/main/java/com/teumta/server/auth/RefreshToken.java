package com.teumta.server.auth;

import com.teumta.server.member.Member;
import jakarta.persistence.*;

import java.time.Instant;

// 로그인한 기기 하나의 refresh token. 원문이 아니라 SHA-256 해시만 저장해서,
// DB가 새어 나가도 그 값으로 토큰을 새로 받을 수 없다
@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // SHA-256 해시를 Base64URL로 쓴 값(43자)
    @Column(nullable = false, unique = true, length = 43)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    protected RefreshToken() {}

    public RefreshToken(Member member, String tokenHash, Instant expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
