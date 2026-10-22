package com.brightline.monitor.repository;

import com.brightline.monitor.domain.HealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthSnapshotRepository extends JpaRepository<HealthSnapshot, Long> {}
