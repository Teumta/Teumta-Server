package com.teumta.server.member;

import com.teumta.server.auth.RefreshTokenRepository;
import com.teumta.server.common.error.BusinessException;
import com.teumta.server.common.error.ErrorCode;
import com.teumta.server.station.Station;
import com.teumta.server.station.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StationRepository stationRepository;

    public MemberService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                         StationRepository stationRepository) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberResponse::from)
                // 탈퇴한 뒤 남은 access token(최대 30분)으로 들어온 경우
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    // 집역·회사역 저장. 둘을 함께 받아 통째로 바꾼다(PUT). null이면 그 역을 지운다
    @Transactional
    public MemberResponse updateStations(Long memberId, UpdateStationsRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Station home = findStation(request.homeStationId());
        Station work = findStation(request.workStationId());
        // 집과 회사가 같은 역이면 오갈 경로가 없다
        if (home != null && work != null && home.getId().equals(work.getId())) {
            throw new BusinessException(ErrorCode.SAME_HOME_AND_WORK);
        }
        member.changeStations(home, work);
        return MemberResponse.from(member);
    }

    private Station findStation(String stationId) {
        if (stationId == null) {
            return null;
        }
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_STATION));
    }

    // 탈퇴: 회원(집역·회사역 포함)과 모든 기기의 refresh token을 지운다. 바로 다시 같은 이메일로 가입할 수 있다
    @Transactional
    public void withdraw(Long memberId) {
        // refresh token이 회원을 가리키므로(외래 키) 토큰부터 지운다
        refreshTokenRepository.deleteAllByMemberId(memberId);
        memberRepository.deleteById(memberId);
    }
}
