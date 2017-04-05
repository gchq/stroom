/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.spring;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.node.server.GlobalProperties;
import stroom.statistics.server.common.MetaDataStatisticImpl;
import stroom.statistics.server.common.MetaDataStatisticTemplate;
import stroom.util.config.StroomProperties;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Version;

import javax.inject.Named;
import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

@Configuration
public class StatisticsConfiguration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StatisticsConfiguration.class);

    public StatisticsConfiguration() {
        LOGGER.info("StatisticsConfiguration loading...");
    }

    /**
     * This bean must be returned as a class and not an interface otherwise annotation scanning will not work.
     */
    @Bean
    public MetaDataStatisticImpl metaDataStatistic() {
        final MetaDataStatisticImpl metaDataStatistic = new MetaDataStatisticImpl();
        metaDataStatistic.setTemplates(Arrays.asList(
                new MetaDataStatisticTemplate("Meta Data-Streams Received", "receivedTime", Arrays.asList("Feed")),
                new MetaDataStatisticTemplate("Meta Data-Stream Size", "receivedTime", "StreamSize", Arrays.asList("Feed"))));
        return metaDataStatistic;
    }

    @Bean
    public ComboPooledDataSource statisticsDataSource(final GlobalProperties globalProperties) throws PropertyVetoException {
        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setDriverClass(StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverClassName"));
        dataSource.setJdbcUrl(StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverUrl|trace"));
        dataSource.setUser(StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverUsername"));
        dataSource.setPassword(StroomProperties.getProperty("stroom.statistics.sql.jdbcDriverPassword"));
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(StroomProperties.getIntProperty("stroom.statistics.sql.jdbcMaxPoolSize", 10));
        dataSource.setMaxIdleTimeExcessConnections(60);
        dataSource.setIdleConnectionTestPeriod(60);
        dataSource.setPreferredTestQuery("select 1");
        dataSource.setConnectionTesterClassName(StroomProperties.getProperty("stroom.statistics.connectionTesterClassName"));
        dataSource.setDescription("SQL statistics data source");
        return dataSource;
    }

    @Bean
    public Flyway statisticsFlyway(@Named("statisticsDataSource") final ComboPooledDataSource dataSource) throws PropertyVetoException {
        final Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setLocations("stroom/statistics/sql/db/migration/mysql");

        Version version = null;
        boolean usingFlyWay = false;
        LOGGER.info("Testing installed statistics schema version");

        try {
            try (final Connection connection = dataSource.getConnection()) {
                try (final Statement statement = connection.createStatement()) {
                    try (final ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version ORDER BY installed_rank DESC")) {
                        if (resultSet.next()) {
                            usingFlyWay = true;

                            final String ver = resultSet.getString(1);
                            final String[] parts = ver.split("\\.");
                            int maj = 0;
                            int min = 0;
                            int pat = 0;
                            if (parts.length > 0) {
                                maj = Integer.valueOf(parts[0]);
                            }
                            if (parts.length > 1) {
                                min = Integer.valueOf(parts[1]);
                            }
                            if (parts.length > 2) {
                                pat = Integer.valueOf(parts[2]);
                            }

                            version = new Version(maj, min, pat);
                        }
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage());
            // Ignore.
        }

        if (version != null) {
            LOGGER.info("Detected current statistics version is v" + version.toString());
        } else {
            LOGGER.info("This is a new statistics installation!");
        }


        if (version == null) {
            // If we have no version then this is a new statistics instance so perform full FlyWay migration.
            flyway.migrate();
        } else if (usingFlyWay) {
            // If we are already using FlyWay then allow FlyWay to attempt migration.
            flyway.migrate();
        } else {
            final String message = "The current statistics version cannot be upgraded to v5+. You must be on v4.0.60 or later.";
            LOGGER.fatal(message);
            throw new RuntimeException(message);
        }

        return flyway;
    }
}
