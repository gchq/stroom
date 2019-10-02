package stroom.db.util;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Singleton to hold a map of Hikari configurations keyed by their {@link DbConfig}. This means
 * if we have multiple modules that share the same connection and pool details we can re-use an existing
 * one.
 *
 * It assumes that db config and pools cannot be changed at runtime
 */
@Singleton
public class HikariConfigHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariConfigHolder.class);

    private final ConcurrentMap<DbConfig, HikariConfigWrapper> dbConfigToHikariConfigMap = new ConcurrentHashMap<>();

    public HikariConfigHolder() {
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());
    }

    public HikariConfig getHikariConfig(final HasDbConfig config) {

        String requestingConfigClass = config.getClass().getSimpleName();
        LOGGER.debug("Getting Hikari database config for {}, current map size {}",
                requestingConfigClass, dbConfigToHikariConfigMap.size());
        final DbConfig dbConfig = config.getDbConfig();

        final HikariConfigWrapper hikariConfigWrapper = dbConfigToHikariConfigMap
                .compute(dbConfig, (key, existingValue) -> {

                    final HikariConfigWrapper value;
                    if (existingValue == null) {
                        LOGGER.info("Creating new Hikari database config for {}",
                                requestingConfigClass);
                        value = new HikariConfigWrapper(
                                HikariUtil.createConfig(config),
                                requestingConfigClass);
                    } else {
                        LOGGER.info("Found existing Hikari config for {} that matches the required config for {}",
                                existingValue.getSource(),
                                config.getClass().getSimpleName());
                        value = existingValue;
                    }
                    return value;
                });

        Objects.requireNonNull(hikariConfigWrapper, "Should not be null here");
        return hikariConfigWrapper.getHikariConfig();
    }

    private static class HikariConfigWrapper {
        private final HikariConfig hikariConfig;
        private final String source;

        HikariConfigWrapper(final HikariConfig hikariConfig, final String source) {
            this.hikariConfig = Objects.requireNonNull(hikariConfig);
            this.source = Objects.requireNonNull(source);
        }

        HikariConfig getHikariConfig() {
            return hikariConfig;
        }

        String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return "HiikariConfigWrapper{" +
                    "hikariConfig=" + hikariConfig +
                    ", source='" + source + '\'' +
                    '}';
        }
    }
}
