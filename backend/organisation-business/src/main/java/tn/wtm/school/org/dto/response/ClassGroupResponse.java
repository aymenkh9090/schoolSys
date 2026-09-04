package tn.wtm.school.org.dto.response;

import lombok.*;
import tn.wtm.school.org.enums.Specialite;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassGroupResponse {



    Long idClasse;
    String code;
    Specialite codeSpecialite;
    Integer nbEleve;
    Boolean estActif;
    Long schoolYearId;
    String schoolYearNom;
    Long levelId;
    String levelNom;
    String levelCode;
    Integer nombreAffectations;




}
