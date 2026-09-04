package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.SubjectRequest;
import tn.wtm.school.org.dto.response.SubjectResponse;
import tn.wtm.school.org.entity.Subject;

import java.util.List;

@Mapper(componentModel = "spring", uses = SubjectLevelMapper.class, builder = @Builder(disableBuilder = true))
public interface SubjectMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idMatiere",     ignore = true)
    @Mapping(target = "tenantId",      ignore = true)
    @Mapping(target = "subjectLevels", ignore = true)
    @Mapping(target = "necessiteLab",     defaultValue = "false")
    @Mapping(target = "necessiteSport",   defaultValue = "false")
    @Mapping(target = "typeSalleRequise", defaultValue = "NORMALE")
    @Mapping(target = "estPrincipale",    defaultValue = "false")
    @Mapping(target = "estEnseignee",     defaultValue = "true")
    Subject toEntity(SubjectRequest dto);

    // ── toResponse (niveaux imbriqués via SubjectLevelMapper) ─────────────────
    @Mapping(target = "nombreNiveaux",
            expression = "java(s.getSubjectLevels() != null ? s.getSubjectLevels().size() : 0)")
    @Mapping(target = "niveaux", source = "subjectLevels")
    SubjectResponse toResponse(Subject s);

    List<SubjectResponse> toResponseList(List<Subject> list);

    // ── toResponse léger — sans niveaux (pour les listes) ────────────────────
    @Named("toResponseLight")
    @Mapping(target = "nombreNiveaux", constant = "0")
    @Mapping(target = "niveaux",       ignore = true)
    SubjectResponse toResponseLight(Subject s);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idMatiere",     ignore = true)
    @Mapping(target = "tenantId",      ignore = true)
    @Mapping(target = "subjectLevels", ignore = true)
    void updateFromDto(SubjectRequest dto, @MappingTarget Subject entity);

}
