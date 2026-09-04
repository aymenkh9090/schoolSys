package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.ClassGroupRequest;
import tn.wtm.school.org.dto.response.ClassGroupResponse;
import tn.wtm.school.org.entity.ClassGroup;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ClassGroupMapper {
    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idClasse",            ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "schoolYear",          ignore = true)   // résolu dans le service
    @Mapping(target = "level",               ignore = true)   // résolu dans le service
    @Mapping(target = "teachingAssignments", ignore = true)
    @Mapping(target = "estActif",            defaultValue = "true")
    ClassGroup toEntity(ClassGroupRequest dto);

    // ── toResponse ────────────────────────────────────────────────────────────
    @Mapping(target = "schoolYearId",       source = "schoolYear.idAnnee")
    @Mapping(target = "schoolYearNom",      source = "schoolYear.nom")
    @Mapping(target = "levelId",            source = "level.idNiveau")
    @Mapping(target = "levelNom",           source = "level.nom")
    @Mapping(target = "levelCode",          source = "level.code")
    @Mapping(target = "nombreAffectations",
            expression = "java(cg.getTeachingAssignments() != null ? cg.getTeachingAssignments().size() : 0)")
    ClassGroupResponse toResponse(ClassGroup cg);

    List<ClassGroupResponse> toResponseList(List<ClassGroup> list);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idClasse",            ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "schoolYear",          ignore = true)
    @Mapping(target = "level",               ignore = true)
    @Mapping(target = "teachingAssignments", ignore = true)
    void updateFromDto(ClassGroupRequest dto, @MappingTarget ClassGroup entity);

}
