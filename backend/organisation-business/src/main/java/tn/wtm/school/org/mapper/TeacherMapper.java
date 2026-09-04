package tn.wtm.school.org.mapper;


import org.mapstruct.*;
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;

import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring", uses = TeachingAssignmentMapper.class, builder = @Builder(disableBuilder = true))
public interface TeacherMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idEnseignant",        ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "teachingAssignments", ignore = true)
    @Mapping(target = "estEnPoste",          defaultValue = "true")
    Teacher toEntity(TeacherRequest dto);

    // ── toResponse (affectations imbriquées via TeachingAssignmentMapper) ─────
    @Mapping(target = "nomComplet",
            expression = "java(t.getPrenom() + ' ' + t.getNom())")
    @Mapping(target = "nombreAffectations",
            expression = "java(t.getTeachingAssignments() != null ? t.getTeachingAssignments().size() : 0)")
    @Mapping(target = "totalHeures",
            expression = "java(calculateTotalHeures(t))")
    @Mapping(target = "affectations", source = "teachingAssignments")
    TeacherResponse toResponse(Teacher t);

    // ── toResponse léger — sans affectations (pour les listes/dropdowns) ──────
    @Named("toResponseLight")
    @Mapping(target = "nomComplet",
            expression = "java(t.getPrenom() + ' ' + t.getNom())")
    @Mapping(target = "nombreAffectations", constant = "0")
    @Mapping(target = "totalHeures",       ignore = true)
    @Mapping(target = "affectations",       ignore = true)
    TeacherResponse toResponseLight(Teacher t);

    @IterableMapping(qualifiedByName = "toResponseLight")
    List<TeacherResponse> toResponseLightList(List<Teacher> list);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idEnseignant",        ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "teachingAssignments", ignore = true)
    void updateFromDto(TeacherRequest dto, @MappingTarget Teacher entity);

    default Double calculateTotalHeures(Teacher teacher) {
        if (teacher == null || teacher.getTeachingAssignments() == null) {
            return 0.0;
        }

        return teacher.getTeachingAssignments().stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getIsActive()))
                .map(TeachingAssignment::getSubjectSessionType)
                .filter(Objects::nonNull)
                .map(subjectSessionType -> subjectSessionType.getDuration() != null
                        ? subjectSessionType.getDuration()
                        : 0.0)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

}
