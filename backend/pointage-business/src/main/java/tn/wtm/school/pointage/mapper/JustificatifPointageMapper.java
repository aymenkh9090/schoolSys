package tn.wtm.school.pointage.mapper;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.entity.JustificatifPointage;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface JustificatifPointageMapper {

    JustificatifPointageReponse toResponse(JustificatifPointage entity);
}
