package stroom.config.global.impl;

import stroom.authentication.authenticate.api.AuthenticationService;
import stroom.config.common.UriFactory;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import com.codahale.metrics.annotation.Timed;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Objects;
import java.util.Optional;

public class GlobalConfigResourceImpl implements GlobalConfigResource {


    private final GlobalConfigService globalConfigService;
    private final NodeService nodeService;
    private final UiConfig uiConfig;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final UriFactory uriFactory;

    @Inject
    GlobalConfigResourceImpl(final GlobalConfigService globalConfigService,
                             final NodeService nodeService,
                             final UiConfig uiConfig,
                             final NodeInfo nodeInfo,
                             final WebTargetFactory webTargetFactory,
                             final UriFactory uriFactory) {
        this.globalConfigService = Objects.requireNonNull(globalConfigService);
        this.nodeService = Objects.requireNonNull(nodeService);
        this.uiConfig = uiConfig;
        this.nodeInfo = Objects.requireNonNull(nodeInfo);
        this.webTargetFactory = webTargetFactory;
        this.uriFactory = uriFactory;
    }

    @Timed
    @Override
    public ListConfigResponse list(final GlobalConfigCriteria criteria) {
        return globalConfigService.list(criteria);
    }

    @Timed
    @Override
    public ListConfigResponse listByNode(final String nodeName,
                                         final GlobalConfigCriteria criteria) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        ListConfigResponse listConfigResponse;

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            listConfigResponse = list(criteria);

        } else {
            // A different node to make a rest call to the required node
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    GlobalConfigResource.BASE_PATH,
                    GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                    nodeName);
            try {
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(criteria));

                if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                listConfigResponse = response.readEntity(ListConfigResponse.class);

                Objects.requireNonNull(listConfigResponse, "Null listConfigResponse");
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return listConfigResponse;
    }

    @Timed
    @Override
    public ConfigProperty getPropertyByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(
                PropertyPath.fromPathString(propertyPath));
        return optConfigProperty.orElseThrow(NotFoundException::new);
    }

    @Timed
    public OverrideValue<String> getYamlValueByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(
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

        OverrideValue<String> yamlOverride;

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            yamlOverride = getYamlValueByName(propertyName);

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    GlobalConfigResource.BASE_PATH,
                    GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                    propertyName,
                    GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                    nodeName);
            try {
                // A different node to make a rest call to the required node
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(LogUtil.message("Property {} not found", propertyName));
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                yamlOverride = response.readEntity(OverrideValue.class);

                Objects.requireNonNull(yamlOverride, "Null yamlOverride");
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return yamlOverride;
    }

    @Timed
    @Override
    public ConfigProperty create(final ConfigProperty configProperty) {
        RestUtil.requireNonNull(configProperty, "configProperty not supplied");
        RestUtil.requireNonNull(configProperty.getName(), "configProperty name cannot be null");

        try {
            return globalConfigService.update(configProperty);
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
            return globalConfigService.update(configProperty);
        } catch (ConfigPropertyValidationException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @Timed
    @Override
    public UiConfig fetchUiConfig() {
        // Temporary code to fix url paths.
        if (uriFactory != null) {
            final UrlConfig urlConfig = new UrlConfig(
                    uriFactory.uiUri(AuthenticationService.USERS_URL_PATH).toString(),
                    uriFactory.uiUri(AuthenticationService.API_KEYS_URL_PATH).toString(),
                    uriFactory.uiUri(AuthenticationService.CHANGE_PASSWORD_URL_PATH).toString());
            uiConfig.setUrl(urlConfig);
        }

        return uiConfig;
    }
}
