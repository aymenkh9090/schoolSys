package tn.wtm.school.security.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    /** Nom commercial du produit. Centralisé ici : il apparaît dans l'objet,
     *  dans le corps du message et comme nom d'expéditeur affiché. */
    private static final String BRAND = "SchoolSys";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void sendWelcomeEmail(WelcomeEmailData data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            if (StringUtils.hasText(fromAddress)) {
                // Le destinataire voit "SchoolSys", pas une adresse Gmail nue.
                // L'adresse elle-même reste celle du compte authentifié :
                // Gmail réécrit toute autre valeur.
                helper.setFrom(fromAddress, BRAND);
            }
            helper.setTo(data.toEmail());
            helper.setSubject("Votre compte " + BRAND + " a été créé");
            helper.setText(buildHtml(data), true);
            mailSender.send(message);
            log.info("[Email] Mail de bienvenue envoyé à {}", data.toEmail());
        } catch (Exception e) {
            // Ne jamais logger tempPassword. Ne jamais propager : la création du compte
            // a déjà réussi côté Keycloak/BDD, un échec d'email ne doit pas la remettre en cause.
            log.warn("[Email] Échec envoi mail à {} : {}", data.toEmail(), e.getMessage());
        }
    }

    private String buildHtml(WelcomeEmailData data) {
        String greetingName = escape(StringUtils.hasText(data.fullName()) ? data.fullName() : data.username());
        String schoolLine = StringUtils.hasText(data.tenantName())
                ? "<p style=\"margin:0 0 16px;color:#4b5468;\">Établissement : <b>" + escape(data.tenantName()) + "</b></p>"
                : "";

        return """
                <!doctype html>
                <html>
                  <body style="margin:0;padding:24px;background:#f4f6fb;font-family:Arial,Helvetica,sans-serif;color:#101726;">
                    <table role="presentation" width="100%%" style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #ccd3e0;border-radius:6px;">
                      <tr>
                        <td style="padding:32px;">
                          <p style="margin:0 0 16px;font-size:18px;font-weight:bold;">Bienvenue sur %s, %s</p>
                          %s
                          <p style="margin:0 0 16px;color:#4b5468;">Un compte vient d'être créé pour vous. Voici vos identifiants de connexion :</p>
                          <table role="presentation" style="width:100%%;background:#f4f6fb;border:1px solid #ccd3e0;border-radius:4px;margin:0 0 20px;">
                            <tr>
                              <td style="padding:14px 18px;">
                                <p style="margin:0 0 6px;font-size:13px;color:#7c86a0;">Identifiant</p>
                                <p style="margin:0 0 14px;font-family:monospace;font-size:15px;">%s</p>
                                <p style="margin:0 0 6px;font-size:13px;color:#7c86a0;">Mot de passe temporaire</p>
                                <p style="margin:0;font-family:monospace;font-size:15px;font-weight:bold;">%s</p>
                              </td>
                            </tr>
                          </table>
                          <p style="margin:0 0 24px;color:#4b5468;">Ce mot de passe est temporaire : à votre première connexion, il vous sera demandé d'en choisir un nouveau.</p>
                          <a href="%s" style="display:inline-block;background:#0d6e6e;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:4px;font-weight:bold;">Se connecter</a>
                          <p style="margin:24px 0 0;font-size:12px;color:#7c86a0;">Si vous n'êtes pas à l'origine de cette demande, contactez l'administrateur de votre établissement.</p>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(BRAND, greetingName, schoolLine, escape(data.username()), escape(data.tempPassword()), data.loginUrl());
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
