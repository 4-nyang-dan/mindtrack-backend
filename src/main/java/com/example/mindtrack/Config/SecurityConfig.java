package com.example.mindtrack.Config;

import com.example.mindtrack.Config.JWT.JwtAuthFilter;<<<<<<<HEAD
import com.example.mindtrack.Config.JWT.JwtUtil;=======
import com.example.mindtrack.Service.CustomUserDetailsService;

>>>>>>>2533295(redis TTL 설정 및 docker db 연결)
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;<<<<<<<HEAD
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;=======
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;>>>>>>>2533295(redis TTL 설정 및 docker db 연결)
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**",
                                                                "/upload-screenshot",
                                                                "/api/suggestions/stream",
                                                                "/api/auth/**")
                                                .permitAll()
                                                .anyRequest().authenticated());
                http.csrf(csrf -> csrf.disable());
                http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
                http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
        }
}
