package tn.wtm.school.org.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tn.wtm.school.org.enums.Specialite;

import java.util.List;

public record BulkCreateClassGroupRequest(

        @NotNull(message = "L'année scolaire est obligatoire")
        Long schoolYearId,

        @Min(value = 1, message = "La taille minimale d'une classe est 1")
        @Max(value = 60, message = "La taille maximale d'une classe est 60")
        Integer defaultSize,

        @NotEmpty(message = "Au moins un niveau est requis")
        @Valid
        List<LevelEntry> levels

) {
    public record LevelEntry(

            @NotBlank(message = "Le code du niveau est obligatoire")
            String levelCode,

            @NotNull(message = "Le nombre de classes est obligatoire")
            @Min(value = 1, message = "Le nombre de classes doit être au moins 1")
            @Max(value = 30, message = "Le nombre de classes ne peut pas dépasser 30")
            Integer count,

            @NotBlank(message = "Le préfixe est obligatoire")
            @Size(max = 5, message = "Le préfixe ne doit pas dépasser 5 caractères")
            String prefix,

            Specialite specialite
    ) {}
}
