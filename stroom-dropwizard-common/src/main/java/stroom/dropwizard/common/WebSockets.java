package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.HasHealthCheck;
import stroom.util.guice.WebSocketBinder.WebSocketType;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsWebSocket;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.metrics.jetty9.websockets.InstWebSocketServerContainerInitializer;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

public class WebSockets {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSockets.class);

    private static final String WS_PATH_KEY = "webSocketPath";

    private final Environment environment;
    private final Map<WebSocketType, Provider<IsWebSocket>> providerMap;
    private ServerContainer wsContainer;

    @Inject
    WebSockets(final Environment environment,
               final Map<WebSocketType, Provider<IsWebSocket>> providerMap) {
        this.environment = environment;
        this.providerMap = providerMap;
    }

    public void register() {
        environment.lifecycle().addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarting(final LifeCycle event) {
                start();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void start() {
        try {
            wsContainer =
                    InstWebSocketServerContainerInitializer.configureContext(
                            environment.getApplicationContext(),
                            environment.metrics());

            LOGGER.debug("defaultMaxSessionIdleTimeout (ms): {}", wsContainer.getDefaultMaxSessionIdleTimeout());
            LOGGER.debug("defaultAsyncSendTimeout (ms): {}", wsContainer.getDefaultAsyncSendTimeout());
            LOGGER.info("Adding web socket endpoints:");

            final Set<String> allPaths = new HashSet<>();

            final int maxNameLength = providerMap
                    .keySet()
                    .stream()
                    .mapToInt(type -> type.getWebSocketClass().getName().length())
                    .max()
                    .orElse(0);

            final List<WebSocketProvider> providers = providerMap
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        final Class<?> webSocketClass = entry.getKey().getWebSocketClass();
                        final Provider<IsWebSocket> provider = entry.getValue();
                        return new WebSocketProvider(
                                webSocketClass,
                                provider);
                    })
                    .sorted(Comparator.comparing(WebSocketProvider::getPath))
                    .filter(provider -> filter(maxNameLength, allPaths, provider))
                    .toList();

            providers.forEach(provider -> {
                final ServerEndpointConfig.Builder builder = ServerEndpointConfig
                        .Builder
                        .create(provider.getWebSocketClass(), provider.getPath());

                // We have tyo use a custom configurator to allow Guice to create the endpoint instance.
                builder.configurator(new Configurator() {
                    @Override
                    public <T> T getEndpointInstance(final Class<T> endpointClass) {
                        if (!endpointClass.equals(provider.webSocketClass)) {
                            throw new RuntimeException("Unexpected class requested");
                        }
                        return (T) provider.getProvider().get();
                    }
                });

                try {
                    wsContainer.addEndpoint(builder.build());
                } catch (final DeploymentException e) {
                    throw new RuntimeException(LogUtil.message("Error deploying WS endpoint {}",
                            provider.getName()));
                }
                registerHealthCheck(provider);
            });
        } catch (final ServletException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean filter(final int maxNameLength,
                           final Set<String> allPaths,
                           final WebSocketProvider provider) {
        final String name = provider.getWebSocketClass().getName();
        if (allPaths.contains(provider.getPath())) {
            LOGGER.error("\t{} => {}   {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    provider.getPath(),
                    ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message(
                    "Duplicate Web Socket path {}",
                    provider.getPath()));
        } else {
            LOGGER.info("\t{} => {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    provider.getPath());
        }

        allPaths.add(provider.getPath());
        return true;
    }

    private void registerHealthCheck(final WebSocketProvider provider) {

        final HealthCheckRegistry healthCheckRegistry = environment.healthChecks();

        if (HasHealthCheck.class.isAssignableFrom(provider.getWebSocketClass())) {
            LOGGER.info("Adding health check for web socket {}", provider.getName());
            // object has a getHealth method so build a HealthCheck that wraps it and
            // adds in the web socket path information
            healthCheckRegistry.register(provider.getName(), new HealthCheck() {
                @Override
                protected Result check() {
                    // Decorate the existing health check results with the full path spec
                    // as the web socket doesn't know its own full path
                    Result result = ((HasHealthCheck) provider.getProvider().get()).getHealth();

                    ResultBuilder builder = Result.builder();
                    if (result.isHealthy()) {
                        builder
                                .healthy()
                                .withMessage(result.getMessage())
                                .withDetail(WS_PATH_KEY, provider.getPath())
                                .build();
                    } else {
                        builder
                                .unhealthy(result.getError())
                                .withMessage(result.getMessage())
                                .withDetail(WS_PATH_KEY, provider.getPath())
                                .build();
                    }
                    builder
                            .withMessage(result.getMessage())
                            .withDetail(WS_PATH_KEY, provider.getPath());

                    if (result.getDetails() != null) {
                        if (result.getDetails().containsKey(WS_PATH_KEY)) {
                            LOGGER.warn("Overriding health check detail for {} {} in web socket {}",
                                    WS_PATH_KEY,
                                    result.getDetails().get(WS_PATH_KEY),
                                    provider.getName());
                        }
                        result.getDetails().forEach(builder::withDetail);
                    }

                    result = builder.build();

                    return result;
                }
            });
        }
    }

    private static class WebSocketProvider {

        private final Class<?> webSocketClass;
        private final Provider<IsWebSocket> provider;
        private final String name;
        private final String path;

        public WebSocketProvider(final Class<?> webSocketClass,
                                 final Provider<IsWebSocket> provider) {
            this.webSocketClass = webSocketClass;
            this.provider = provider;
            this.name = webSocketClass.getName();

            final ServerEndpoint annotation = webSocketClass.getAnnotation(ServerEndpoint.class);
            if (annotation == null) {
                throw new RuntimeException(webSocketClass.getCanonicalName() +
                        " does not have a " +
                        ServerEndpoint.class.getCanonicalName() +
                        " annotation");
            }
            this.path = annotation.value();
        }

        public Class<?> getWebSocketClass() {
            return webSocketClass;
        }

        public Provider<IsWebSocket> getProvider() {
            return provider;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }
    }
}
