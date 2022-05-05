package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.forwarder.ForwardHttpPostConfig;
import stroom.proxy.app.forwarder.ForwardHttpPostHandlersFactory;
import stroom.proxy.app.forwarder.ForwardStreamHandler;
import stroom.proxy.app.forwarder.ForwarderDestinationsImpl;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.ProxyRepositoryStreamHandlers;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.test.common.TemporaryPathCreator;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.BuildInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestReceiveStreamHandlers extends StroomUnitTest {

    @Mock
    private SequentialFileStore sequentialFileStore;

    @Test
    void testStoreAndForward(@TempDir Path tempDir) {
        final ReceiveStreamHandlers streamHandlers =
                getProxyHandlerFactory(
                        tempDir,
                        true,
                        true,
                        List.of("https://url1", "https://url2"));
        streamHandlers.handle("test", null, new AttributeMap(), handler ->
                assertThat(handler instanceof ProxyRepositoryStreamHandler).as(
                        "Expecting a handler that stores").isTrue());
    }

    @SuppressWarnings("checkstyle:Indentation")
    @Test
    void testForwardOnlySingle(@TempDir Path tempDir) {
        assertThatThrownBy(() -> {
                    final ReceiveStreamHandlers streamHandlers =
                            getProxyHandlerFactory(
                                    tempDir,
                                    false,
                                    true,
                                    List.of("https://url1"));
                    streamHandlers.handle("test", null, new AttributeMap(), handler ->
                            assertThat(handler instanceof ForwardStreamHandler).as(
                                    "Expecting a handler that forward to other URLS").isTrue());
                },
                "Expected unreachable host exception")
                .isInstanceOf(UncheckedIOException.class);
    }

    @SuppressWarnings("checkstyle:Indentation")
    @Test
    void testForwardOnlyMulti(@TempDir Path tempDir) {
        assertThatThrownBy(() -> {
                    final ReceiveStreamHandlers streamHandlers =
                            getProxyHandlerFactory(
                                    tempDir,
                                    false,
                                    true,
                                    List.of("https://url1", "https://url2"));
                    streamHandlers.handle("test", null, new AttributeMap(), handler ->
                            assertThat(handler instanceof ForwardStreamHandler).as(
                                    "Expecting a handler that forward to other URLS").isTrue());
                },
                "Expected error trying to forward to multiple destinations without storing.")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testStore(@TempDir Path tempDir) {
        final ReceiveStreamHandlers streamHandlers =
                getProxyHandlerFactory(
                        tempDir,
                        true,
                        false,
                        List.of("https://url1", "https://url2"));
        streamHandlers.handle("test", null, new AttributeMap(), handler ->
                assertThat(handler instanceof ProxyRepositoryStreamHandler).as(
                        "Expecting a handler that stores").isTrue());
    }

    private ReceiveStreamHandlers getProxyHandlerFactory(final Path tempDir,
                                                         final boolean isStoringEnabled,
                                                         final boolean isForwardingEnabled,
                                                         final List<String> forwardUrlList) {
        final LogStreamConfig logRequestConfig = new LogStreamConfig();
        final ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig()
                .withRepoDir(FileUtil.getCanonicalPath(getCurrentTestDir()))
                .withStoringEnabled(isStoringEnabled);

        final ProxyConfig.Builder builder = ProxyConfig.builder();
        forwardUrlList.forEach(url -> builder.addForwardDestination(ForwardHttpPostConfig.withForwardUrl(url, url)));

        try {
            final Entries mockStroomZipOutputStream = Mockito.mock(Entries.class);
            Mockito.when(sequentialFileStore.getEntries(Mockito.any())).thenReturn(
                    mockStroomZipOutputStream);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final ProxyRepositoryStreamHandlers proxyRepositoryRequestHandlerProvider =
                new ProxyRepositoryStreamHandlers(sequentialFileStore);

        final LogStream logStream = new LogStream(logRequestConfig);

        final long now = System.currentTimeMillis();
        final BuildInfo buildInfo = new BuildInfo(now, "test version", now);

        final PathCreator pathCreator = new TemporaryPathCreator(tempDir);
//        final PathCreator pathCreator = new PathCreator(() -> tempDir, () -> tempDir);

        final ProxyConfig proxyConfig = builder.build();

        ForwardHttpPostHandlersFactory forwardHttpPostHandlersFactory = new ForwardHttpPostHandlersFactory(
                logStream,
                pathCreator,
                () -> buildInfo);

        final ForwarderDestinations forwarderDestinations = new ForwarderDestinationsImpl(
                proxyConfig,
                proxyRepoConfig,
                forwardHttpPostHandlersFactory,
                null);

        return new ReceiveStreamHandlers(
                proxyRepoConfig,
                proxyRepositoryRequestHandlerProvider,
                forwarderDestinations,
                proxyConfig);
    }
}
