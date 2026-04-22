package com.tanwir.panache.deployment;

import com.tanwir.arc.deployment.SyntheticBeanBuildItem;
import com.tanwir.core.deployment.DataSourceBuildItem;
import com.tanwir.core.deployment.ExecutionTime;
import com.tanwir.core.deployment.MigrationsCompleteBuildItem;
import com.tanwir.core.deployment.BuildProducer;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.Record;
import com.tanwir.panache.MiniEntity;
import com.tanwir.panache.MiniPanacheContext;

import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Deployment processor for the Panache persistence extension.
 *
 * <p>Mirrors {@code io.quarkus.hibernate.orm.deployment.HibernateOrmProcessor} and
 * {@code io.quarkus.agroal.deployment.AgroalProcessor} from real Quarkus.
 *
 * <h2>Build steps</h2>
 * <ol>
 *   <li>{@link #announceFeature} — announces the {@code "panache"} feature</li>
 *   <li>{@link #discoverEntities} — scans the classpath for {@link MiniEntity}-annotated
 *       classes, producing one {@link EntityBuildItem} per entity</li>
 *   <li>Data source — created by {@code mini-quarkus-datasource} (Agroal), not this processor</li>
 *   <li>{@link #buildSchema} — creates tables from all discovered entities (after Flyway if enabled)</li>
 *   <li>{@link #exposeDataSource} — registers {@link DataSource} as an injectable CDI bean</li>
 * </ol>
 *
 * <h2>Dependency ordering in the build pipeline</h2>
 * <pre>
 *   ArcDeploymentProcessor.build (BeanContainerBuildItem)
 *         ↓ required by
 *   PanacheDeploymentProcessor.exposeDataSource
 * </pre>
 * All other Panache steps have no container dependency.
 */
public class PanacheDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(PanacheDeploymentProcessor.class);

    @BuildStep
    public void announceFeature(BuildProducer<FeatureBuildItem> features) {
        features.produce(new FeatureBuildItem("panache"));
        LOG.debug("[deployment] Panache feature announced");
    }

    /**
     * Discovers all {@link MiniEntity}-annotated classes via classpath scanning.
     *
     * <p>Mirrors the real Quarkus approach where Jandex indexes are used to find
     * {@code @Entity}-annotated classes at build time. In mini-quarkus we scan the
     * thread context classloader's resources for compiled {@code .class} files.
     *
     * <p>Entity classes are expected to be on the application classpath — they are
     * discovered by reading the {@code mini-entity-classes.txt} resource file written
     * at compile time by the annotation processor, or by scanning the classloader.
     */
    @BuildStep
    public void discoverEntities(BuildProducer<EntityBuildItem> entities) {
        // Primary: read compile-time generated entity list (written by ArcBeanProcessor)
        List<String> classNames = readEntityList();

        if (!classNames.isEmpty()) {
            LOG.infof("[deployment] Found %d entity class(es) from compile-time index", classNames.size());
            for (String className : classNames) {
                try {
                    Class<?> cls = Thread.currentThread().getContextClassLoader().loadClass(className);
                    if (cls.isAnnotationPresent(MiniEntity.class)) {
                        entities.produce(new EntityBuildItem(cls));
                        LOG.debugf("[deployment] Discovered @MiniEntity: %s", cls.getSimpleName());
                    }
                } catch (ClassNotFoundException e) {
                    LOG.warnf("[deployment] Cannot load entity class %s: %s", className, e.getMessage());
                }
            }
        } else {
            LOG.debug("[deployment] No mini-entity-classes.txt found; skipping entity discovery");
        }
    }

    /**
     * Creates the database schema for all discovered entities.
     * Runs after the data source and optional Flyway migrations (see {@link MigrationsCompleteBuildItem}).
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void buildSchema(
            PanacheRecorder recorder,
            DataSourceBuildItem dataSource,
            MigrationsCompleteBuildItem __migrationsDone,
            List<EntityBuildItem> entities) {
        recorder.buildSchema(entities);
    }

    /**
     * Exposes {@link DataSource} as a synthetic CDI bean so application code
     * can {@code @Inject DataSource} directly.
     *
     * <p>Mirrors how Quarkus's Agroal extension makes the datasource injectable.
     * Does NOT depend on {@link BeanContainerBuildItem} — that would create a circular
     * dependency with {@code ArcDeploymentProcessor#build} (which needs all
     * {@link SyntheticBeanBuildItem}s). Instead, we produce the synthetic bean item
     * early and let the lazy supplier pull the datasource from {@link MiniPanacheContext}
     * once the container is actually asked for the bean. This is the same pattern used
     * by {@code MutinyDeploymentProcessor#exposeEventBus} for {@code MiniEventBus}.
     */
    @BuildStep
    public SyntheticBeanBuildItem exposeDataSource(DataSourceBuildItem dataSource) {
        LOG.info("[deployment] Exposing DataSource as CDI synthetic bean");
        return SyntheticBeanBuildItem.configure(DataSource.class)
                .supplier(MiniPanacheContext::getDataSource)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the compile-time entity class list from
     * {@code META-INF/mini-entity-classes.txt} on the classpath.
     * This file is generated by the config/arc annotation processor.
     */
    private static List<String> readEntityList() {
        List<String> names = new ArrayList<>();
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/mini-entity-classes.txt")) {
            if (is == null) return names;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) names.add(line);
                }
            }
        } catch (Exception e) {
            LOG.warnf("[deployment] Could not read mini-entity-classes.txt: %s", e.getMessage());
        }
        return names;
    }
}
