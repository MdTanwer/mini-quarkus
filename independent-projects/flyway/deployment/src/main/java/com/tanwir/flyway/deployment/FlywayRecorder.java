package com.tanwir.flyway.deployment;

import com.tanwir.core.deployment.Recorder;
import com.tanwir.flyway.FlywayConfig;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.jboss.logging.Logger;

import javax.sql.DataSource;

@Recorder
public class FlywayRecorder {

    private static final Logger LOG = Logger.getLogger(FlywayRecorder.class);

    public void migrateIfNeeded(DataSource dataSource) {
        if (!FlywayConfig.migrateAtStart()) {
            LOG.info("[flyway] migrate-at-start is false; skipping");
            return;
        }
        try {
            var config = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations(FlywayConfig.locations());
            String bl = FlywayConfig.baselineVersion();
            if (bl != null && !bl.isBlank()) {
                config = config.baselineOnMigrate(true).baselineVersion(bl.trim());
            }
            Flyway flyway = config.load();
            int applied = flyway.migrate().migrationsExecuted;
            LOG.infof("[flyway] Migrations applied: %d", applied);
        } catch (FlywayException e) {
            if (e.getMessage() != null && e.getMessage().contains("No migration")) {
                LOG.info("[flyway] No migrations in configured locations; continuing");
            } else {
                throw e;
            }
        }
    }
}
