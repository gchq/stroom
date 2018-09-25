/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.config.global.impl.db;


import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.global.api.ConfigProperty;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.StroomFrequencySchedule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static stroom.config.impl.db.stroom.tables.Config.CONFIG;
import static stroom.config.impl.db.stroom.tables.ConfigHistory.CONFIG_HISTORY;

@Singleton
class GlobalConfigServiceImpl implements GlobalConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigServiceImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigServiceImpl.class);

    private final ConnectionProvider connectionProvider;
    private final Security security;
    private final SecurityContext securityContext;
    private final Map<String, ConfigProperty> globalProperties = new HashMap<>();
    private final ConfigMapper configMapper;

    @Inject
    GlobalConfigServiceImpl(final ConnectionProvider connectionProvider,
                            final Security security,
                            final SecurityContext securityContext,
                            final ConfigMapper configMapper) {
        this.connectionProvider = connectionProvider;
        this.security = security;
        this.securityContext = securityContext;
        this.configMapper = configMapper;

        initialise();
    }


    private Object updateConfigObject(final String key, final String value) {
        return configMapper.updateConfigObject(key, value);
    }

    private void initialise() {
        // Setup DB properties.
        LOGGER.info("Adding global properties to the DB");
        loadMappedProperties();
        loadDBProperties();
    }

    private void loadMappedProperties() {
        try {
            final Collection<ConfigProperty> configPropertyList = configMapper.getGlobalProperties();
            for (final ConfigProperty configProperty : configPropertyList) {
                globalProperties.put(configProperty.getName(), configProperty);
                updateConfigObject(configProperty.getName(), configProperty.getValue());
            }
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void loadDBProperties() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final Map<String, ConfigProperty> map = new HashMap<>(globalProperties);

            create
                    .selectFrom(CONFIG)
                    .fetch()
                    .forEach(record -> {
                        if (record.getName() != null && record.getVal() != null) {
                            final ConfigProperty configProperty = map.remove(record.getName());
                            if (configProperty != null) {
                                configProperty.setId(record.getId());
                                configProperty.setValue(record.getVal());
                                configProperty.setSource(ConfigProperty.SourceType.DATABASE);

                                updateConfigObject(record.getName(), record.getVal());
                            } else {
                                // Delete old property.
//                                deleteFromDb(record.getName());
                            }
                        }
                    });

//            // Add remaining properties to the db.
//            map.forEach((k, v) -> create(v));

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void updateConfigObjectsFromDB() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final Map<String, ConfigProperty> map = new HashMap<>(globalProperties);

            create
                    .selectFrom(CONFIG)
                    .fetch()
                    .forEach(record -> {
                        if (record.getName() != null && record.getVal() != null) {
                            final ConfigProperty configProperty = map.remove(record.getName());
                            if (configProperty != null) {
                                configProperty.setId(record.getId());
                                configProperty.setValue(record.getVal());
                                configProperty.setSource(ConfigProperty.SourceType.DATABASE);

                                Object typedValue = updateConfigObject(record.getName(), record.getVal());
//                                configProperty.setTypedValue(typedValue);
                            }
                        }
                    });
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Refresh in background
     */
    @SuppressWarnings("unused") // Called by scheduler
    @StroomFrequencySchedule("1m")
    @JobTrackedSchedule(jobName = "Property Cache Reload", description = "Reload properties in the cluster")
    public void updateConfigObjects() {
        updateConfigObjectsFromDB();
    }

    @Override
    public List<ConfigProperty> list() {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            updateConfigObjectsFromDB();

            final List<ConfigProperty> list = new ArrayList<>(globalProperties.values());
            list.sort(Comparator.comparing(ConfigProperty::getName));

            return list;
        });
    }

    @Override
    public ConfigProperty load(final ConfigProperty configProperty) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            final ConfigProperty loaded = globalProperties.get(configProperty.getName());

            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                create
                        .selectFrom(CONFIG)
                        .where(CONFIG.ID.eq(configProperty.getId()))
                        .fetchOptional()
                        .ifPresent(record -> {
                            if (record.getName() != null && record.getVal() != null) {
                                if (loaded != null) {
                                    loaded.setId(record.getId());
                                    loaded.setValue(record.getVal());
                                    loaded.setSource(ConfigProperty.SourceType.DATABASE);
                                }
                            }
                        });
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return loaded;
        });
    }

//    public ConfigProperty create(final ConfigProperty configProperty) {
//        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
//            try (final Connection connection = connectionProvider.getConnection()) {
//                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//
//                // Insert value.
//                create
//                        .insertInto(CONFIG, CONFIG.NAME, CONFIG.VAL)
//                        .values(configProperty.getName(), configProperty.getValue())
//                        .execute();
//
//                // Record history.
//                recordHistory(configProperty);
//
//                // Update property.
//                update(configProperty.getName(), configProperty.getValue());
//            } catch (final SQLException e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//
//            return configProperty;
//        });
//    }

    @Override
    public ConfigProperty save(final ConfigProperty configProperty) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

                LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                        "Saving property [{}] with new value [{}]",
                        configProperty.getName(), configProperty.getValue()));


                // Change value in DB
                int rowsAffected = create
                        .update(CONFIG)
                        .set(CONFIG.VAL, configProperty.getValue())
                        .where(CONFIG.NAME.eq(configProperty.getName()))
                        .execute();

                if (rowsAffected == 0) {
                    LOGGER.debug("No record to update with key {}, so inserting new record", configProperty.getName());
                    create
                            .insertInto(CONFIG,
                                    CONFIG.NAME,
                                    CONFIG.VAL)
                            .values(configProperty.getName(), configProperty.getValue())
                            .execute();
                }

                // Record history.
                recordHistory(configProperty);

                // Update property in the config object tree
                updateConfigObject(configProperty.getName(), configProperty.getValue());

                // update the property in
                final ConfigProperty configPropertyFromMap = globalProperties.get(configProperty.getName());
                if (configPropertyFromMap != null) {
                    configPropertyFromMap.setSource(ConfigProperty.SourceType.DATABASE);
                    configPropertyFromMap.setValue(configProperty.getValue());
                } else {
                    configProperty.setSource(ConfigProperty.SourceType.DATABASE);
                    globalProperties.put(configProperty.getName(), configProperty);
                }

            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return configProperty;
        });
    }

    private void deleteFromDb(final String name) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            LAMBDA_LOGGER.debug(() ->
                    LambdaLogger.buildMessage("Deleting property [{}]", name));
            create
                    .deleteFrom(CONFIG)
                    .where(CONFIG.NAME.eq(name))
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void recordHistory(final ConfigProperty configProperty) {
        // Record history.
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .insertInto(CONFIG_HISTORY,
                            CONFIG_HISTORY.UPDATE_TIME,
                            CONFIG_HISTORY.UPDATE_USER,
                            CONFIG_HISTORY.NAME,
                            CONFIG_HISTORY.VAL)
                    .values(System.currentTimeMillis(), securityContext.getUserId(), configProperty.getName(), configProperty.getValue())
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<String, ConfigProperty> entry : globalProperties.entrySet()) {
            sb.append(entry.getKey());
            sb.append("=");
            sb.append(entry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}
