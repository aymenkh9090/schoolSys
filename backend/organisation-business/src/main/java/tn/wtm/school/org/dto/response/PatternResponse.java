package tn.wtm.school.org.dto.response;

import lombok.*;
import tn.wtm.school.org.enums.PatternType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatternResponse {

    Long idPattern;
    String name;
    Double totalHours;
    Integer sessionCount;
    String repartition;
    PatternType patternType;
    Boolean active;
    // SchoolYear info (null = pattern par défaut)
    Long schoolYearId;
    String schoolYearNom;
    // SubjectLevel info
    Long subjectLevelId;
    String subjectCode;
    String subjectLib;
    String levelCode;
    String levelNom;
    // Nested details
    List<PatternDetailResponse> details;

}
