package tn.wtm.school.planning.constraints.service;

import tn.wtm.school.planning.constraints.dto.response.ConstraintDefinitionResponse;

import java.util.List;

public interface ConstraintDefinitionService {

    List<ConstraintDefinitionResponse> findAll();

    ConstraintDefinitionResponse findById(Long id);

    ConstraintDefinitionResponse findByCode(String code);
}
