package tn.wtm.school.absence.mapper;

import org.mapstruct.*;
import tn.wtm.school.absence.dto.requete.EnregistrementCahierRequete;
import tn.wtm.school.absence.dto.reponse.CahierSeanceReponse;
import tn.wtm.school.absence.entity.CahierSeance;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CahierSeanceMapper {

    @Mapping(target = "seanceAppelId", source = "seanceAppel.id")
    CahierSeanceReponse toResponse(CahierSeance cahier);

    @IgnoreAuditFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "seanceAppel", ignore = true)
    @Mapping(target = "enseignantId", ignore = true)
    @Mapping(target = "estVerrouille", ignore = true)
    @Mapping(target = "verrouillageAt", ignore = true)
    void updateFromRequete(EnregistrementCahierRequete requete, @MappingTarget CahierSeance cahier);
}
