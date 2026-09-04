package tn.wtm.school.absence.mapper;

import org.mapstruct.*;
import tn.wtm.school.absence.dto.reponse.HistoriqueAppelReponse;
import tn.wtm.school.absence.entity.HistoriqueAppel;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface HistoriqueAppelMapper {

    @Mapping(target = "ligneAppelId", source = "ligneAppel.id")
    HistoriqueAppelReponse toResponse(HistoriqueAppel historique);

    List<HistoriqueAppelReponse> toResponseList(List<HistoriqueAppel> historiques);
}
