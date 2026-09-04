package tn.wtm.school.org.dto.response;


import lombok.*;
import tn.wtm.school.org.enums.RoomType;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubjectResponse {

    private Long idMatiere;
    private String codeMatiere;
    private String libMatiere;
    private String description;
    private Boolean necessiteLab;
    private Boolean necessiteSport;
    private RoomType typeSalleRequise;
    private String couleur;
    private String abreviation;
    private Boolean estPrincipale;
    private Boolean estEnseignee;
    private Integer nombreNiveaux;
    private List<SubjectLevelResponse> niveaux;



}
