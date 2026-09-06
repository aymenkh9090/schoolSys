package tn.wtm.school.planning.solver.validation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Verdict de la validation métier sur un emploi du temps produit.
 *
 * <p>Immuable, et sans dépendance à Timefold : c'est tout l'intérêt de l'étape E.
 * Le solveur ne juge un emploi du temps qu'à travers les contraintes que
 * l'établissement a bien voulu activer ; ce rapport, lui, dit ce qui est vrai de
 * l'emploi du temps, que la contrainte correspondante ait été cochée ou non.
 */
public record ValidationReport(List<ValidationFinding> findings) {

    /** Nombre d'exemples cités par famille de constat dans {@link #resume()}. */
    private static final int EXEMPLES_PAR_CODE = 3;

    public ValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static ValidationReport conforme() {
        return new ValidationReport(List.of());
    }

    /** Aucun constat bloquant — l'emploi du temps peut être remis. */
    public boolean estConforme() {
        return bloquants().isEmpty();
    }

    public List<ValidationFinding> bloquants() {
        return parGravite(ValidationSeverity.BLOQUANT);
    }

    public List<ValidationFinding> avertissements() {
        return parGravite(ValidationSeverity.AVERTISSEMENT);
    }

    private List<ValidationFinding> parGravite(ValidationSeverity gravite) {
        return findings.stream().filter(f -> f.severity() == gravite).toList();
    }

    /**
     * Résumé destiné à être archivé sur le job et relu bien après la génération.
     *
     * <p>Il regroupe par famille et ne cite que quelques exemples : un rapport de
     * huit cents lignes ne serait pas lu, et l'utilisateur a besoin de savoir
     * <em>ce qui</em> ne va pas, pas de l'inventaire complet — que
     * {@code /score-explanation} lui donne s'il le veut.
     *
     * <p>Chaîne vide quand il n'y a rien à dire : l'appelant peut alors laisser
     * la colonne à {@code null} plutôt que d'y archiver un silence.
     */
    public String resume() {
        if (findings.isEmpty()) {
            return "";
        }
        return findings.stream()
                .collect(Collectors.groupingBy(
                        f -> f.severity() + "|" + f.code(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().get(0).severity()))
                .map(ValidationReport::ligneDeFamille)
                .collect(Collectors.joining("\n"));
    }

    private static String ligneDeFamille(Map.Entry<String, List<ValidationFinding>> famille) {
        List<ValidationFinding> constats = famille.getValue();
        ValidationFinding premier = constats.get(0);

        StringBuilder ligne = new StringBuilder()
                .append(premier.severity() == ValidationSeverity.BLOQUANT ? "[bloquant] " : "[avertissement] ")
                .append(premier.code())
                .append(" (").append(constats.size()).append(") : ");

        ligne.append(constats.stream()
                .limit(EXEMPLES_PAR_CODE)
                .map(ValidationFinding::ligne)
                .collect(Collectors.joining(" ; ")));

        int reste = constats.size() - EXEMPLES_PAR_CODE;
        if (reste > 0) {
            ligne.append(" ; … et ").append(reste).append(" autre").append(reste > 1 ? "s" : "");
        }
        return ligne.toString();
    }
}
