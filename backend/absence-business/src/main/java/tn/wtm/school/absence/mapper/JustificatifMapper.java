package tn.wtm.school.absence.mapper;

import org.mapstruct.*;
import tn.wtm.school.absence.dto.reponse.JustificatifReponse;
import tn.wtm.school.absence.entity.JustificatifAbsence;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface JustificatifMapper {

    @Mapping(target = "ligneAppelId", source = "ligneAppel.id")
    @Mapping(target = "dateSeance", source = "ligneAppel.seanceAppel.dateSeance")
    @Mapping(target = "groupeClasseId", source = "ligneAppel.seanceAppel.groupeClasseId")
    @Mapping(target = "matiereId", source = "ligneAppel.seanceAppel.matiereId")
    JustificatifReponse toResponse(JustificatifAbsence justificatif);

    List<JustificatifReponse> toResponseList(List<JustificatifAbsence> justificatifs);
}
