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
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.config.global.impl.validation.ConfigValidator;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.ListConfigResponse;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PropertyPath;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Singleton // Needs to be singleton to prevent initialise being called multiple times
public class GlobalConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigService.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigService.class);

    private final ConfigPropertyDao dao;
    private final SecurityContext securityContext;
    private final ConfigMapper configMapper;
    private final ConfigValidator configValidator;
    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    GlobalConfigService(final ConfigPropertyDao dao,
                        final SecurityContext securityContext,
                        final ConfigMapper configMapper,
                        final ConfigValidator configValidator,
                        final ClusterDispatchAsyncHelper dispatchHelper) {
        this.dao = dao;
        this.securityContext = securityContext;
        this.configMapper = configMapper;
        this.configValidator = configValidator;
        this.dispatchHelper = dispatchHelper;

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

        final List<ConfigProperty> allDbProps = dao.list();
        final List<ConfigProperty> validDbProps = new ArrayList<>(allDbProps.size());

        allDbProps.forEach(dbConfigProp -> {
            if (dbConfigProp.getName() == null || !configMapper.validatePropertyPath(dbConfigProp.getName())) {
                LOGGER.debug("Property {} is in the database but not in the appConfig model",
                    dbConfigProp.getName().toString());
                if (deleteUnknownProps) {
                    deleteFromDb(dbConfigProp.getName());
                }
            } else {
                validDbProps.add(dbConfigProp);
            }
        });

        configMapper.decorateAllDbConfigProperty(validDbProps);
    }

    /**
     * Refresh in background
     */
    void updateConfigObjects() {
        updateConfigFromDb();
    }

//    public List<ConfigProperty> list(final FindGlobalConfigCriteria criteria) {
//        if (criteria.getName() != null) {
//            return list(configProperty ->
//                criteria.getName().isMatch(configProperty.getName().toString()));
//        } else {
//            return list();
//        }
//    }

    public ListConfigResponse list(final Predicate<ConfigProperty> filter,
                                   final PageRequest pageRequest) {
        Objects.requireNonNull(filter);

        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            // Ensure the global config properties are up to date with the db values
            // TODO This is not ideal as each time the filter is changed we hit the db to
            //   update the config props.
            updateConfigFromDb();

            return configMapper.getGlobalProperties().stream()
                    .sorted(Comparator.comparing(ConfigProperty::getName))
                    .filter(filter)
                    .collect(ListConfigResponse.collector(pageRequest, ListConfigResponse::new));
        });
    }

    public ListConfigResponse list() {
        return list(v -> true, null);
    }

    /**
     * @param propertyPath The name of the prop to fetch, e.g. stroom.path.temp
     * @return A {@link ConfigProperty} if it is a valid prop name. The prop may or may not exist in the
     * DB. If it doesn't exist in the db then the property will be obtained from the object model.
     */
    public Optional<ConfigProperty> fetch(final PropertyPath propertyPath) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            // update the global config from the returned db record then return the corresponding
            // object from global properties which may have a yaml value in it and a different
            // effective value
            return dao.fetch(propertyPath.toString())
                    .map(configMapper::decorateDbConfigProperty)
                    .or(() ->
                            configMapper.getGlobalProperty(propertyPath));
        });
    }

    /**
     * @param id The DB primary key for the prop
     * @return A {@link ConfigProperty} if it exists in the DB, i.e. it has a db override value.
     * This means a valid prop can return an empty if the prop only has a default/yaml value.
     */
    public Optional<ConfigProperty> fetch(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {

            // update the global config from the returned db record then return the corresponding
            // object from global properties which may have a yaml value in it and a different
            // effective value
            final Optional<ConfigProperty> optionalConfigProperty = dao.fetch(id)
                    .map(configMapper::decorateDbConfigProperty);

            return optionalConfigProperty;
        });
    }

