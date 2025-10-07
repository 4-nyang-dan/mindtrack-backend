package com.example.mindtrack.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {
    @NotBlank
    @JsonProperty("userId")
    private String userId;

    @NotBlank
    @JsonProperty("password")
    private String password;
}
