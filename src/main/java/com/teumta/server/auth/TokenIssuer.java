package com.teumta.server.auth;

import com.teumta.server.auth.dto.TokenResponse;
import com.teumta.server.common.security.SecurityConfig;
import com.teumta.server.member.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

// access token(JWT)과 refresh token(임의 문자열)을 만든다
@Component
public class TokenIssuer {
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom random = new SecureRandom();

    public TokenIssuer(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokenRepository,
                       @Value("${teumta.jwt.access-token-ttl}") Duration accessTokenTtl,
                       @Value("${teumta.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    // 호출하는 쪽(AuthService)의 트랜잭션 안에서 refresh token을 저장한다
    public TokenResponse issue(Member member) {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpired(member.getId(), now);

        // access token: 서명한 JWT. 서버는 DB를 보지 않고 서명과 만료만 확인한다
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER)
                // 토큰마다 다른 ID. 같은 초에 두 번 발급해도 토큰이 똑같아지지 않는다
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(member.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        // refresh token: 뜻 없는 임의 값(256비트). DB에 해시로 저장해서 로그아웃·탈퇴 때 지울 수 있다
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(member, hash(refreshToken), now.plus(refreshTokenTtl)));

        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenTtl.toSeconds());
    }

    public static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 Java에 들어 있어서 여기로 오지 않는다
            throw new IllegalStateException(e);
        }
    }
}
