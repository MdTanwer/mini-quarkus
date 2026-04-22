package com.tanwir.flyway.deployment;

import com.tanwir.core.deployment.DataSourceBuildItem;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.ExecutionTime;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MigrationsCompleteBuildItem;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.Record;
import com.tanwir.core.deployment.BuildProducer;

import org.jboss.logging.Logger;

/**
 * Runs Flyway on the default datasource — same ordering contract as
 * {@code quarkus-flyway} (after JDBC datasource, before ORM/DDL that depends on schema).
 */
public class FlywayDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(FlywayDeploymentProcessor.class);

    @BuildStep
    public void feature(BuildProducer<FeatureBuildItem> out) {
        out.produce(new FeatureBuildItem("flyway"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public MigrationsCompleteBuildItem run(FlywayRecorder recorder, DataSourceBuildItem ds) {
        recorder.migrateIfNeeded(ds.dataSource());
        LOG.info("[flyway] Migrations step completed; downstream extensions may use schema");
        return new MigrationsCompleteBuildItem();
    }
}
