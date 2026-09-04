package tn.wtm.school.security.email;

/**
 * Données nécessaires à l'email de bienvenue, indépendamment du flow appelant
 * (création de tenant par le super admin, ou création d'un compte école).
 */
public record WelcomeEmailData(
        String toEmail,
        String fullName,
        String username,
        String tempPassword,
        String loginUrl,
        String tenantName
) {}
