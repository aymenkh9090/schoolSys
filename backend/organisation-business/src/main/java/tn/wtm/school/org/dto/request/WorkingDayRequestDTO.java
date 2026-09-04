package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Un jour de travail configuré par l'admin
public record WorkingDayRequestDTO(

        @NotNull(message = "Le jour est obligatoire")
        DayOfWeek dayOfWeek,

        // nullable → considéré actif par défaut
        Boolean active,

        @NotNull(message = "Début matin obligatoire")
        LocalTime morningStart,

        @NotNull(message = "Fin matin obligatoire")
        LocalTime morningEnd,

        // nullable → pas d'après-midi ce jour
        LocalTime afternoonStart,
        LocalTime afternoonEnd
) {}
