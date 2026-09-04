package tn.wtm.school.org.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CustomizePatternRequest(

        @NotNull(message = "Le total des heures est obligatoire")
        @DecimalMin(value = "0.5", message = "Le total des heures doit être au moins 0.5h")
        @DecimalMax(value = "40.0", message = "Le total des heures ne peut pas dépasser 40h")
        Double totalHours,

        String repartition,

        @NotEmpty(message = "Au moins une séance est requise")
        @Valid
        List<PatternDetailRequest> sessions
) {}
