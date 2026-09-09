package tn.wtm.school.pointage.mapper;

import org.junit.jupiter.api.Test;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.entity.SuiviHeuresEnseignant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'un relevé d'heures hebdomadaire rend au client.
 *
 * <p>Deux des champs recopiés ici ne sont pas saisis : {@code heuresManquees}
 * et {@code tauxPresence} sont calculés par le service au moment de
 * l'enregistrement, puis stockés. Le mapper doit donc les rendre — c'est le
 * seul chemin par lequel l'administration les lit, et un oubli les afficherait
 * à zéro sans que rien ne signale l'écart.</p>
 */
class SuiviHeuresMapperTest {

    private final SuiviHeuresMapper mapper = new SuiviHeuresMapperImpl();

    @Test
    void recopieLaSemaineEtSesHeures() {
        SuiviHeuresEnseignantReponse reponse = mapper.toResponse(entite());

        assertThat(reponse.getId()).isEqualTo(12L);
        assertThat(reponse.getEnseignantId()).isEqualTo(2980L);
        assertThat(reponse.getNumeroSemaine()).isEqualTo(37);
        assertThat(reponse.getAnneeAcademique()).isEqualTo("2026-2027");
        assertThat(reponse.getHeuresPrevues()).isEqualTo(20.0);
        assertThat(reponse.getHeuresRealisees()).isEqualTo(15.0);
        assertThat(reponse.getNotes()).isEqualTo("Deux heures rendues en fin de semaine");
    }

    /** Les deux chiffres dérivés voyagent aussi : ce sont eux qu'on lit. */
    @Test
    void rendLesDeuxChiffresCalculesParLeService() {
        SuiviHeuresEnseignantReponse reponse = mapper.toResponse(entite());

        assertThat(reponse.getHeuresManquees()).isEqualTo(5.0);
        assertThat(reponse.getTauxPresence()).isEqualTo(75.0);
    }

    @Test
    void rendUneReponseNulleQuandLentiteEstNulle() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    private SuiviHeuresEnseignant entite() {
        SuiviHeuresEnseignant suivi = SuiviHeuresEnseignant.builder()
                .id(12L)
                .enseignantId(2980L)
                .numeroSemaine(37)
                .anneeAcademique("2026-2027")
                .heuresPrevues(20.0)
                .heuresRealisees(15.0)
                .heuresManquees(5.0)
                .tauxPresence(75.0)
                .notes("Deux heures rendues en fin de semaine")
                .build();
        suivi.setTenantId("28");
        return suivi;
    }
}
