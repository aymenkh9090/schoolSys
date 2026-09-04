package tn.wtm.school.planning.constraints.enums;

/**
 * Origine d'une contrainte personnalisée.
 *
 * <p>Tracer l'origine n'est pas cosmétique : c'est ce qui permet, plus tard, de
 * répondre à « qui a mis cette règle ? » et de distinguer une règle saisie par le
 * directeur d'une règle proposée par l'assistant puis confirmée. Dans les deux
 * cas la règle a été validée par un humain — l'IA n'écrit jamais directement en
 * base — mais la responsabilité n'est pas la même.</p>
 */
public enum ConstraintSource {

    /** Saisie manuellement via le formulaire de l'interface. */
    MANUAL,

    /** Traduite depuis une phrase en langage naturel, puis confirmée par l'utilisateur. */
    AI_TRANSLATED,

    /** Proposée spontanément par l'assistant après analyse, puis confirmée. */
    AI_SUGGESTED
}
