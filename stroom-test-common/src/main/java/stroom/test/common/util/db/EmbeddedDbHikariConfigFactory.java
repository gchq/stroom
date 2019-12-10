package stroom.test.common.util.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.HikariConfigFactory;
import stroom.db.util.HikariUtil;

class EmbeddedDbHikariConfigFactory implements HikariConfigFactory {
    @Override
    public HikariConfig create(final HasDbConfig config) {
        // Set the connection pool up for testing.
        config.getDbConfig().getConnectionPoolConfig().setIdleTimeout(1000L);
        config.getDbConfig().getConnectionPoolConfig().setMaxLifetime(1000L);
        config.getDbConfig().getConnectionPoolConfig().setMaxPoolSize(2);

        DbTestUtil.applyConfig(DbTestUtil.getOrCreateConfig(), config.getDbConfig().getConnectionConfig());

        return HikariUtil.createConfig(config);
    }
}
