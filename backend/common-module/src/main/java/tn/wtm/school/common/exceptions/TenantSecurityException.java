package tn.wtm.school.common.exceptions;

public class TenantSecurityException extends RuntimeException {
    public TenantSecurityException(String message) {
        super(message);
    }
}
