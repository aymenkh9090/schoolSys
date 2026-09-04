package tn.wtm.school.absence.port;

import java.util.List;

public interface PortEleveGroupe {
    List<Long> trouverIdsElevesParGroupe(String tenantId, Long groupeClasseId);
}
