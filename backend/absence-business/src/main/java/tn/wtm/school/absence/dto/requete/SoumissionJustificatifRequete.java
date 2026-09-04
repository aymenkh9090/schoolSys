package tn.wtm.school.absence.dto.requete;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.absence.enums.TypeJustificatif;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SoumissionJustificatifRequete {

    @NotNull(message = "L'identifiant de la ligne d'appel est obligatoire")
    private Long ligneAppelId;

    @NotNull(message = "Le type de document est obligatoire")
    private TypeJustificatif typeDocument;

    private String referenceDocument;

    /**
     * Auteur du dépôt. Laissé vide par les clients : l'API y met le compte
     * connecté, qui seul fait foi — un identifiant saisi à la main ne prouve rien.
     */
    private Long soumisParId;
}
