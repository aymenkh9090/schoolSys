package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.SubjectLevelRequest;
import tn.wtm.school.org.dto.response.SubjectLevelResponse;
import tn.wtm.school.org.entity.SubjectLevel;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {SubjectSessionTypeMapper.class, PatternMapper.class},
        builder = @Builder(disableBuilder = true)
)
public interface SubjectLevelMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idNiveauMatiere",     ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "subject",             ignore = true)  // résolu dans le service
    @Mapping(target = "level",               ignore = true)  // résolu dans le service
    @Mapping(target = "teachingAssignments", ignore = true)
    @Mapping(target = "subjectSessionTypes", ignore = true)
    @Mapping(target = "patterns",            ignore = true)
    @Mapping(target = "estObligatoire",      defaultValue = "true")
    SubjectLevel toEntity(SubjectLevelRequest dto);

    // ── toResponse (sessionTypes + patterns imbriqués) ────────────────────────
    @Mapping(target = "subjectId",       source = "subject.idMatiere")
    @Mapping(target = "subjectCode",     source = "subject.codeMatiere")
    @Mapping(target = "subjectLib",      source = "subject.libMatiere")
    @Mapping(target = "subjectCouleur",  source = "subject.couleur")
    @Mapping(target = "levelId",         source = "level.idNiveau")
    @Mapping(target = "levelNom",        source = "level.nom")
    @Mapping(target = "levelCode",       source = "level.code")
    @Mapping(target = "sessionTypes",    source = "subjectSessionTypes")  // SubjectSessionTypeMapper auto
    @Mapping(target = "patterns",        source = "patterns")              // PatternMapper auto
    SubjectLevelResponse toResponse(SubjectLevel sl);

    List<SubjectLevelResponse> toResponseList(List<SubjectLevel> list);

    // ── toResponse léger — sans patterns ni sessionTypes (pour les listes) ────
    @Named("toResponseLight")
    @Mapping(target = "subjectId",      source = "subject.idMatiere")
    @Mapping(target = "subjectCode",    source = "subject.codeMatiere")
    @Mapping(target = "subjectLib",     source = "subject.libMatiere")
    @Mapping(target = "subjectCouleur", source = "subject.couleur")
    @Mapping(target = "levelId",        source = "level.idNiveau")
    @Mapping(target = "levelNom",       source = "level.nom")
    @Mapping(target = "levelCode",      source = "level.code")
    @Mapping(target = "sessionTypes",   ignore = true)
    @Mapping(target = "patterns",       ignore = true)
    SubjectLevelResponse toResponseLight(SubjectLevel sl);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idNiveauMatiere",     ignore = true)
    @Mapping(target = "tenantId",            ignore = true)
    @Mapping(target = "subject",             ignore = true)
    @Mapping(target = "level",               ignore = true)
    @Mapping(target = "teachingAssignments", ignore = true)
    @Mapping(target = "subjectSessionTypes", ignore = true)
    @Mapping(target = "patterns",            ignore = true)
    void updateFromDto(SubjectLevelRequest dto, @MappingTarget SubjectLevel entity);


}
