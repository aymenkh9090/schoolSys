package tn.wtm.school.common.exceptions;

import lombok.Getter;

import java.util.Set;

@Getter
public class ObjectValidationException extends RuntimeException {

    private final Set<String> violations;
    private final String violationSource;


    public ObjectValidationException(Set<String> violations, String violationSource) {
        super("Object Violation failed");
        this.violations = violations;
        this.violationSource = violationSource;
    }






}
