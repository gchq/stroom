package stroom.config.global.impl;

import stroom.config.common.UriFactory;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeService;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.AutoLogged;
import stroom.util.shared.AutoLogged.OperationType;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.core.GenericType;
import java.util.Objects;
import java.util.Optional;

@AutoLogged
public class GlobalConfigResourceImpl implements GlobalConfigResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigResourceImpl.class);

    private final Provider<GlobalConfigService> globalConfigServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final UiConfig uiConfig;
    private final UriFactory uriFactory;

    @Inject
    GlobalConfigResourceImpl(final Provider<GlobalConfigService> globalConfigServiceProvider,
                             final Provider<NodeService> nodeServiceProvider,
                             final UiConfig uiConfig,
                             final UriFactory uriFactory) {

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
        return nodeServiceProvider.get().remoteRestCall(
                nodeName,
                ListConfigResponse.class,
                ResourcePaths.buildAuthenticatedApiPath(
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

        return nodeServiceProvider.get().remoteRestCall(
                nodeName,
                ResourcePaths.buildAuthenticatedApiPath(
                        GlobalConfigResource.BASE_PATH,
                        GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                        propertyName,
                        GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                        nodeName),
                () ->
                        getYamlValueByName(propertyName),
                SyncInvoker::get,
                response -> response.readEntity(new GenericType<OverrideValue<String>>() {}));
    }

    @Timed
    @Override
    public ConfigProperty create(final ConfigProperty configProperty) {
        RestUtil.requireNonNull(configProperty, "configProperty not supplied");
        RestUtil.requireNonNull(configProperty.getName(), "configProperty name cannot be null");

        try {
            return globalConfigServiceProvider.get().update(configProperty);
        } catch (ConfigPropertyValidationException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Timed
    @Override
    public ConfigProperty update(final String propertyName, final ConfigProperty configProperty) {
        RestUtil.requireNonNull(propertyName, "propertyName not supplied");
        RestUtil.requireNonNull(configProperty, "configProperty not supplied");

        if (!propertyName.equals(configProperty.getNameAsString())) {
            throw new BadRequestException(LogUtil.message("Property names don't match, {} & {}",
                    propertyName, configProperty.getNameAsString()));
        }

        try {
            return globalConfigServiceProvider.get().update(configProperty);
        } catch (ConfigPropertyValidationException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Timed
    @Override
    public UiConfig fetchUiConfig() {
        // Temporary code to fix url paths.
        if (this.uriFactory != null) {
            final UrlConfig urlConfig = new UrlConfig(
                    this.uriFactory.uiUri(AuthenticationService.USERS_URL_PATH).toString(),
                    this.uriFactory.uiUri(AuthenticationService.API_KEYS_URL_PATH).toString(),
                    this.uriFactory.uiUri(AuthenticationService.CHANGE_PASSWORD_URL_PATH).toString());
            uiConfig.setUrl(urlConfig);
        }

        return uiConfig;
    }
}
