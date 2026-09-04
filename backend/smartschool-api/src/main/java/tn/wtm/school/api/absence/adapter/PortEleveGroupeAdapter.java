package tn.wtm.school.api.absence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.absence.port.PortEleveGroupe;
import tn.wtm.school.org.service.EleveService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PortEleveGroupeAdapter implements PortEleveGroupe {

    private final EleveService eleveService;

    @Override
    public List<Long> trouverIdsElevesParGroupe(String tenantId, Long groupeClasseId) {
        return eleveService.listerIdsParClasse(tenantId, groupeClasseId);
    }
}
