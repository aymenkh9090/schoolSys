package tn.wtm.school.org.dto.response;

public record MissingAssignmentEntry(
        Long classGroupId,
        String classGroupCode,
        String levelCode,
        Long subjectLevelId,
        String subjectCode,
        String subjectNom,
        Long subjectSessionTypeId,
        String sessionType
) {}
