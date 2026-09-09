package tn.wtm.school.pointage.mapper;

import org.junit.jupiter.api.Test;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.requete.PointageRequete;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que le mapper de pointage recopie — et surtout ce qu'il refuse de recopier.
 *
 * <p>L'implémentation est engendrée par MapStruct, mais le contrat, lui, est
 * écrit à la main : chaque {@code @Mapping(ignore = true)} de l'interface est
 * une décision, et ce sont ces décisions que ces tests fixent. Elles ne se
 * voient nulle part à l'exécution, et une ligne retirée par mégarde de
 * l'interface passerait la compilation sans un mot.</p>
 *
 * <p>L'implémentation engendrée est instanciée directement : elle n'a besoin
 * d'aucun contexte Spring, {@code componentModel = "spring"} ne posant qu'une
 * annotation {@code @Component} sur une classe à constructeur vide.</p>
 */
class PresencePersonnelMapperTest {

    private final PresencePersonnelMapper mapper = new PresencePersonnelMapperImpl();

    // ── Requête → entité ─────────────────────────────────────────────────────────

    @Test
    void recopieLesChampsDeSaisieDeLaRequete() {
        PresencePersonnel entite = mapper.toEntity(requete());

        assertThat(entite.getMembrePersonnelId()).isEqualTo(2980L);
        assertThat(entite.getTypePersonnel()).isEqualTo(TypePersonnel.ENSEIGNANT);
        assertThat(entite.getDatePointage()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(entite.getPeriode()).isEqualTo(Periode.MATIN);
        assertThat(entite.getStatut()).isEqualTo(StatutPresencePersonnel.EN_RETARD);
        assertThat(entite.getHeureArrivee()).isEqualTo(LocalTime.of(8, 12));
        assertThat(entite.getHeureDepart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(entite.getMinutesRetard()).isEqualTo(12);
        assertThat(entite.getNote()).isEqualTo("Bus en retard");
    }

    /**
     * <b>La règle qui compte.</b> {@code saisiPar} et {@code saisiA} ne viennent
     * jamais de la requête : le service les remplit depuis le compte connecté et
     * l'horloge. Si le mapper les recopiait, un client pourrait attribuer sa
     * propre saisie à n'importe quel collègue, et l'antidater — c'est-à-dire
     * effacer la seule trace qui rend un pointage contestable.
     */
    @Test
    void neRecopieNiLauteurNiLhorodatageDeLaSaisie() {
        PresencePersonnel entite = mapper.toEntity(requete());

        assertThat(entite.getSaisiPar()).isNull();
        assertThat(entite.getSaisiA()).isNull();
    }

    /**
     * L'identifiant et l'établissement ne viennent pas davantage de la requête :
     * le premier est engendré par la base, le second par le contexte de la
     * requête HTTP. Les recopier laisserait écrire dans l'établissement d'autrui.
     */
    @Test
    void neRecopieNiLidentifiantNiLetablissement() {
        PresencePersonnel entite = mapper.toEntity(requete());

        assertThat(entite.getId()).isNull();
        assertThat(entite.getTenantId()).isNull();
    }

    /** Les colonnes d'audit sont posées par les écouteurs JPA, pas par l'appelant. */
    @Test
    void neRecopieAucuneColonneDaudit() {
        PresencePersonnel entite = mapper.toEntity(requete());

        assertThat(entite.createdAt).isNull();
        assertThat(entite.updatedAt).isNull();
        assertThat(entite.createdBy).isNull();
        assertThat(entite.updatedBy).isNull();
        assertThat(entite.version).isNull();
    }

    @Test
    void rendUneEntiteNulleQuandLaRequeteEstNulle() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    // ── Entité → réponse ─────────────────────────────────────────────────────────

    @Test
    void recopieLeContenuDuPointageDansLaReponse() {
        PresencePersonnelReponse reponse = mapper.toResponse(entite());

        assertThat(reponse.getId()).isEqualTo(7L);
        assertThat(reponse.getMembrePersonnelId()).isEqualTo(2980L);
        assertThat(reponse.getTypePersonnel()).isEqualTo(TypePersonnel.ENSEIGNANT);
        assertThat(reponse.getDatePointage()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(reponse.getPeriode()).isEqualTo(Periode.MATIN);
        assertThat(reponse.getStatut()).isEqualTo(StatutPresencePersonnel.EN_RETARD);
        assertThat(reponse.getMinutesRetard()).isEqualTo(12);
        assertThat(reponse.getSaisiPar()).isEqualTo("aymen.bouraoui");
        assertThat(reponse.getSaisiA()).isNotNull();
    }

    /**
     * Le justificatif et le nom du membre restent vides ici : ils ne sont pas
     * dans la table. C'est le service qui les rapporte, l'un depuis le dépôt de
     * justificatifs, l'autre depuis le module organisation via un port — le nom
     * ne se déduisant même pas du seul identifiant, dont la signification dépend
     * du type de personnel.
     */
    @Test
    void laisseAuServiceLeJustificatifEtLeNomDuMembre() {
        PresencePersonnelReponse reponse = mapper.toResponse(entite());

        assertThat(reponse.getJustificatif()).isNull();
        assertThat(reponse.getNomMembre()).isNull();
    }

    @Test
    void rendUneReponseNulleQuandLentiteEstNulle() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    // ── Fabriques ────────────────────────────────────────────────────────────────

    private PointageRequete requete() {
        return PointageRequete.builder()
                .membrePersonnelId(2980L)
                .typePersonnel(TypePersonnel.ENSEIGNANT)
                .datePointage(LocalDate.of(2026, 9, 8))
                .periode(Periode.MATIN)
                .statut(StatutPresencePersonnel.EN_RETARD)
                .heureArrivee(LocalTime.of(8, 12))
                .heureDepart(LocalTime.of(12, 0))
                .minutesRetard(12)
                .note("Bus en retard")
                .build();
    }

    private PresencePersonnel entite() {
        PresencePersonnel presence = PresencePersonnel.builder()
                .id(7L)
                .membrePersonnelId(2980L)
                .typePersonnel(TypePersonnel.ENSEIGNANT)
                .datePointage(LocalDate.of(2026, 9, 8))
                .periode(Periode.MATIN)
                .statut(StatutPresencePersonnel.EN_RETARD)
                .heureArrivee(LocalTime.of(8, 12))
                .minutesRetard(12)
                .note("Bus en retard")
                .saisiPar("aymen.bouraoui")
                .saisiA(LocalDateTime.of(2026, 9, 8, 8, 30))
                .build();
        presence.setTenantId("28");
        return presence;
    }
}
