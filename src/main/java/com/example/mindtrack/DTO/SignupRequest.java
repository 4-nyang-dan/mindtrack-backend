package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "아이디는 영문, 숫자, ., _, -만 허용합니다.")
    @JsonProperty("userId")
    private String userId;

    @Email
    @NotBlank
    @JsonProperty("email")
    private String email;

    @NotBlank
    @Size(min = 4, max = 64)
    @JsonProperty("password")
    private String password;
}