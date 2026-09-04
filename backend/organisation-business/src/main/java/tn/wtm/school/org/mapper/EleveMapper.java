package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.EleveRequest;
import tn.wtm.school.org.dto.response.EleveResponse;
import tn.wtm.school.org.entity.Eleve;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface EleveMapper {

    @IgnoreAuditFields
    @Mapping(target = "idEleve", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "classeGroup", ignore = true)
    Eleve toEntity(EleveRequest request);

    @Mapping(target = "classeId", source = "classeGroup.idClasse")
    @Mapping(target = "classeCode", source = "classeGroup.code")
    EleveResponse toResponse(Eleve eleve);

    List<EleveResponse> toResponseList(List<Eleve> eleves);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idEleve", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "classeGroup", ignore = true)
    void updateFromRequest(EleveRequest request, @MappingTarget Eleve eleve);
}
