package stroom.test.common.util.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.HikariConfigHolder;
import stroom.db.util.HikariUtil;

class EmbeddedDbHikariConfigHolder implements HikariConfigHolder {
    @Override
    public HikariConfig getOrCreateHikariConfig(final HasDbConfig config) {
//        DbTestUtil.applyConfig(DbTestUtil.getOrCreateConfig(), config.getDbConfig().getConnectionConfig());
        return HikariUtil.createConfig(config);
    }
}
