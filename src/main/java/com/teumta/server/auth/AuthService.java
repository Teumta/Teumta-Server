package com.teumta.server.auth;

import com.teumta.server.auth.dto.LoginRequest;
import com.teumta.server.auth.dto.SignupRequest;
import com.teumta.server.auth.dto.TokenResponse;
import com.teumta.server.common.error.BusinessException;
import com.teumta.server.common.error.ErrorCode;
import com.teumta.server.member.Member;
import com.teumta.server.member.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public AuthService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    // 가입하면 바로 로그인한 것으로 보고 토큰을 준다(앱이 로그인을 한 번 더 부르지 않아도 된다)
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        try {
            // saveAndFlush: INSERT를 바로 보내서, 같은 이메일이 동시에 가입하면 여기서 unique 제약에 걸린다
            Member member = memberRepository.saveAndFlush(
                    new Member(email, passwordEncoder.encode(request.password()), Instant.now()));
            return tokenIssuer.issue(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(normalize(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return tokenIssuer.issue(member);
    }

    // 토큰 갱신(회전): 받은 refresh token을 지우고 새 쌍을 준다. 한 번 쓴 refresh token은 다시 못 쓴다
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(TokenIssuer.hash(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        // 지운 줄이 0이면 다른 요청이 먼저 같은 토큰을 썼다
        int deleted = refreshTokenRepository.deleteOne(saved.getId());
        if (deleted == 0 || saved.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return tokenIssuer.issue(saved.getMember());
    }

    // 로그아웃: 이 기기의 refresh token만 지운다. 없는 토큰이어도 성공으로 본다(이미 로그아웃된 상태)
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(TokenIssuer.hash(refreshToken));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
