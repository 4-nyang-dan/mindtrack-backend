package com.example.mindtrack.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "아이디는 영문, 숫자, ., _, -만 허용합니다.")
    private String userId;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 4, max = 64)
    private String password;
}