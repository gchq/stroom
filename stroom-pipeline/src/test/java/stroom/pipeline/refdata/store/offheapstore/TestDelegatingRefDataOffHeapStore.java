package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.store.RefDataStore.StorageType;
import stroom.pipeline.refdata.store.RefDataStoreTestModule;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.sysinfo.SystemInfoResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

class TestDelegatingRefDataOffHeapStore extends AbstractRefDataOffHeapStoreTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDelegatingRefDataOffHeapStore.class);

    @Test
    void testMultipleFeeds() {
        bulkLoadAndAssert(RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS,
                true,
                1000);

        final Map<String, RefDataOffHeapStore> feedNameToStoreMap = getDelegatingStore().getFeedNameToStoreMap();
        feedNameToStoreMap.forEach((feedName, store) -> {
            LOGGER.info("feedName: {}, store: {}, streams: {}, KV entryCount: {}",
                    feedName,
                    store,
                    getRefStreamIds(store),
                    store.getKeyValueEntryCount());
        });

        assertThat(feedNameToStoreMap)
                .containsOnlyKeys(
                        RefDataStoreTestModule.FEED_1_NAME,
                        RefDataStoreTestModule.FEED_2_NAME);

        // Stream 1 is in twice with different pipe versions
        assertThat(feedNameToStoreMap)
                .extractingByKey(RefDataStoreTestModule.FEED_1_NAME)
                .extracting(
                        AbstractRefDataOffHeapStoreTest::getRefStreamIds,
                        Assertions.as(InstanceOfAssertFactories.LIST))
                .containsExactlyInAnyOrder(
                        RefDataStoreTestModule.REF_STREAM_1_ID,
                        RefDataStoreTestModule.REF_STREAM_1_ID,
                        RefDataStoreTestModule.REF_STREAM_3_ID);

        assertThat(feedNameToStoreMap)
                .extractingByKey(RefDataStoreTestModule.FEED_2_NAME)
                .extracting(
                        AbstractRefDataOffHeapStoreTest::getRefStreamIds,
                        Assertions.as(InstanceOfAssertFactories.LIST))
                .containsExactlyInAnyOrder(
                        RefDataStoreTestModule.REF_STREAM_4_ID);
    }

    @Test
    void testMigration() {
        final RefDataOffHeapStore legacyStore = getLegacyStore(true);

        bulkLoadAndAssert(RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS,
                true, 1_000,
                false,
                legacyStore);


        final long keyValueCount1 = legacyStore.getKeyValueEntryCount();
        final long rangeValueCount1 = legacyStore.getRangeValueEntryCount();
        logEntryCounts();

        // No feed stores at this point
        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(0);

        // Trigger migration of all with the same stream id as this
        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_1_DEF);

        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(1);

        // Nothing happens here as it has same stream id as the one above so has already
        // been migrated
        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_2_DEF);

        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_3_DEF);

        // Trigger migration of all streams for its feed
        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_4_DEF);

        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(2);

        LOGGER.info(LogUtil.inSeparatorLine("Purging"));

        // cut off should not matter as access time is now epoch
        getDelegatingStore().purgeOldData();

        for (final RefDataOffHeapStore feedStore : getDelegatingStore().getFeedNameToStoreMap().values()) {
            LOGGER.info("Dumping contents of store {}", feedStore.getLmdbEnvironment().getLocalDir().getFileName());
            feedStore.logAllContents(LOGGER::info);
        }

        // These counts include feed stores and legacy
        final long keyValueCount2 = getDelegatingStore().getKeyValueEntryCount();
        final long rangeValueCount2 = getDelegatingStore().getRangeValueEntryCount();

        assertThat(keyValueCount2)
                .isEqualTo(keyValueCount1);
        assertThat(rangeValueCount2)
                .isEqualTo(rangeValueCount1);
    }

    private long getFeedSpecificKeyEntryCount() {
        return getDelegatingStore().getFeedNameToStoreMap()
                .values()
                .stream()
                .mapToLong(RefDataOffHeapStore::getKeyValueEntryCount)
                .sum();
    }

    private long getFeedSpecificRangeEntryCount() {
        return getDelegatingStore().getFeedNameToStoreMap()
                .values()
                .stream()
                .mapToLong(RefDataOffHeapStore::getRangeValueEntryCount)
                .sum();
    }

    private void triggerMigrationThenPurge(final RefStreamDefinition refStreamDefinition) {
        LOGGER.info(LogUtil.inSeparatorLine("Triggering migration for stream: {}, feed: {}",
                refStreamDefinition.getStreamId(),
                getDelegatingStore().getFeedName(refStreamDefinition).orElse("?")));

        // Trigger migration of all with the same stream id as this
        getDelegatingStore().getEffectiveStore(RefDataStoreTestModule.REF_STREAM_1_DEF);

        logEntryCounts();

        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(1);

        LOGGER.info(LogUtil.inSeparatorLine("Purging"));

        // cut off should not matter as access time is now epoch
        getDelegatingStore().purgeOldData();

        logEntryCounts();
    }

    private void logEntryCounts() {
        final RefDataOffHeapStore legacyStore = getLegacyStore(false);
        LOGGER.info("Legacy keyValueEntryCount: {}, getRangeValueEntryCount: {}",
                legacyStore.getKeyValueEntryCount(),
                legacyStore.getRangeValueEntryCount());
        LOGGER.info("Delegating keyValueEntryCount: {}, getRangeValueEntryCount: {}",
                getFeedSpecificKeyEntryCount(),
                getFeedSpecificRangeEntryCount());
    }

    @Test
    void getMapNames() {
    }

    @Test
    void getLoadState() {
    }

    @Test
    void exists() {
    }

    @Test
    void getValue() {
    }

    @Test
    void getValueProxy() {
    }

    @Test
    void consumeValueBytes() {
    }

    @Test
    void doWithLoaderUnlessComplete() {
    }

    @Test
    void list() {
    }

    @Test
    void testList() {
    }

    @Test
    void consumeEntries() {
        loadDefaultData();

        final Set<Long> refStreamIds = new HashSet<>();
        getDelegatingStore().consumeEntries(
                null,
                null,
                entry -> {
                    LOGGER.info("k: {}, v: {}, streamId: {}, pipeVer: {}",
                            entry.getKey(),
                            entry.getValue(),
                            entry.getMapDefinition().getRefStreamDefinition().getStreamId(),
                            entry.getMapDefinition().getRefStreamDefinition().getPipelineVersion());
                    refStreamIds.add(entry.getMapDefinition().getRefStreamDefinition().getStreamId());
                });

        // Delegating over all feed specific stores
        assertThat(refStreamIds)
                .containsExactlyInAnyOrder(
                        RefDataStoreTestModule.REF_STREAM_1_ID,
                        RefDataStoreTestModule.REF_STREAM_3_ID,
                        RefDataStoreTestModule.REF_STREAM_4_ID);
    }

    @Test
    void listProcessingInfo() {
    }

    @Test
    void testListProcessingInfo() {
    }

    @Test
    void getKeyValueEntryCount() {
    }

    @Test
    void getRangeValueEntryCount() {
    }

    @Test
    void getProcessingInfoEntryCount() {
    }

    @Test
    void purgeOldData() {
    }

    @Test
    void testPurgeOldData() {
    }

    @Test
    void purge() {
    }

    @Test
    void getStorageType() {
        assertThat(refDataStore.getStorageType())
                .isEqualTo(StorageType.OFF_HEAP);
    }

    @Test
    void testGetSystemInfo() throws JsonProcessingException {
        loadDefaultData();

        final SystemInfoResult systemInfo = getDelegatingStore().getSystemInfo();
//        ObjectMapper objectMapper = new ObjectMapper();
//        final ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
//        final String json = objectWriter.writeValueAsString(systemInfo.getDetails());
        LOGGER.info("System info:\n{}", JsonUtil.writeValueAsString(systemInfo.getDetails()));
        assertThat(systemInfo.getDetails())
                .extractingByKey("Store count", as(InstanceOfAssertFactories.INTEGER))
                .isEqualTo(2);
        final List<String> paths = (List<String>) systemInfo.getDetails().get("Feed store paths");

        assertThat(String.join(" ", paths))
                .contains(RefDataStoreTestModule.FEED_1_NAME)
                .contains(RefDataStoreTestModule.FEED_2_NAME);
    }

    @Test
    void testGetSystemInfo_byFeed() {
        loadDefaultData();

        final String feedName = RefDataStoreTestModule.FEED_1_NAME;

        final SystemInfoResult systemInfo1 = getDelegatingStore().getSystemInfo(
                Map.of(DelegatingRefDataOffHeapStore.PARAM_NAME_FEED, feedName));
        final SystemInfoResult systemInfo2 = getDelegatingStore().getEffectiveStore(feedName).getSystemInfo();

        // Ensure param gives us the sys info for the feed specific store
        // Purge cut of is time sensitive
        assertThat(TestUtil.mapWithoutKeys(systemInfo1.getDetails(), "Purge cut off"))
                .isEqualTo(TestUtil.mapWithoutKeys(systemInfo2.getDetails(), "Purge cut off"));
    }

    private void loadDefaultData() {
        bulkLoad(RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS, true, 1_000);
    }

    private RefDataOffHeapStore getLegacyStore(final boolean createIfNotExists) {
        return ((DelegatingRefDataOffHeapStore) refDataStore).getLegacyRefDataStore(createIfNotExists);
    }

    private DelegatingRefDataOffHeapStore getDelegatingStore() {
        return (DelegatingRefDataOffHeapStore) refDataStore;
    }
}
