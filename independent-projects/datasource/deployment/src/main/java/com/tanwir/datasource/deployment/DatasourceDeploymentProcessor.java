package com.tanwir.datasource.deployment;

import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.DataSourceBuildItem;
import com.tanwir.core.deployment.ExecutionTime;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.Record;
import com.tanwir.core.deployment.RuntimeValue;
import com.tanwir.core.deployment.BuildProducer;

import org.jboss.logging.Logger;

/**
 * Agroal-backed JDBC datasource — deployment half of {@code quarkus-agroal} + {@code quarkus-jdbc-postgresql}.
 */
public class DatasourceDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(DatasourceDeploymentProcessor.class);

    @BuildStep
    public void feature(BuildProducer<FeatureBuildItem> out) {
        out.produce(new FeatureBuildItem("datasource"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public DataSourceBuildItem dataSource(DatasourceRecorder recorder) {
        RuntimeValue<javax.sql.DataSource> v = recorder.create();
        return new DataSourceBuildItem(v.getValue());
    }
}
