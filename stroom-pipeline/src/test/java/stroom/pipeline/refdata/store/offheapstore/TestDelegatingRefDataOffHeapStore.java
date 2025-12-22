/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore.StorageType;
import stroom.pipeline.refdata.store.RefDataStoreTestModule;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

// Aim of this is to load data which will go into multiple ref stream defs across multiple stores,
// then check that the delegating store can access it all from the appropriate delegate store.
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
        final long procInfoEntryCount1 = legacyStore.getProcessingInfoEntryCount();
        logEntryCounts();

        // No feed stores at this point
        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(0);

        // Trigger migration of all with the same stream id as this
        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_1_DEF, 1);

        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(1);

        // Nothing happens here as it has same stream id as the one above so has already
        // been migrated
        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_2_DEF, 1);

        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_3_DEF, 1);

        // Trigger migration of all streams for its feed
        triggerMigrationThenPurge(RefDataStoreTestModule.REF_STREAM_4_DEF, 2);

        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(2);

        LOGGER.info(LogUtil.inSeparatorLine("Purging"));

        // cut off should not matter as access time is now epoch
        getDelegatingStore().purgeOldData();

        for (final RefDataOffHeapStore feedStore : getDelegatingStore().getFeedNameToStoreMap().values()) {
            LOGGER.debug("Dumping contents of store {}", feedStore.getLmdbEnvironment().getLocalDir().getFileName());
            feedStore.logAllContents(LOGGER::debug);
        }

        // These counts include feed stores and legacy (if present)
        final long keyValueCount2 = getDelegatingStore().getKeyValueEntryCount();
        final long rangeValueCount2 = getDelegatingStore().getRangeValueEntryCount();
        final long procInfoEntryCount2 = getDelegatingStore().getProcessingInfoEntryCount();

        // kv counts match those from legacy store at start
        assertThat(keyValueCount2)
                .isEqualTo(keyValueCount1);
        assertThat(rangeValueCount2)
                .isEqualTo(rangeValueCount1);
        assertThat(procInfoEntryCount2)
                .isEqualTo(procInfoEntryCount1);

        // Legacy store is not empty
        final long keyValueCount2Legacy = getLegacyStore(false).getKeyValueEntryCount();
        final long rangeValueCount2Legacy = getLegacyStore(false).getRangeValueEntryCount();
        final long procInfoEntryCount2Legacy = getLegacyStore(false).getProcessingInfoEntryCount();

        assertThat(keyValueCount2Legacy)
                .isZero();
        assertThat(rangeValueCount2Legacy)
                .isZero();
        assertThat(procInfoEntryCount2Legacy)
                .isZero();
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

    private void triggerMigrationThenPurge(final RefStreamDefinition refStreamDefinition,
                                           final int expectedFeedStoreCount) {
        LOGGER.info(LogUtil.inSeparatorLine("Triggering migration for stream: {}, feed: {}",
                refStreamDefinition.getStreamId(),
                getDelegatingStore().getFeedName(refStreamDefinition).orElse("?")));

        // Trigger migration of all with the same stream id as this
        getDelegatingStore().getEffectiveStore(refStreamDefinition);

        logEntryCounts();

        assertThat(getDelegatingStore().getFeedNameToStoreMap())
                .hasSize(expectedFeedStoreCount);

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
        loadDefaultData();

        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(
                refStreamDef -> {
                    final Set<String> mapNames = getDelegatingStore().getMapNames(refStreamDef);
                    LOGGER.info("mapNames: {}", mapNames);
                    assertThat(mapNames)
                            .hasSize(MAPS_PER_REF_STREAM_DEF);
                }
        );
    }

    @Test
    void getLoadState() {
        loadDefaultData();

        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(refStreamDef -> {
            final ProcessingState processingState = getDelegatingStore().getLoadState(refStreamDef)
                    .orElseThrow();
            assertThat(processingState)
                    .isEqualTo(ProcessingState.COMPLETE);
        });
    }

    @Test
    void exists() {
        loadDefaultData();

        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(refStreamDef -> {
            final List<MapDefinition> mapDefinitions = getDefaultMapDefinitions(refStreamDef);
            mapDefinitions.forEach(mapDefinition -> {
                final boolean exists = getDelegatingStore().exists(mapDefinition);
                assertThat(exists)
                        .isTrue();
            });
        });
    }

    @Test
    void getValue() {
        loadDefaultData();

        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(refStreamDef -> {
            final List<MapDefinition> mapDefinitions = getDefaultMapDefinitions(refStreamDef);
            mapDefinitions.forEach(mapDefinition -> {
                // Find first entry for this map def and get its KV key (range entries have '-'
                // in the key
                final String key = getDelegatingStore().list(Integer.MAX_VALUE, refStoreEntry ->
                                refStoreEntry.getMapDefinition().equals(mapDefinition)
                                        && !refStoreEntry.getKey().contains("-"))
                        .get(0)
                        .getKey();

                // Look up our key
                final RefDataValue val = getDelegatingStore().getValue(mapDefinition, key)
                        .orElseThrow();
                assertThat(val)
                        .isNotNull();
            });
        });
    }

    @Test
    void getValueProxy() {
        loadDefaultData();

        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(refStreamDef -> {
            final List<MapDefinition> mapDefinitions = getDefaultMapDefinitions(refStreamDef);
            mapDefinitions.forEach(mapDefinition -> {
                // Find first entry for this map def and get its KV key (range entries have '-'
                // in the key
                final String key = getDelegatingStore().list(Integer.MAX_VALUE, refStoreEntry ->
                                refStoreEntry.getMapDefinition().equals(mapDefinition)
                                        && !refStoreEntry.getKey().contains("-"))
                        .get(0)
                        .getKey();

                // Look up our key
                final RefDataValueProxy refDataValueProxy = getDelegatingStore().getValueProxy(mapDefinition, key);
                assertThat(refDataValueProxy.getKey())
                        .isEqualTo(key);
                assertThat(refDataValueProxy.supplyValue())
                        .isPresent();
            });
        });
    }

    @Test
    void consumeValueBytes() {
        loadDefaultData();

        RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS.forEach(refStreamDef -> {
            final List<MapDefinition> mapDefinitions = getDefaultMapDefinitions(refStreamDef);
            mapDefinitions.forEach(mapDefinition -> {
                // Find first entry for this map def and get its KV key (range entries have '-'
                // in the key
                final String key = getDelegatingStore().list(Integer.MAX_VALUE, refStoreEntry ->
                                refStoreEntry.getMapDefinition().equals(mapDefinition)
                                        && !refStoreEntry.getKey().contains("-"))
                        .get(0)
                        .getKey();

                // Look up our key
                final AtomicBoolean wasConsumed = new AtomicBoolean(false);
                final boolean wasFound = getDelegatingStore().consumeValueBytes(
                        mapDefinition, key, typedByteBuffer -> {
                            assertThat(typedByteBuffer)
                                    .isNotNull();
                            wasConsumed.set(true);
                        });
                assertThat(wasFound)
                        .isTrue();
                assertThat(wasConsumed)
                        .isTrue();
            });
        });
    }

    @Test
    void consumeEntries() {
        loadDefaultData();

        final Set<Long> refStreamIds = new HashSet<>();
        getDelegatingStore().consumeEntries(
                null,
                null,
                entry -> {
                    LOGGER.debug("k: {}, v: {}, streamId: {}, pipeVer: {}",
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
    void getKeyValueEntryCount() {
        loadDefaultData();
        final long keyValueEntryCount = getDelegatingStore().getKeyValueEntryCount();
        assertThat(keyValueEntryCount)
                .isEqualTo((long) ENTRIES_PER_MAP_DEF
                        * getDefaultRefStreamDefinitions().size()
                        * MAPS_PER_REF_STREAM_DEF);
    }

    @Test
    void getRangeValueEntryCount() {
        loadDefaultData();
        final long rangeValueEntryCount = getDelegatingStore().getRangeValueEntryCount();
        assertThat(rangeValueEntryCount)
                .isEqualTo((long) ENTRIES_PER_MAP_DEF
                        * getDefaultRefStreamDefinitions().size()
                        * MAPS_PER_REF_STREAM_DEF);
    }

    @Test
    void getProcessingInfoEntryCount() {
        loadDefaultData();
        final long processingInfoEntryCount = getDelegatingStore().getProcessingInfoEntryCount();
        assertThat(processingInfoEntryCount)
                .isEqualTo(getDefaultRefStreamDefinitions().size());
    }

    @Test
    void purgeOldData() {
        loadDefaultData();
        long processingInfoEntryCount = getDelegatingStore().getProcessingInfoEntryCount();
        assertThat(processingInfoEntryCount)
                .isEqualTo(getDefaultRefStreamDefinitions().size());

        getDelegatingStore().purgeOldData(StroomDuration.ZERO);

        processingInfoEntryCount = getDelegatingStore().getProcessingInfoEntryCount();
        assertThat(processingInfoEntryCount)
                .isZero();

        final long keyValueEntryCount = getDelegatingStore().getKeyValueEntryCount();
        assertThat(keyValueEntryCount)
                .isZero();

        final long rangeValueEntryCount = getDelegatingStore().getRangeValueEntryCount();
        assertThat(rangeValueEntryCount)
                .isZero();
    }

    @Test
    void getStorageType() {
        assertThat(refDataStore.getStorageType())
                .isEqualTo(StorageType.OFF_HEAP);
    }

    @Test
    void testGetSizeOnDisk() {
        loadDefaultData();
        final long sizeOnDisk = getDelegatingStore().getSizeOnDisk();
        final long sizeOnDiskFeed1 = getDelegatingStore().getEffectiveStore(RefDataStoreTestModule.FEED_1_NAME)
                .getSizeOnDisk();
        final long sizeOnDiskFeed2 = getDelegatingStore().getEffectiveStore(RefDataStoreTestModule.FEED_2_NAME)
                .getSizeOnDisk();
        assertThat(sizeOnDisk)
                .isEqualTo(sizeOnDiskFeed1 + sizeOnDiskFeed2);
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
        final Map<String, Object> paths = (Map<String, Object>) systemInfo.getDetails().get("Feed store paths");

        assertThat(paths.keySet())
                .contains(RefDataStoreTestModule.FEED_1_NAME)
                .contains(RefDataStoreTestModule.FEED_2_NAME);
    }

    @Test
    void testGetSystemInfo_byFeed() {
        loadDefaultData();
        final String feedName = RefDataStoreTestModule.FEED_1_NAME;

        final SystemInfoResult delegatingStoreInfo = getDelegatingStore().getSystemInfo(
                Map.of(DelegatingRefDataOffHeapStore.PARAM_NAME_FEED, feedName));
        final SystemInfoResult feedSpecificStoreInfo = getDelegatingStore().getEffectiveStore(feedName)
                .getSystemInfo();

        // Ensure param gives us the sys info for the feed specific store
        // Purge cut of is time sensitive
        assertThat(TestUtil.mapWithoutKeys(delegatingStoreInfo.getDetails(), "Purge cut off"))
                .isEqualTo(TestUtil.mapWithoutKeys(feedSpecificStoreInfo.getDetails(), "Purge cut off"));
    }

    private void loadDefaultData() {
        bulkLoad(RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS, true, 1_000);
    }

    private List<RefStreamDefinition> getDefaultRefStreamDefinitions() {
        return RefDataStoreTestModule.DEFAULT_REF_STREAM_DEFINITIONS;
    }

    private List<MapDefinition> getDefaultMapDefinitions(final RefStreamDefinition refStreamDefinition) {
        return IntStream.rangeClosed(1, MAPS_PER_REF_STREAM_DEF)
                .mapToObj(i -> new MapDefinition(refStreamDefinition, buildDefaultMapName(i)))
                .collect(Collectors.toList());
    }

    private RefDataOffHeapStore getLegacyStore(final boolean createIfNotExists) {
        return ((DelegatingRefDataOffHeapStore) refDataStore).getLegacyRefDataStore(createIfNotExists);
    }

    private DelegatingRefDataOffHeapStore getDelegatingStore() {
        return (DelegatingRefDataOffHeapStore) refDataStore;
    }
}
