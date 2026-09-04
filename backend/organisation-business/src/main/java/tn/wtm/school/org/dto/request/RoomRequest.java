package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import tn.wtm.school.org.enums.RoomType;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomRequest {



    @NotBlank(message = "Le code de la salle est obligatoire")
    @Size(max = 10, message = "Le code salle ne doit pas dépasser 10 caractères")
    String codeSalle;

    @NotNull(message = "Le type de salle est obligatoire")
    RoomType typeSalle;

    @Min(value = 1, message = "La capacité doit être au moins 1")
    @Max(value = 500, message = "La capacité ne peut pas dépasser 500")
    Integer capacite;

    @Size(max = 10, message = "Le code bloc ne doit pas dépasser 10 caractères")
    String codeBloc;

    @Size(max = 10, message = "Le numéro d'étage ne doit pas dépasser 10 caractères")
    String numEtage;

    @Size(max = 255, message = "Les équipements ne doivent pas dépasser 255 caractères")
    String equipements;

    Boolean estDisponible;



}
