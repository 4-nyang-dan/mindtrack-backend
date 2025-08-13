package com.example.mindtrack.DTO;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String userId;
    private String email;
    private String token;
}
