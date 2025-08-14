package com.example.mindtrack.Service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.mindtrack.Config.JWT.JwtUtil;
import com.example.mindtrack.DTO.AuthResponse;
import com.example.mindtrack.DTO.LoginRequest;
import com.example.mindtrack.DTO.SignupRequest;
import com.example.mindtrack.Domain.Users;
import com.example.mindtrack.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponse signup(SignupRequest req) {
        // userId 중복 체크 (email은 중복 허용)
        if (userRepository.findByUserId(req.getUserId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        Users u = new Users();
        u.setUserId(req.getUserId());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword())); // 해시 저장

        Users saved = userRepository.save(u);
        String token = jwtUtil.generateToken(saved.getUserId(), saved.getEmail());
        return new AuthResponse(saved.getUserId(), saved.getEmail(), token);
    }

    public AuthResponse login(LoginRequest req) {
        Users user = userRepository.findByUserId(req.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtUtil.generateToken(user.getUserId(), user.getEmail());
        return new AuthResponse(user.getUserId(), user.getEmail(), token);
    }
}