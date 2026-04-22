package com.tanwir.hibernate.deployment;

import com.tanwir.arc.deployment.SyntheticBeanBuildItem;
import com.tanwir.core.deployment.BuildProducer;
import com.tanwir.core.deployment.BuildStep;
import com.tanwir.core.deployment.DataSourceBuildItem;
import com.tanwir.core.deployment.ExecutionTime;
import com.tanwir.core.deployment.ExtensionProcessor;
import com.tanwir.core.deployment.MigrationsCompleteBuildItem;
import com.tanwir.core.deployment.MiniExtensionManager.FeatureBuildItem;
import com.tanwir.core.deployment.Record;
import com.tanwir.hibernate.MiniHibernateContext;

import jakarta.persistence.Entity;

import org.hibernate.SessionFactory;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Hibernate ORM extension: build-time entity index + SessionFactory + CDI synthetic bean.
 */
public class HibernateOrmDeploymentProcessor implements ExtensionProcessor {

    private static final Logger LOG = Logger.getLogger(HibernateOrmDeploymentProcessor.class);

    @BuildStep
    public void feature(BuildProducer<FeatureBuildItem> out) {
        out.produce(new FeatureBuildItem("hibernate-orm"));
    }

    @BuildStep
    public void discoverJpaEntities(BuildProducer<JpaEntityBuildItem> out) {
        List<String> names = readJpaEntityList();
        for (String name : names) {
            try {
                Class<?> cls = Thread.currentThread().getContextClassLoader().loadClass(name);
                if (cls.getAnnotation(Entity.class) != null) {
                    out.produce(new JpaEntityBuildItem(cls));
                    LOG.debugf("[deployment] JPA entity: %s", cls.getSimpleName());
                }
            } catch (ClassNotFoundException e) {
                LOG.warnf("[deployment] Cannot load JPA entity class %s", name);
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public HibernateOrmInitCompleteBuildItem bootstrap(
            HibernateOrmRecorder recorder,
            DataSourceBuildItem dataSource,
            MigrationsCompleteBuildItem __migrationsDone,
            List<JpaEntityBuildItem> entities) {
        recorder.bootstrap(dataSource.dataSource(), entities);
        return new HibernateOrmInitCompleteBuildItem();
    }

    @BuildStep
    public void registerSessionFactory(
            HibernateOrmInitCompleteBuildItem __ready,
            List<JpaEntityBuildItem> entities,
            BuildProducer<SyntheticBeanBuildItem> synthetic) {
        if (entities.isEmpty()) {
            return;
        }
        LOG.info("[deployment] Registering SessionFactory as synthetic CDI bean");
        synthetic.produce(SyntheticBeanBuildItem.configure(SessionFactory.class)
                .supplier(MiniHibernateContext::getSessionFactory)
                .build());
    }

    private static List<String> readJpaEntityList() {
        List<String> names = new ArrayList<>();
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/jpa-entity-classes.txt")) {
            if (is == null) {
                return names;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        names.add(line);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("[deployment] Could not read jpa-entity-classes.txt: %s", e.getMessage());
        }
        return names;
    }
}
