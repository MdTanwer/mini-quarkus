package com.tanwir.hibernate.deployment;

import com.tanwir.core.deployment.Recorder;
import com.tanwir.hibernate.MiniHibernateConfig;
import com.tanwir.hibernate.MiniHibernateContext;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

@Recorder
public class HibernateOrmRecorder {

    private static final Logger LOG = Logger.getLogger(HibernateOrmRecorder.class);

    public void bootstrap(DataSource dataSource, List<JpaEntityBuildItem> entities) {
        if (entities == null || entities.isEmpty()) {
            LOG.info("[hibernate-orm] No JPA entities; skipping SessionFactory");
            return;
        }
        var srb = new StandardServiceRegistryBuilder();
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DATASOURCE, dataSource);
        settings.put("hibernate.hbm2ddl.auto", MiniHibernateConfig.hbm2ddl());
        srb.applySettings(settings);
        var ssr = srb.build();
        var sources = new MetadataSources(ssr);
        for (JpaEntityBuildItem e : entities) {
            sources.addAnnotatedClass(e.entityClass());
        }
        try {
            SessionFactory sf = sources.getMetadataBuilder()
                    .build()
                    .getSessionFactoryBuilder()
                    .build();
            MiniHibernateContext.setSessionFactory(sf);
            LOG.infof("[hibernate-orm] SessionFactory up with %d entity type(s)", entities.size());
        } catch (RuntimeException ex) {
            ssr.close();
            throw ex;
        }
    }
}
