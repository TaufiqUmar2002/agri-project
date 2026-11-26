package com.umar.logging;

import com.umar.enums.logging.LogType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Centralized logging facade supporting dynamic per-module & per-log-type toggles
 * via BOTH property files (`agreeshield.logging.*`) and DB configuration (`logging_module_config`).
 */
@Slf4j
@Service
public class CentralLoggingService {

    public record LogEntry(Instant timestamp, String moduleName, LogType type, String component, String message) {}

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final LoggingProperties properties;
    private final Deque<LogEntry> recentEntries = new ConcurrentLinkedDeque<>();

    @Value("${spring.application.name:default-module}")
    private String springAppName;

    private volatile boolean schemaInitialized = false;

    public CentralLoggingService(ObjectProvider<JdbcTemplate> jdbcProvider, LoggingProperties properties) {
        this.jdbcProvider = jdbcProvider;
        this.properties = properties;
    }

    public String currentModuleName() {
        return (properties.getModuleName() != null && !properties.getModuleName().isBlank())
                ? properties.getModuleName() : springAppName;
    }

    @PostConstruct
    public void ensureSchema() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null || schemaInitialized) {
            return;
        }
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS logging_module_config (
                        module_name VARCHAR(64) PRIMARY KEY,
                        logging_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        info_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        debug_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        warn_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        exception_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        request_response_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        security_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        external_call_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        updated_by VARCHAR(64),
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
            seedModuleIfAbsent(jdbc, currentModuleName());
            schemaInitialized = true;
        } catch (Exception ex) {
            log.debug("logging_module_config auto-init skipped: {}", ex.getMessage());
        }
    }

    private void seedModuleIfAbsent(JdbcTemplate jdbc, String moduleName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM logging_module_config WHERE module_name = ?",
                Integer.class, moduleName);
        if (count != null && count == 0) {
            jdbc.update("""
                    INSERT INTO logging_module_config
                    (module_name, logging_enabled, info_enabled, debug_enabled, warn_enabled,
                     exception_enabled, request_response_enabled, security_enabled, external_call_enabled,
                     updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'property-default', ?)
                    """,
                    moduleName,
                    properties.isEnabled(),
                    properties.isInfoEnabled(),
                    properties.isDebugEnabled(),
                    properties.isWarnEnabled(),
                    properties.isExceptionEnabled(),
                    properties.isRequestResponseEnabled(),
                    properties.isSecurityEnabled(),
                    properties.isExternalCallEnabled(),
                    Timestamp.from(Instant.now()));
        }
    }

    public boolean isTypeEnabled(LogType type) {
        return isTypeEnabled(currentModuleName(), type);
    }

    public boolean isTypeEnabled(String moduleName, LogType type) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (properties.isPreferDb()) {
            JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
            if (jdbc != null) {
                ensureSchema();
                try {
                    String col = columnFor(type);
                    List<Map<String, Object>> rows = jdbc.queryForList(
                            "SELECT logging_enabled, " + col + " FROM logging_module_config WHERE module_name = ?",
                            moduleName);
                    if (!rows.isEmpty()) {
                        Map<String, Object> row = rows.getFirst();
                        boolean master = Boolean.TRUE.equals(readBool(row, "logging_enabled"));
                        boolean specific = Boolean.TRUE.equals(readBool(row, col));
                        return master && specific;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return propertyDefaultFor(type);
    }

    private Boolean readBool(Map<String, Object> row, String col) {
        Object v = row.get(col);
        if (v == null) v = row.get(col.toUpperCase(Locale.ROOT));
        return v instanceof Boolean b ? b : (v != null && Boolean.parseBoolean(String.valueOf(v)));
    }

    private String columnFor(LogType type) {
        return switch (type) {
            case INFO -> "info_enabled";
            case DEBUG -> "debug_enabled";
            case WARN -> "warn_enabled";
            case EXCEPTION -> "exception_enabled";
            case REQUEST_RESPONSE -> "request_response_enabled";
            case SECURITY -> "security_enabled";
            case EXTERNAL_CALL -> "external_call_enabled";
        };
    }

    private boolean propertyDefaultFor(LogType type) {
        return switch (type) {
            case INFO -> properties.isInfoEnabled();
            case DEBUG -> properties.isDebugEnabled();
            case WARN -> properties.isWarnEnabled();
            case EXCEPTION -> properties.isExceptionEnabled();
            case REQUEST_RESPONSE -> properties.isRequestResponseEnabled();
            case SECURITY -> properties.isSecurityEnabled();
            case EXTERNAL_CALL -> properties.isExternalCallEnabled();
        };
    }

    public Map<String, Object> updateModuleConfig(String moduleName, Map<String, Object> patch) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("JdbcTemplate unavailable for logging DB configuration");
        }
        ensureSchema();
        seedModuleIfAbsent(jdbc, moduleName);
        Map<String, Object> current = getModuleConfig(moduleName);
        boolean loggingEnabled = boolOrDefault(patch, "loggingEnabled", readBool(current, "logging_enabled"));
        boolean infoEnabled = boolOrDefault(patch, "infoEnabled", readBool(current, "info_enabled"));
        boolean debugEnabled = boolOrDefault(patch, "debugEnabled", readBool(current, "debug_enabled"));
        boolean warnEnabled = boolOrDefault(patch, "warnEnabled", readBool(current, "warn_enabled"));
        boolean exceptionEnabled = boolOrDefault(patch, "exceptionEnabled", readBool(current, "exception_enabled"));
        boolean reqResEnabled = boolOrDefault(patch, "requestResponseEnabled", readBool(current, "request_response_enabled"));
        boolean securityEnabled = boolOrDefault(patch, "securityEnabled", readBool(current, "security_enabled"));
        boolean externalEnabled = boolOrDefault(patch, "externalCallEnabled", readBool(current, "external_call_enabled"));
        String updatedBy = patch.get("updatedBy") != null ? String.valueOf(patch.get("updatedBy")) : "admin";

        jdbc.update("""
                UPDATE logging_module_config
                SET logging_enabled = ?, info_enabled = ?, debug_enabled = ?, warn_enabled = ?,
                    exception_enabled = ?, request_response_enabled = ?, security_enabled = ?,
                    external_call_enabled = ?, updated_by = ?, updated_at = ?
                WHERE module_name = ?
                """,
                loggingEnabled, infoEnabled, debugEnabled, warnEnabled,
                exceptionEnabled, reqResEnabled, securityEnabled, externalEnabled,
                updatedBy, Timestamp.from(Instant.now()), moduleName);
        return getModuleConfig(moduleName);
    }

    private boolean boolOrDefault(Map<String, Object> patch, String key, Boolean defVal) {
        if (patch.containsKey(key) && patch.get(key) != null) {
            return Boolean.parseBoolean(String.valueOf(patch.get(key)));
        }
        return Boolean.TRUE.equals(defVal);
    }

    public Map<String, Object> getModuleConfig(String moduleName) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return Map.of("moduleName", moduleName, "loggingEnabled", properties.isEnabled());
        }
        ensureSchema();
        seedModuleIfAbsent(jdbc, moduleName);
        return jdbc.queryForMap("SELECT * FROM logging_module_config WHERE module_name = ?", moduleName);
    }

    public List<Map<String, Object>> listModules() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) return List.of();
        ensureSchema();
        return jdbc.queryForList("SELECT * FROM logging_module_config ORDER BY module_name");
    }

    public void info(String component, String msg) {
        emit(LogType.INFO, component, msg, null);
    }

    public void debug(String component, String msg) {
        emit(LogType.DEBUG, component, msg, null);
    }

    public void warn(String component, String msg) {
        emit(LogType.WARN, component, msg, null);
    }

    public void exception(String component, String code, Throwable ex) {
        String detail = code + ": " + (ex != null ? ex.getMessage() : "");
        emit(LogType.EXCEPTION, component, detail, ex);
    }

    public void requestResponse(String method, String path, int status, long durationMs) {
        emit(LogType.REQUEST_RESPONSE, "HTTP",
                method + " " + path + " -> " + status + " (" + durationMs + " ms)", null);
    }

    public void security(String component, String msg) {
        emit(LogType.SECURITY, component, msg, null);
    }

    public void externalCall(String targetService, String operation, String outcome) {
        emit(LogType.EXTERNAL_CALL, targetService, operation + " -> " + outcome, null);
    }

    private void emit(LogType type, String component, String msg, Throwable ex) {
        if (!isTypeEnabled(type)) {
            return;
        }
        String module = currentModuleName();
        switch (type) {
            case DEBUG -> log.debug("[{}][{}] {}", module, component, msg);
            case WARN -> log.warn("[{}][{}] {}", module, component, msg);
            case EXCEPTION -> log.error("[{}][{}] {}", module, component, msg, ex);
            default -> log.info("[{}][{}][{}] {}", module, type.name(), component, msg);
        }
        recentEntries.addFirst(new LogEntry(Instant.now(), module, type, component, msg));
        while (recentEntries.size() > 200) {
            recentEntries.pollLast();
        }
    }

    public List<LogEntry> getRecentEntries() {
        return List.copyOf(recentEntries);
    }

    public void clearRecentEntries() {
        recentEntries.clear();
    }
}
