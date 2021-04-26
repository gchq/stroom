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
import stroom.node.api.NodeService;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import com.codahale.metrics.annotation.Timed;
import event.logging.ComplexLoggedOutcome;
import event.logging.UpdateEventAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.core.GenericType;

@AutoLogged
public class GlobalConfigResourceImpl implements GlobalConfigResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigResourceImpl.class);

    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<GlobalConfigService> globalConfigServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<UiConfig> uiConfig;
    private final Provider<UriFactory> uriFactory;

    @Inject
    GlobalConfigResourceImpl(final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                             final Provider<GlobalConfigService> globalConfigServiceProvider,
                             final Provider<NodeService> nodeServiceProvider,
                             final Provider<UiConfig> uiConfig,
                             final Provider<UriFactory> uriFactory) {

        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.globalConfigServiceProvider = Objects.requireNonNull(globalConfigServiceProvider);
        this.nodeServiceProvider = Objects.requireNonNull(nodeServiceProvider);
        this.uiConfig = uiConfig;
        this.uriFactory = uriFactory;
    }

    @Timed
    @Override
    public ListConfigResponse list(final GlobalConfigCriteria criteria) {
        return globalConfigServiceProvider.get().list(criteria);
    }

    @Timed
    @Override
    public ListConfigResponse listByNode(final String nodeName,
                                         final GlobalConfigCriteria criteria) {
        return nodeServiceProvider.get().remoteRestResult(
                nodeName,
                ListConfigResponse.class,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        GlobalConfigResource.BASE_PATH,
                        GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                        nodeName),
                () ->
                        list(criteria),
                builder ->
                        builder.post(Entity.json(criteria)));
    }

    @Timed
    @Override
    public ConfigProperty getPropertyByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        final Optional<ConfigProperty> optConfigProperty = globalConfigServiceProvider.get().fetch(
                PropertyPath.fromPathString(propertyPath));
        return optConfigProperty.orElseThrow(NotFoundException::new);
    }

    @Timed
    public OverrideValue<String> getYamlValueByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        final Optional<ConfigProperty> optConfigProperty = globalConfigServiceProvider.get().fetch(
                PropertyPath.fromPathString(propertyPath));
        return optConfigProperty
                .map(ConfigProperty::getYamlOverrideValue)
                .orElseThrow(() -> new NotFoundException(LogUtil.message("Property {} not found", propertyPath)));
    }

    @Timed
    @Override
    public OverrideValue<String> getYamlValueByNodeAndName(final String propertyName,
                                                           final String nodeName) {
        RestUtil.requireNonNull(propertyName, "propertyName not supplied");
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        return nodeServiceProvider.get().remoteRestResult(
                nodeName,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        GlobalConfigResource.BASE_PATH,
                        GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                        propertyName,
                        GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                        nodeName),
                () ->
                        getYamlValueByName(propertyName),
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
                                        () -> globalConfigService.fetch(configProperty.getId())
                                                .orElse(null),
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
        // Temporary code to fix url paths.
        if (this.uriFactory != null) {
            final UrlConfig urlConfig = new UrlConfig(
                    this.uriFactory.get().uiUri(AuthenticationService.USERS_URL_PATH).toString(),
                    this.uriFactory.get().uiUri(AuthenticationService.API_KEYS_URL_PATH).toString(),
                    this.uriFactory.get().uiUri(AuthenticationService.CHANGE_PASSWORD_URL_PATH).toString());
            uiConfig.get().setUrl(urlConfig);
        }

        return uiConfig.get();
    }
}
