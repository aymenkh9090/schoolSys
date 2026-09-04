package tn.wtm.school.security.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Génère des mots de passe temporaires forts.
 * Utilise SecureRandom (cryptographiquement sûr) et l'algorithme Fisher-Yates.
 */
@Component
public class PasswordGeneratorUtil {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS    = "0123456789";
    private static final String SPECIALS  = "@#$!%*?&";
    private static final String ALL_CHARS = LOWERCASE + UPPERCASE + DIGITS + SPECIALS;
    private static final int PASSWORD_LENGTH = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        char[] password = new char[PASSWORD_LENGTH];

        // Garantir au moins 1 char de chaque catégorie
        password[0] = randomChar(LOWERCASE);
        password[1] = randomChar(UPPERCASE);
        password[2] = randomChar(DIGITS);
        password[3] = randomChar(SPECIALS);

        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password[i] = randomChar(ALL_CHARS);
        }

        // Fisher-Yates Shuffle
        for (int i = PASSWORD_LENGTH - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char temp   = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }

    private char randomChar(String charset) {
        return charset.charAt(SECURE_RANDOM.nextInt(charset.length()));
    }
}
