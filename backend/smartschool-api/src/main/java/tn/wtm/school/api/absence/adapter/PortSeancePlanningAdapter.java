package tn.wtm.school.api.absence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.absence.port.PortSeancePlanning;
import tn.wtm.school.planning.solver.repository.TimetableSessionRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortSeancePlanningAdapter implements PortSeancePlanning {

    private final TimetableSessionRepository timetableSessionRepository;

    @Override
    public Optional<CreneauSeance> trouverCreneau(String tenantId, Long seancePlanningId) {
        if (seancePlanningId == null) {
            return Optional.empty();
        }
        return timetableSessionRepository
                .findByIdTimetableSessionAndTenantId(seancePlanningId, tenantId)
                .map(s -> new CreneauSeance(s.getDay(), s.getStartTime(), s.getEndTime()));
    }
}
