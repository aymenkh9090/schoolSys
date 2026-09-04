package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.PatternDetailRequest;
import tn.wtm.school.org.dto.response.PatternDetailResponse;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.enums.RoomType;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PatternDetailMapper {

    // ── toEntity ──────────────────────────────────────────────────────────────
    @IgnoreAuditFields
    @Mapping(target = "idPatternDetail",    ignore = true)
    @Mapping(target = "tenantId",           ignore = true)
    @Mapping(target = "pattern",            ignore = true)   // résolu dans PatternService
    @Mapping(target = "subjectSessionType", ignore = true)   // résolu dans PatternService
    @Mapping(target = "isSplit",            defaultValue = "false")
    @Mapping(target = "weekParity",         defaultExpression = "java(tn.wtm.school.org.enums.WeekParity.ALL)")
    // requiredRoomType : convertit String → enum via la méthode helper
    @Mapping(target = "requiredRoomType",   expression = "java(mapRoomType(dto.getRequiredRoomType()))")
    PatternDetail toEntity(PatternDetailRequest dto);

    // ── toResponse ────────────────────────────────────────────────────────────
    @Mapping(target = "subjectSessionTypeId",      source = "subjectSessionType.idSubjectSessionType")
    @Mapping(target = "sessionTypeDuration",       source = "subjectSessionType.duration")
    @Mapping(target = "sessionTypeRequiresSplit",  source = "subjectSessionType.requiresSplit")
    PatternDetailResponse toResponse(PatternDetail detail);

    List<PatternDetailResponse> toResponseList(List<PatternDetail> list);

    // ── updateFromDto ─────────────────────────────────────────────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idPatternDetail",    ignore = true)
    @Mapping(target = "tenantId",           ignore = true)
    @Mapping(target = "pattern",            ignore = true)
    @Mapping(target = "subjectSessionType", ignore = true)
    @Mapping(target = "requiredRoomType",   expression = "java(mapRoomType(dto.getRequiredRoomType()))")
    void updateFromDto(PatternDetailRequest dto, @MappingTarget PatternDetail entity);

    // ── Helper : String → RoomType (nullable) ─────────────────────────────────
    default RoomType mapRoomType(String value) {
        if (value == null || value.isBlank()) return null;
        return RoomType.valueOf(value.trim().toUpperCase());
    }

}
