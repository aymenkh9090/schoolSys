package tn.wtm.school.org.mapper;


import org.mapstruct.*;
import tn.wtm.school.org.dto.request.LevelRequest;
import tn.wtm.school.org.dto.response.LevelResponse;
import tn.wtm.school.org.entity.Level;

import java.util.List;

@Mapper(componentModel = "spring", uses = ClassGroupMapper.class, builder = @Builder(disableBuilder = true))
public interface LevelMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idNiveau",       ignore = true)
    @Mapping(target = "tenantId",       ignore = true)
    @Mapping(target = "classGroups",    ignore = true)
    @Mapping(target = "subjectLevels",  ignore = true)
    @Mapping(target = "estActif",       defaultValue = "true")
    Level toEntity(LevelRequest dto);

    // ── toResponse (avec classes imbriquées) ──────────────────────────────────
    @Mapping(target = "nombreClasses",
            expression = "java(level.getClassGroups() != null ? level.getClassGroups().size() : 0)")
    @Mapping(target = "nombreMatieres",
            expression = "java(level.getSubjectLevels() != null ? level.getSubjectLevels().size() : 0)")
    @Mapping(target = "classes", source = "classGroups")
    LevelResponse toResponse(Level level);

    List<LevelResponse> toResponseList(List<Level> levels);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idNiveau",       ignore = true)
    @Mapping(target = "tenantId",       ignore = true)
    @Mapping(target = "classGroups",    ignore = true)
    @Mapping(target = "subjectLevels",  ignore = true)
    void updateFromDto(LevelRequest dto, @MappingTarget Level entity);

}
