package tn.wtm.school.org.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.enums.UserRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolUserResponse {

    private Long id;
    private String tenantId;
    private String email;
    private String nomComplet;
    private UserRole role;
    private String matiere;
    private String telephone;
    private boolean actif;
    /** Id de la fiche enseignant liée (role=TEACHER uniquement). */
    private Long enseignantId;
    private String keycloakUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Présent uniquement dans la réponse POST (création). Null pour les GET. */
    private String username;
    /** Présent uniquement dans la réponse POST (création). NE JAMAIS LOGGER. */
    private String tempPassword;

    public static SchoolUserResponse from(SchoolUser user) {
        return SchoolUserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .email(user.getEmail())
                .nomComplet(user.getNomComplet())
                .role(user.getRole())
                .matiere(user.getMatiere())
                .telephone(user.getTelephone())
                .actif(user.isActif())
                .enseignantId(user.getTeacher() != null ? user.getTeacher().getIdEnseignant() : null)
                .keycloakUserId(user.getKeycloakUserId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
