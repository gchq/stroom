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
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.FindGlobalConfigCriteria;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton // Needs to be singleton to prevent initialise being called multiple times
class GlobalConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigService.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigService.class);

    private final ConfigPropertyDao dao;
    private final SecurityContext securityContext;
    private final ConfigMapper configMapper;

    @Inject
    GlobalConfigService(final ConfigPropertyDao dao,
                        final SecurityContext securityContext,
                        final ConfigMapper configMapper) {
        this.dao = dao;
        this.securityContext = securityContext;
        this.configMapper = configMapper;

        initialise();
    }

    private void initialise() {
        // At this point the configMapper.getGlobalProperties() will contain the name, defaultValue
        // and the yamlValue. It will also contain any info gained from the config class annotations,
        // e.g. @Readonly
        LOGGER.info("Initialising application config with global database properties");
        updateConfigFromDb(true);
    }

    private void updateConfigFromDb() {
        LOGGER.info("Updating application config with global database properties");
        updateConfigFromDb(false);
    }

    private void updateConfigFromDb(final boolean deleteUnknownProps) {
        // Get all props held in the DB, which may be a subset of those in the config
        // object model
        dao.list().forEach(dbConfigProperty -> {
            final String fullPath = dbConfigProperty.getName();
            if (fullPath != null) {

                try {
                    // Update the object model and global config property with the value from the DB
                    configMapper.updateDatabaseValue(dbConfigProperty);
                } catch (ConfigMapper.UnknownPropertyException e) {
                    LOGGER.debug("Property {} is in the database but not in the appConfig model", fullPath);
                    if (deleteUnknownProps) {
                        // Delete old property that is not in the object model
                        deleteFromDb(dbConfigProperty.getName());
                    }
                }
            } else {
                LOGGER.warn("Bad config record in the database {}", dbConfigProperty);
            }
        });
    }

    /**
     * Refresh in background
     */
    void updateConfigObjects() {
        updateConfigFromDb();
    }

    public List<ConfigProperty> list(FindGlobalConfigCriteria criteria) {
        if (criteria.getName() != null) {
            return list(v -> criteria.getName().isMatch(v.getName()));
        } else {
            return list();
        }
    }

    private List<ConfigProperty> list(final Predicate<ConfigProperty> filter) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            // Ensure the global config properties are up to date with the db values
            updateConfigFromDb();

            return configMapper.getGlobalProperties().stream()
                    .sorted(Comparator.comparing(ConfigProperty::getName))
                    .filter(filter)
                    .collect(Collectors.toList());
        });
    }

    public List<ConfigProperty> list() {
        return list(v -> true);
    }

    public Optional<ConfigProperty> fetch(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            // update the global config from the returned db record then return the corresponding
            // object from global properties which may have a yaml value in it and a different
            // effective value
            return dao.fetch(id)
                    .map(configMapper::updateDatabaseValue);
        });
    }

    public ConfigProperty update(final ConfigProperty configProperty) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {

            LAMBDA_LOGGER.debug(LambdaLogUtil.message(
                    "Saving property [{}] with new database value [{}]",
                    configProperty.getName(), configProperty.getDatabaseOverrideValue()));

            // Make sure we can parse the string value,
            // into an object (e.g. if it is a docref, list, map etc)
            final ConfigProperty persistedConfigProperty;
            if (!configProperty.hasDatabaseOverride()) {
                if (configProperty.getId() != null) {
                    // getDatabaseValue is unset so we need to remove it from the DB
                    dao.delete(configProperty.getName());
                    // this is now orphaned so clear the ID
                    configProperty.setId(null);
                }
                persistedConfigProperty = configProperty;
            } else {
                configProperty.getDatabaseOverrideValue().ifOverridePresent(optDbValue ->
                        optDbValue.ifPresent(dbValue ->
                                configMapper.validateStringValue(configProperty.getName(), dbValue)));

                AuditUtil.stamp(securityContext.getUserId(), configProperty);

                if (configProperty.getId() == null) {
                    persistedConfigProperty = dao.create(configProperty);
                } else {
                    persistedConfigProperty = dao.update(configProperty);
                }
            }

            // Update property in the config object tree
            configMapper.updateDatabaseValue(persistedConfigProperty);

            return persistedConfigProperty;
        });
    }

    private void deleteFromDb(final String name) {
        LAMBDA_LOGGER.warn(() ->
                LogUtil.message("Deleting property {} as it is not valid in the object model", name));
        dao.delete(name);
    }
}
