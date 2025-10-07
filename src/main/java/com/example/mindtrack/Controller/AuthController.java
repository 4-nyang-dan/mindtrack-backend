package com.example.mindtrack.Controller;

import com.example.mindtrack.DTO.AuthResponse;
import com.example.mindtrack.DTO.LoginRequest;
import com.example.mindtrack.DTO.SignupRequest;
import com.example.mindtrack.Service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        log.info("📩 [AuthController.signup] 받은 JSON: " + req);
        log.info("userId=" + req.getUserId() + ", email=" + req.getEmail() + ", password=" + req.getPassword());
        return authService.signup(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        log.info("[login] Content-Type: " + request.getContentType());
        log.info("[login] Body userId: " + req.getUserId());
        log.info("[login] Body password: " + req.getPassword());
        return authService.login(req);
    }

/*    @PostMapping("/logout")
    public AuthResponse logout(@Valid @RequestBody LogoutReqest req) {
        return authService.logout(req);
    }*/
    @GetMapping("/test")
    public String test() {
        return "✅ Auth test endpoint OK";
}

}
