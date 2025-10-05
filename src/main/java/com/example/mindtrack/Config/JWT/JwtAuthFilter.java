package com.example.mindtrack.Config.JWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwt;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();

        // 인증이 필요 없는 경로는 필터 건너뛰기
        if (path.startsWith("/api/auth")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/upload-screenshot")
                || path.startsWith("/api/suggestions/stream")) {
            chain.doFilter(req, res);
            return;
        }

        String uri = req.getRequestURI();

        // ✅ JWT 인증 예외 경로
        if (
                uri.startsWith("/api/auth/") ||       // 로그인, 회원가입
                uri.startsWith("/api/suggestions/stream") || // SSE 구독
                uri.startsWith("/swagger-ui") ||      // Swagger 문서
                uri.startsWith("/v3/api-docs") ||     // OpenAPI
                uri.startsWith("/upload-screenshot")  // Electron 이미지 업로드
        ) {
            chain.doFilter(req, res);
            return;
        }

        // ✅ JWT 인증 처리
        String auth = req.getHeader("Authorization");

        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                String userId = jwt.extractUserId(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                // 잘못된/만료 토큰 → 인증 없이 계속 진행
            }
        }

        chain.doFilter(req, res);
    }
}
