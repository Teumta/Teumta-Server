package com.teumta.server.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {
    // access token의 발급자(iss). 만들 때(TokenIssuer)와 확인할 때(jwtDecoder) 같은 값을 쓴다
    public static final String JWT_ISSUER = "teumta";

    // 어떤 주소를 열고 닫을지. 위에서부터 처음 맞는 규칙을 쓴다
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorAuthenticationEntryPoint entryPoint) throws Exception {
        http
                // 쿠키로 로그인하지 않으므로(토큰을 헤더에 담는다) CSRF 공격 대상이 아니다
                .csrf(csrf -> csrf.disable())
                // 서버가 로그인 상태(세션)를 기억하지 않는다. 요청마다 access token으로 확인한다
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // 시간표는 로그인 없이 쓴다(계산은 로그인 없이 된다)
                        .requestMatchers(HttpMethod.GET, "/api/stations", "/api/trains").permitAll()
                        // 실시간 도착도 로그인 없이 본다. 서버가 1분씩 모아 묻고 하루 한도를 지킨다
                        .requestMatchers(HttpMethod.GET, "/api/realtime/arrivals").permitAll()
                        .requestMatchers("/actuator/health/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                // Authorization: Bearer <access token> 을 읽어 서명·만료를 확인하는 필터를 켠다
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 서명 키: 서버만 아는 비밀값(256비트). 이 값을 아는 쪽만 진짜 토큰을 만들 수 있다
    @Bean
    SecretKey jwtSecretKey(@Value("${teumta.jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("teumta.jwt.secret은 32바이트(256비트) 이상이어야 합니다");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    // 받은 토큰 확인: 서명(HS256), 만료 시각, 발급자(teumta)
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JWT_ISSUER));
        return decoder;
    }
}
