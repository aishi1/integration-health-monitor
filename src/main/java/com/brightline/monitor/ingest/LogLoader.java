package com.brightline.monitor.ingest;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.ErrorClass;
import com.brightline.monitor.domain.Integration;
import com.brightline.monitor.repository.IntegrationRepository;
import com.brightline.monitor.repository.RequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the database from the sample JSON on first start (only when request_log is empty). */
@Component
public class LogLoader implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LogLoader.class);
    private static final String INSERT = "insert into request_log "
            + "(integration_id, requested_at, status, status_code, latency_ms, error_class, success) "
            + "values (?, ?, ?, ?, ?, ?, ?)";

    private final HealthProperties props;
    private final ResourceLoader resources;
    private final ObjectMapper mapper;
    private final IntegrationRepository integrations;
    private final RequestLogRepository logs;
    private final JdbcTemplate jdbc;

    public LogLoader(HealthProperties props, ResourceLoader resources, ObjectMapper mapper,
                     IntegrationRepository integrations, RequestLogRepository logs, JdbcTemplate jdbc) {
        this.props = props;
        this.resources = resources;
        this.mapper = mapper;
        this.integrations = integrations;
        this.logs = logs;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws Exception {
        String location = props.sampleLogs();
        if (location == null || location.isBlank() || logs.count() > 0) return;
        Resource resource = resources.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            int n = ingest(mapper.readValue(in, LogFile.class));
            log.info("Seeded {} log entries from {}", n, location);
        }
    }

    /** Reusable for a future upload/API endpoint. Integrations are auto-created by name; rows are batch-inserted. */
    @Transactional
    public int ingest(LogFile file) {
        List<LogEntry> entries = file.logs();
        Map<String, Long> ids = new HashMap<>();
        for (LogEntry e : entries) {
            ids.computeIfAbsent(e.integration(), name -> integrations.findByName(name)
                    .orElseGet(() -> integrations.save(new Integration(name, props.defaultCadenceMinutes())))
                    .getId());
        }
        jdbc.batchUpdate(INSERT, entries, 1000, (ps, e) -> {
            ErrorClass ec = ErrorClass.classify(e.status(), e.statusCode());
            ps.setLong(1, ids.get(e.integration()));
            ps.setTimestamp(2, Timestamp.valueOf(e.timestamp()));
            ps.setString(3, e.status());
            if (e.statusCode() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, e.statusCode());
            ps.setInt(5, e.latencyMs());
            ps.setString(6, ec.name());
            ps.setBoolean(7, ec == ErrorClass.NONE);
        });
        return entries.size();
    }
}
