package tn.wtm.school.absence.dto.reponse;

import lombok.*;

import java.time.LocalDate;

/**
 * Une séance du cahier, enrichie de ses libellés, prête à être indexée.
 *
 * <p>C'est le format que consomme l'assistant IA pour construire son index
 * sémantique. Il porte à la fois le texte à indexer (sujet, chapitre, activités,
 * remarques, travail demandé) et de quoi CITER la source dans la réponse :
 * la date, la classe, la matière, l'enseignant. Une réponse d'assistant qui ne
 * peut pas être remontée à la séance qui l'a produite n'est pas vérifiable, et
 * ne vaut donc pas mieux qu'une invention.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CahierCorpusReponse {

    private Long id;
    private Long seanceAppelId;

    private Long enseignantId;
    private String enseignantNom;

    private Long groupeClasseId;
    private String classeCode;

    private Long matiereId;
    private String matiereLibelle;

    private LocalDate dateSeance;
    private String anneeAcademique;

    private String sujet;
    private String chapitre;
    private String activites;
    private String remarques;
    private String travailDemande;
    private LocalDate dateEcheance;
}
