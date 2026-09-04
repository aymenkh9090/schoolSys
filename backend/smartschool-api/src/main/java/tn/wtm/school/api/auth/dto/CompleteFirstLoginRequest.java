package tn.wtm.school.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompleteFirstLoginRequest(
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "8 caractères minimum, avec majuscule, minuscule et chiffre"
        )
        String newPassword
) {}
