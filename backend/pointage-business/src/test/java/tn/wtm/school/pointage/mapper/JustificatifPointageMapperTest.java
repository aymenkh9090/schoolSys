package tn.wtm.school.pointage.mapper;

import org.junit.jupiter.api.Test;
import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.entity.JustificatifPointage;
import tn.wtm.school.pointage.enums.StatutJustificatifPointage;
import tn.wtm.school.pointage.enums.TypeJustificatifPointage;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'un justificatif de pointage rend au client.
 *
 * <p>La réponse est plus large que l'entité : elle porte quatre champs de
 * contexte — nom du membre, type de personnel, jour et créneau — qui
 * appartiennent au <b>pointage</b> justifié, pas au justificatif. Le mapper les
 * laisse donc vides, et le service les rapporte ensuite. Ces tests fixent ce
 * partage : le jour où quelqu'un croira que le mapper suffit, la file d'attente
 * de l'administration n'afficherait que des numéros.</p>
 */
class JustificatifPointageMapperTest {

    private final JustificatifPointageMapper mapper = new JustificatifPointageMapperImpl();

    @Test
    void recopieLeJustificatifEtSaDecision() {
        JustificatifPointageReponse reponse = mapper.toResponse(entite());

        assertThat(reponse.getId()).isEqualTo(44L);
        assertThat(reponse.getMembrePersonnelId()).isEqualTo(2980L);
        assertThat(reponse.getPresencePersonnelId()).isEqualTo(7L);
        assertThat(reponse.getTypeDocument()).isEqualTo(TypeJustificatifPointage.CERTIFICAT_MEDICAL);
        assertThat(reponse.getDescription()).isEqualTo("Arrêt de travail de 2 jours");
        assertThat(reponse.getCheminDocument()).isEqualTo("/documents/cm-2026-118.pdf");
        assertThat(reponse.getStatut()).isEqualTo(StatutJustificatifPointage.APPROUVE);
        assertThat(reponse.getCommentaireAdmin()).isEqualTo("Certificat conforme");
        assertThat(reponse.getTraitePar()).isEqualTo("directrice");
        assertThat(reponse.getSoumisA()).isNotNull();
        assertThat(reponse.getTraiteA()).isNotNull();
    }

    /** Le contexte du pointage justifié n'est pas dans la table du justificatif. */
    @Test
    void laisseAuServiceLeContexteDuPointageJustifie() {
        JustificatifPointageReponse reponse = mapper.toResponse(entite());

        assertThat(reponse.getNomMembre()).isNull();
        assertThat(reponse.getTypePersonnel()).isNull();
        assertThat(reponse.getDatePointage()).isNull();
        assertThat(reponse.getPeriode()).isNull();
    }

    /**
     * Un justificatif à peine construit porte déjà {@code EN_ATTENTE} : la
     * valeur par défaut de l'entité traverse le mapper, elle n'est pas perdue.
     */
    @Test
    void conserveLeStatutParDefautDunJustificatifNonTraite() {
        JustificatifPointageReponse reponse = mapper.toResponse(new JustificatifPointage());

        assertThat(reponse.getStatut()).isEqualTo(StatutJustificatifPointage.EN_ATTENTE);
        assertThat(reponse.getTraiteA()).isNull();
    }

    @Test
    void rendUneReponseNulleQuandLentiteEstNulle() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    private JustificatifPointage entite() {
        JustificatifPointage justificatif = JustificatifPointage.builder()
                .id(44L)
                .membrePersonnelId(2980L)
                .presencePersonnelId(7L)
                .typeDocument(TypeJustificatifPointage.CERTIFICAT_MEDICAL)
                .description("Arrêt de travail de 2 jours")
                .cheminDocument("/documents/cm-2026-118.pdf")
                .commentaireAdmin("Certificat conforme")
                .statut(StatutJustificatifPointage.APPROUVE)
                .soumisA(LocalDateTime.of(2026, 9, 8, 9, 0))
                .traitePar("directrice")
                .traiteA(LocalDateTime.of(2026, 9, 9, 10, 30))
                .build();
        justificatif.setTenantId("28");
        return justificatif;
    }
}
