package stroom.db.util;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.HasDbConfig;

public interface HikariConfigHolder {
    HikariConfig getOrCreateHikariConfig(HasDbConfig config);
}
