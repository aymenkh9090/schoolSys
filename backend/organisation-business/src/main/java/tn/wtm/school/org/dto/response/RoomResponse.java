package tn.wtm.school.org.dto.response;

import lombok.*;
import tn.wtm.school.org.enums.RoomType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {

    Long idSalle;
    String codeSalle;
    RoomType typeSalle;
    Integer capacite;
    String codeBloc;
    String numEtage;
    String equipements;
    Boolean estDisponible;

}
