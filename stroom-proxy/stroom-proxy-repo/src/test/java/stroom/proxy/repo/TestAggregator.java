package stroom.proxy.repo;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.CharsetConstants;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.dao.AggregateDao;
import stroom.proxy.repo.dao.AggregateDao.Aggregate;
import stroom.proxy.repo.dao.ForwardUrlDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.proxy.repo.dao.SourceEntryDao.SourceItem;
import stroom.util.io.StreamUtil;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestAggregator {

    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceEntryDao sourceEntryDao;
    @Inject
    private ProxyRepo proxyRepo;
    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
    @Inject
    private AggregateDao aggregateDao;
    @Inject
    private Aggregator aggregator;
    @Inject
    private AggregateForwarder aggregateForwarder;
    @Inject
    private ForwardUrlDao forwardUrlDao;
    @Inject
    private Cleanup cleanup;
    @Inject
    private MockForwardDestinations mockForwardDestinations;
    @Inject
    private TestSourceEntries testSourceEntries;

    @BeforeEach
    void beforeEach() {
        aggregateForwarder.clear();
        aggregator.clear();
        proxyRepoSourceEntries.clear();
        proxyRepoSources.clear();
        mockForwardDestinations.clear();
    }

    @Test
    void testCloseOldAggregates() {
        aggregator.closeOldAggregates();
    }

    @Test
    void testWithSourceEntries() {
        assertThat(sourceEntryDao.getNewSourceItems().size()).isZero();
        assertThat(sourceEntryDao.getNewSourceItemsForSource(1).size()).isZero();
        ensureNonDeletable();

        // Add entries.
        final long sourceId = testSourceEntries.addEntries();
        ensureNonDeletable();

        // Check that there is now something to aggregate.
        assertThat(sourceEntryDao.getNewSourceItems().size()).isEqualTo(1000);
        assertThat(sourceEntryDao.getNewSourceItemsForSource(sourceId).size()).isEqualTo(1000);

        // Make sure we have no existing aggregates.
        int count = aggregator.closeOldAggregates(System.currentTimeMillis());
        assertThat(count).isEqualTo(0);

        // Aggregate.
        aggregator.aggregate();

        // Check that we now have nothing left to aggregate.
        assertThat(sourceEntryDao.getNewSourceItems().size()).isZero();
        assertThat(sourceEntryDao.getNewSourceItemsForSource(1).size()).isZero();
        ensureNonDeletable();

        // Check that the new aggregates are not yet completed.
        assertThat(aggregateDao.getCompletedAggregates(Integer.MAX_VALUE).size()).isZero();
        // Close all aggregates.
        aggregator.closeOldAggregates(System.currentTimeMillis());
        // Check that we now have some aggregates.
        final List<Aggregate> completedAggregates =
                aggregateDao.getCompletedAggregates(Integer.MAX_VALUE);
        assertThat(completedAggregates.size()).isEqualTo(10);
        ensureNonDeletable();

        // Now forward the completed aggregates.
        final String forwardUrl = "http://test-url.com";
        final int forwardUrlId = forwardUrlDao.getForwardUrlId(forwardUrl);
        for (final Aggregate aggregate : completedAggregates) {
            aggregateForwarder.forwardAggregateData(
                    aggregate,
                    forwardUrlId,
                    forwardUrl);
        }

        // Check we still can't delete sources.
        ensureNonDeletable();

        // Now delete forward records and aggregates.
        for (final Aggregate aggregate : completedAggregates) {
            aggregateForwarder.setForwardSuccess(aggregate.getAggregateId());
        }

        // Now we should be able to delete sources.
        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isEqualTo(3000);
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isEqualTo(1000);
        assertThat(sourceDao.getDeletableSources(Integer.MAX_VALUE).size()).isZero();

        cleanup.deleteUnusedSourceEntries();

        // WE should now have no source entries but some sources we can delete.
        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isZero();
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isZero();
        assertThat(sourceDao.getDeletableSources(Integer.MAX_VALUE).size()).isEqualTo(1);
    }

    private void ensureNonDeletable() {
        assertThat(sourceEntryDao.getDeletableSourceEntryIds().size()).isZero();
        assertThat(sourceEntryDao.getDeletableSourceItemIds().size()).isZero();
        assertThat(sourceDao.getDeletableSources(Integer.MAX_VALUE).size()).isZero();
    }

    @Test
    void testAddItem() {
        // Make sure we have no existing aggregates.
        int count = aggregator.closeOldAggregates(System.currentTimeMillis());
        assertThat(count).isEqualTo(0);

        for (int i = 0; i < 10; i++) {
            // Add an item but make sure no aggregation takes place.
            count = aggregator.addItem(new SourceItem(1, "TEST_FEED", null, 10));
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

        proxyRepoSources.addChangeListener((source) ->
                proxyRepoSourceEntries.examineSource(source));

        proxyRepoSourceEntries.addChangeListener(sourceId ->
                aggregator.aggregate(sourceId));

        aggregator.addChangeListener(total::addAndGet);

        aggregator.addChangeListener(count -> aggregateForwarder.forward());

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
                    (StroomZipOutputStreamImpl) proxyRepo.getStroomZipOutputStream(attributeMap)) {

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
        aggregateForwarder.shutdown();

        assertThat(mockForwardDestinations.getForwardCount()).isEqualTo(10);
    }
}
