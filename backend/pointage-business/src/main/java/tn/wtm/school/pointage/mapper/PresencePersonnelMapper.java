package tn.wtm.school.pointage.mapper;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.requete.PointageRequete;
import tn.wtm.school.pointage.entity.PresencePersonnel;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PresencePersonnelMapper {

    @Mapping(target = "justificatif", ignore = true)
    @Mapping(target = "nomMembre", ignore = true)
    PresencePersonnelReponse toResponse(PresencePersonnel entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "saisiPar", ignore = true)
    @Mapping(target = "saisiA", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    PresencePersonnel toEntity(PointageRequete requete);
}
