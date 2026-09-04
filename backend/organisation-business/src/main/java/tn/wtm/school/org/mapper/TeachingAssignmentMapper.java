package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.TeachingAssignmentRequest;
import tn.wtm.school.org.dto.response.TeachingAssignmentResponse;
import tn.wtm.school.org.entity.TeachingAssignment;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TeachingAssignmentMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idTeachingAssignment", ignore = true)
    @Mapping(target = "tenantId",             ignore = true)
    @Mapping(target = "schoolYear",           ignore = true)  // résolu dans le service
    @Mapping(target = "teacher",              ignore = true)  // résolu dans le service
    @Mapping(target = "classGroup",           ignore = true)  // résolu dans le service
    @Mapping(target = "subjectLevel",         ignore = true)  // résolu dans le service
    @Mapping(target = "subjectSessionType",   ignore = true)  // résolu dans le service
    @Mapping(target = "isActive",             defaultValue = "true")
    TeachingAssignment toEntity(TeachingAssignmentRequest dto);

    // ── toResponse (toutes les infos aplaties) ────────────────────────────────
    @Mapping(target = "schoolYearId",        source = "schoolYear.idAnnee")
    @Mapping(target = "schoolYearNom",       source = "schoolYear.nom")
    @Mapping(target = "teacherId",           source = "teacher.idEnseignant")
    @Mapping(target = "teacherCode",         source = "teacher.codeEnseignant")
    @Mapping(target = "teacherNomComplet",
            expression = "java(ta.getTeacher().getPrenom() + ' ' + ta.getTeacher().getNom())")
    @Mapping(target = "teacherEmail",            source = "teacher.email")
    @Mapping(target = "classGroupId",            source = "classGroup.idClasse")
    @Mapping(target = "classGroupCode",          source = "classGroup.code")
    @Mapping(target = "classGroupSpecialite",    source = "classGroup.codeSpecialite")
    @Mapping(target = "classGroupNbEleve",       source = "classGroup.nbEleve")
    @Mapping(target = "subjectLevelId",          source = "subjectLevel.idNiveauMatiere")
    @Mapping(target = "subjectCode",             source = "subjectLevel.subject.codeMatiere")
    @Mapping(target = "subjectLib",              source = "subjectLevel.subject.libMatiere")
    @Mapping(target = "subjectCouleur",          source = "subjectLevel.subject.couleur")
    @Mapping(target = "levelCode",               source = "subjectLevel.level.code")
    @Mapping(target = "levelNom",                source = "subjectLevel.level.nom")
    @Mapping(target = "subjectSessionTypeId",    source = "subjectSessionType.idSubjectSessionType")
    @Mapping(target = "sessionDuration",         source = "subjectSessionType.duration")
    @Mapping(target = "sessionRequiresSplit",     source = "subjectSessionType.requiresSplit")
    @Mapping(target = "sessionGroupCount",        source = "subjectSessionType.groupCount")
    TeachingAssignmentResponse toResponse(TeachingAssignment ta);

    List<TeachingAssignmentResponse> toResponseList(List<TeachingAssignment> list);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idTeachingAssignment", ignore = true)
    @Mapping(target = "tenantId",             ignore = true)
    @Mapping(target = "schoolYear",           ignore = true)
    @Mapping(target = "teacher",              ignore = true)
    @Mapping(target = "classGroup",           ignore = true)
    @Mapping(target = "subjectLevel",         ignore = true)
    @Mapping(target = "subjectSessionType",   ignore = true)
    void updateFromDto(TeachingAssignmentRequest dto, @MappingTarget TeachingAssignment entity);

}
