package tn.wtm.school.org.dto.response;

import lombok.*;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.enums.SessionType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeachingAssignmentResponse {

    Long idTeachingAssignment;
    // SchoolYear
    Long schoolYearId;
    String schoolYearNom;
    // Teacher
    Long teacherId;
    String teacherCode;
    String teacherNomComplet;
    String teacherEmail;
    // ClassGroup
    Long classGroupId;
    String classGroupCode;
    Specialite classGroupSpecialite;
    Integer classGroupNbEleve;
    // SubjectLevel → Subject
    Long subjectLevelId;
    String subjectCode;
    String subjectLib;
    String subjectCouleur;
    // SubjectLevel → Level
    String levelCode;
    String levelNom;
    // SubjectSessionType
    Long subjectSessionTypeId;
    Double sessionDuration;
    Boolean sessionRequiresSplit;
    Integer sessionGroupCount;
    // Config affectation
    Integer priority;
    Boolean isActive;

}
