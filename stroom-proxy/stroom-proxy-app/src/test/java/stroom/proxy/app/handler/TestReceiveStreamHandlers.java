package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.forwarder.ForwardDestinationConfig;
import stroom.proxy.app.forwarder.ForwardStreamHandler;
import stroom.proxy.app.forwarder.ForwardStreamHandlersImpl;
import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.repo.ForwardStreamHandlers;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoSources;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.ProxyRepositoryStreamHandlers;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.shared.BuildInfo;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReceiveStreamHandlers extends StroomUnitTest {

    @Test
    void testStoreAndForward() {
        final ReceiveStreamHandlers streamHandlers =
                getProxyHandlerFactory(
                        true,
                        true,
                        List.of("https://url1", "https://url2"));
        streamHandlers.handle(new AttributeMap(), handler ->
                assertThat(handler instanceof ProxyRepositoryStreamHandler).as(
                        "Expecting a handler that stores").isTrue());
    }

    @Test
    void testForwardOnlySingle() {
        assertThatThrownBy(() -> {
                    final ReceiveStreamHandlers streamHandlers =
                            getProxyHandlerFactory(
                                    false,
                                    true,
                                    List.of("https://url1"));
                    streamHandlers.handle(new AttributeMap(), handler ->
                            assertThat(handler instanceof ForwardStreamHandler).as(
                                    "Expecting a handler that forward to other URLS").isTrue());
                },
                "Expected unreachable host exception")
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void testForwardOnlyMulti() {
        assertThatThrownBy(() -> {
                    final ReceiveStreamHandlers streamHandlers =
                            getProxyHandlerFactory(
                                    false,
                                    true,
                                    List.of("https://url1", "https://url2"));
                    streamHandlers.handle(new AttributeMap(), handler ->
                            assertThat(handler instanceof ForwardStreamHandler).as(
                                    "Expecting a handler that forward to other URLS").isTrue());
                },
                "Expected error trying to forward to multiple destinations without storing.")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testStore() {
        final ReceiveStreamHandlers streamHandlers =
                getProxyHandlerFactory(
                        true,
                        false,
                        List.of("https://url1", "https://url2"));
        streamHandlers.handle(new AttributeMap(), handler ->
                assertThat(handler instanceof ProxyRepositoryStreamHandler).as(
                        "Expecting a handler that stores").isTrue());
    }

    private ReceiveStreamHandlers getProxyHandlerFactory(final boolean isStoringEnabled,
                                                         final boolean isForwardingEnabled,
                                                         final List<String> forwardUrlList) {
        final LogStreamConfig logRequestConfig = new LogStreamConfig();
        final ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
        final ForwarderConfig forwarderConfig = new ForwarderConfig();

        proxyRepoConfig.setRepoDir(FileUtil.getCanonicalPath(getCurrentTestDir()));
        proxyRepoConfig.setStoringEnabled(isStoringEnabled);
        forwarderConfig.setForwardingEnabled(isForwardingEnabled);

        for (final String url : forwardUrlList) {
            ForwardDestinationConfig destinationConfig = new ForwardDestinationConfig();
            destinationConfig.setForwardUrl(url);
            forwarderConfig.getForwardDestinations().add(destinationConfig);
        }

        final ProxyRepoSources proxyRepoSources = new ProxyRepoSources(null);
        final ProxyRepo proxyRepo = new ProxyRepo(
                proxyRepoConfig.getRepoDir(),
                proxyRepoConfig.getFormat(),
                proxyRepoSources,
                0L,
                0L);
        final ProxyRepositoryStreamHandlers proxyRepositoryRequestHandlerProvider =
                new ProxyRepositoryStreamHandlers(proxyRepo);

        final LogStream logStream = new LogStream(logRequestConfig);

        final BuildInfo buildInfo = new BuildInfo("now", "test version", "now");

        final ForwardStreamHandlers forwardStreamHandlers = new ForwardStreamHandlersImpl(
                logStream,
                forwarderConfig,
                proxyRepoConfig,
                () -> buildInfo);

        return new ReceiveStreamHandlers(
                proxyRepoConfig,
                proxyRepositoryRequestHandlerProvider,
                forwardStreamHandlers,
                forwarderConfig);
    }
}
