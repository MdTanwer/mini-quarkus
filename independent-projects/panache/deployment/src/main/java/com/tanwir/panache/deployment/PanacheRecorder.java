package com.tanwir.panache.deployment;

import com.tanwir.core.deployment.Recorder;
import com.tanwir.panache.MiniPanacheContext;

import org.jboss.logging.Logger;

import java.util.List;

/**
 * Recorder that initializes the Panache data source and schema at deployment time.
 *
 * <p>Mirrors {@code io.quarkus.agroal.runtime.AgroalRecorder} (DataSource creation) and
 * {@code io.quarkus.hibernate.orm.runtime.HibernateOrmRecorder} (schema generation)
 * from real Quarkus.
 *
 * <p>The recorder:
 * <ol>
 *   <li>Registers all discovered {@link com.tanwir.panache.MiniEntity} classes with the context</li>
 *   <li>Creates the database schema when {@code mini.panache.ddl.auto} is not {@code none}
 *       (mirrors {@code hibernate.hbm2ddl.auto})</li>
 * </ol>
 */
@Recorder
public class PanacheRecorder {

    private static final Logger LOG = Logger.getLogger(PanacheRecorder.class);

    /**
     * Registers entity classes with {@link MiniPanacheContext} and creates the schema.
     *
     * @param entities all {@link EntityBuildItem}s produced during deployment
     */
    public void buildSchema(List<EntityBuildItem> entities) {
        if (MiniConfigBridge.panacheDdlNone()) {
            LOG.infof("[panache] Registering %d entity class(es); DDL disabled (mini.panache.ddl.auto=none)",
                    entities.size());
            for (EntityBuildItem item : entities) {
                MiniPanacheContext.registerEntity(item.entityClass());
            }
            return;
        }
        LOG.infof("[panache] Registering %d entity class(es) and creating schema", entities.size());
        for (EntityBuildItem item : entities) {
            MiniPanacheContext.registerEntity(item.entityClass());
            LOG.debugf("[panache] Registered entity: %s", item.entityClass().getSimpleName());
        }
        MiniPanacheContext.createSchema();
    }
}
