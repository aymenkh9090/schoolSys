package tn.wtm.school.api.demo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Patronymes et prénoms tunisiens courants, utilisés pour donner au jeu de
 * données des enseignants et des élèves qui se lisent comme un vrai registre
 * d'établissement.
 *
 * <p><b>Pourquoi un tirage déterministe.</b> Toutes les méthodes indexent la
 * liste plutôt que de tirer au hasard : deux démarrages du seeder produisent
 * exactement le même registre. C'est ce qui permet de reprendre une capture
 * d'écran, une requête SQL ou un emploi du temps d'une session à l'autre sans
 * que les noms aient bougé sous les pieds du lecteur.
 *
 * <p>Les listes sont volontairement de tailles premières entre elles : le
 * couple (patronyme, prénom) ne se répète qu'au bout de
 * {@code PATRONYMES.size() * PRENOMS_*.size()} tirages, très au-delà des
 * quelques centaines de personnes que le jeu de données crée.
 */
final class NomsTunisiens {

    private NomsTunisiens() {
    }

    /** 47 patronymes — nombre premier, voir la note de classe. */
    private static final List<String> PATRONYMES = List.of(
            "Ben Ali", "Trabelsi", "Cherif", "Mansour", "Gharbi", "Mejri", "Ayari",
            "Jlassi", "Kacem", "Bouzid", "Saidi", "Baccar", "Hamdi", "Zouari",
            "Feki", "Ben Salah", "Ayoub", "Dridi", "Chaabane", "Rekik", "Guesmi",
            "Sassi", "Marzouki", "Belhaj", "Nasri", "Khelifi", "Jendoubi", "Souissi",
            "Ferchichi", "Ben Amor", "Bouaziz", "Hammami", "Louati", "Mabrouk",
            "Ncibi", "Ouertani", "Riahi", "Sfar", "Tounsi", "Yahyaoui", "Zaied",
            "Abidi", "Bahri", "Chouchane", "Dhaouadi", "Essid", "Ghariani"
    );

    /** 43 prénoms masculins. */
    private static final List<String> PRENOMS_MASCULINS = List.of(
            "Mohamed", "Ahmed", "Ali", "Youssef", "Omar", "Sami", "Mourad", "Yassine",
            "Khaled", "Hatem", "Tarek", "Walid", "Bilel", "Anis", "Nizar", "Zied",
            "Hedi", "Slim", "Karim", "Aymen", "Wassim", "Marouane", "Hamza", "Firas",
            "Adel", "Ridha", "Fethi", "Lotfi", "Mehdi", "Nabil", "Rami", "Skander",
            "Taieb", "Wael", "Yassin", "Hichem", "Jalel", "Kais", "Maher", "Nader",
            "Oussama", "Raouf", "Seif"
    );

    /** 41 prénoms féminins. */
    private static final List<String> PRENOMS_FEMININS = List.of(
            "Nadia", "Leila", "Sonia", "Rania", "Imen", "Meriem", "Nour", "Rim",
            "Fatma", "Asma", "Ines", "Amira", "Sarra", "Dorra", "Emna", "Hela",
            "Ikram", "Jihene", "Khadija", "Lamia", "Manel", "Nesrine", "Olfa",
            "Rahma", "Salma", "Sana", "Sirine", "Wafa", "Yosra", "Zeineb", "Amel",
            "Basma", "Chaima", "Donia", "Farah", "Ghada", "Hiba", "Kaouther",
            "Maha", "Nawres", "Sabrine"
    );

    /** Patronyme de rang {@code i}, la liste étant parcourue en boucle. */
    static String patronyme(int i) {
        return PATRONYMES.get(Math.floorMod(i, PATRONYMES.size()));
    }

    /**
     * Prénom de rang {@code i}. Les rangs pairs prennent la liste masculine, les
     * impairs la féminine : sur un effectif de classe, la mixité est ainsi
     * garantie sans tirage aléatoire.
     */
    static String prenom(int i) {
        List<String> source = (i % 2 == 0) ? PRENOMS_MASCULINS : PRENOMS_FEMININS;
        return source.get(Math.floorMod(i / 2, source.size()));
    }

    /**
     * Forme un identifiant d'adresse e-mail à partir d'un nom : sans accent, sans
     * espace, en minuscules. « Ben Salah » / « Nour » donne {@code nour.bensalah}.
     */
    static String identifiantMail(String prenom, String nom) {
        return sansAccent(prenom) + "." + sansAccent(nom);
    }

    private static String sansAccent(String texte) {
        String plie = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return plie.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
