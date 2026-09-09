package tn.wtm.school.api.pointage.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.port.PortMembrePersonnel;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PortMembrePersonnelAdapter implements PortMembrePersonnel {

    private final TeacherRepository teacherRepository;
    private final SchoolUserRepository schoolUserRepository;

    @Override
    public Map<Long, String> nomsParIds(String tenantId, TypePersonnel type, Collection<Long> ids) {
        if (type == null || ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return switch (type) {
            case ENSEIGNANT -> teacherRepository.findByTenantIdAndIdEnseignantIn(tenantId, ids).stream()
                    .collect(Collectors.toMap(Teacher::getIdEnseignant, PortMembrePersonnelAdapter::nomEnseignant));
            // Surveillants et administratifs sont pointés via leur compte SchoolUser.
            case SURVEILLANT, ADMINISTRATIF -> schoolUserRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                    .collect(Collectors.toMap(SchoolUser::getId, PortMembrePersonnelAdapter::nomUtilisateur));
        };
    }

    private static String nomEnseignant(Teacher t) {
        return (nullSafe(t.getPrenom()) + " " + nullSafe(t.getNom())).trim();
    }

    private static String nomUtilisateur(SchoolUser u) {
        return (u.getNomComplet() != null && !u.getNomComplet().isBlank()) ? u.getNomComplet() : u.getEmail();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
