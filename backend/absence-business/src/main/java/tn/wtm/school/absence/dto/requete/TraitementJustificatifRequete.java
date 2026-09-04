package tn.wtm.school.absence.dto.requete;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.absence.enums.StatutJustificatif;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TraitementJustificatifRequete {

    @NotNull(message = "La décision est obligatoire")
    private StatutJustificatif decision;

    private String notesAdmin;

    /**
     * Auteur de la décision. Laissé vide par les clients : l'API y met le compte
     * connecté, qui seul fait foi.
     */
    private Long traiteParId;
}
