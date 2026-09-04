package tn.wtm.school.common.service;

import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.TenantSecurityException;

public abstract class TenantService {

    protected final String currentTenant() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantSecurityException("TenantId obligatoire pour acceder aux donnees metier.");
        }
        return tenantId.trim();
    }
}
