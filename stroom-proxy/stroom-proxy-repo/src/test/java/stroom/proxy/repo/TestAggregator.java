package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.CharsetConstants;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.Aggregator;
import stroom.proxy.repo.ProxyRepo;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoSources;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import javax.inject.Inject;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestAggregator {

    @Inject
    private ProxyRepoConfig proxyRepoConfig;
    @Inject
    private ProxyRepo proxyRepo;
    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
    @Inject
    private Aggregator aggregator;

    private static String repoDir;
    private static String initialRepoDir;


    @BeforeAll
    static void beforeAll() throws IOException {
        initialRepoDir = ProxyRepoConfig.repoDir;
        repoDir = FileUtil.getCanonicalPath(Files.createTempDirectory("stroom-proxy"));
        ProxyRepoConfig.repoDir = repoDir;
    }

    @AfterAll
    static void afterAll() {
        ProxyRepoConfig.repoDir = initialRepoDir;
    }

    @Test
    void testCloseOldAggregates() {
        aggregator.closeOldAggregates();
    }

    @Test
    void testAggregates() throws IOException {
//        if (proxyRepoConfig.isStoringEnabled()) {
//            // Create a service to cleanup the repo to remove empty dirs and stale lock files.
//            final FrequencyExecutor cleanupRepoExecutor = new FrequencyExecutor(
//                    ProxyRepo.class.getSimpleName(),
//                    () -> proxyRepo.clean(false),
//                    cleanupConfig.getCleanupFrequency().toMillis());
//            services.add(cleanupRepoExecutor);

        // Add executor to open source files and scan entries
        final ChangeListenerExecutor proxyRepoSourceEntriesExecutor = new ChangeListenerExecutor(
                ProxyRepoSourceEntries.class.getSimpleName(),
                proxyRepoSourceEntries::examine,
                100);
        proxyRepoSources.addChangeListener((sourceId, sourcePath) -> proxyRepoSourceEntriesExecutor.onChange());
        proxyRepoSourceEntriesExecutor.start();
//            services.add(proxyRepoSourceEntriesExecutor);

//            if (proxyRepoFileScannerConfig.isScanningEnabled()) {
//                // Add executor to scan proxy files from a repo where a repo is not populated by receiving data.
//                final FrequencyExecutor proxyRepoFileScannerExecutor = new FrequencyExecutor(
//                        ProxyRepoFileScanner.class.getSimpleName(),
//                        proxyRepoFileScanner::scan,
//                        proxyRepoFileScannerConfig.getScanFrequency().toMillis());
//                services.add(proxyRepoFileScannerExecutor);
//            }
//
//            if (forwarderConfig.isForwardingEnabled() &&
//                    forwarderConfig.getForwardDestinations() != null &&
//                    forwarderConfig.getForwardDestinations().size() > 0) {
//                final FrequencyExecutor aggregatorExecutor = new FrequencyExecutor(
//                        Aggregator.class.getSimpleName(),
//                        aggregator::aggregate,
//                        aggregatorConfig.getAggregationFrequency().toMillis());
//                services.add(aggregatorExecutor);
//
//                final ChangeListenerExecutor forwarderExecutor = new ChangeListenerExecutor(
//                        Forwarder.class.getSimpleName(),
//                        forwarder::forward,
//                        100);
//                // Forward whenever we have new aggregates.
//                aggregator.addChangeListener(forwarderExecutor::onChange);
//                services.add(forwarderExecutor);
//
//                final ChangeListenerExecutor cleanupExecutor = new ChangeListenerExecutor(
//                        Cleanup.class.getSimpleName(),
//                        cleanup::cleanup,
//                        cleanupConfig.getCleanupFrequency().toMillis());
//                // Cleanup whenever we have forwarded data.
//                forwarder.addChangeListener(cleanupExecutor::onChange);
//                services.add(cleanupExecutor);
//            }
//        }


        for (int i = 0; i < 10; i++) {
            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(StandardHeaderArguments.FEED, "FEED_" + i + "_EVENTS");
            attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);

            try (final StroomZipOutputStreamImpl outputStream =
                    (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream()) {

                try (final OutputStream entryOutputStream = outputStream.addEntry("001" +
                        StroomZipFileType.META.getExtension())) {
                    AttributeMapUtil.write(attributeMap, entryOutputStream);
                }

                try (final OutputStream entryOutputStream = outputStream.addEntry("001" +
                        StroomZipFileType.DATA.getExtension())) {
                    StreamUtil.streamToStream(
                            new ByteArrayInputStream("SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET)),
                            entryOutputStream);
                }
            }

            proxyRepoSources.addSource("test" + i, System.currentTimeMillis());
        }
    }
}
