package stroom.config.global.impl;

import com.codahale.metrics.health.HealthCheck;
import kotlin.reflect.KProperty1;
import stroom.config.global.shared.ClusterConfigProperty;
import stroom.config.global.shared.ConfigProperty;
import stroom.node.api.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
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

    @Inject
    GlobalConfigResourceImpl(final GlobalConfigService globalConfigService,
                             final SecurityContext securityContext,
                             final NodeService nodeService,
                             final ExecutorProvider executorProvider) {
        this.globalConfigService = Objects.requireNonNull(globalConfigService);
        this.securityContext = Objects.requireNonNull(securityContext);
        this.nodeService = Objects.requireNonNull(nodeService);
        this.executorProvider = Objects.requireNonNull(executorProvider);
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
    public ClusterConfigProperty getClusterPropertyByName(final String propertyName) {
        return securityContext.secureResult(() -> {
            List<CompletableFuture<ConfigProperty>> futures = nodeService.findNodeNames(new FindNodeCriteria())
                .stream()
                .map(this::getPropertyFromNode)
                .collect(Collectors.toList());

            final ClusterConfigProperty clusterConfigProperty = futures.stream()
                .map(CompletableFuture::join)
                .map(ClusterConfigProperty::new)
                .reduce(ClusterConfigProperty::merge)
                .collect(Collectors.toList());

            allNodeNames.forEach(nodeName -> {
            });


        });
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }

    private CompletableFuture<ConfigProperty> getPropertyFromNode(final String nodeName) {
        // TODO
        return null;
    }
}
