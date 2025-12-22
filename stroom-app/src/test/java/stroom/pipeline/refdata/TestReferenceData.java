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

package stroom.pipeline.refdata;

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineSerialiser;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.cache.DocumentPermissionCache;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Range;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lmdbjava.Env;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestReferenceData extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceData.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(5);

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";
    private static final String VALUE_3 = "value3";
    private static final String VALUE_4 = "value4";
    private static final String SID_TO_PF_1 = "SID_TO_PF_1";
    private static final String SID_TO_PF_2 = "SID_TO_PF_2";
    private static final String SID_TO_PF_3 = "SID_TO_PF_3";
    private static final String SID_TO_PF_4 = "SID_TO_PF_4";
    private static final String IP_TO_LOC_MAP_NAME = "IP_TO_LOC_MAP_NAME";
    public static final String DUMMY_FEED = "DUMMY_FEED";
    public static final String DUMMY_TYPE = "DummyType";

    private Env<ByteBuffer> lmdbEnv = null;
    private Path dbDir = null;

    @Mock
    private DocumentPermissionCache mockDocumentPermissionCache;
    @Mock
    private ReferenceDataLoader mockReferenceDataLoader;

    @SuppressWarnings("unused")
    @Inject
    private RefDataStoreFactory refDataStoreFactory;

    @SuppressWarnings("unused")
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;
    @Inject
    private PipelineSerialiser pipelineSerialiser;

    private ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
    private RefDataStore refDataStore;

    @SuppressWarnings("unused")
    @Inject
    private FeedStore feedStore;

    @SuppressWarnings("unused")
    @Inject
    private PipelineStore pipelineStore;

    @Inject
    private Provider<RefDataStoreHolder> refDataStoreHolderProvider;
    @Inject
    private PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    @Inject
    private ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    @Inject
    private StagingValueOutputStream stagingValueOutputStream;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private MetaService metaService;
    @Inject
    private TaskContextFactory taskContextFactory;

    @BeforeEach
    void setup() throws IOException {

        dbDir = Files.createTempDirectory("stroom");
        LOGGER.debug("Creating LMDB environment with maxSize: {}, dbDir {}",
                getMaxSizeBytes(), dbDir.toAbsolutePath().toString());

        lmdbEnv = Env.create()
                .setMapSize(getMaxSizeBytes().getBytes())
                .setMaxDbs(10)
                .open(dbDir.toFile());

        LOGGER.debug("Creating LMDB environment in dbDir {}", getDbDir().toAbsolutePath().toString());

        referenceDataConfig.getLmdbConfig().setLocalDir(getDbDir().toAbsolutePath().toString());

        setDbMaxSizeProperty(DB_MAX_SIZE);
        refDataStore = refDataStoreFactory.getOffHeapStore();

        Mockito.when(mockDocumentPermissionCache.canUseDocument(Mockito.any()))
                .thenReturn(true);
    }

    @AfterEach
    void teardown() {
        if (lmdbEnv != null) {
            lmdbEnv.close();
        }
        lmdbEnv = null;
        if (Files.isDirectory(dbDir)) {
            FileUtil.deleteDir(dbDir);
        }
    }

    @Test
    void testSimple() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_1");
            final DocRef feed2Ref = feedStore.createDocument("TEST_FEED_2");
            final DocRef pipeline1Ref = pipelineStore.createDocument("TEST_PIPELINE_1");
            final DocRef pipeline2Ref = pipelineStore.createDocument("TEST_PIPELINE_2");

            final List<PipelineReference> pipelineReferences = new ArrayList<>();
            pipelineReferences.add(new PipelineReference(pipeline1Ref, feed1Ref, StreamTypeNames.REFERENCE));
            pipelineReferences.add(new PipelineReference(pipeline2Ref, feed2Ref, StreamTypeNames.REFERENCE));

            // Set up the effective streams to be used for each
            final Map<String, EffectiveMetaSet> effectiveMetasByFeed = new HashMap<>();
            for (final DocRef feedDocRef : List.of(feed1Ref, feed2Ref)) {
                final String feedName = feedDocRef.getName();
                final EffectiveMetaSet streamSet = EffectiveMetaSet.builder(feedName, DUMMY_TYPE)
                        .add(
                                createMeta(feedName).getId(),
                                DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z"))
                        .add(
                                createMeta(feedName).getId(),
                                DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z"))
                        .add(
                                createMeta(feedName).getId(),
                                DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.000Z"))
                        .build();
                effectiveMetasByFeed.put(feedName, streamSet);
            }

            try (final CacheManager cacheManager = new CacheManagerImpl()) {

                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                        cacheManager, null, null, null, ReferenceDataConfig::new) {
                    @Override
                    protected EffectiveMetaSet create(final EffectiveStreamKey key) {
                        final EffectiveMetaSet effectiveMetaSet = effectiveMetasByFeed.get(key.getFeed());
                        LOGGER.debug("Cache key: {}, returned {}", key, effectiveMetaSet);
                        return effectiveMetaSet;
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        new EffectiveStreamService(effectiveStreamCache),
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        refDataStoreHolderProvider.get(),
                        new RefDataLoaderHolder(),
                        pipelineStore,
                        new MockSecurityContext(),
                        taskContextFactory,
                        null,
                        null);

                final Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addUserDataToMockReferenceDataLoader(
                        pipeline1Ref,
                        effectiveMetasByFeed.get(feed1Ref.getName()),
                        Arrays.asList(SID_TO_PF_1, SID_TO_PF_2),
                        mockLoaderActionsMap);

                addUserDataToMockReferenceDataLoader(
                        pipeline2Ref,
                        effectiveMetasByFeed.get(feed2Ref.getName()),
                        Arrays.asList(SID_TO_PF_3, SID_TO_PF_4),
                        mockLoaderActionsMap);

                // set up the mock loader to load the appropriate data when triggered by a lookup call
                Mockito.doAnswer(invocation -> {
                    final RefStreamDefinition refStreamDefinition = invocation.getArgument(0);

                    final Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
                    action.run();
                    return null;
                }).when(mockReferenceDataLoader).load(Mockito.any(RefStreamDefinition.class));

                // perform lookups (which will trigger a load if required) and assert the result
                // Feed 1 contains these maps
                checkData(referenceData,
                        pipelineReferences,
                        SID_TO_PF_1,
                        effectiveMetasByFeed.get(feed1Ref.getName()).asList());
                checkData(referenceData,
                        pipelineReferences,
                        SID_TO_PF_2,
                        effectiveMetasByFeed.get(feed1Ref.getName()).asList());

                // Feed 2 contains these maps
                checkData(referenceData,
                        pipelineReferences,
                        SID_TO_PF_3,
                        effectiveMetasByFeed.get(feed2Ref.getName()).asList());
                checkData(referenceData,
                        pipelineReferences,
                        SID_TO_PF_4,
                        effectiveMetasByFeed.get(feed2Ref.getName()).asList());
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    /**
     * Make sure that it copes with a map not being in an early stream but is in a later one.
     */
    @Test
    void testMissingMaps() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_1");
            final DocRef pipeline1Ref = pipelineStore.createDocument("TEST_PIPELINE_1");

            final List<PipelineReference> pipelineReferences = new ArrayList<>();
            pipelineReferences.add(new PipelineReference(pipeline1Ref, feed1Ref, StreamTypeNames.REFERENCE));
            final String feedName = feed1Ref.getName();
            // Set up the effective streams to be used for each
            final EffectiveMeta stream1 = buildEffectiveMeta(
                    createMeta(feed1Ref.getName()).getId(),
                    "2008-01-01T09:47:00.000Z");
            final EffectiveMeta stream2 = buildEffectiveMeta(
                    createMeta(feed1Ref.getName()).getId(),
                    "2009-01-01T09:47:00.000Z");
            final EffectiveMeta stream3 = buildEffectiveMeta(
                    createMeta(feed1Ref.getName()).getId(),
                    "2010-01-01T09:47:00.000Z");

            final EffectiveMetaSet streamSet1 = EffectiveMetaSet.singleton(stream1);
            final EffectiveMetaSet streamSet2and3 = EffectiveMetaSet.of(stream2, stream3);

            final EffectiveMetaSet streamSetAll = EffectiveMetaSet.of(stream1, stream2, stream3);

            try (final CacheManager cacheManager = new CacheManagerImpl()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                        cacheManager, null, null, null, ReferenceDataConfig::new) {

                    @Override
                    protected EffectiveMetaSet create(final EffectiveStreamKey key) {
                        return streamSetAll.stream()
                                .collect(EffectiveMetaSet.collector(DUMMY_FEED, DUMMY_TYPE));
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        new EffectiveStreamService(effectiveStreamCache),
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        refDataStoreHolderProvider.get(),
                        new RefDataLoaderHolder(),
                        pipelineStore,
                        new MockSecurityContext(),
                        taskContextFactory,
                        null,
                        null);

                final Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addUserDataToMockReferenceDataLoader(
                        pipeline1Ref,
                        streamSet1,
                        List.of(SID_TO_PF_1),
                        mockLoaderActionsMap);
                addUserDataToMockReferenceDataLoader(
                        pipeline1Ref,
                        streamSet2and3,
                        List.of(SID_TO_PF_1, SID_TO_PF_2),
                        mockLoaderActionsMap);

                // set up the mock loader to load the appropriate data when triggered by a lookup call
                Mockito.doAnswer(
                                invocation -> {
                                    final RefStreamDefinition refStreamDefinition = invocation.getArgument(0);

                                    final Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
                                    action.run();
                                    return null;
                                }).when(mockReferenceDataLoader)
                        .load(Mockito.any(RefStreamDefinition.class));

                // perform lookups (which will trigger a load if required) and assert the result
                final List<Tuple3<String, String, Boolean>> cases = List.of(
                        Tuple.of("2008-01-01T09:47:00.000Z", SID_TO_PF_2, false), // map not in this stream
                        Tuple.of("2009-01-01T09:47:00.111Z", SID_TO_PF_2, true), // Map found in this stream
                        Tuple.of("2010-01-01T09:47:00.111Z", SID_TO_PF_2, true),

                        Tuple.of("2008-01-01T09:47:00.000Z", SID_TO_PF_1, true),
                        Tuple.of("2009-01-01T09:47:00.111Z", SID_TO_PF_1, true),
                        Tuple.of("2010-01-01T09:47:00.111Z", SID_TO_PF_1, true));

                for (final Tuple3<String, String, Boolean> testCase : cases) {
                    final Optional<String> optFoundValue = lookup(
                            referenceData,
                            pipelineReferences,
                            testCase._1,
                            testCase._2, // Map is NOT in the stream
                            USER_1);
                    Assertions.assertThat(optFoundValue.isPresent())
                            .isEqualTo(testCase._3);
                }
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private void addUserDataToMockReferenceDataLoader(final DocRef pipelineRef,
                                                      final EffectiveMetaSet effectiveStreams,
                                                      final List<String> mapNames,
                                                      final Map<RefStreamDefinition, Runnable> mockLoaderActions) {

        for (final EffectiveMeta effectiveStream : effectiveStreams) {

            final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineRef, pipelineStore.readDocument(pipelineRef).getVersion(), effectiveStream.getId());


            mockLoaderActions.put(refStreamDefinition, () -> {
                refDataStore.doWithLoaderUnlessComplete(
                        refStreamDefinition, effectiveStream.getEffectiveMs(), refDataLoader -> {

                            refDataLoader.initialise(false);
                            for (final String mapName : mapNames) {
                                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                                doLoaderPut(refDataLoader,
                                        mapDefinition,
                                        USER_1,
                                        buildValue(mapDefinition, VALUE_1));
                                doLoaderPut(refDataLoader,
                                        mapDefinition,
                                        USER_2,
                                        buildValue(mapDefinition, VALUE_2));
                            }
                            refDataLoader.completeProcessing();
                        });
            });
        }
    }

    private void addKeyValueDataToMockReferenceDataLoader(final DocRef pipelineRef,
                                                          final EffectiveMetaSet effectiveStreams,
                                                          final List<Tuple3<String, String, String>> mapKeyValueTuples,
                                                          final Map<RefStreamDefinition, Runnable> mockLoaderActions) {

        for (final EffectiveMeta effectiveStream : effectiveStreams) {

            final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineRef, pipelineStore.readDocument(pipelineRef).getVersion(), effectiveStream.getId());


            mockLoaderActions.put(refStreamDefinition, () -> {
                refDataStore.doWithLoaderUnlessComplete(
                        refStreamDefinition, effectiveStream.getEffectiveMs(), refDataLoader -> {

                            refDataLoader.initialise(false);
                            for (final Tuple3<String, String, String> mapKeyValueTuple : mapKeyValueTuples) {
                                final String mapName = mapKeyValueTuple._1();
                                final String key = mapKeyValueTuple._2();
                                final String value = mapKeyValueTuple._3();
                                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                                doLoaderPut(refDataLoader, mapDefinition, key, StringValue.of(value));
                            }
                            refDataLoader.completeProcessing();
                        });
            });
        }
    }

    private void addRangeValueDataToMockReferenceDataLoader(
            final DocRef pipelineRef,
            final EffectiveMetaSet effectiveStreams,
            final List<Tuple3<String, Range<Long>, String>> mapRangeValueTuples,
            final Map<RefStreamDefinition, Runnable> mockLoaderActions) {

        for (final EffectiveMeta effectiveStream : effectiveStreams) {

            final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineRef, pipelineStore.readDocument(pipelineRef).getVersion(), effectiveStream.getId());


            mockLoaderActions.put(refStreamDefinition, () -> {
                refDataStore.doWithLoaderUnlessComplete(
                        refStreamDefinition, effectiveStream.getEffectiveMs(), refDataLoader -> {

                            refDataLoader.initialise(false);
                            for (final Tuple3<String, Range<Long>, String> mapKeyValueTuple : mapRangeValueTuples) {
                                final String mapName = mapKeyValueTuple._1();
                                final Range<Long> range = mapKeyValueTuple._2();
                                final String value = mapKeyValueTuple._3();
                                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                                doLoaderPut(refDataLoader, mapDefinition, range, StringValue.of(value));
                            }
                            refDataLoader.completeProcessing();
                        });
            });
        }
    }

    private StringValue buildValue(final MapDefinition mapDefinition, final String value) {
        return StringValue.of(
                mapDefinition.getRefStreamDefinition().getPipelineDocRef().getUuid() + "|" +
                mapDefinition.getRefStreamDefinition().getStreamId() + "|" +
                mapDefinition.getMapName() + "|" +
                value
        );
    }

    private void checkData(final ReferenceData data,
                           final List<PipelineReference> pipelineReferences,
                           final String mapName,
                           final List<EffectiveMeta> effectiveMetas) {
        final String expectedValuePart = VALUE_1;

        Optional<String> optFoundValue;

        optFoundValue = lookup(data, pipelineReferences, "2010-01-01T09:47:00.111Z", mapName, USER_1);
        doValueAsserts(optFoundValue, effectiveMetas.get(2).getId(), mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2015-01-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, effectiveMetas.get(2).getId(), mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2009-10-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, effectiveMetas.get(1).getId(), mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, effectiveMetas.get(1).getId(), mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2008-01-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, effectiveMetas.get(0).getId(), mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2006-01-01T09:47:00.000Z", mapName, USER_1);
        assertThat(optFoundValue).isEmpty();

        optFoundValue = lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1_X");
        assertThat(optFoundValue).isEmpty();

        optFoundValue = lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", "SID_TO_PF_X", USER_1);
        assertThat(optFoundValue).isEmpty();
    }

    private void doValueAsserts(final Optional<String> optFoundValue,
                                final long expectedStreamId,
                                final String expectedMapName,
                                final String expectedValuePart) {
        assertThat(optFoundValue)
                .isNotEmpty();
        final String[] parts = optFoundValue.get()
                .split("\\|");
        assertThat(parts)
                .hasSize(4);
        assertThat(Long.parseLong(parts[1]))
                .isEqualTo(expectedStreamId);
        assertThat(parts[2])
                .isEqualTo(expectedMapName);
        assertThat(parts[3])
                .isEqualTo(expectedValuePart);
    }

    @Test
    void testNestedMaps() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_V1");
            final FeedDoc feedDoc = feedStore.readDocument(feed1Ref);
            feedDoc.setReference(true);
            feedStore.writeDocument(feedDoc);

            final DocRef pipelineRef = pipelineStore.createDocument("12345");
            final PipelineReference pipelineReference = new PipelineReference(pipelineRef,
                    feed1Ref,
                    StreamTypeNames.REFERENCE);
            final List<PipelineReference> pipelineReferences = Collections.singletonList(pipelineReference);

            final EffectiveMetaSet streamSet = EffectiveMetaSet.builder(DUMMY_FEED, DUMMY_TYPE)
                    .add(createMeta(feed1Ref.getName()).getId(), 0L)
                    .build();

            try (final CacheManager cacheManager = new CacheManagerImpl()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                        null,
                        null,
                        null,
                        ReferenceDataConfig::new) {
                    @Override
                    protected EffectiveMetaSet create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        new EffectiveStreamService(effectiveStreamCache),
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        refDataStoreHolderProvider.get(),
                        new RefDataLoaderHolder(),
                        pipelineStore,
                        new MockSecurityContext(),
                        taskContextFactory,
                        null,
                        null);

                final Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addKeyValueDataToMockReferenceDataLoader(
                        pipelineRef,
                        streamSet,
                        Arrays.asList(
                                Tuple.of("CARD_NUMBER_TO_PF_NUMBER", "011111", "091111"),
                                Tuple.of("NUMBER_TO_SID", "091111", USER_1)
                        ),
                        mockLoaderActionsMap);

                Mockito.doAnswer(invocation -> {
                    final RefStreamDefinition refStreamDefinition = invocation.getArgument(0);

                    final Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
                    action.run();
                    return null;
                }).when(mockReferenceDataLoader).load(Mockito.any(RefStreamDefinition.class));

                Optional<String> optFoundValue;
                optFoundValue = lookup(
                        referenceData, pipelineReferences, 0, "CARD_NUMBER_TO_PF_NUMBER", "011111");
                assertThat(optFoundValue).contains("091111");

                optFoundValue = lookup(
                        referenceData, pipelineReferences, 0, "NUMBER_TO_SID", "091111");
                assertThat(optFoundValue).contains(USER_1);

                optFoundValue = lookup(referenceData, pipelineReferences, 0,
                        "CARD_NUMBER_TO_PF_NUMBER/NUMBER_TO_SID", "011111");
                assertThat(optFoundValue).contains(USER_1);

            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    @Test
    void testRange() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_V1");
            final FeedDoc feedDoc = feedStore.readDocument(feed1Ref);
            feedDoc.setReference(true);
            feedStore.writeDocument(feedDoc);

            final DocRef pipelineRef = pipelineStore.createDocument("12345");
            final PipelineReference pipelineReference = new PipelineReference(pipelineRef,
                    feed1Ref,
                    StreamTypeNames.REFERENCE);
            final List<PipelineReference> pipelineReferences = Collections.singletonList(pipelineReference);

            final EffectiveMetaSet streamSet = EffectiveMetaSet.builder(DUMMY_FEED, DUMMY_TYPE)
                    .add(createMeta(feed1Ref.getName()).getId(), 0L)
                    .build();

            try (final CacheManager cacheManager = new CacheManagerImpl()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                        null,
                        null,
                        null,
                        ReferenceDataConfig::new) {
                    @Override
                    protected EffectiveMetaSet create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        new EffectiveStreamService(effectiveStreamCache),
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        refDataStoreHolderProvider.get(),
                        new RefDataLoaderHolder(),
                        pipelineStore,
                        new MockSecurityContext(),
                        taskContextFactory,
                        null,
                        null);

                final Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addRangeValueDataToMockReferenceDataLoader(
                        pipelineRef,
                        streamSet,
                        Arrays.asList(
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(2L, 30L), VALUE_1),
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(40L, 41L), VALUE_2),
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(500L, 2000L), VALUE_3),
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(3000L, 3001L), VALUE_4)),
                        mockLoaderActionsMap);

                Mockito.doAnswer(invocation -> {
                    final RefStreamDefinition refStreamDefinition = invocation.getArgument(0);

                    final Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
                    action.run();
                    return null;
                }).when(mockReferenceDataLoader).load(Mockito.any(RefStreamDefinition.class));

                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "1"))
                        .isEmpty();
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "2"))
                        .contains(VALUE_1);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "10"))
                        .contains(VALUE_1);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "29"))
                        .contains(VALUE_1);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "30"))
                        .isEmpty();
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "40"))
                        .contains(VALUE_2);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "500"))
                        .contains(VALUE_3);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "1000"))
                        .contains(VALUE_3);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "1999"))
                        .contains(VALUE_3);
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "2000"))
                        .isEmpty();
                assertThat(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC_MAP_NAME", "3000"))
                        .contains(VALUE_4);
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }


    private Optional<String> lookup(final ReferenceData referenceData,
                                    final List<PipelineReference> pipelineReferences,
                                    final String time,
                                    final String mapName,
                                    final String key) {
        LOGGER.debug("Looking up {}, {}, {}", time, mapName, key);
        final Optional<String> optValue = lookup(referenceData,
                pipelineReferences,
                DateUtil.parseNormalDateTimeString(time),
                mapName,
                key);
        LOGGER.debug("Found {}", optValue.orElse("EMPTY"));
        return optValue;
    }

    private Optional<String> lookup(final ReferenceData referenceData,
                                    final List<PipelineReference> pipelineReferences,
                                    final long time,
                                    final String mapName,
                                    final String key) {
        final LookupIdentifier lookupIdentifier = LookupIdentifier.of(mapName, key, time);
        final ReferenceDataResult result = new ReferenceDataResult(lookupIdentifier);

        referenceData.ensureReferenceDataAvailability(
                pipelineReferences,
                lookupIdentifier,
                result);

        if (result.getRefDataValueProxy().isPresent()) {
            return result.getRefDataValueProxy()
                    .flatMap(RefDataValueProxy::supplyValue)
                    .flatMap(val -> Optional.of(((StringValue) val).getValue()));
        } else {
            return Optional.empty();
        }
    }

    private void setDbMaxSizeProperty(final ByteSize size) {
        referenceDataConfig = referenceDataConfig.withLmdbConfig(referenceDataConfig.getLmdbConfig()
                .withMaxStoreSize(size));
    }

    private Path getDbDir() {
        return dbDir;
    }

    private ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    private EffectiveMeta buildEffectiveMeta(final long id, final String effectiveTimeStr) {
        return new EffectiveMeta(id,
                DUMMY_FEED,
                DUMMY_TYPE,
                DateUtil.parseNormalDateTimeString(effectiveTimeStr));
    }

    private EffectiveMeta buildEffectiveMeta(final long id, final long effectiveMs) {
        return new EffectiveMeta(id, "DUMMY_FEED", "DummyType", effectiveMs);
    }

    private void doLoaderPut(final RefDataLoader refDataLoader,
                             final MapDefinition mapDefinition,
                             final String key,
                             final RefDataValue refDataValue) {
        writeValue(refDataValue);
        refDataLoader.put(mapDefinition, key, stagingValueOutputStream);
    }

    private void doLoaderPut(final RefDataLoader refDataLoader,
                             final MapDefinition mapDefinition,
                             final Range<Long> range,
                             final RefDataValue refDataValue) {
        writeValue(refDataValue);
        refDataLoader.put(mapDefinition, range, stagingValueOutputStream);
    }

    private void writeValue(final RefDataValue refDataValue) {
        stagingValueOutputStream.clear();
        try {
            if (refDataValue instanceof StringValue) {
                final StringValue stringValue = (StringValue) refDataValue;
                stagingValueOutputStream.write(stringValue.getValue());
                stagingValueOutputStream.setTypeId(StringValue.TYPE_ID);
            } else if (refDataValue instanceof FastInfosetValue) {
                stagingValueOutputStream.write(((FastInfosetValue) refDataValue).getByteBuffer());
                stagingValueOutputStream.setTypeId(FastInfosetValue.TYPE_ID);
            } else if (refDataValue instanceof NullValue) {
                stagingValueOutputStream.setTypeId(NullValue.TYPE_ID);
            } else {
                throw new RuntimeException("Unexpected type " + refDataValue.getClass().getSimpleName());
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error writing value: {}", e.getMessage()), e);
        }
    }

    private Meta createMeta(final String feedName) {
        return createMeta(null, feedName, StreamTypeNames.RAW_EVENTS);
    }

    private Meta createMeta(final Meta parent, final String feedName, final String typeName) {
        return securityContext.asProcessingUserResult(() -> {
            final Meta meta = metaService.create(createProps(parent, feedName, typeName));
            metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);
            return meta;
        });
    }

    private MetaProperties createProps(final Meta parent, final String feedName, final String typeName) {
        final long now = System.currentTimeMillis();
        return MetaProperties.builder()
                .parent(parent)
                .feedName(feedName)
                .typeName(typeName)
                .createMs(now)
                .build();
    }
}
