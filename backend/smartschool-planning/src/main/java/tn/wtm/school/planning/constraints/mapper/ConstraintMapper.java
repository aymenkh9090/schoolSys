package tn.wtm.school.planning.constraints.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tn.wtm.school.planning.constraints.dto.request.ConstraintProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.ConstraintSettingRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintDefinitionResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSettingResponse;
import tn.wtm.school.planning.constraints.entity.ConstraintDefinition;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface ConstraintMapper {

    ConstraintDefinitionResponse toDefinitionResponse(ConstraintDefinition definition);

    List<ConstraintDefinitionResponse> toDefinitionResponseList(List<ConstraintDefinition> definitions);

    ConstraintProfile toProfile(ConstraintProfileRequest request);

    ConstraintProfileResponse toProfileResponse(ConstraintProfile profile);

    List<ConstraintProfileResponse> toProfileResponseList(List<ConstraintProfile> profiles);

    @Mapping(target = "profile", ignore = true)
    @Mapping(target = "definition", ignore = true)
    ConstraintSetting toSetting(ConstraintSettingRequest request);

    @Mapping(target = "profileId",       source = "profile.idConstraintProfile")
    @Mapping(target = "definitionId",    source = "definition.idConstraintDefinition")
    @Mapping(target = "constraintCode",  source = "definition.code")
    @Mapping(target = "constraintName",  source = "definition.name")
    @Mapping(target = "description",     source = "definition.description")
    @Mapping(target = "category",        source = "definition.category")
    @Mapping(target = "type",            source = "definition.type")
    ConstraintSettingResponse toSettingResponse(ConstraintSetting setting);

    List<ConstraintSettingResponse> toSettingResponseList(List<ConstraintSetting> settings);
}
