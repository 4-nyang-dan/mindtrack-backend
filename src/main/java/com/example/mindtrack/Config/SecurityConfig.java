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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
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
                http.sessionManagement((session) ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                );
                http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
