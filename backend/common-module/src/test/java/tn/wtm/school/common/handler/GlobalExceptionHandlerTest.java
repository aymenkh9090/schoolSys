package tn.wtm.school.common.handler;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ErrorResponse;
import tn.wtm.school.common.exceptions.ObjectValidationException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.common.exceptions.UnauthorizedException;
import tn.wtm.school.security.exception.KeycloakIntegrationException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaque exception métier doit arriver au client avec son statut HTTP et un
 * message qu'un utilisateur peut lire — jamais une trace technique.
 */
class GlobalExceptionHandlerTest {

    enum Jour { LUNDI, MARDI }

    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static void verifier(ResponseEntity<ErrorResponse> reponse, HttpStatus statut, String erreur) {
        assertThat(reponse.getStatusCode()).isEqualTo(statut);
        assertThat(reponse.getBody()).isNotNull();
        assertThat(reponse.getBody().getStatus()).isEqualTo(statut.value());
        assertThat(reponse.getBody().getError()).isEqualTo(erreur);
        assertThat(reponse.getBody().getTimestamp()).isNotNull();
    }

    // ── corps de requête illisible ────────────────────────────────────────────

    @Test
    void corpsIllisible_messageGenerique() {
        var ex = new HttpMessageNotReadableException("JSON cassé", null, new MockHttpInputMessage(new byte[0]));

        var reponse = handler.handleHttpMessageNotReadable(ex);

        verifier(reponse, HttpStatus.BAD_REQUEST, "Validation_Exception");
        assertThat(reponse.getBody().getMessage()).isEqualTo("Corps de la requête invalide");
    }

    @Test
    void valeurDEnumInconnue_nommeLeChampEtLesValeursAcceptees() {
        InvalidFormatException cause = InvalidFormatException.from(null, "inconnu", "DIMANCHE", Jour.class);
        cause.prependPath(new JsonMappingException.Reference(Object.class, "jour"));
        var ex = new HttpMessageNotReadableException("JSON", cause, new MockHttpInputMessage(new byte[0]));

        var reponse = handler.handleHttpMessageNotReadable(ex);

        assertThat(reponse.getBody().getMessage())
                .isEqualTo("jour : valeur invalide \"DIMANCHE\". Valeurs acceptées : LUNDI, MARDI");
    }

    @Test
    void valeurDEnumInconnue_sansChemin_pointDInterrogation() {
        InvalidFormatException cause = InvalidFormatException.from(null, "inconnu", "X", Jour.class);
        var ex = new HttpMessageNotReadableException("JSON", cause, new MockHttpInputMessage(new byte[0]));

        assertThat(handler.handleHttpMessageNotReadable(ex).getBody().getMessage()).startsWith("? : valeur invalide");
    }

    @Test
    void formatInvalideHorsEnum_messageGenerique() {
        InvalidFormatException cause = InvalidFormatException.from(null, "nombre", "abc", Integer.class);
        var ex = new HttpMessageNotReadableException("JSON", cause, new MockHttpInputMessage(new byte[0]));

        assertThat(handler.handleHttpMessageNotReadable(ex).getBody().getMessage())
                .isEqualTo("Corps de la requête invalide");
    }

    // ── paramètres ────────────────────────────────────────────────────────────

    @Test
    void parametreManquant_nommeLeParametre() {
        var reponse = handler.handleMissingParameter(new MissingServletRequestParameterException("classeId", "Long"));

        verifier(reponse, HttpStatus.BAD_REQUEST, "Validation_Exception");
        assertThat(reponse.getBody().getMessage()).isEqualTo("Paramètre requis manquant : classeId");
    }

    @Test
    void parametreDeMauvaisType_nommeLeParametre() {
        var ex = new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);

        var reponse = handler.handleTypeMismatch(ex);

