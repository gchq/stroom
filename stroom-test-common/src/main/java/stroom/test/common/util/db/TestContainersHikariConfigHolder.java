package stroom.test.common.util.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.HikariConfigHolder;
import stroom.db.util.HikariUtil;

public class TestContainersHikariConfigHolder implements HikariConfigHolder {

    @Override
    public HikariConfig getOrCreateHikariConfig(final HasDbConfig config) {
        DbTestUtil.applyTestContainersConfig(config);

        return HikariUtil.createConfig(config);
    }
}
