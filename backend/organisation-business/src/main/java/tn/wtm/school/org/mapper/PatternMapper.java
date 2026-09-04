package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.PatternRequest;
import tn.wtm.school.org.dto.response.PatternResponse;
import tn.wtm.school.org.entity.Pattern;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = PatternDetailMapper.class, builder = @Builder(disableBuilder = true))
public interface PatternMapper {


    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idPattern",      ignore = true)
    @Mapping(target = "tenantId",       ignore = true)
    @Mapping(target = "subjectLevel",   ignore = true)   // résolu dans le service
    @Mapping(target = "schoolYear",     ignore = true)   // résolu dans le service
    @Mapping(target = "patternDetails", ignore = true)   // géré séparément dans le service
    Pattern toEntity(PatternRequest dto);

    // ── toResponse (details imbriqués via PatternDetailMapper) ────────────────
    @Mapping(target = "schoolYearId",   source = "schoolYear.idAnnee")
    @Mapping(target = "schoolYearNom",  source = "schoolYear.nom")
    @Mapping(target = "subjectLevelId", source = "subjectLevel.idNiveauMatiere")
    @Mapping(target = "subjectCode",    source = "subjectLevel.subject.codeMatiere")
    @Mapping(target = "subjectLib",     source = "subjectLevel.subject.libMatiere")
    @Mapping(target = "levelCode",      source = "subjectLevel.level.code")
    @Mapping(target = "levelNom",       source = "subjectLevel.level.nom")
    @Mapping(target = "details",        source = "patternDetails")
    PatternResponse toResponse(Pattern pattern);

    List<PatternResponse> toResponseList(List<Pattern> list);

    List<PatternResponse> toResponseList(Set<Pattern> set);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idPattern",      ignore = true)
    @Mapping(target = "tenantId",       ignore = true)
    @Mapping(target = "subjectLevel",   ignore = true)
    @Mapping(target = "schoolYear",     ignore = true)
    @Mapping(target = "patternDetails", ignore = true)
    void updateFromDto(PatternRequest dto, @MappingTarget Pattern entity);

}
