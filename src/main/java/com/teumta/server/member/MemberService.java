package com.teumta.server.member;

import com.teumta.server.auth.RefreshTokenRepository;
import com.teumta.server.common.error.BusinessException;
import com.teumta.server.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public MemberService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberResponse::from)
                // 탈퇴한 뒤 남은 access token(최대 30분)으로 들어온 경우
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    // 탈퇴: 회원과 모든 기기의 refresh token을 지운다. 바로 다시 같은 이메일로 가입할 수 있다
    @Transactional
    public void withdraw(Long memberId) {
        // refresh token이 회원을 가리키므로(외래 키) 토큰부터 지운다
        refreshTokenRepository.deleteAllByMemberId(memberId);
        memberRepository.deleteById(memberId);
    }
}
