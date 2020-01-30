package stroom.config.global.impl;

import com.codahale.metrics.health.HealthCheck;
import kotlin.reflect.KProperty1;
import stroom.config.global.shared.ClusterConfigProperty;
import stroom.config.global.shared.ConfigProperty;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.util.HasHealthCheck;
import stroom.util.guice.ResourcePaths;
import stroom.util.jersey.WebTargetFactory;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GlobalConfigResourceImpl implements GlobalConfigResource, HasHealthCheck {
    private final GlobalConfigService globalConfigService;
    private final SecurityContext securityContext;
    private final NodeService nodeService;
    private final ExecutorProvider executorProvider;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;

    @Inject
    GlobalConfigResourceImpl(final GlobalConfigService globalConfigService,
                             final SecurityContext securityContext,
                             final NodeService nodeService,
                             final ExecutorProvider executorProvider,
                             final NodeInfo nodeInfo,
                             final WebTargetFactory webTargetFactory) {
        this.globalConfigService = Objects.requireNonNull(globalConfigService);
        this.securityContext = Objects.requireNonNull(securityContext);
        this.nodeService = Objects.requireNonNull(nodeService);
        this.executorProvider = Objects.requireNonNull(executorProvider);
        this.nodeInfo = Objects.requireNonNull(nodeInfo);
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public List<ConfigProperty> getAllConfig() {
        return securityContext.secureResult(() -> {
            try {
                return globalConfigService.list();
            } catch (final RuntimeException e) {
                throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    @Override
    public ConfigProperty getPropertyByName(final String propertyName) {
        return securityContext.secureResult(() -> {
            try {
                final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);
                return optConfigProperty.orElseThrow(NotFoundException::new);
            } catch (final RuntimeException e) {
                throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    @Override
    public String getYamlValueByName(final String propertyName) {
        return securityContext.secureResult(() -> {
            try {
                final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);
                return optConfigProperty
                    .flatMap(configProperty ->
                        configProperty.getYamlOverrideValue().getValue())
                    .orElseThrow(NotFoundException::new);
            } catch (final RuntimeException e) {
                throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    @Override
    public String getYamlValueByNodeAndName(final String propertyName,
                                            final String nodeName) {
        String yamlValue;
        String path = ResourcePaths.buildPath(
                GlobalConfigResource.BASE_PATH,
                GlobalConfigResource.PROPERTIES_SUB_PATH,
                propertyName,
                GlobalConfigResource.YAML_VALUE_SUB_PATH);
        try {
            final long now = System.currentTimeMillis();

            // If this is the node that was contacted then just return the latency we have incurred within this method.
            if (NodeCallUtil.executeLocally(nodeService, nodeInfo, nodeName)) {
                yamlValue = getYamlValueByName(propertyName);
            } else {
                String url = NodeCallUtil.getUrl(nodeService, nodeName);
                url += path;
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                final String ping = response.readEntity(Long.class);
                Objects.requireNonNull(ping, "Null ping");
                return System.currentTimeMillis() - now;
            }

        } catch (final WebApplicationException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
//            throw new ServerErrorException(e.getMessage(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
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

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }

//    private CompletableFuture<ClusterConfigProperty> getPropertyFromNode(final String nodeName) {
//
//        // TODO if we get an error we should return a ClusterConfigProperty with a YAML override value of
//        // ERROR or UNKNOWN or similar.
//        return CompletableFuture.supplyAsync(() -> {
//
//            ClusterConfigProperty clusterConfigProperty;
//            try {
//
//            } catch (Throwable e) {
//                return new ClusterConfigProperty()
//            }
//        }, executorProvider.getExecutor());
//    }
}
