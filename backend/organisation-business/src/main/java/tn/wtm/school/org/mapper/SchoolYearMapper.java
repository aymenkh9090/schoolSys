package tn.wtm.school.org.mapper;


import org.mapstruct.*;
import tn.wtm.school.org.dto.request.SchoolYearRequest;
import tn.wtm.school.org.dto.response.SchoolYearResponse;
import tn.wtm.school.org.entity.SchoolYear;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SchoolYearMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idAnnee",           ignore = true)
    @Mapping(target = "tenantId",          ignore = true)
    @Mapping(target = "classGroups",       ignore = true)
    @Mapping(target = "teachingAssignments", ignore = true)
    @Mapping(target = "estActive",         defaultValue = "true")
    @Mapping(target = "estCourante",       defaultValue = "false")
    SchoolYear toEntity(SchoolYearRequest dto);

    // ── toResponse ────────────────────────────────────────────────────────────
    @Mapping(target = "nombreClasses",
            expression = "java(sy.getClassGroups() != null ? sy.getClassGroups().size() : 0)")
    @Mapping(target = "nombreAffectations",
            expression = "java(sy.getTeachingAssignments() != null ? sy.getTeachingAssignments().size() : 0)")
    SchoolYearResponse toResponse(SchoolYear sy);

    // ── updateFromDto (PUT/PATCH) ──────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idAnnee",             ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "classGroups",         ignore = true)
    @Mapping(target = "teachingAssignments", ignore = true)
    void updateFromDto(SchoolYearRequest dto, @MappingTarget SchoolYear entity);

}
