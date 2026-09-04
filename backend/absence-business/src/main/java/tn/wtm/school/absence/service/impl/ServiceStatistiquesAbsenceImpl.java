package tn.wtm.school.absence.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.absence.dto.reponse.ResumeAbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.StatistiquesAbsenceReponse;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.absence.repository.LigneAppelRepository;
import tn.wtm.school.absence.service.ServiceStatistiquesAbsence;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.service.TenantService;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceStatistiquesAbsenceImpl extends TenantService implements ServiceStatistiquesAbsence {

    private final LigneAppelRepository ligneAppelRepository;

    @Override
    public ResumeAbsenceEleveReponse resumeParEleve(Long eleveId, String anneeAcademique) {
        if (eleveId == null) {
            throw new BadRequestException("L'identifiant de l'élève est obligatoire");
        }
        if (anneeAcademique == null || anneeAcademique.isBlank()) {
            throw new BadRequestException("L'année académique est obligatoire");
        }

        String tenantId = currentTenant();
        List<LigneAppel> lignes = ligneAppelRepository.findByEleveEtAnnee(tenantId, eleveId, anneeAcademique);

        long totalAbsences = compter(lignes, StatutPresence.ABSENT);
        long absJustifiees = lignes.stream()
                .filter(l -> l.getStatut() == StatutPresence.ABSENT && Boolean.TRUE.equals(l.getEstJustifie()))
                .count();
        long absNonJustifiees = totalAbsences - absJustifiees;
        long totalRetards = compter(lignes, StatutPresence.RETARD);
        long totalExclusions = compter(lignes, StatutPresence.EXCLU);

        return ResumeAbsenceEleveReponse.builder()
                .eleveId(eleveId)
                .anneeAcademique(anneeAcademique)
                .totalAbsences(totalAbsences)
                .absencesJustifiees(absJustifiees)
                .absencesNonJustifiees(absNonJustifiees)
                .totalRetards(totalRetards)
                .totalExclusions(totalExclusions)
                .periode(anneeAcademique)
                .build();
    }

    @Override
    public StatistiquesAbsenceReponse resumeParClasse(Long groupeClasseId, LocalDate debut, LocalDate fin) {
        if (groupeClasseId == null) {
            throw new BadRequestException("L'identifiant du groupe classe est obligatoire");
        }

        String tenantId = currentTenant();

        // Seules les bornes renseignées deviennent des prédicats : une période
        // ouverte n'est pas envoyée comme paramètre nul (que PostgreSQL refuse).
        Specification<LigneAppel> criteres = (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();
            predicats.add(cb.equal(racine.get("tenantId"), tenantId));
            predicats.add(cb.equal(
                    racine.get("seanceAppel").get("groupeClasseId"), groupeClasseId));
            if (debut != null) {
                predicats.add(cb.greaterThanOrEqualTo(
                        racine.get("seanceAppel").get("ouvertureAt"), debut.atStartOfDay()));
            }
            if (fin != null) {
                predicats.add(cb.lessThanOrEqualTo(
                        racine.get("seanceAppel").get("ouvertureAt"), fin.atTime(23, 59, 59)));
            }
            return cb.and(predicats.toArray(new Predicate[0]));
        };

        List<LigneAppel> lignes = ligneAppelRepository.findAll(criteres);

        return construireStatistiques(tenantId, groupeClasseId, debut, fin, lignes,
                debut + " → " + fin);
    }

    @Override
    public StatistiquesAbsenceReponse dashboardAdmin(String tenantId, String periode) {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = currentTenant();
        }

        List<LigneAppel> lignes = ligneAppelRepository.findByTenantId(tenantId);
        return construireStatistiques(tenantId, null, null, null, lignes, periode);
    }

    // ── Privé ────────────────────────────────────────────────────────────────────

    private StatistiquesAbsenceReponse construireStatistiques(String tenantId, Long groupeClasseId,
                                                               LocalDate debut, LocalDate fin,
                                                               List<LigneAppel> lignes, String periode) {
        long totalSeances = lignes.stream()
                .map(l -> l.getSeanceAppel().getId())
                .distinct()
                .count();
        long totalAbsences = compter(lignes, StatutPresence.ABSENT);
        long totalJustifiees = lignes.stream()
                .filter(l -> l.getStatut() == StatutPresence.ABSENT && Boolean.TRUE.equals(l.getEstJustifie()))
                .count();
        long totalNonJustifiees = totalAbsences - totalJustifiees;
        long totalRetards = compter(lignes, StatutPresence.RETARD);
        long totalExclusions = compter(lignes, StatutPresence.EXCLU);
        double tauxAbsenteisme = lignes.isEmpty() ? 0.0
                : Math.round((double) totalAbsences / lignes.size() * 10000.0) / 100.0;

        return StatistiquesAbsenceReponse.builder()
                .groupeClasseId(groupeClasseId)
                .tenantId(tenantId)
                .periode(periode)
                .debut(debut)
                .fin(fin)
                .totalSeances(totalSeances)
                .totalAbsences(totalAbsences)
                .totalJustifiees(totalJustifiees)
                .totalNonJustifiees(totalNonJustifiees)
                .totalRetards(totalRetards)
                .totalExclusions(totalExclusions)
                .tauxAbsenteisme(tauxAbsenteisme)
                .build();
    }

    private long compter(List<LigneAppel> lignes, StatutPresence statut) {
        return lignes.stream().filter(l -> l.getStatut() == statut).count();
    }
}
