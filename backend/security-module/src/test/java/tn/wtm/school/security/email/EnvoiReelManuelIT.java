package tn.wtm.school.security.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

/**
 * Vérification manuelle de bout en bout : envoie un VRAI mail via Gmail.
 * Lancé à la demande, jamais en CI :
 *   set -a; . ../.env; set +a
 *   mvn -pl security-module test -Dtest=EnvoiReelManuelIT -DfailIfNoTests=false
 */
class EnvoiReelManuelIT {

    @Test
    void envoieUnMailDeBienvenueReel() {
        String user = System.getenv("SMTP_USERNAME");
        String pass = System.getenv("SMTP_PASSWORD");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                user != null && !user.isBlank() && pass != null && !pass.isBlank(),
                "SMTP_USERNAME/SMTP_PASSWORD absents : test ignoré");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(user);
        sender.setPassword(pass);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        EmailServiceImpl service = new EmailServiceImpl(sender);
        ReflectionTestUtils.setField(service, "fromAddress", user);

        service.sendWelcomeEmail(new WelcomeEmailData(
                user,
                "Aymen Bouraoui",
                "aymen.bouraoui",
                "Tmp-Demo-4821",
                "http://localhost:3000",
                "Lycée Ibn Khaldoun"
        ));
    }
}
