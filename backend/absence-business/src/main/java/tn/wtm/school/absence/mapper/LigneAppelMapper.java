package tn.wtm.school.absence.mapper;

import org.mapstruct.*;
import tn.wtm.school.absence.dto.reponse.LigneAppelReponse;
import tn.wtm.school.absence.entity.LigneAppel;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface LigneAppelMapper {

    LigneAppelReponse toResponse(LigneAppel ligneAppel);

    List<LigneAppelReponse> toResponseList(List<LigneAppel> lignes);
}
