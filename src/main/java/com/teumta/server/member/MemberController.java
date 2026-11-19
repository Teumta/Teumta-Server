package com.teumta.server.member;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 회원 본인의 API. 모두 access token이 있어야 한다(SecurityConfig)
@RestController
@RequestMapping("/api/members/me")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // @AuthenticationPrincipal Jwt: Security가 검사를 마친 access token. subject에 회원 ID가 들어 있다
    @GetMapping
    public MemberResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return memberService.getMe(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Jwt jwt) {
        memberService.withdraw(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
