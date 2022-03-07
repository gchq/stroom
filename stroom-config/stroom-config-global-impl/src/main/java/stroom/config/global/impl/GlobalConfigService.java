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


import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskContext;
import stroom.util.AuditUtil;
import stroom.util.config.AppConfigValidator;
import stroom.util.config.ConfigValidator;
import stroom.util.config.PropertyUtil;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PropertyPath;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;

public class GlobalConfigService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigService.class);

    private static final FilterFieldMappers<ConfigProperty> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_NAME,
                    ConfigProperty::getNameAsString),
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_VALUE,
                    configProperty -> configProperty.getEffectiveValue().orElse("")),
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_SOURCE,
                    configProperty -> configProperty.getSource().getName()),
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_DESCRIPTION,
                    ConfigProperty::getDescription));

//    private static final Comparator<ConfigProperty> com = Comparator.comparing(configProperty ->
//    configProperty.getEffectiveValueMasked().orElse(""));

    private static final Map<String, Comparator<ConfigProperty>> FIELD_COMPARATORS = Map.of(
            GlobalConfigResource.FIELD_DEF_NAME.getDisplayName(), Comparator.comparing(
                    ConfigProperty::getNameAsString, String::compareToIgnoreCase),
            GlobalConfigResource.FIELD_DEF_VALUE.getDisplayName(), Comparator.comparing(
                    (ConfigProperty prop) ->
                            prop.getEffectiveValueMasked().orElse(""), String::compareToIgnoreCase),
            GlobalConfigResource.FIELD_DEF_SOURCE.getDisplayName(), Comparator.comparing(ConfigProperty::getSource));

    private final GlobalConfigBootstrapService globalConfigBootstrapService;
    private final ConfigPropertyDao dao;
    private final SecurityContext securityContext;
    private final ConfigMapper configMapper;
    private final AppConfigValidator appConfigValidator;
    private final TaskContext taskContext;
    private final NodeInfo nodeInfo;

    @Inject
    GlobalConfigService(final GlobalConfigBootstrapService globalConfigBootstrapService,
                        final ConfigPropertyDao dao,
                        final SecurityContext securityContext,
                        final ConfigMapper configMapper,
                        final AppConfigValidator appConfigValidator,
                        final TaskContext taskContext,
                        final NodeInfo nodeInfo) {
        this.globalConfigBootstrapService = globalConfigBootstrapService;
        this.dao = dao;
        this.securityContext = securityContext;
        this.configMapper = configMapper;
        this.appConfigValidator = appConfigValidator;
        this.taskContext = taskContext;
        this.nodeInfo = nodeInfo;
    }

//    private void initialise() {
//        // At this point the configMapper.getGlobalProperties() will contain the name, defaultValue
//        // and the yamlValue. It will also contain any info gained from the config class annotations,
//        // e.g. @Readonly
//        updateConfigFromDb(true);
//        LOGGER.info("Initialised application config with global database properties");
//    }


    /**
     * Refresh in background
     */
    void updateConfigObjects() {
        taskContext.info(() -> "Updating config from DB");
        globalConfigBootstrapService.updateConfigFromDb(false);
    }

    public ListConfigResponse list(final GlobalConfigCriteria criteria) {
        Objects.requireNonNull(criteria);

        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {

            // This will only update if something has changed in the db config
            globalConfigBootstrapService.updateConfigFromDb(false);

            final PageRequest pageRequest = criteria.getPageRequest() != null
                    ? criteria.getPageRequest()
                    : new PageRequest(0, Integer.MAX_VALUE);

            final Optional<Comparator<ConfigProperty>> optConfigPropertyComparator = buildComparator(criteria);

            final String fullyQualifyInput = QuickFilterPredicateFactory.fullyQualifyInput(
                    criteria.getQuickFilterInput(),
                    FIELD_MAPPERS);

            return QuickFilterPredicateFactory.filterStream(
                    criteria.getQuickFilterInput(),
                    FIELD_MAPPERS,
                    configMapper.getGlobalProperties().stream(),
                    optConfigPropertyComparator.orElse(null))
                    .collect(ListConfigResponse.collector(
                            pageRequest,
                            (configProperties, pageResponse) ->
                                    new ListConfigResponse(configProperties,
                                            pageResponse,
                                            nodeInfo.getThisNodeName(),
                                            fullyQualifyInput)));
        });
    }

    private Optional<Comparator<ConfigProperty>> buildComparator(final GlobalConfigCriteria criteria) {
        if (criteria != null && criteria.getSortList() != null && !criteria.getSortList().isEmpty()) {
            return Optional.of(CompareUtil.buildCriteriaComparator(FIELD_COMPARATORS, criteria));
        } else {
            return Optional.empty();
        }
    }

    public ListConfigResponse list() {
        return list(new GlobalConfigCriteria(
                new PageRequest(0, Integer.MAX_VALUE),
                Collections.emptyList(),
                null));
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
                    .map(configProp ->
                            configMapper.decorateDbConfigProperty(configProp))
                    .or(() -> {

                        return configMapper.getGlobalProperty(propertyPath);
                    });
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

            LOGGER.debug(() -> LogUtil.message(
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

            // Having updated a prop make sure the in mem config is correct.
            globalConfigBootstrapService.updateConfigFromDb(false);

            return persistedConfigProperty;
        });
    }

    private void validateConfigProperty(final ConfigProperty configProperty) {
        // We need to validate the effective value as the DB value may have been set to null
        // and the validation may demand NotNull. In this instance the yaml/default should provide
        // a value to satisfy the validation.

        final PropertyPath propertyPath = configProperty.getName();
        final String effectiveValueStr = configProperty.getEffectiveValue().orElse(null);
        final Object effectiveValue;
        try {
            effectiveValue = configMapper.validateValueSerialisation(propertyPath, effectiveValueStr);
        } catch (Exception e) {
            throw new ConfigPropertyValidationException(LogUtil.message("Error parsing [{}]: {}",
                    effectiveValueStr, e.getMessage(), e));
        }

        final PropertyUtil.Prop prop = configMapper.getProp(propertyPath)
                .orElseThrow(() ->
                        new RuntimeException(LogUtil.message("No prop object exists for {}",
                                configProperty.getName())));

        final AbstractConfig parentConfigObject = (AbstractConfig) prop.getParentObject();
        final String propertyName = propertyPath.getPropertyName();

        ConfigValidator.Result<AbstractConfig> result = appConfigValidator.validateValue(
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
}
