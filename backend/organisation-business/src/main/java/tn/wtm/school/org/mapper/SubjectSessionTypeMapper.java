package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.SubjectSessionTypeRequest;
import tn.wtm.school.org.dto.response.SubjectSessionTypeResponse;
import tn.wtm.school.org.entity.SubjectSessionType;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SubjectSessionTypeMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idSubjectSessionType", ignore = true)
    @Mapping(target = "tenantId",             ignore = true)
    @Mapping(target = "subjectLevel",         ignore = true)  // résolu dans le service
    @Mapping(target = "patternDetails",       ignore = true)
    @Mapping(target = "teachingAssignments",  ignore = true)
    @Mapping(target = "estActif",             ignore = true)
    SubjectSessionType toEntity(SubjectSessionTypeRequest dto);

    // ── toResponse ────────────────────────────────────────────────────────────
    @Mapping(target = "subjectLevelId",  source = "subjectLevel.idNiveauMatiere")
    @Mapping(target = "subjectCode",     source = "subjectLevel.subject.codeMatiere")
    @Mapping(target = "levelCode",       source = "subjectLevel.level.code")
    SubjectSessionTypeResponse toResponse(SubjectSessionType sst);

    List<SubjectSessionTypeResponse> toResponseList(List<SubjectSessionType> list);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idSubjectSessionType", ignore = true)
    @Mapping(target = "tenantId",             ignore = true)
    @Mapping(target = "subjectLevel",         ignore = true)
    @Mapping(target = "patternDetails",       ignore = true)
    @Mapping(target = "teachingAssignments",  ignore = true)
    @Mapping(target = "estActif",             ignore = true)
    void updateFromDto(SubjectSessionTypeRequest dto, @MappingTarget SubjectSessionType entity);

}
