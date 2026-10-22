package com.brightline.monitor.repository;

import com.brightline.monitor.domain.Integration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {
    Optional<Integration> findByName(String name);
}
