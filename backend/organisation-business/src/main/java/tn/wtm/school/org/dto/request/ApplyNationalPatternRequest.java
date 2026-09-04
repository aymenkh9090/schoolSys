package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ApplyNationalPatternRequest(
        @NotBlank(message = "Le code pays est obligatoire")
        String country,

        @NotEmpty(message = "Au moins un niveau est requis")
        List<String> levels,

        Long schoolYearId
) {}
