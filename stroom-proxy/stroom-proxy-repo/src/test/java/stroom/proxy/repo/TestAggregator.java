package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.CharsetConstants;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.io.StreamUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestAggregator {

    @Inject
    private ProxyRepo proxyRepo;
    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
    @Inject
    private Aggregator aggregator;
    @Inject
    private Forwarder forwarder;
    @Inject
    private MockForwardDestinations mockForwardDestinations;

    @BeforeEach
    void beforeEach() {
        proxyRepoSources.clear();
        aggregator.clear();
    }

    @Test
    void testCloseOldAggregates() {
        aggregator.closeOldAggregates();
    }

    @Test
    void testAddItem() {
        // Make sure we have no existing aggregates.
        int count = aggregator.closeOldAggregates(System.currentTimeMillis());
        assertThat(count).isEqualTo(0);

        for (int i = 0; i < 10; i++) {
            // Add an item but make sure no aggregation takes place.
            count = aggregator.addItem(1, "TEST_FEED", null, 10);
            assertThat(count).isEqualTo(0);
        }

        // Now force aggregation and ensure we end up with 1 aggregate.
        count = aggregator.closeOldAggregates(System.currentTimeMillis());
        assertThat(count).isEqualTo(1);
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

//        // Add executor to open source files and scan entries
//        final ChangeListenerExecutor proxyRepoSourceEntriesExecutor = new ChangeListenerExecutor(
//                ProxyRepoSourceEntries.class.getSimpleName(),
//                proxyRepoSourceEntries::examine,
//                100);
//        proxyRepoSources.addChangeListener((sourceId, sourcePath) -> proxyRepoSourceEntriesExecutor.onChange());
//        proxyRepoSourceEntriesExecutor.start();

        final AtomicInteger total = new AtomicInteger();

        proxyRepoSources.addChangeListener((sourceId, sourcePath) ->
                proxyRepoSourceEntries.examineSource(sourceId, sourcePath));

        proxyRepoSourceEntries.addChangeListener(sourceId ->
                aggregator.aggregate(sourceId));

        aggregator.addChangeListener(total::addAndGet);

        aggregator.addChangeListener(count -> forwarder.forward());

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
        }

        // Produce final aggregates.
        aggregator.closeOldAggregates(System.currentTimeMillis());

        assertThat(total.get()).isEqualTo(10);

        // Force all final work to be done.
        proxyRepoSourceEntries.shutdown();
        forwarder.shutdown();

        assertThat(mockForwardDestinations.getForwardCount()).isEqualTo(10);
    }
}
