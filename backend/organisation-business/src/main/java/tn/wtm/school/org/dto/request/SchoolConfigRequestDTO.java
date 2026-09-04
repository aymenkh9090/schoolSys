package tn.wtm.school.org.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Config complète envoyée par l'admin
public record SchoolConfigRequestDTO(

        @NotEmpty(message = "Au moins un jour requis")
        List<@Valid WorkingDayRequestDTO> workingDays,

        @NotNull
        @Min(value = 30, message = "Minimum 30 minutes")
        @Max(value = 120, message = "Maximum 120 minutes")
        Integer slotDurationMinutes
) {}