package com.tanwir.datasource.deployment;

import com.tanwir.core.deployment.Recorder;
import com.tanwir.core.deployment.RuntimeValue;
import com.tanwir.datasource.DatasourceConfig;
import com.tanwir.panache.MiniPanacheContext;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;

import org.jboss.logging.Logger;

import java.sql.SQLException;

import javax.sql.DataSource;

@Recorder
public class DatasourceRecorder {

    private static final Logger LOG = Logger.getLogger(DatasourceRecorder.class);

    public RuntimeValue<DataSource> create() {
        String url = DatasourceConfig.jdbcUrl();
        String user = DatasourceConfig.username();
        String password = DatasourceConfig.password();

        LOG.infof("[datasource] Creating Agroal pool for: %s", redactUrl(url));
        try {
            Class<?> driver = resolveDriver(url);
            AgroalDataSourceConfigurationSupplier cfg = new AgroalDataSourceConfigurationSupplier();
            AgroalConnectionPoolConfigurationSupplier pool = cfg.connectionPoolConfiguration();
            pool.maxSize(10);
            pool.minSize(0);
            AgroalConnectionFactoryConfigurationSupplier cf = pool.connectionFactoryConfiguration();
            cf.jdbcUrl(url);
            cf.connectionProviderClass(driver);
            cf.principal(new NamePrincipal(user));
            cf.credential(new SimplePassword(password));
            AgroalDataSourceConfiguration built = cfg.get();
            AgroalDataSource ag = AgroalDataSource.from(built);
            MiniPanacheContext.setDataSource(ag);
            LOG.info("[datasource] DataSource started");
            return new RuntimeValue<>(ag);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC driver not found for URL: " + url, e);
        } catch (SQLException e) {
            throw new RuntimeException("Agroal DataSource start failed: " + e.getMessage(), e);
        }
    }

    private static String redactUrl(String url) {
        if (url == null) {
            return "null";
        }
        if (url.contains("password=")) {
            return url.replaceAll("password=[^;&]+", "password=****");
        }
        return url;
    }

    private static Class<?> resolveDriver(String url) throws ClassNotFoundException {
        if (url.startsWith("jdbc:postgresql:")) {
            return Class.forName("org.postgresql.Driver", true, Thread.currentThread().getContextClassLoader());
        }
        if (url.startsWith("jdbc:h2:")) {
            return Class.forName("org.h2.Driver", true, Thread.currentThread().getContextClassLoader());
        }
        throw new ClassNotFoundException("No built-in driver mapping for URL: " + url);
    }
}
