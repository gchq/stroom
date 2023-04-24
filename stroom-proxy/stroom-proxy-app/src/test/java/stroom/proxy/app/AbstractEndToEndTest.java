package stroom.proxy.app;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ForwardFileConfig;
import stroom.proxy.app.forwarder.ForwardHttpPostConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.feed.remote.FeedStatus;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.receive.common.FeedStatusResource;
import stroom.receive.common.ReceiveDataServlet;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.dropwizard.server.DefaultServerFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;


public class AbstractEndToEndTest extends AbstractApplicationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEndToEndTest.class);

    protected static final String FEED_TEST_EVENTS_1 = "TEST-EVENTS_1";
    protected static final String FEED_TEST_EVENTS_2 = "TEST-EVENTS_2";

    final WireMockProxyDestination wireMockProxyDestination = new WireMockProxyDestination();

    // Use RegisterExtension instead of @WireMockTest so we can set up the req listener
    @SuppressWarnings("unused")
    @RegisterExtension
    public final WireMockExtension wireMockExtension = wireMockProxyDestination.createExtension();

    @BeforeEach
    void setup(final WireMockRuntimeInfo wmRuntimeInfo) {
        LOGGER.info("WireMock running on: {}", wmRuntimeInfo.getHttpBaseUrl());
        wireMockProxyDestination.clear();
    }

    public static ForwardHttpPostConfig createForwardHttpPostConfig() {
        return ForwardHttpPostConfig.builder()
                .enabled(true)
                .forwardUrl("http://localhost:"
                        + WireMockProxyDestination.DEFAULT_STROOM_PORT
                        + getDataFeedPath())
                .name("Stroom datafeed")
                .userAgent("Junit test")
                .build();
    }

    public ForwardFileConfig createForwardFileConfig() {
        return new ForwardFileConfig(
                true,
                "My forward file",
                "forward_dest");
    }

    public static FeedStatusConfig createFeedStatusConfig() {
        return new FeedStatusConfig(
                true,
                FeedStatus.Receive,
                "http://localhost:"
                        + WireMockProxyDestination.DEFAULT_STROOM_PORT
                        + ResourcePaths.buildAuthenticatedApiPath(FeedStatusResource.BASE_RESOURCE_PATH),
                null,
                null);
    }

    /**
     * A count of all the meta files in the {@link ForwardFileConfig} locations.
     */
    public long getForwardFileMetaCount() {
        final List<ForwardConfig> forwardConfigs = NullSafe.getOrElseGet(
                getConfig(),
                Config::getProxyConfig,
                ProxyConfig::getForwardDestinations,
                Collections::emptyList);

        if (!forwardConfigs.isEmpty()) {
            return forwardConfigs.stream()
                    .filter(forwardConfig -> forwardConfig instanceof ForwardFileConfig)
                    .map(ForwardFileConfig.class::cast)
                    .mapToLong(forwardConfig -> {
                        if (!forwardConfig.getPath().isBlank()) {
                            try (Stream<Path> pathStream = Files.walk(
                                    getPathCreator().toAppPath(forwardConfig.getPath()))) {
                                return pathStream
                                        .filter(path -> path.toString().endsWith(".meta"))
                                        .count();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            return 0L;
                        }
                    })
                    .sum();
        } else {
            return 0L;
        }
    }

    /**
     * Get all the files in the directories specified in {@link ForwardFileConfig}.
     * The dir will contain .meta and .zip pairs. Each pair is one item in the returned list.
     */
    public List<ForwardFileItem> getForwardFiles() {

        final List<ForwardConfig> forwardConfigs = NullSafe.getOrElseGet(
                getConfig(),
                Config::getProxyConfig,
                ProxyConfig::getForwardDestinations,
                Collections::emptyList);

        final List<ForwardFileItem> allForwardFileItems = forwardConfigs.stream()
                .filter(forwardConfig -> forwardConfig instanceof ForwardFileConfig)
                .map(ForwardFileConfig.class::cast)
                .flatMap(forwardFileConfig -> {
                    final Path forwardDir = getPathCreator().toAppPath(forwardFileConfig.getPath());
                    final SequentialFileStore sequentialFileStore = new SequentialFileStore(() -> forwardDir);
                    int id = 1;
                    final List<ForwardFileItem> forwardFileItems = new ArrayList<>();
                    while (true) {
                        final FileSet fileSet = sequentialFileStore.getStoreFileSet(id);
                        if (!Files.exists(fileSet.getMeta())) {
                            LOGGER.info("id {} does not exist. dir: {}", id, fileSet.getDir());
                            break;
                        }
                        final String zipFileName = fileSet.getZipFileName();
                        final String baseName = zipFileName.substring(0, zipFileName.indexOf('.'));
                        final List<ZipItem> zipItems = new ArrayList<>();
                        final String metaContent;
                        try {
                            metaContent = Files.readString(fileSet.getMeta());

                            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fileSet.getZip()))) {
                                final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                                while (entries.hasMoreElements()) {
                                    final ZipArchiveEntry entry = entries.nextElement();
                                    if (!entry.isDirectory()) {
                                        final String zipEntryName = entry.getName();
                                        final String zipEntryBaseName = zipEntryName.substring(
                                                0, zipEntryName.indexOf('.'));
                                        final String zipEntryExt = FilenameUtils.getExtension(zipEntryName);
                                        final StroomZipFileType zipEntryType =
                                                StroomZipFileType.fromExtension("." + zipEntryExt);
                                        final String zipEntryContent = new String(
                                                zipFile.getInputStream(entry).readAllBytes(),
                                                StandardCharsets.UTF_8);
                                        zipItems.add(new ZipItem(
                                                zipEntryType,
                                                zipEntryBaseName,
                                                zipEntryContent));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        id++;
                        forwardFileItems.add(new ForwardFileItem(
                                zipFileName,
                                baseName,
                                metaContent,
                                zipItems));
                    }
                    return forwardFileItems.stream();
                })
                .toList();

        return allForwardFileItems;
    }

    public static String getDataFeedPath() {
        return ResourcePaths.buildUnauthenticatedServletPath(ReceiveDataServlet.DATA_FEED_PATH_PART);
    }

    public PostDataHelper createPostDataHelper() {
        final String url = buildProxyAppPath(ResourcePaths.buildUnauthenticatedServletPath("datafeed"));
        return new PostDataHelper(getClient(), url);
    }

    void waitForHealthyProxyApp(final Duration timeout) {

        final Instant startTime = Instant.now();
        final String healthCheckUrl = buildProxyAdminPath("/healthcheck");

        boolean didTimeout = true;
        Response response = null;

        LOGGER.info("Waiting for proxy to start using " + healthCheckUrl);
        while (startTime.plus(timeout).isAfter(Instant.now())) {
            try {
                response = getClient().target(healthCheckUrl)
                        .request()
                        .get();
                if (Family.SUCCESSFUL.equals(response.getStatusInfo().toEnum().getFamily())) {
                    didTimeout = false;
                    LOGGER.info("Proxy is ready and healthy");
                    break;
                } else {
                    throw new RuntimeException(LogUtil.message("Proxy is unhealthy, got {} code",
                            response.getStatus()));
                }
            } catch (Exception e) {
                // Expected, so sleep and go round again
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while sleeping");
            }
        }
        if (didTimeout) {
            // Get the healtcheck content so we can see what is wrong. Likely a feed status check issue
            final Map<String, Object> map = response.readEntity(new GenericType<Map<String, Object>>() {
            });
            throw new RuntimeException(LogUtil.message(
                    "Timed out waiting for proxy to start. Last response: {}", map));
        }
    }

    String getProxyBaseAppUrl() {
        final String appPath = ((DefaultServerFactory) getConfig().getServerFactory()).getApplicationContextPath();
        return "http://localhost:" + getDropwizard().getLocalPort() + appPath;
    }

    String getProxyBaseAdminUrl() {
        final String adminPath = ((DefaultServerFactory) getConfig().getServerFactory()).getAdminContextPath();
        return "http://localhost:" + getDropwizard().getAdminPort() + adminPath;
    }

    String buildProxyAppPath(final String path) {
        return getProxyBaseAppUrl().replaceAll("/$", "") + path;
    }

    String buildProxyAdminPath(final String path) {
        return getProxyBaseAdminUrl().replaceAll("/$", "") + path;
    }

    void assertFileContents() {
        final List<ForwardFileItem> forwardFileItems = getForwardFiles();

        // Check number of forwarded files.
        Assertions.assertThat(forwardFileItems)
                .hasSize(4);

        // Check feed names.
        Assertions.assertThat(forwardFileItems)
                .extracting(forwardFileItem ->
                        forwardFileItem.getMetaAttributeMap().get(StandardHeaderArguments.FEED))
                .containsExactlyInAnyOrder(
                        FEED_TEST_EVENTS_1,
                        FEED_TEST_EVENTS_2,
                        FEED_TEST_EVENTS_1,
                        FEED_TEST_EVENTS_2);

        // Check zip content file count.
        Assertions.assertThat(forwardFileItems.stream()
                        .map(forwardFileItem -> forwardFileItem.zipItems().size())
                        .toList())
                .containsExactlyInAnyOrder(7, 7, 3, 3);

        // Check zip contents.
        final List<String> expectedFiles = List.of(
                "001.mf",
                "001.meta",
                "001.dat",
                "002.meta",
                "002.dat",
                "003.meta",
                "003.dat",
                "004.meta",
                "004.dat");
        assertForwardFileItemContent(forwardFileItems, expectedFiles);
    }

    void assertSimpleDataFeedRequestContent(final List<DataFeedRequest> dataFeedRequests) {
        final List<String> expectedFiles = List.of(
                "001.dat",
                "001.meta");
        assertDataFeedRequestContent(dataFeedRequests, expectedFiles);
    }

    private void assertDataFeedRequestContent(final List<DataFeedRequest> dataFeedRequests,
                                              final List<String> expectedFiles) {
        dataFeedRequests.forEach(dataFeedRequest -> {
            for (int i = 0; i < dataFeedRequest.getDataFeedRequestItems().size(); i++) {
                final DataFeedRequestItem zipItem = dataFeedRequest.getDataFeedRequestItems().get(i);
                final String expectedName = expectedFiles.get(i);
                final String actualName = zipItem.baseName() + "." + zipItem.type();
                Assertions.assertThat(actualName).isEqualTo(expectedName);
                Assertions.assertThat(zipItem.content().length()).isGreaterThan(1);
            }
        });
    }

    private void assertForwardFileItemContent(final List<ForwardFileItem> forwardFileItems,
                                              final List<String> expectedFiles) {
        forwardFileItems.forEach(forwardFileItem -> {
            for (int i = 0; i < forwardFileItem.zipItems().size(); i++) {
                final ZipItem zipItem = forwardFileItem.zipItems().get(i);
                final String expectedName = expectedFiles.get(i);
                final String actualName = zipItem.baseName() + zipItem.type().getExtension();
                Assertions.assertThat(actualName).isEqualTo(expectedName);
                Assertions.assertThat(zipItem.content().length()).isGreaterThan(1);
            }
        });
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class DataFeedRequest {

        private final List<DataFeedRequestItem> dataFeedRequestItems;

        public DataFeedRequest(final List<DataFeedRequestItem> dataFeedRequestItems) {
            this.dataFeedRequestItems = dataFeedRequestItems;
        }

        public List<DataFeedRequestItem> getDataFeedRequestItems() {
            return dataFeedRequestItems;
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public record DataFeedRequestItem(String name,
                                      String baseName,
                                      String type,
                                      String content) {

    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Represents a meta + zip pair as created by the file forwarder
     *
     * @param name
     * @param basePath
     * @param metaContent
     * @param zipItems    One for each item in the zip
     */
    public record ForwardFileItem(String name,
                                  String basePath,
                                  String metaContent,
                                  List<ZipItem> zipItems) {

        public AttributeMap getMetaAttributeMap() {
            return AttributeMapUtil.create(metaContent);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Represents an item in a zip file. May be meta or data
     */
    public record ZipItem(StroomZipFileType type,
                          String baseName,
                          String content) {

        public AttributeMap getContentAsAttributeMap() {
            if (StroomZipFileType.META.equals(type)) {
                return AttributeMapUtil.create(content);
            } else {
                throw new UnsupportedOperationException(LogUtil.message(
                        "Can't convert {} to an AttributeMap", type));
            }
        }
    }
}
