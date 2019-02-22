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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.global.api.ConfigProperty;
import stroom.config.impl.db.jooq.tables.records.ConfigRecord;
import stroom.db.util.AuditUtil;
import stroom.db.util.JooqUtil;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static stroom.config.impl.db.jooq.tables.Config.CONFIG;

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
        LOGGER.info("Setting up configuration properties");
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
        final Map<String, ConfigProperty> map = new HashMap<>(globalProperties);

        final List<ConfigProperty> list = JooqUtil.contextResult(connectionProvider, context -> context
                .fetch(CONFIG)
                .into(ConfigProperty.class));

        list.forEach(record -> {
            if (record.getName() != null && record.getValue() != null) {
                final ConfigProperty configProperty = map.remove(record.getName());
                if (configProperty != null) {
                    configProperty.setId(record.getId());
                    configProperty.setValue(record.getValue());
                    configProperty.setSource(ConfigProperty.SourceType.DATABASE);

                    updateConfigObject(record.getName(), record.getValue());
                } else {
                    // Delete old property that is not in the object model
                    deleteFromDb(record.getName());
                }
            }
        });
    }

    private synchronized void updateConfigObjectsFromDB() {
        final Map<String, ConfigProperty> map = new HashMap<>(globalProperties);

        final List<ConfigProperty> list = JooqUtil.contextResult(connectionProvider, context -> context
                .fetch(CONFIG)
                .into(ConfigProperty.class));

        list.forEach(record -> {
            if (record.getName() != null && record.getValue() != null) {
                final ConfigProperty configProperty = map.remove(record.getName());
                if (configProperty != null) {
                    configProperty.setId(record.getId());
                    configProperty.setValue(record.getValue());
                    configProperty.setSource(ConfigProperty.SourceType.DATABASE);

                    Object typedValue = updateConfigObject(record.getName(), record.getValue());
//                                configProperty.setTypedValue(typedValue);
                }
            }
        });
    }

    /**
     * Refresh in background
     */
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
    public ConfigProperty fetch(final int id) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            final ConfigProperty result = JooqUtil.contextResult(connectionProvider, context -> context
                    .fetchOne(CONFIG, CONFIG.ID.eq(id))
                    .into(ConfigProperty.class));
            result.setSource(ConfigProperty.SourceType.DATABASE);
            return result;
        });
    }

//    public ConfigProperty create(final ConfigProperty configProperty) {
//        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
//            JooqUtil.context(connectionProvider, context -> context
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
    public ConfigProperty update(final ConfigProperty configProperty) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                    "Saving property [{}] with new value [{}]",
                    configProperty.getName(), configProperty.getValue()));


            AuditUtil.stamp(securityContext.getUserId(), configProperty);

            final ConfigProperty result = JooqUtil.contextWithOptimisticLocking(connectionProvider, context -> {
                final ConfigRecord configRecord = context.newRecord(CONFIG, configProperty);
                configRecord.update();
                return configRecord.into(ConfigProperty.class);
            });


//                // Change value in DB
//                int rowsAffected = create
//                        .update(CONFIG)
//                        .set(CONFIG.VAL, configProperty.getValue())
//                        .where(CONFIG.NAME.eq(configProperty.getName()))
//                        .execute();
//
//                if (rowsAffected == 0) {
//                    LOGGER.debug("No record to update with key {}, so inserting new record", configProperty.getName());
//                    create
//                            .insertInto(CONFIG,
//                                    CONFIG.NAME,
//                                    CONFIG.VAL)
//                            .values(configProperty.getName(), configProperty.getValue())
//                            .execute();
//                }
//
//                // Record history.
//                recordHistory(configProperty);

            // Update property in the config object tree
            updateConfigObject(result.getName(), result.getValue());

            // update the property in
            final ConfigProperty configPropertyFromMap = globalProperties.get(result.getName());
            if (configPropertyFromMap != null) {
                configPropertyFromMap.setSource(ConfigProperty.SourceType.DATABASE);
                configPropertyFromMap.setValue(result.getValue());
            } else {
                result.setSource(ConfigProperty.SourceType.DATABASE);
                globalProperties.put(result.getName(), result);
            }


            return result;
        });
    }

    private void deleteFromDb(final String name) {
        JooqUtil.context(connectionProvider, context -> {
            LAMBDA_LOGGER.warn(() ->
                    LambdaLogger.buildMessage("Deleting property {} as it is not valid " +
                            "in the object model", name));
            context
                    .deleteFrom(CONFIG)
                    .where(CONFIG.NAME.eq(name))
                    .execute();
        });
    }

//    private void recordHistory(final ConfigProperty configProperty) {
//        // Record history.
//        JooqUtil.context(connectionProvider, context -> context
//            create
//                    .insertInto(CONFIG_HISTORY,
//                            CONFIG_HISTORY.UPDATE_TIME,
//                            CONFIG_HISTORY.UPDATE_USER,
//                            CONFIG_HISTORY.NAME,
//                            CONFIG_HISTORY.VAL)
//                    .values(
//                            System.currentTimeMillis(),
//                            securityContext.getUserId(),
//                            configProperty.getName(),
//                            configProperty.getValue())
//                    .execute();
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//    }

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
