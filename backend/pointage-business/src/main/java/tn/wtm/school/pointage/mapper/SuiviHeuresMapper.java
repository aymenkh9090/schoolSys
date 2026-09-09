package tn.wtm.school.pointage.mapper;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.entity.SuiviHeuresEnseignant;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SuiviHeuresMapper {

    SuiviHeuresEnseignantReponse toResponse(SuiviHeuresEnseignant entity);
}
