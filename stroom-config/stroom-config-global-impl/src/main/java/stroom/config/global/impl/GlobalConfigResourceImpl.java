package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UiPreferences;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import com.codahale.metrics.annotation.Timed;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Objects;
import java.util.Optional;

public class GlobalConfigResourceImpl implements GlobalConfigResource {

    private static final FilterFieldMappers<ConfigProperty> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_NAME,
                    ConfigProperty::getNameAsString),
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_EFFECTIVE_VALUE,
                    configProperty -> configProperty.getEffectiveValue().orElse("")),
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_SOURCE,
                    configProperty -> configProperty.getSource().getName()),
            FilterFieldMapper.of(
                    GlobalConfigResource.FIELD_DEF_DESCRIPTION,
                    ConfigProperty::getDescription));

    private final GlobalConfigService globalConfigService;
    private final NodeService nodeService;
    private final UiConfig uiConfig;
    private final UiPreferences uiPreferences;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;

    @Inject
    GlobalConfigResourceImpl(final GlobalConfigService globalConfigService,
                             final NodeService nodeService,
                             final UiConfig uiConfig,
                             final UiPreferences uiPreferences,
                             final NodeInfo nodeInfo,
                             final WebTargetFactory webTargetFactory) {
        this.globalConfigService = Objects.requireNonNull(globalConfigService);
        this.nodeService = Objects.requireNonNull(nodeService);
        this.uiConfig = uiConfig;
        this.uiPreferences = uiPreferences;
        this.nodeInfo = Objects.requireNonNull(nodeInfo);
        this.webTargetFactory = webTargetFactory;
    }

    @Timed
    @Override
    public ListConfigResponse list(final String partialName,
                                   final long offset,
                                   final Integer size) {
        final ListConfigResponse resultList = globalConfigService.list(
                QuickFilterPredicateFactory.createFuzzyMatchPredicate(partialName, FIELD_MAPPERS),
                new PageRequest(offset, size != null
                        ? size
                        : Integer.MAX_VALUE));

        return resultList;
    }

    @Timed
    @Override
    public ListConfigResponse listByNode(final String nodeName,
                                         final String partialName,
                                         final long offset,
                                         final Integer size) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        ListConfigResponse listConfigResponse;

        final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                GlobalConfigResource.BASE_PATH,
                GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                nodeName);

        try {
            // If this is the node that was contacted then just resolve it locally
            if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                listConfigResponse = list(partialName, offset, size);
            } else {
                // A different node to make a rest call to the required node

                final Response response = webTargetFactory
                        .create(url)
                        .queryParam("partialName", partialName)
                        .queryParam("offset", String.valueOf(offset))
                        .queryParam("size", String.valueOf(size))
                        .request(MediaType.APPLICATION_JSON)
                        .get();

                if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                listConfigResponse = response.readEntity(ListConfigResponse.class);

                Objects.requireNonNull(listConfigResponse, "Null listConfigResponse");
            }
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
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

        final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                GlobalConfigResource.BASE_PATH,
                GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                propertyName,
                GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                nodeName);

        try {
            // If this is the node that was contacted then just resolve it locally
            if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                yamlOverride = getYamlValueByName(propertyName);
            } else {
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
            }
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
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
        return uiConfig;
    }

    @Override
    public UiPreferences uiPreferences() {
        return uiPreferences;
    }
}
