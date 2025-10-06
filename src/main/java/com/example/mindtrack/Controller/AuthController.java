package com.example.mindtrack.Controller;

import com.example.mindtrack.DTO.AuthResponse;
import com.example.mindtrack.DTO.LoginRequest;
import com.example.mindtrack.DTO.SignupRequest;
import com.example.mindtrack.Service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/test")
    public String test() {
        return "✅ Auth test endpoint OK";
}

}
