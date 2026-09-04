package tn.wtm.school.absence.mapper;

import org.mapstruct.*;
import tn.wtm.school.absence.dto.reponse.AppelReponse;
import tn.wtm.school.absence.entity.SeanceAppel;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true), uses = {LigneAppelMapper.class})
public interface SeanceAppelMapper {

    @Mapping(target = "lignesAppel", source = "lignesAppel")
    AppelReponse toResponse(SeanceAppel seanceAppel);
}
