package tn.wtm.school.org.dto.response;


import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubjectLevelResponse {


    Long idNiveauMatiere;
    // Subject info
    Long subjectId;
    String subjectCode;
    String subjectLib;
    String subjectCouleur;
    // Level info
    Long levelId;
    String levelNom;
    String levelCode;
    // Config
    Double heuresSemaine;
    String description;
    Boolean estObligatoire;
    Double coefficient;
    Integer maxHeuresConsecutives;
    // Nested
    List<SubjectSessionTypeResponse> sessionTypes;
    List<PatternResponse> patterns;








}
