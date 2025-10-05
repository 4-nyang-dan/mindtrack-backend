package com.example.mindtrack.Config;

import com.example.mindtrack.Config.JWT.JwtAuthFilter;
import com.example.mindtrack.Config.JWT.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.mindtrack.Service.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final CustomUserDetailsService customUserDetailsService; // DB 기반 인증 추가

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                // ✅ FastAPI → Spring 콜백 허용
                                                // "/analysis/result" 은 FastAPI가 Spring으로 보내는 콜백 엔드포인트이기 때문에 JWT 인증 없이
                                                // 접근해야 하는 “외부 시스템 전용” 엔드포인트
                                                .requestMatchers("/analysis/result").permitAll()
                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**",
                                                                "/api/auth/**",
                                                                "/upload-screenshot",
                                                                "/api/suggestions/stream")
                                                .permitAll()
                                                .anyRequest().authenticated());
                http.csrf(csrf -> csrf.disable());
                http.sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
                http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
        }
}
