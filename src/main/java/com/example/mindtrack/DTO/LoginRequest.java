package com.example.mindtrack.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {
    @NotBlank
    private String userId;

    @NotBlank
    private String password;
}
