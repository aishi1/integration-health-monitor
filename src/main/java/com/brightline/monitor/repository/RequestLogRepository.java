package com.brightline.monitor.repository;

import com.brightline.monitor.domain.LogPoint;
import com.brightline.monitor.domain.RequestLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    /** Both bounds inclusive, so a 60-minute window at 09:55 still includes the 09:00 sample. */
    List<RequestLog> findByIntegrationIdAndRequestedAtBetweenOrderByRequestedAtAsc(
            Long integrationId, LocalDateTime from, LocalDateTime to);

    Optional<RequestLog> findTopByIntegrationIdAndRequestedAtLessThanEqualOrderByRequestedAtDesc(
            Long integrationId, LocalDateTime to);

    @Query("select max(r.requestedAt) from RequestLog r")
    Optional<LocalDateTime> findMaxRequestedAt();

    /** Narrow projection for reports; avoids loading full entities for tens of thousands of rows. */
    @Query("select new com.brightline.monitor.domain.LogPoint(r.integration.id, r.requestedAt, r.errorClass, r.latencyMs) "
            + "from RequestLog r where r.requestedAt >= :from and r.requestedAt <= :to")
    List<LogPoint> findPoints(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
