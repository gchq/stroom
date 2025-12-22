/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.config.global.impl;

import stroom.annotation.impl.AnnotationState;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.impl.ExplorerConfig;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.impl.StroomReceiptPolicyConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import event.logging.ComplexLoggedOutcome;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.UpdateEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.SyncInvoker;
import jakarta.ws.rs.core.GenericType;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@AutoLogged
public class GlobalConfigResourceImpl implements GlobalConfigResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigResourceImpl.class);

    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<GlobalConfigService> globalConfigServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<UiConfig> uiConfig;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final Provider<ExplorerConfig> explorerConfigProvider;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<AnnotationState> annotationStateProvider;

    @Inject
    GlobalConfigResourceImpl(final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                             final Provider<GlobalConfigService> globalConfigServiceProvider,
                             final Provider<NodeService> nodeServiceProvider,
                             final Provider<UiConfig> uiConfig,
                             final Provider<NodeInfo> nodeInfoProvider,
                             final Provider<OpenIdConfiguration> openIdConfigProvider,
                             final Provider<ExplorerConfig> explorerConfigProvider,
                             final Provider<AuthenticationConfig> authenticationConfigProvider,
                             final Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider,
                             final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final Provider<AnnotationState> annotationStateProvider) {

        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.globalConfigServiceProvider = Objects.requireNonNull(globalConfigServiceProvider);
        this.nodeServiceProvider = Objects.requireNonNull(nodeServiceProvider);
        this.uiConfig = uiConfig;
        this.nodeInfoProvider = nodeInfoProvider;
        this.openIdConfigProvider = openIdConfigProvider;
        this.explorerConfigProvider = explorerConfigProvider;
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.stroomReceiptPolicyConfigProvider = stroomReceiptPolicyConfigProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.annotationStateProvider = annotationStateProvider;
    }


    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Timed
    @Override
    public ListConfigResponse list(final GlobalConfigCriteria criteria) {
        return listWithLogging(criteria);
    }

    private ListConfigResponse listWithoutLogging(final GlobalConfigCriteria criteria) {
        final ListConfigResponse list = globalConfigServiceProvider.get().list(criteria);
        List<ConfigProperty> values = list.getValues();
        values = values.stream()
                .map(this::sanitise)
                .collect(Collectors.toList());
        return new ListConfigResponse(
                values,
                list.getPageResponse(),
                nodeInfoProvider.get().getThisNodeName());
    }

    private ListConfigResponse listWithLogging(final GlobalConfigCriteria criteria) {
        LOGGER.debug("list called for {}", criteria);

        final StroomEventLoggingService eventLoggingService = stroomEventLoggingServiceProvider.get();

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "list"))
                .withDescription("List filtered configuration properties")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(buildRawQuery(criteria.getQuickFilterInput()))
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final ListConfigResponse sanitisedResult = listWithoutLogging(criteria);

                    // Ignore the previous searchEventAction as it didn't have anything useful on it
                    final SearchEventAction newSearchEventAction = SearchEventAction.builder()
                            .withQuery(buildRawQuery(criteria.getQuickFilterInput()))
                            .withResultPage(StroomEventLoggingUtil.createResultPage(sanitisedResult))
                            .withTotalResults(BigInteger.valueOf(sanitisedResult.size()))
                            .build();

                    return ComplexLoggedOutcome.success(sanitisedResult, newSearchEventAction);
                })
                .getResultAndLog();
    }


    // logging handled by initial call to list() on ui node. Don't want to log the call to each node
    @AutoLogged(OperationType.UNLOGGED)
    @Timed
    @Override
    public ListConfigResponse listByNode(final String nodeName,
                                         final GlobalConfigCriteria criteria) {
        LOGGER.debug("listByNode called for node: {}, criteria: {}", nodeName, criteria);
        return nodeServiceProvider.get().remoteRestResult(
                nodeName,
                ListConfigResponse.class,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        GlobalConfigResource.BASE_PATH,
                        GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                        nodeName),
                () ->
                        listWithoutLogging(criteria),
                builder ->
                        builder.post(Entity.json(criteria)));
    }

    @Timed
    @Override
    public ConfigProperty getPropertyByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        Optional<ConfigProperty> optConfigProperty = globalConfigServiceProvider.get().fetch(
                PropertyPath.fromPathString(propertyPath));
        optConfigProperty = sanitise(optConfigProperty);
        return optConfigProperty.orElseThrow(NotFoundException::new);
    }

    @Timed
    public OverrideValue<String> getYamlValueByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        Optional<ConfigProperty> optConfigProperty = globalConfigServiceProvider.get().fetch(
                PropertyPath.fromPathString(propertyPath));
        optConfigProperty = sanitise(optConfigProperty);
        return optConfigProperty
                .map(ConfigProperty::getYamlOverrideValue)
                .orElseThrow(() -> new NotFoundException(LogUtil.message("Property {} not found", propertyPath)));
    }

    private Optional<ConfigProperty> sanitise(final Optional<ConfigProperty> optionalConfigProperty) {
        return optionalConfigProperty.map(this::sanitise);
    }

    private ConfigProperty sanitise(final ConfigProperty configProperty) {
        if (configProperty.isPassword()) {
            configProperty.setDefaultValue(null);
            if (configProperty.getDatabaseOverrideValue().isHasOverride()) {
                configProperty.setDatabaseOverrideValue(OverrideValue.withNullValue(String.class));
            } else {
                configProperty.setDatabaseOverrideValue(OverrideValue.unSet(String.class));
            }
            if (configProperty.getYamlOverrideValue().isHasOverride()) {
                configProperty.setYamlOverrideValue(OverrideValue.withNullValue(String.class));
            } else {
                configProperty.setYamlOverrideValue(OverrideValue.unSet(String.class));
            }
        }
        return configProperty;
    }

    @Timed
    @Override
    public OverrideValue<String> getYamlValueByNodeAndName(final String propertyPath,
                                                           final String nodeName) {
        RestUtil.requireNonNull(propertyPath, "propertyName not supplied");
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        return nodeServiceProvider.get().remoteRestResult(
                nodeName,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        GlobalConfigResource.BASE_PATH,
                        GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                        propertyPath,
                        GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                        nodeName),
                () ->
                        getYamlValueByName(propertyPath),
                SyncInvoker::get,
                response -> response.readEntity(new GenericType<OverrideValue<String>>() {
                }));
    }

    @Timed
    @Override
    public ConfigProperty create(final ConfigProperty configProperty) {
        RestUtil.requireNonNull(configProperty, "configProperty not supplied");
        RestUtil.requireNonNull(configProperty.getName(), "configProperty name cannot be null");

        try {
            return globalConfigServiceProvider.get().update(configProperty);
        } catch (final ConfigPropertyValidationException e) {
            throw RestUtil.badRequest(e);
        }
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Timed
    @Override
    public ConfigProperty update(final String propertyName, final ConfigProperty configProperty) {
        RestUtil.requireNonNull(propertyName, "propertyName not supplied");
        RestUtil.requireNonNull(configProperty, "configProperty not supplied");

        if (!propertyName.equals(configProperty.getNameAsString())) {
            throw RestUtil.badRequest(LogUtil.message("Property names don't match, {} & {}",
                    propertyName, configProperty.getNameAsString()));
        }

        try {
            final GlobalConfigService globalConfigService = globalConfigServiceProvider.get();
            final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();

            return stroomEventLoggingService.loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                    .withDescription("Updating property " + configProperty.getNameAsString())
                    .withDefaultEventAction(UpdateEventAction.builder()
                            .withAfter(stroomEventLoggingService.convertToMulti(() -> configProperty))
                            .build())
                    .withComplexLoggedResult(eventAction -> {
                        // Do the update
                        final ConfigProperty persistedProperty = globalConfigService.update(configProperty);

                        return ComplexLoggedOutcome.success(
                                persistedProperty,
                                stroomEventLoggingService.buildUpdateEventAction(
                                        () -> {
                                            if (configProperty.getId() == null) {
                                                return null;
                                            }
                                            return globalConfigService.fetch(configProperty.getId())
                                                    .orElse(null);
                                        },
                                        () -> persistedProperty));
                    })
                    .getResultAndLog();
        } catch (final ConfigPropertyValidationException e) {
            throw RestUtil.badRequest(e);
        }
    }

    // This one gets called by the GWT UI
    @Unauthenticated
    @AutoLogged(OperationType.UNLOGGED) // Called constantly by UI code not user. No need to log.
    @Timed
    @Override
    public ExtendedUiConfig fetchExtendedUiConfig() {
        final boolean isExternalIdp = NullSafe.test(
                openIdConfigProvider.get().getIdentityProviderType(),
                IdpType::isExternal);

        // Add additional back-end config that is also need in the UI without having to expose
        // the back-end config classes.
        return new ExtendedUiConfig(
                uiConfig.get(),
                isExternalIdp,
                explorerConfigProvider.get().getDependencyWarningsEnabled(),
                authenticationConfigProvider.get().getMaxApiKeyExpiryAge().toMillis(),
                stroomReceiptPolicyConfigProvider.get().getObfuscatedFields(),
                receiveDataConfigProvider.get().getReceiptCheckMode(),
                annotationStateProvider.get().getLastChangeTime());
    }

    private Query buildRawQuery(final String userInput) {
        return Strings.isNullOrEmpty(userInput)
                ? new Query()
                : Query.builder()
                        .withRaw("Configuration property matches \"" + userInput + "\"")
                        .build();
    }
}
