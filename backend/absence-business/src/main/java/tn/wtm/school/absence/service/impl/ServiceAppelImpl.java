package tn.wtm.school.absence.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.absence.dto.requete.ModificationStatutRequete;
import tn.wtm.school.absence.dto.requete.OuvertureAppelRequete;
import tn.wtm.school.absence.dto.reponse.AbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.AppelReponse;
import tn.wtm.school.absence.dto.reponse.HistoriqueAppelReponse;
import tn.wtm.school.absence.dto.reponse.LigneAppelReponse;
import tn.wtm.school.absence.dto.reponse.SignalementEleveReponse;
import tn.wtm.school.absence.entity.HistoriqueAppel;
import tn.wtm.school.absence.entity.JustificatifAbsence;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.enums.RaisonVerrouillage;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.absence.mapper.HistoriqueAppelMapper;
import tn.wtm.school.absence.mapper.LigneAppelMapper;
import tn.wtm.school.absence.mapper.SeanceAppelMapper;
import tn.wtm.school.absence.port.PortContexteScolaire;
import tn.wtm.school.absence.port.PortEleveGroupe;
import tn.wtm.school.absence.port.PortSeancePlanning;
import tn.wtm.school.absence.port.PortSeancePlanning.CreneauSeance;
import tn.wtm.school.absence.repository.HistoriqueAppelRepository;
import tn.wtm.school.absence.repository.LigneAppelRepository;
import tn.wtm.school.absence.repository.SeanceAppelRepository;
import tn.wtm.school.absence.service.ServiceAppel;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceAppelImpl extends TenantService implements ServiceAppel {

    private final SeanceAppelRepository seanceAppelRepository;
    private final LigneAppelRepository ligneAppelRepository;
    private final HistoriqueAppelRepository historiqueAppelRepository;
    private final SeanceAppelMapper seanceAppelMapper;
    private final LigneAppelMapper ligneAppelMapper;
    private final HistoriqueAppelMapper historiqueAppelMapper;
    private final PortEleveGroupe portEleveGroupe;
    private final PortSeancePlanning portSeancePlanning;
    private final PortContexteScolaire portContexteScolaire;

    @Override
    @Transactional
    public AppelReponse ouvrirOuRecupererAppel(OuvertureAppelRequete requete) {
        String tenantId = currentTenant();

        // Une séance du planning revient chaque semaine : l'appel existant n'est
        // réutilisé que s'il porte sur le même jour de cours, sinon on en ouvre un
        // nouveau pour la semaine courante.
        LocalDate jour = requete.getDateSeance() != null ? requete.getDateSeance() : LocalDate.now();
        if (jour.isAfter(LocalDate.now())) {
            throw new BadRequestException("Impossible d'ouvrir l'appel d'une séance à venir : " + jour);
        }

        CreneauSeance creneau = portSeancePlanning
                .trouverCreneau(tenantId, requete.getSeancePlanningId())
                .orElse(null);

        if (creneau != null && creneau.jour() != jour.getDayOfWeek()) {
            throw new BadRequestException(
                    "Cette séance a lieu le " + creneau.jour() + " : impossible d'ouvrir son appel pour le " + jour);
        }

        return seanceAppelRepository
                .findByTenantIdAndSeancePlanningIdAndDateSeance(tenantId, requete.getSeancePlanningId(), jour)
                .map(seanceAppelMapper::toResponse)
                .orElseGet(() -> creerNouvelleSeance(tenantId, requete, jour, creneau));
    }

    @Override
    public AppelReponse recupererAppel(Long seanceAppelId) {
        return seanceAppelMapper.toResponse(trouverSeance(seanceAppelId));
    }

    @Override
    public List<AppelReponse> listerSeances(LocalDate date, Long groupeClasseId, Long enseignantId, Boolean estVerrouille) {
        String tenantId = currentTenant();
        LocalDate jour = date != null ? date : LocalDate.now();

        return seanceAppelRepository
                .rechercher(tenantId, jour, groupeClasseId, enseignantId, estVerrouille)
                .stream()
                .map(seanceAppelMapper::toResponse)
                .toList();
    }

    @Override
    public List<AbsenceEleveReponse> listerAbsencesEleve(Long eleveId, LocalDate debut, LocalDate fin, boolean retards) {
        if (eleveId == null) {
            throw new BadRequestException("L'identifiant de l'élève est obligatoire");
        }

        List<StatutPresence> statuts = retards
                ? List.of(StatutPresence.ABSENT, StatutPresence.RETARD, StatutPresence.EXCLU)
                : List.of(StatutPresence.ABSENT);

        // Période ouverte : on borne largement plutôt que de laisser passer un
        // paramètre nul, que PostgreSQL refuserait faute de type.
        LocalDate depuis = debut != null ? debut : LocalDate.of(1900, 1, 1);
        LocalDate jusqua = fin != null ? fin : LocalDate.of(2999, 12, 31);

        return ligneAppelRepository
                .findAbsencesEleve(currentTenant(), eleveId, statuts, depuis, jusqua)
                .stream()
                .map(this::versAbsenceEleve)
                .toList();
    }

    /** Ligne d'appel + sa séance + le dernier justificatif déposé, en une seule vue. */
    private AbsenceEleveReponse versAbsenceEleve(LigneAppel ligne) {
        SeanceAppel seance = ligne.getSeanceAppel();

        // Le dernier dépôt fait foi : une absence refusée puis re-justifiée doit
        // afficher l'état du nouveau justificatif, pas celui du premier.
        JustificatifAbsence dernier = ligne.getJustificatifs().stream()
                .max(Comparator.comparing(JustificatifAbsence::getSoumisAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        return AbsenceEleveReponse.builder()
                .ligneAppelId(ligne.getId())
                .seanceAppelId(seance.getId())
                .eleveId(ligne.getEleveId())
                .dateSeance(seance.getDateSeance())
                .groupeClasseId(seance.getGroupeClasseId())
                .matiereId(seance.getMatiereId())
                .enseignantId(seance.getEnseignantId())
                .anneeAcademique(seance.getAnneeAcademique())
                .ouvertureAt(seance.getOuvertureAt())
                .statut(ligne.getStatut())
                .estJustifie(ligne.getEstJustifie())
                .minutesRetard(ligne.getMinutesRetard())
                .raisonExclusion(ligne.getRaisonExclusion())
                .seanceVerrouillee(seance.getEstVerrouille())
                .justificatifId(dernier != null ? dernier.getId() : null)
                .statutJustificatif(dernier != null ? dernier.getStatut() : null)
                .build();
    }

    /**
     * Fenêtre par défaut du suivi : une semaine.
     *
     * Assez large pour couvrir un week-end et un jour férié, assez courte pour
     * qu'un enseignant ne relise pas le mois écoulé avant d'entrer en classe.
     * Une absence plus ancienne et toujours non justifiée relève du dossier de
     * l'élève, que la vie scolaire traite depuis le web.
     */
    private static final int JOURS_SUIVIS_PAR_DEFAUT = 7;

    @Override
    public List<SignalementEleveReponse> listerSignalementsClasse(Long groupeClasseId, LocalDate debut, LocalDate fin) {
        if (groupeClasseId == null) {
            throw new BadRequestException("L'identifiant de la classe est obligatoire");
        }

        LocalDate jusqua = fin != null ? fin : LocalDate.now();
        LocalDate depuis = debut != null ? debut : jusqua.minusDays(JOURS_SUIVIS_PAR_DEFAUT);
        if (depuis.isAfter(jusqua)) {
            throw new BadRequestException("La date de début est postérieure à la date de fin");
        }

        String tenantId = currentTenant();

        // Le retard est volontairement exclu : un élève arrivé avec dix minutes
        // de retard est en classe, et l'annoncer au professeur suivant comme un
        // manquement en cours noierait les deux cas qui, eux, appellent une
        // réaction — l'absence et l'exclusion.
        List<LigneAppel> lignes = ligneAppelRepository.findSignalementsClasse(
                tenantId, groupeClasseId,
                List.of(StatutPresence.ABSENT, StatutPresence.EXCLU),
                depuis, jusqua);

        if (lignes.isEmpty()) return List.of();

        // Les libellés sont résolus en deux requêtes pour toute la liste, et non
        // une par ligne : la classe entière tient dans un écran de téléphone,
        // son chargement doit tenir dans un aller-retour.
        Set<Long> matieres = lignes.stream()
                .map(l -> l.getSeanceAppel().getMatiereId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> enseignants = lignes.stream()
                .map(l -> l.getSeanceAppel().getEnseignantId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> libellesMatieres = portContexteScolaire.libellesMatieres(tenantId, matieres);
        Map<Long, String> nomsEnseignants = portContexteScolaire.nomsEnseignants(tenantId, enseignants);

        return lignes.stream()
                .map(ligne -> versSignalement(tenantId, ligne, libellesMatieres, nomsEnseignants))
                .toList();
    }

    private SignalementEleveReponse versSignalement(String tenantId,
                                                    LigneAppel ligne,
                                                    Map<Long, String> libellesMatieres,
                                                    Map<Long, String> nomsEnseignants) {
        SeanceAppel seance = ligne.getSeanceAppel();

        // Le dernier dépôt fait foi : une absence refusée puis re-justifiée doit
        // afficher l'état du nouveau justificatif, pas celui du premier.
        JustificatifAbsence dernier = ligne.getJustificatifs().stream()
                .max(Comparator.comparing(JustificatifAbsence::getSoumisAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        LocalTime heureDebut = portSeancePlanning
                .trouverCreneau(tenantId, seance.getSeancePlanningId())
                .map(CreneauSeance::heureDebut)
                .orElse(null);

        return SignalementEleveReponse.builder()
                .eleveId(ligne.getEleveId())
                .ligneAppelId(ligne.getId())
                .seanceAppelId(seance.getId())
                .statut(ligne.getStatut())
                .dateSeance(seance.getDateSeance())
                .heureDebut(heureDebut)
                .matiereId(seance.getMatiereId())
                .matiere(libellesMatieres.get(seance.getMatiereId()))
                .enseignantId(seance.getEnseignantId())
                .enseignant(nomsEnseignants.get(seance.getEnseignantId()))
                .raisonExclusion(ligne.getRaisonExclusion())
                .justificatifId(dernier != null ? dernier.getId() : null)
                .statutJustificatif(dernier != null ? dernier.getStatut() : null)
                .build();
    }

    @Override
    @Transactional
    public LigneAppelReponse modifierStatutEleve(Long ligneAppelId, ModificationStatutRequete requete) {
        if (requete.getStatut() == null) {
            throw new BadRequestException("Le statut est obligatoire");
        }

        String tenantId = currentTenant();
        LigneAppel ligne = ligneAppelRepository.findByTenantIdAndId(tenantId, ligneAppelId)
                .orElseThrow(() -> new ResourceNotFoundException("Ligne d'appel introuvable : " + ligneAppelId));

        if (Boolean.TRUE.equals(ligne.getSeanceAppel().getEstVerrouille())) {
            throw new BusinessException("Modification impossible : la séance est verrouillée");
        }

        validerReglesMetier(requete);

        StatutPresence ancienStatut = ligne.getStatut();

        ligne.setStatut(requete.getStatut());

        if (requete.getStatut() == StatutPresence.EXCLU) {
            ligne.setRaisonExclusion(requete.getRaisonExclusion());
            ligne.setExclusionAt(LocalDateTime.now());
            if (requete.getModifiePar() != null) {
                ligne.setExcluPar(requete.getModifiePar());
            }
        }

        if (requete.getStatut() == StatutPresence.RETARD) {
            ligne.setArriveeAt(requete.getArriveeAt());
            long minutes = ChronoUnit.MINUTES.between(
                    ligne.getSeanceAppel().getOuvertureAt(), requete.getArriveeAt());
            ligne.setMinutesRetard((int) Math.max(0, minutes));
        }

        ligneAppelRepository.save(ligne);

        HistoriqueAppel historique = HistoriqueAppel.builder()
                .ligneAppel(ligne)
                .modifiePar(requete.getModifiePar() != null ? requete.getModifiePar() : 0L)
                .modifieAt(LocalDateTime.now())
                .statutPrecedent(ancienStatut)
                .nouveauStatut(requete.getStatut())
                .raisonModification(requete.getRaisonExclusion())
                .adresseIp(requete.getAdresseIp())
                .build();
        historiqueAppelRepository.save(historique);

        return ligneAppelMapper.toResponse(ligne);
    }

    @Override
    @Transactional
    public void verrouillerSeance(Long seanceAppelId, Long adminId) {
        SeanceAppel seance = trouverSeance(seanceAppelId);

        if (Boolean.TRUE.equals(seance.getEstVerrouille())) {
            throw new BusinessException("La séance est déjà verrouillée");
        }

        seance.setEstVerrouille(true);
        seance.setVerrouillageAt(LocalDateTime.now());
        seance.setVerrouillagePar(adminId);
        seance.setRaisonVerrouillage(RaisonVerrouillage.MANUEL_ADMIN);
        seanceAppelRepository.save(seance);
    }

    @Override
    public List<HistoriqueAppelReponse> recupererHistorique(Long seanceAppelId) {
        String tenantId = currentTenant();
        SeanceAppel seance = trouverSeance(seanceAppelId);
        return seance.getLignesAppel().stream()
                .flatMap(l -> historiqueAppelRepository
                        .findByTenantIdAndLigneAppel_IdOrderByModifieAtAsc(tenantId, l.getId()).stream())
                .map(historiqueAppelMapper::toResponse)
                .toList();
    }

    // ── Privé ────────────────────────────────────────────────────────────────────

    private AppelReponse creerNouvelleSeance(String tenantId, OuvertureAppelRequete requete,
                                             LocalDate jour, CreneauSeance creneau) {
        SeanceAppel seance = SeanceAppel.builder()
                .seancePlanningId(requete.getSeancePlanningId())
                .enseignantId(requete.getEnseignantId())
                .groupeClasseId(requete.getGroupeClasseId())
                .matiereId(requete.getMatiereId())
                .anneeAcademique(requete.getAnneeAcademique())
                .dateSeance(jour)
                .ouvertureAt(LocalDateTime.now())
                // Fin réelle du créneau : c'est elle qui déclenche le verrouillage
                // automatique. Séance hors emploi du temps (ouverture manuelle) :
                // pas d'heure de fin connue, la feuille reste à clôturer à la main.
                .fermetureAt(creneau != null ? jour.atTime(creneau.heureFin()) : null)
                .estVerrouille(false)
                .build();
        seance = seanceAppelRepository.save(seance);

        List<Long> eleveIds = portEleveGroupe.trouverIdsElevesParGroupe(tenantId, requete.getGroupeClasseId());
        final SeanceAppel seanceSaved = seance;
        List<LigneAppel> lignes = eleveIds.stream()
                .map(eleveId -> LigneAppel.builder()
                        .seanceAppel(seanceSaved)
                        .eleveId(eleveId)
                        .statut(StatutPresence.PRESENT)
                        .estJustifie(false)
                        .build())
                .toList();
        List<LigneAppel> lignesSauvegardees = ligneAppelRepository.saveAll(lignes);
        seanceSaved.getLignesAppel().addAll(lignesSauvegardees);

        return seanceAppelMapper.toResponse(seanceSaved);
    }

    private SeanceAppel trouverSeance(Long seanceAppelId) {
        if (seanceAppelId == null) {
            throw new BadRequestException("L'identifiant de la séance est obligatoire");
        }
        String tenantId = currentTenant();
        return seanceAppelRepository.findWithLignes(tenantId, seanceAppelId)
                .orElseThrow(() -> new ResourceNotFoundException("Séance d'appel introuvable : " + seanceAppelId));
    }

    private void validerReglesMetier(ModificationStatutRequete requete) {
        if (requete.getStatut() == StatutPresence.EXCLU
                && (requete.getRaisonExclusion() == null || requete.getRaisonExclusion().isBlank())) {
            throw new BadRequestException("La raison d'exclusion est obligatoire pour le statut EXCLU");
        }
        if (requete.getStatut() == StatutPresence.RETARD && requete.getArriveeAt() == null) {
            throw new BadRequestException("L'heure d'arrivée est obligatoire pour le statut RETARD");
        }
    }
}
