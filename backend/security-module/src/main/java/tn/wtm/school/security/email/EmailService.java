package tn.wtm.school.security.email;

public interface EmailService {

    /**
     * Envoie le mail de bienvenue avec le mot de passe temporaire.
     * Best-effort : un échec d'envoi ne doit jamais faire échouer la création du compte.
     */
    void sendWelcomeEmail(WelcomeEmailData data);
}
