package tn.wtm.school.planning.constraints.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.planning.constraints.dto.response.ConstraintDefinitionResponse;
import tn.wtm.school.planning.constraints.mapper.ConstraintMapper;
import tn.wtm.school.planning.constraints.repository.ConstraintDefinitionRepository;
import tn.wtm.school.planning.constraints.service.ConstraintDefinitionService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConstraintDefinitionServiceImpl implements ConstraintDefinitionService {

    private final ConstraintDefinitionRepository repository;
    private final ConstraintMapper mapper;

    @Override
    public List<ConstraintDefinitionResponse> findAll() {
        return mapper.toDefinitionResponseList(repository.findAllByOrderByCategoryAscCodeAsc());
    }

    @Override
    public ConstraintDefinitionResponse findById(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant de la contrainte est obligatoire");
        }
        return mapper.toDefinitionResponse(
                repository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Contrainte introuvable avec l'ID : " + id))
        );
    }

    @Override
    public ConstraintDefinitionResponse findByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code de la contrainte est obligatoire");
        }
        return mapper.toDefinitionResponse(
                repository.findByCode(code.trim())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Contrainte introuvable avec le code : " + code))
        );
    }
}
