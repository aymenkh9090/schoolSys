package tn.wtm.school.common.logging;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger applicatif central.
 * Objectif: garder le meme format de logs dans tous les modules metier,
 * surtout pour tracer tenantId + action sans dupliquer la mise en forme.
 */
public final class AppLogger {


    private final Logger logger;

    private AppLogger(Class<?> source) {
        this.logger = LoggerFactory.getLogger(source);
    }

    public static AppLogger getLogger(Class<?> source) {
        return new AppLogger(source);
    }

    public void info(String tenantId, String action, String message, Object... args) {
        logger.info(prefix(tenantId, action) + message, args);
    }

    public void debug(String tenantId, String action, String message, Object... args) {
        logger.debug(prefix(tenantId, action) + message, args);
    }

    public void warn(String tenantId, String action, String message, Object... args) {
        logger.warn(prefix(tenantId, action) + message, args);
    }

    public void error(String tenantId, String action, String message, Throwable throwable) {
        logger.error(prefix(tenantId, action) + message, throwable);
    }

    private String prefix(String tenantId, String action) {
        return "[tenantId=" + safe(tenantId) + "] [action=" + safe(action) + "] ";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value.trim();
    }




}
