package tn.wtm.school.common.context;

import tn.wtm.school.common.exceptions.TenantSecurityException;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT   = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_ID  = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();

    private TenantContext() {}

    // ── Tenant ────────────────────────────────────────────────────────────

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static String getRequiredTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantSecurityException("TenantId obligatoire pour acceder aux donnees metier.");
        }
        return tenantId.trim();
    }

    public static boolean hasTenant() {
        return getTenantId() != null && !getTenantId().isBlank();
    }

    // ── Utilisateur (JWT Phase 2) ─────────────────────────────────────────

    public static void setUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static String getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setUsername(String username) {
        CURRENT_USERNAME.set(username);
    }

    public static String getUsername() {
        return CURRENT_USERNAME.get();
    }

    // ── Clear ─────────────────────────────────────────────────────────────

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER_ID.remove();
        CURRENT_USERNAME.remove();
    }
}
