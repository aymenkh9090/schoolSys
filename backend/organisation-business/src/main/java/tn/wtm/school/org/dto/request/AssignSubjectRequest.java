package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignSubjectRequest(

        @NotNull(message = "L'année scolaire est obligatoire")
        Long schoolYearId,

        @NotNull(message = "L'enseignant est obligatoire")
        Long teacherId,

        @NotNull(message = "La matière-niveau est obligatoire")
        Long subjectLevelId,

        @NotNull(message = "Le type de séance est obligatoire")
        Long subjectSessionTypeId,

        @NotEmpty(message = "Au moins une classe est requise")
        List<Long> classGroupIds,

        @Min(value = 1, message = "La priorité doit être au moins 1")
        @Max(value = 100, message = "La priorité ne peut pas dépasser 100")
        Integer priority,

        Boolean isActive
) {}
