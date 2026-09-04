package tn.wtm.school.absence.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OuvertureAppelRequete {

    @NotNull(message = "L'identifiant de la séance planning est obligatoire")
    private Long seancePlanningId;

    @NotNull(message = "L'identifiant de l'enseignant est obligatoire")
    private Long enseignantId;

    @NotNull(message = "L'identifiant du groupe classe est obligatoire")
    private Long groupeClasseId;

    @NotBlank(message = "L'année académique est obligatoire")
    private String anneeAcademique;

    private Long matiereId;

    /**
     * Jour de cours concerné. Absent = aujourd'hui, cas normal de l'appel fait
     * pendant la séance ; renseigné pour régulariser une séance passée.
     */
    private LocalDate dateSeance;
}
