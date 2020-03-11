package stroom.config.global.impl;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.ui.config.shared.UiConfig;
import stroom.util.HasHealthCheck;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class GlobalConfigResourceImpl implements GlobalConfigResource, HasHealthCheck {
    private final GlobalConfigService globalConfigService;
    private final SecurityContext securityContext;
    private final NodeService nodeService;
    private final UiConfig uiConfig;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;

    @Inject
    GlobalConfigResourceImpl(final GlobalConfigService globalConfigService,
                             final SecurityContext securityContext,
                             final NodeService nodeService,
                             final UiConfig uiConfig,
                             final NodeInfo nodeInfo,
                             final WebTargetFactory webTargetFactory) {
        this.globalConfigService = Objects.requireNonNull(globalConfigService);
        this.securityContext = Objects.requireNonNull(securityContext);
        this.nodeService = Objects.requireNonNull(nodeService);
        this.uiConfig = uiConfig;
        this.nodeInfo = Objects.requireNonNull(nodeInfo);
        this.webTargetFactory = webTargetFactory;
    }

    @Timed
    @Override
    public ListConfigResponse list(final String partialName,
                                   final long offset,
                                   final Integer size) {
        return securityContext.secureResult(() -> {
            try {
                final ListConfigResponse resultList = globalConfigService.list(
                    buildPredicate(partialName),
                    new PageRequest(offset, size != null ? size : Integer.MAX_VALUE));

                return resultList;
            } catch (final RuntimeException e) {
                throw new ServerErrorException(e.getMessage() != null
                        ? e.getMessage()
                        : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    @Timed
    @Override
    public ListConfigResponse listByNode(final String nodeName,
                                         final String partialName,
                                         final long offset,
                                         final Integer size) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        ListConfigResponse listConfigResponse;

        final String resourcePath = ResourcePaths.buildAuthenticatedApiPath(
            GlobalConfigResource.BASE_PATH, GlobalConfigResource.PROPERTIES_SUB_PATH);

        try {
            // If this is the node that was contacted then just resolve it locally
            if (NodeCallUtil.executeLocally(nodeInfo, nodeName)) {
                listConfigResponse = list(partialName, offset, size);
            } else {
                // A different node to make a rest call to the required node
                String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName);
                url += resourcePath;
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
        } catch (final WebApplicationException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
        return listConfigResponse;
    }

    private Predicate<ConfigProperty> buildPredicate(final String partialName) {
        if (partialName != null && !partialName.isEmpty()) {
            return configProperty ->
                configProperty.getNameAsString().toLowerCase().contains(partialName.toLowerCase());
        } else {
            return configProperty -> true;
        }
    }

    @Timed
    @Override
    public ConfigProperty getPropertyByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        return securityContext.secureResult(() -> {
            try {
                final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(
                        PropertyPath.fromPathString(propertyPath));
                return optConfigProperty.orElseThrow(NotFoundException::new);
            } catch (final RuntimeException e) {
                throw new ServerErrorException(e.getMessage() != null
                        ? e.getMessage()
                        : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    @Timed
    @Override
    public OverrideValue<String> getYamlValueByName(final String propertyPath) {
        RestUtil.requireNonNull(propertyPath, "propertyPath not supplied");
        return securityContext.secureResult(() -> {
            try {
                final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(
                        PropertyPath.fromPathString(propertyPath));
                return optConfigProperty
                        .map(ConfigProperty::getYamlOverrideValue)
                        .orElseThrow(() -> new NotFoundException(LogUtil.message("Property {} not found", propertyPath)));
            } catch (final RuntimeException e) {
                throw new ServerErrorException(e.getMessage() != null
                        ? e.getMessage()
                        : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    @Timed
    @Override
    public OverrideValue<String> getYamlValueByNodeAndName(final String propertyName,
                                                           final String nodeName) {
        RestUtil.requireNonNull(propertyName, "propertyName not supplied");
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        OverrideValue<String> yamlOverride;

        final String resourcePath = ResourcePaths.buildAuthenticatedApiPath(
            GlobalConfigResource.BASE_PATH,
            GlobalConfigResource.PROPERTIES_SUB_PATH,
            propertyName,
            GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH);
        try {
            // If this is the node that was contacted then just resolve it locally
            if (NodeCallUtil.executeLocally(nodeInfo, nodeName)) {
                yamlOverride = getYamlValueByName(propertyName);
            } else {
                // A different node to make a rest call to the required node
                String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName);
                url += resourcePath;
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
        } catch (final WebApplicationException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
        return yamlOverride;
    }

//    @Timed
//    @Override
//    public ResultPage<ConfigProperty> find(final FindGlobalConfigCriteria criteria) {
//        return ResultPage.createPageLimitedList(globalConfigService.list(criteria), criteria.obtainPageRequest());
//    }

    @Timed
    @Override
    public ConfigProperty create(final ConfigProperty configProperty) {
        RestUtil.requireNonNull(configProperty, "configProperty not supplied");
        RestUtil.requireNonNull(configProperty.getName(), "configProperty name cannot be null");

        try {
            return globalConfigService.update(configProperty);
        } catch (ConfigPropertyValidationException e) {
            throw new BadRequestException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
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
        } catch (RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    //    @Override
//    public ClusterConfigProperty getClusterPropertyByName(final String propertyName) {
//        return securityContext.secureResult(() -> {
//            // For each node, get the property value for that node, asynchronously
//            final List<CompletableFuture<ClusterConfigProperty>> futures = nodeService
//                    .findNodeNames(new FindNodeCriteria())
//                    .stream()
//                    .map(this::getPropertyFromNode)
//                    .collect(Collectors.toList());
//
//            // TODO Need to deal with failure to connect to a node
//            // TODO Need to apply a timeout and mark the node as NOT KNOWN in some way
//            // As the results come in from the nodes, merge them together into one object
//            final ClusterConfigProperty clusterConfigProperty = futures.stream()
//                .map(CompletableFuture::join)
//                .reduce(ClusterConfigProperty::merge)
//                    .orElse(null);
//
//            return clusterConfigProperty;
//        });
//    }


    @Timed
    @Override
    public UiConfig fetchUiConfig() {
        return uiConfig;
    }

    @Timed
    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}
