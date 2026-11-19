package com.teumta.server.member;

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
}
