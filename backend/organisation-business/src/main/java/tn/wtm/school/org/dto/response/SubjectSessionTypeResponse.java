package tn.wtm.school.org.dto.response;

import lombok.*;
import tn.wtm.school.org.enums.SessionType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectSessionTypeResponse {

    Long idSubjectSessionType;
    Long subjectLevelId;
    String subjectCode;
    String levelCode;
    SessionType type;
    Double duration;
    Boolean requiresSplit;
    Integer groupCount;
    Boolean estActif;

}