//    private ClusterConfigProperty buildClusterConfigProperty(final ConfigProperty configProperty) {
//        // TODO need to run this periodically and cache it, else we have to wait too long
//        //   for all nodes to answer
//        final DefaultClusterResultCollector<NodeConfigResult> collector = dispatchHelper
//                .execAsync(new NodeConfigClusterTask(),
//                        5,
//                        TimeUnit.SECONDS,
//                        TargetType.ENABLED);
//
//        ClusterConfigProperty clusterConfigProperty = new ClusterConfigProperty(configProperty);
//        collector.getResponseMap().forEach((nodeName, response) -> {
//            if (response == null) {
//                // TODO
//
//            } else if (response.getError() != null) {
//                // TODO
//
//            } else {
//                clusterConfigProperty.putYamlOverrideValue(
//                        nodeName, response.getResult().getYamlOverrideValue());
//            }
//        });
//        return clusterConfigProperty;
//    }

    public ConfigProperty update(final ConfigProperty configProperty) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {

            LAMBDA_LOGGER.debug(LambdaLogUtil.message(
                    "Saving property [{}] with new database value [{}]",
                    configProperty.getName(), configProperty.getDatabaseOverrideValue()));

            // Make sure we can parse the string value,
            // into an object (e.g. if it is a docref, list, map etc)
            final ConfigProperty persistedConfigProperty;
            if (configProperty.hasDatabaseOverride()) {

                // Ensure the value is a valid serialised form and that the de-serialised form
                // passes javax validation
                validateConfigProperty(configProperty);

                AuditUtil.stamp(securityContext.getUserId(), configProperty);

                if (configProperty.getId() == null) {
                    try {
                        persistedConfigProperty = dao.create(configProperty);
                    } catch (Exception e) {
                        throw new RuntimeException(LogUtil.message("Error inserting property {}: {}",
                                configProperty.getName(), e.getMessage()));
                    }
                } else {
                    try {
                        persistedConfigProperty = dao.update(configProperty);
                    } catch (Exception e) {
                        throw new RuntimeException(LogUtil.message("Error updating property {} with id {}: {}",
                                configProperty.getName(), configProperty.getId(), e.getMessage()));
                    }
                }
            } else {
                if (configProperty.getId() != null) {
                    // getDatabaseValue is unset so we need to remove it from the DB
                    try {
                        dao.delete(configProperty.getName());
                    } catch (Exception e) {
                        throw new RuntimeException(LogUtil.message("Error deleting property {}: {}",
                                configProperty.getName(), e.getMessage()));
                    }
                    // this is now orphaned so clear the ID
                    configProperty.setId(null);
                }
                persistedConfigProperty = configProperty;
            }

            // Update property in the config object tree
            configMapper.decorateDbConfigProperty(persistedConfigProperty);

            return persistedConfigProperty;
        });
    }

    private void validateConfigProperty(final ConfigProperty configProperty) {
        // We need to validate the effective value as the DB value may have been set to null
        // and the validation may demand NotNull. In this instance the yaml/default should provide
        // a value to satisfy the validation.

        final PropertyPath propertyPath = configProperty.getName();
        final String effectiveValueStr = configProperty.getEffectiveValue().orElse(null);
        final Object effectiveValue = configMapper.convertValue(propertyPath, effectiveValueStr);

        final PropertyUtil.Prop prop = configMapper.getProp(propertyPath)
                .orElseThrow(() ->
                        new RuntimeException(LogUtil.message("No prop object exists for {}", configProperty.getName())));

        final AbstractConfig parentConfigObject = (AbstractConfig) prop.getParentObject();
        final String propertyName = propertyPath.getPropertyName();

        ConfigValidator.Result result = configValidator.validateValue(
                parentConfigObject.getClass(), propertyName, effectiveValue);

        // TODO ideally we would handle warnings in some way, but that is probably a job for a new UI
        if (result.hasErrors()) {
            // We may have more than one message for the one prop, as each prop can have many validation annotations
            final StringBuilder stringBuilder = new StringBuilder()
                    .append("Value [").append(effectiveValueStr).append("] ")
                    .append(" for property ")
                    .append(propertyPath.toString())
                    .append(" is invalid:");

            result.handleErrors(error -> {
                stringBuilder
                        .append("\n")
                        .append(error.getMessage());
            });
            throw new ConfigPropertyValidationException(stringBuilder.toString());
        }
    }

    private void deleteFromDb(final PropertyPath name) {
        LAMBDA_LOGGER.warn(() ->
                LogUtil.message("Deleting property {} as it is not valid in the object model", name));
        dao.delete(name);
    }
}
