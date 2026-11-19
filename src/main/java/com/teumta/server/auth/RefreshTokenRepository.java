package com.teumta.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 지운 줄 수를 돌려준다. 같은 토큰으로 동시에 두 번 갱신하면 한쪽만 1을 받는다
    @Modifying
    @Query("delete from RefreshToken t where t.id = :id")
    int deleteOne(Long id);

    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from RefreshToken t where t.member.id = :memberId")
    void deleteAllByMemberId(Long memberId);

    // 로그아웃하지 않은 기기의 토큰이 쌓이지 않게, 새로 발급할 때 만료된 것을 치운다
    @Modifying
    @Query("delete from RefreshToken t where t.member.id = :memberId and t.expiresAt <= :now")
    void deleteExpired(Long memberId, Instant now);
}
