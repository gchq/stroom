package stroom.config.global.impl;

import stroom.config.common.UriFactory;
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
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import com.codahale.metrics.annotation.Timed;
import event.logging.ComplexLoggedOutcome;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.UpdateEventAction;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.core.GenericType;

@AutoLogged
public class GlobalConfigResourceImpl implements GlobalConfigResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigResourceImpl.class);

    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<GlobalConfigService> globalConfigServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<UiConfig> uiConfig;
    private final Provider<UriFactory> uriFactory;
    private final Provider<NodeInfo> nodeInfoProvider;

    @Inject
    GlobalConfigResourceImpl(final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                             final Provider<GlobalConfigService> globalConfigServiceProvider,
                             final Provider<NodeService> nodeServiceProvider,
                             final Provider<UiConfig> uiConfig,
                             final Provider<UriFactory> uriFactory,
                             final Provider<NodeInfo> nodeInfoProvider) {

        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.globalConfigServiceProvider = Objects.requireNonNull(globalConfigServiceProvider);
        this.nodeServiceProvider = Objects.requireNonNull(nodeServiceProvider);
        this.uiConfig = uiConfig;
        this.uriFactory = uriFactory;
        this.nodeInfoProvider = nodeInfoProvider;
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
                nodeInfoProvider.get().getThisNodeName(),
                list.getQualifiedFilterInput());
    }

    private ListConfigResponse listWithLogging(final GlobalConfigCriteria criteria) {
        LOGGER.info("list called for {}, is", criteria);

        final StroomEventLoggingService eventLoggingService = stroomEventLoggingServiceProvider.get();

        return eventLoggingService.loggedResult(
                StroomEventLoggingUtil.buildTypeId(this, "list"),
                "List filtered configuration properties",
                SearchEventAction.builder()
                        .withQuery(buildRawQuery(criteria.getQuickFilterInput()))
                        .build(),
                searchEventAction -> {
                    // Do the work
                    final ListConfigResponse sanitisedResult = listWithoutLogging(criteria);

                    // Ignore the previous searchEventAction as it didn't have anything useful on it
                    final SearchEventAction newSearchEventAction = SearchEventAction.builder()
                            .withQuery(buildRawQuery(sanitisedResult.getQualifiedFilterInput()))
                            .withResultPage(StroomEventLoggingUtil.createResultPage(sanitisedResult))
                            .withTotalResults(BigInteger.valueOf(sanitisedResult.size()))
                            .build();

                    return ComplexLoggedOutcome.success(sanitisedResult, newSearchEventAction);
                },
                null);
    }


    // logging handled by initial call to list() on ui node. Don't want to log the call to each node
    @AutoLogged(OperationType.UNLOGGED)
    @Timed
    @Override
    public ListConfigResponse listByNode(final String nodeName,
                                         final GlobalConfigCriteria criteria) {
        LOGGER.info("listByNode called for node: {}, criteria: {}", nodeName, criteria);
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
        } catch (ConfigPropertyValidationException e) {
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

            return stroomEventLoggingService.loggedResult(
                    StroomEventLoggingUtil.buildTypeId(this, "update"),
                    "Updating property " + configProperty.getNameAsString(),
                    UpdateEventAction.builder()
                            .withAfter(stroomEventLoggingService.convertToMulti(() -> configProperty))
                            .build(),
                    eventAction -> {
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
                    },
                    null);
        } catch (ConfigPropertyValidationException e) {
            throw RestUtil.badRequest(e);
        }
    }

    @AutoLogged(OperationType.UNLOGGED) // Called constantly by UI code not user. No need to log.
    @Timed
    @Override
    public UiConfig fetchUiConfig() {
        return uiConfig.get();
    }

    private Query buildRawQuery(final String userInput) {
        return Query.builder()
                .withRaw("Configuration property matches \""
                        + Objects.requireNonNullElse(userInput, "")
                        + "\"")
                .build();
    }
}