        verifier(reponse, HttpStatus.BAD_REQUEST, "Validation_Exception");
        assertThat(reponse.getBody().getMessage()).isEqualTo("Paramètre invalide : id");
    }

    @Test
    void argumentInvalide_premiereErreurDeChamp() {
        var resultat = new BeanPropertyBindingResult(new Object(), "requete");
        resultat.addError(new FieldError("requete", "email", "format invalide"));

        var reponse = handler.handleMethodArgumentNotValid(new MethodArgumentNotValidException(null, resultat));

        verifier(reponse, HttpStatus.BAD_REQUEST, "Validation_Exception");
        assertThat(reponse.getBody().getMessage()).isEqualTo("email : format invalide");
    }

    @Test
    void argumentInvalide_sansErreurDeChamp_messageParDefaut() {
        var resultat = new BeanPropertyBindingResult(new Object(), "requete");

        var reponse = handler.handleMethodArgumentNotValid(new MethodArgumentNotValidException(null, resultat));

        assertThat(reponse.getBody().getMessage()).isEqualTo("Validation échouée");
    }

    // ── exceptions métier : le message passe tel quel ────────────────────────

    @Test
    void exceptionsMetier_statutEtMessage() {
        verifier(handler.handleRessourcesNotFoundExceptions(new ResourceNotFoundException("absent")),
                HttpStatus.NOT_FOUND, "Resource_NotFound_Exception");
        verifier(handler.handleConflictExceptions(new ConflictException("doublon")),
                HttpStatus.CONFLICT, "Conflict_Exception");
        verifier(handler.handleBadRequestException(new BadRequestException("mauvais")),
                HttpStatus.BAD_REQUEST, "Bad_Request_Exception");
        verifier(handler.handleUnauthorizedExceptions(new UnauthorizedException("non")),
                HttpStatus.UNAUTHORIZED, "Unauthorized_Exception");
        verifier(handler.handleTenantSecurityException(new TenantSecurityException("tenant")),
                HttpStatus.FORBIDDEN, "Tenant_Security_Exception");
        verifier(handler.handleIllegalArgumentException(new IllegalArgumentException("arg")),
                HttpStatus.BAD_REQUEST, "Bad_Request_Exception");
        verifier(handler.handleIllegalStateException(new IllegalStateException("etat")),
                HttpStatus.CONFLICT, "Conflict_Exception");

        var metier = handler.handleBusinessException(new BusinessException("séance déjà clôturée"));
        verifier(metier, HttpStatus.UNPROCESSABLE_ENTITY, "Business_Exception");
        assertThat(metier.getBody().getMessage()).isEqualTo("séance déjà clôturée");
    }

    @Test
    void validationObjet_listeLesViolationsTriees_etJamaisLaClasseDuDto() {
        var ex = new ObjectValidationException(Set.of("Le nom est obligatoire.", "Email invalide."),
                "tn.wtm.school.tenant.dto.CreateTenantRequest");

        var reponse = handler.handleObjectValidationException(ex);

        verifier(reponse, HttpStatus.BAD_REQUEST, "Object_Validation_Exception");
        assertThat(reponse.getBody().getMessage()).isEqualTo("Email invalide. Le nom est obligatoire.");
        assertThat(reponse.getBody().getMessage()).doesNotContain("CreateTenantRequest");
        assertThat(reponse.getBody().getViolations()).hasSize(2);
    }

    @Test
    void validationObjet_sansViolation_messageGenerique() {
        assertThat(handler.handleObjectValidationException(new ObjectValidationException(Set.of(), "src"))
                .getBody().getMessage()).isEqualTo("Certains champs saisis sont invalides.");
        assertThat(handler.handleObjectValidationException(new ObjectValidationException(null, "src"))
                .getBody().getMessage()).isEqualTo("Certains champs saisis sont invalides.");
    }

    // ── erreurs techniques : jamais le détail au client ───────────────────────

    @Test
    void erreurInattendue_neRenvoiePasLeMessageTechnique() {
        var reponse = handler.handleAllExceptions(new RuntimeException("NullPointer dans FooImpl"));

        verifier(reponse, HttpStatus.INTERNAL_SERVER_ERROR, "Internal_Server_Error");
        assertThat(reponse.getBody().getMessage()).doesNotContain("FooImpl");
    }

    @Test
    void keycloak_502_sansLeDetailDeLaReponseKeycloak() {
        var reponse = handler.handleKeycloakIntegrationException(
                new KeycloakIntegrationException("HTTP 409 {\"errorMessage\":\"User exists\"}"));

        verifier(reponse, HttpStatus.BAD_GATEWAY, "Keycloak_Integration_Exception");
        assertThat(reponse.getBody().getMessage()).doesNotContain("409").contains("Aucune modification");
    }

    @Test
    void integriteEnBase_409_expliqueQuoiFaire() {
        var ex = new DataIntegrityViolationException("insert", new RuntimeException("violates foreign key fk_x"));

        var reponse = handler.handleDataIntegrityViolation(ex);

        verifier(reponse, HttpStatus.CONFLICT, "Data_Integrity_Exception");
        assertThat(reponse.getBody().getMessage()).doesNotContain("fk_x").startsWith("Opération impossible");
    }

    @Test
    void verrouOptimiste_409() {
        var reponse = handler.handleOptimisticLockingException(
                new ObjectOptimisticLockingFailureException(Object.class, 42L));

        verifier(reponse, HttpStatus.CONFLICT, "Optimistic_Lock_Exception");
        assertThat(reponse.getBody().getMessage()).contains("modifiée par un autre utilisateur");
    }
}
