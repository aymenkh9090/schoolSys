package tn.wtm.school.absence.support;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.mockito.ArgumentMatchers;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Exécute une {@link Specification} sur des doubles de l'API Criteria et rend
 * les prédicats qu'elle a réellement assemblés.
 *
 * <p>Ce détour sert à vérifier une propriété que les trois services à filtres
 * optionnels documentent chacun dans un commentaire : <b>un critère absent ne
 * doit produire aucun prédicat</b>. C'est la raison d'être de ces
 * Specifications — le JPQL à {@code (:param IS NULL OR ...)} qu'elles
 * remplacent envoyait un paramètre nul, que PostgreSQL rejette. Compter les
 * prédicats est la seule façon de vérifier la règle sans base de données.</p>
 */
public final class CapturePredicats {

    private CapturePredicats() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Predicate> assembles(Specification specification) {
        // Le Root répond en cascade : les services naviguent en
        // racine.get("seanceAppel").get("ouvertureAt"), qu'un mock plat casserait
        // sur un NullPointerException dès le second get.
        Root racine = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery requete = mock(CriteriaQuery.class);
        CriteriaBuilder constructeur = mock(CriteriaBuilder.class);
        Predicate predicat = mock(Predicate.class);
        List<Predicate> assembles = new ArrayList<>();

        // Le second argument est explicitement typé Object : sans cela,
        // l'inférence choisit la surcharge equal(Expression, Expression) et le
        // stub porte sur une autre méthode que celle qu'appellent les services.
        lenient().when(constructeur.equal(any(Expression.class), ArgumentMatchers.<Object>any())).thenReturn(predicat);
        lenient().when(constructeur.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicat);
        lenient().when(constructeur.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicat);

        // Les varargs sont relevés par la réponse elle-même, et non par un
        // ArgumentCaptor : en position varargs, celui-ci n'apparie que les
        // appels à un seul argument.
        lenient().when(constructeur.and(any(Predicate[].class))).thenAnswer(invocation -> {
            Arrays.stream(invocation.getArguments()).map(Predicate.class::cast).forEach(assembles::add);
            return predicat;
        });

        specification.toPredicate(racine, requete, constructeur);
        return assembles;
    }
}
