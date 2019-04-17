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

package stroom.config.global.impl;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.global.api.ConfigProperty;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Singleton
class GlobalConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigService.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigService.class);

    private final ConfigPropertyDao dao;
    private final Security security;
    private final SecurityContext securityContext;
    private final Map<String, ConfigProperty> globalProperties = new HashMap<>();
    private final ConfigMapper configMapper;

    @Inject
    GlobalConfigService(final ConfigPropertyDao dao,
                        final Security security,
                        final SecurityContext securityContext,
                        final ConfigMapper configMapper) {
        this.dao = dao;
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

        final List<ConfigProperty> list = dao.list();

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

        final List<ConfigProperty> list = dao.list();

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
    void updateConfigObjects() {
        updateConfigObjectsFromDB();
    }

    public List<ConfigProperty> list() {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            updateConfigObjectsFromDB();

            final List<ConfigProperty> list = new ArrayList<>(globalProperties.values());
            list.sort(Comparator.comparing(ConfigProperty::getName));

            return list;
        });
    }

    public ConfigProperty fetch(final int id) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> dao.fetch(id)
                .map(prop -> {
                    prop.setSource(ConfigProperty.SourceType.DATABASE);
                    return prop;
                }).orElse(null));
    }

    public ConfigProperty update(final ConfigProperty configProperty) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            LAMBDA_LOGGER.debug(LambdaLogUtil.message(
                    "Saving property [{}] with new value [{}]",
                    configProperty.getName(), configProperty.getValue()));


            AuditUtil.stamp(securityContext.getUserId(), configProperty);

            final ConfigProperty result = dao.update(configProperty);


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
        LAMBDA_LOGGER.warn(() -> LogUtil.message("Deleting property {} as it is not valid in the object model", name));
        dao.delete(name);
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
