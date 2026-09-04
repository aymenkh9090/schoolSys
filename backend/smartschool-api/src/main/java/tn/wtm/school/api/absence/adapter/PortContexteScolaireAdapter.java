package tn.wtm.school.api.absence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.absence.port.PortContexteScolaire;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.service.TeacherService;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Branchement du module absence sur le module organisation.
 *
 * <p>L'accès se fait par repository et non par service : on ne veut ici que des
 * libellés, en lot, sans les DTO complets ni les affectations que les services
 * chargent pour leurs propres besoins.</p>
 */
@Component
@RequiredArgsConstructor
public class PortContexteScolaireAdapter implements PortContexteScolaire {

    private final TeacherService teacherService;
    private final TeacherRepository teacherRepository;
    private final ClassGroupRepository classGroupRepository;
    private final SubjectRepository subjectRepository;

    @Override
    public Optional<Long> idEnseignantCourant() {
        return teacherService.getCurrentTeacherId();
    }

    @Override
    public Map<Long, String> nomsEnseignants(String tenantId, Collection<Long> ids) {
        return libelles(ids, () -> teacherRepository.findByTenantIdAndIdEnseignantIn(tenantId, ids),
                Teacher::getIdEnseignant,
                t -> ((t.getPrenom() == null ? "" : t.getPrenom() + " ")
                        + (t.getNom() == null ? "" : t.getNom())).trim());
    }

    @Override
    public Map<Long, String> codesClasses(String tenantId, Collection<Long> ids) {
        return libelles(ids, () -> classGroupRepository.findByTenantIdAndIdClasseIn(tenantId, ids),
                ClassGroup::getIdClasse, ClassGroup::getCode);
    }

    @Override
    public Map<Long, String> libellesMatieres(String tenantId, Collection<Long> ids) {
        return libelles(ids, () -> subjectRepository.findByTenantIdAndIdMatiereIn(tenantId, ids),
                Subject::getIdMatiere, Subject::getLibMatiere);
    }

    /**
     * Charge en lot et indexe par identifiant, en écartant les libellés vides.
     *
     * <p>Un libellé absent doit rester absent de la table : le consommateur
     * préfère ne rien afficher plutôt qu'une chaîne vide, qui donnerait
     * « séance du 12/03 en  avec  » dans une réponse d'assistant.</p>
     */
    private static <T> Map<Long, String> libelles(Collection<Long> ids,
                                                  java.util.function.Supplier<Collection<T>> chargement,
                                                  Function<T, Long> cle,
                                                  Function<T, String> libelle) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return chargement.get().stream()
                .filter(e -> cle.apply(e) != null)
                .filter(e -> libelle.apply(e) != null && !libelle.apply(e).isBlank())
                .collect(Collectors.toMap(cle, libelle, (a, b) -> a));
    }
}
