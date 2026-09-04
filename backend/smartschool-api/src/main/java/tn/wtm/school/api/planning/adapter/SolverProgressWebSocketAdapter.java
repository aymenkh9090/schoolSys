package tn.wtm.school.api.planning.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tn.wtm.school.planning.solver.port.SolverProgressPublisher;

/**
 * Diffuse la progression du solveur sur le topic de l'établissement concerné.
 * Le préfixe par tenant est ce que {@code StompAuthChannelInterceptor} vérifie
 * à l'abonnement : les deux doivent rester alignés.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolverProgressWebSocketAdapter implements SolverProgressPublisher {

    private static final String DESTINATION_TEMPLATE = "/topic/%s/planning/jobs";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(String tenantId, SolverProgress progress) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("[ws] Progression non diffusée — tenant absent (job {})", progress.jobId());
            return;
        }
        messagingTemplate.convertAndSend(DESTINATION_TEMPLATE.formatted(tenantId), progress);
    }
}
