package stroom.test.common.util.test;

import org.assertj.core.api.Assertions;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractMultiNodeResourceTest<R extends RestResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMultiNodeResourceTest.class);

    private static final List<TestNode> DEFAULT_NODES = List.of(
        new TestNode("node1", 8080, true),
        new TestNode("node2", 8081, true),
        new TestNode("node3", 8082, false));

    private static final WebTargetFactory WEB_TARGET_FACTORY = url -> ClientBuilder.newClient(
        new ClientConfig().register(LoggingFeature.class))
        .target(url);

    private static final String CONTAINER_FACTORY = "org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory";

    private final List<TestNode> testNodes;
    private final Map<String, JerseyTest> nodeToJerseyTestMap = new HashMap<>();
    private final Map<String, RequestListener> nodeToListenerMap = new HashMap<>();

    /**
     * Uses a 3 node cluster with node3 being disabled. Node1 is the default node.
     */
    protected AbstractMultiNodeResourceTest() {
        this.testNodes = DEFAULT_NODES;
    }

    /**
     * Uses the supplied nodes for testing.
     */
    @SuppressWarnings("unused")
    protected AbstractMultiNodeResourceTest(final List<TestNode> testNodes) {

        // Force the container factory to ensure the jersey-test-framework-provider-grizzly2
        // dependency is in place. Without forcing it, it will just try to use whatever is there
        // which may be the in memory one which won't work for multi node.
        // Tried using the jetty container factory but there was as dependency version mismatch
        // that causes a method not found error.
        System.setProperty(TestProperties.CONTAINER_FACTORY, CONTAINER_FACTORY);

        try {
            Class.forName(CONTAINER_FACTORY);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("You are missing a test runtime dependency for " +
                "jersey-test-framework-provider-grizzly2");
        }

        this.testNodes = testNodes;
    }

    public abstract String getResourceBasePath();

    public abstract R getRestResource(final TestNode node,
                                      final List<TestNode> allNodes,
                                      final Map<String, String> baseEndPointUrls);

    private String getFullResourcePath() {
        return ResourcePaths.buildAuthenticatedApiPath(getResourceBasePath());
    }

    private String getBaseEndPointUrl(final TestNode node) {
        return "http://localhost:" + node.getPort();
    }

    private Map<String, String> getBaseEndPointUrls() {
        return testNodes.stream()
            .collect(Collectors.toMap(
                TestNode::getNodeName,
                this::getBaseEndPointUrl));
    }

    public void stopNodes() {
        nodeToJerseyTestMap.values().forEach(jerseyTest -> {
            try {
                jerseyTest.tearDown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @AfterEach
    void afterEach() {
        stopNodes();
        nodeToJerseyTestMap.clear();
        nodeToListenerMap.clear();
    }

    protected void initNodes() {

        testNodes.forEach(node -> {

            final String baseEndPointUrl = getBaseEndPointUrl(node);

            RequestListener requestListener = new RequestListener(node);
            nodeToListenerMap.put(node.getNodeName(), requestListener);

            final JerseyTest jerseyTest = new JerseyTestBuilder<>(
                () -> getRestResource(node, testNodes, getBaseEndPointUrls()),
                node.getPort(),
                requestListener)
                .build();

            nodeToJerseyTestMap.put(node.getNodeName(), jerseyTest);

            try {
                LOGGER.info("Starting node [{}] (enabled: {}) at {}",
                    node.getNodeName(), node.isEnabled, baseEndPointUrl);
                jerseyTest.setUp();
            } catch (Exception e) {
                throw new RuntimeException("Error starting jersey test on " + baseEndPointUrl);
            }
        });
    }

    /**
     * Override if you want to use more nodes or different ports
     */
    public List<TestNode> getTestNodes() {
        return DEFAULT_NODES;
    }

    public List<RequestEvent> getRequestEvents(final String nodeName) {
        return nodeToListenerMap.get(nodeName).getRequestLog();
    }

    /**
     * @return The JerseyTest instance for the first node
     */
    public JerseyTest getJerseyTest() {
        return nodeToJerseyTestMap.get(testNodes.get(0).getNodeName());
    }

    /**
     * @return The JerseyTest instance for the first node
     */
    public JerseyTest getJerseyTest(final String nodeName) {
        return nodeToJerseyTestMap.get(nodeName);
    }

    public static WebTargetFactory webTargetFactory() {
        return WEB_TARGET_FACTORY;
    }

    public  <T> T doGetTest(final String subPath,
                            final Class<T> responseType,
                            final T expectedResponse) {

        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);
        T response =  getJerseyTest()
            .target(getResourceBasePath())
            .path(subPath)
            .request()
            .get(responseType);

        Assertions.assertThat(response).isEqualTo(expectedResponse);

        return response;
    }

    public  <T> T doGetTest(final String subPath,
                            final Class<T> responseType,
                            final T expectedResponse,
                            final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = getJerseyTest()
            .target(getResourceBasePath())
            .path(subPath);

        for (Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        T response =  webTarget
            .request()
            .get(responseType);

        Assertions.assertThat(response).isEqualTo(expectedResponse);

        return response;
    }

    public  <T> T doDeleteTest(final String subPath,
                               final Class<T> responseType,
                               final T expectedResponse,
                               final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = getJerseyTest()
            .target(getResourceBasePath())
            .path(subPath);

        for (Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        T response =  webTarget
            .request()
            .delete(responseType);

        Assertions.assertThat(response).isEqualTo(expectedResponse);

        return response;
    }

    public WebTarget getWebTarget(final String subPath) {

        return getJerseyTest()
            .target(getFullResourcePath())
            .path(subPath);
    }

    private static class JerseyTestBuilder<R extends RestResource> {

        private final Supplier<R> resourceSupplier;
        private final int port;
        private final ApplicationEventListener listener;

        public JerseyTestBuilder(final Supplier<R> resourceSupplier,
                                 final int port,
                                 final ApplicationEventListener listener) {
            this.resourceSupplier = resourceSupplier;
            this.port = port;
            this.listener = listener;
        }

        public JerseyTest build() {
            return new JerseyTest() {

                @Override
                protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
                    return super.getTestContainerFactory();

                }

                @Override
                protected Application configure() {
                    return new ResourceConfig()
                        .register(resourceSupplier.get())
                        .register(listener)
                        .register(
                            new LoggingFeature(
                                java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                                Level.INFO,
                                LoggingFeature.Verbosity.PAYLOAD_ANY,
                                LoggingFeature.DEFAULT_MAX_ENTITY_SIZE));
                }

                @Override
                protected URI getBaseUri() {
                    return UriBuilder
                        .fromUri("http://localhost")
                        .port(port)
                        .path(ResourcePaths.API_ROOT_PATH)
                        .build();
                }
            };
        }
    }

    public static class TestNode {
        private final String nodeName;
        private final int port;
        private final boolean isEnabled;

        public TestNode(final String nodeName, final int port, final boolean isEnabled) {
            this.nodeName = nodeName;
            this.port = port;
            this.isEnabled = isEnabled;
        }

        public String getNodeName() {
            return nodeName;
        }

        public int getPort() {
            return port;
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final TestNode testNode = (TestNode) o;
            return port == testNode.port &&
                isEnabled == testNode.isEnabled &&
                Objects.equals(nodeName, testNode.nodeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeName, port, isEnabled);
        }

        @Override
        public String toString() {
            return "TestNode{" +
                "nodeName='" + nodeName + '\'' +
                ", port=" + port +
                ", isEnabled=" + isEnabled +
                '}';
        }
    }

    public static class RequestListener implements ApplicationEventListener {

        private final List<RequestEvent> requestLog = new ArrayList<>();
        private final TestNode node;

        RequestListener(final TestNode node) {
            this.node = node;
        }

        @Override
        public void onEvent(final ApplicationEvent event) {
            LOGGER.info("ApplicationEvent on node {}", node.getNodeName());
        }

        @Override
        public RequestEventListener onRequest(final RequestEvent requestEvent) {
            LOGGER.info("{} to {} request received on node {} ",
                requestEvent.getType(), requestEvent.getUriInfo().getPath(), node.getNodeName());

            requestLog.add(requestEvent);
            return null;
        }

        public List<RequestEvent> getRequestLog() {
            return requestLog;
        }
    }
}