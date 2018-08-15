/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.refdata;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.Persistence;
import stroom.docstore.Store;
import stroom.docstore.memory.MemoryPersistence;
import stroom.entity.DocumentPermissionCache;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Range;
import stroom.feed.MockFeedService;
import stroom.feed.shared.Feed;
import stroom.guice.PipelineScopeRunnable;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineStoreImpl;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.refdata.offheapstore.AbstractRefDataOffHeapStoreTest;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.StringValue;
import stroom.security.MockSecurityContext;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.streamstore.shared.StreamType;
import stroom.util.cache.CacheManager;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestReferenceData extends AbstractRefDataOffHeapStoreTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceData.class);
    private static final String USER_1 = "user1";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";
    private static final String VALUE_3 = "value3";
    private static final String VALUE_4 = "value4";
    private static final String SID_TO_PF_1 = "SID_TO_PF_1";
    private static final String SID_TO_PF_2 = "SID_TO_PF_2";
    private static final String SID_TO_PF_3 = "SID_TO_PF_3";
    private static final String SID_TO_PF_4 = "SID_TO_PF_4";
    public static final String IP_TO_LOC_MAP_NAME = "IP_TO_LOC_MAP_NAME";
    public static final String VALUE_THERE = "there";

    private final MockFeedService feedService = new MockFeedService();

    private final SecurityContext securityContext = new MockSecurityContext();
    private final Persistence persistence = new MemoryPersistence();
    private final PipelineStore pipelineStore = new PipelineStoreImpl(
            new Store<>(persistence, securityContext), securityContext, persistence);

    @Mock
    private DocumentPermissionCache mockDocumentPermissionCache;
    @Mock
    private ReferenceDataLoader mockReferenceDataLoader;

    //    @Inject
//    private RefDataStoreHolder refDataStoreHolder;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    @Before
    public void setup() {
        super.setup();
//        refDataStoreHolder = injector.getInstance(RefDataStoreHolder.class);
        injector.injectMembers(this);

        MockitoAnnotations.initMocks(this);

        Mockito.when(mockDocumentPermissionCache.hasDocumentPermission(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true);
    }

    private PipelineDoc buildPipelineDoc(PipelineReference pipelineReference) {
        PipelineDoc pipelineDoc = new PipelineDoc();
        pipelineDoc.setUuid(pipelineReference.getPipeline().getUuid());
        pipelineDoc.setVersion(UUID.randomUUID().toString());
        return pipelineDoc;
    }

    private RefDataStoreHolder getRefDataStoreHolder() {
        return injector.getInstance(RefDataStoreHolder.class);
    }

    @Test
    public void testSimple() {
        pipelineScopeRunnable.scopeRunnable(() -> {

            final Feed feed1 = feedService.create("TEST_FEED_1");
            final Feed feed2 = feedService.create("TEST_FEED_2");

            final DocRef pipeline1Ref = pipelineStore.createDocument("TEST_PIPELINE_1");
            final DocRef pipeline2Ref = pipelineStore.createDocument("TEST_PIPELINE_2");
            final PipelineDoc pipeline1Doc = new PipelineDoc();

            final List<PipelineReference> pipelineReferences = Arrays.asList(
                    new PipelineReference(pipeline1Ref, DocRefUtil.create(feed1), StreamType.REFERENCE.getName()),
                    new PipelineReference(pipeline2Ref, DocRefUtil.create(feed2), StreamType.REFERENCE.getName()));

            // build pipelineDoc objects for each pipelineReference
            final List<PipelineDoc> pipelineDocs = pipelineReferences.stream()
                    .map(this::buildPipelineDoc)
                    .collect(Collectors.toList());

            // Set up the effective streams to be used for each
            final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
            streamSet.add(new EffectiveStream(1, DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z")));
            streamSet.add(new EffectiveStream(2, DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z")));
            streamSet.add(new EffectiveStream(3, DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.000Z")));

            try (CacheManager cacheManager = new CacheManager()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                        cacheManager, null, null, null) {
                    @Override
                    protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        effectiveStreamCache,
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        getRefDataStoreHolder(),
                        new RefDataLoaderHolder(),
                        new Security(new MockSecurityContext()),
                        pipelineStore);

                Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addUserDataToMockReferenceDataLoader(
                        pipeline1Ref,
                        pipelineDocs.get(0),
                        streamSet,
                        Arrays.asList(SID_TO_PF_1, SID_TO_PF_2),
                        mockLoaderActionsMap);

                addUserDataToMockReferenceDataLoader(
                        pipeline2Ref,
                        pipelineDocs.get(1),
                        streamSet,
                        Arrays.asList(SID_TO_PF_3, SID_TO_PF_4),
                        mockLoaderActionsMap);

                // set up the mock loader to load the appropriate data when triggered by a lookup call
                Mockito.doAnswer(invocation -> {
                    RefStreamDefinition refStreamDefinition = invocation.getArgumentAt(0, RefStreamDefinition.class);

                    Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
                    action.run();
                    return null;
                }).when(mockReferenceDataLoader).load(Mockito.any(RefStreamDefinition.class));

                // perform lookups (which will trigger a load if required) and assert the result
                checkData(referenceData, pipelineReferences, SID_TO_PF_1);
                checkData(referenceData, pipelineReferences, SID_TO_PF_2);
                checkData(referenceData, pipelineReferences, SID_TO_PF_3);
                checkData(referenceData, pipelineReferences, SID_TO_PF_4);

            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private void addUserDataToMockReferenceDataLoader(final DocRef pipelineRef,
                                                      final PipelineDoc pipelineDoc,
                                                      final TreeSet<EffectiveStream> effectiveStreams,
                                                      final List<String> mapNames,
                                                      final Map<RefStreamDefinition, Runnable> mockLoaderActions) {

        for (EffectiveStream effectiveStream : effectiveStreams) {

            RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineRef, pipelineStore.readDocument(pipelineRef).getVersion(), effectiveStream.getStreamId());


            mockLoaderActions.put(refStreamDefinition, () -> {
                refDataStore.doWithLoaderUnlessComplete(
                        refStreamDefinition, effectiveStream.getEffectiveMs(), refDataLoader -> {

                            refDataLoader.initialise(false);
                            for (String mapName : mapNames) {
                                MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                                refDataLoader.put(
                                        mapDefinition,
                                        USER_1,
                                        buildValue(mapDefinition, VALUE_1));
                                refDataLoader.put(
                                        mapDefinition,
                                        "user2",
                                        buildValue(mapDefinition, VALUE_2));
                            }
                            refDataLoader.completeProcessing();
                        });
            });
        }
    }

    private void addKeyValueDataToMockReferenceDataLoader(final DocRef pipelineRef,
                                                          final PipelineDoc pipelineDoc,
                                                          final TreeSet<EffectiveStream> effectiveStreams,
                                                          final List<Tuple3<String, String, String>> mapKeyValueTuples,
                                                          final Map<RefStreamDefinition, Runnable> mockLoaderActions) {

        for (EffectiveStream effectiveStream : effectiveStreams) {

            RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineRef, pipelineStore.readDocument(pipelineRef).getVersion(), effectiveStream.getStreamId());


            mockLoaderActions.put(refStreamDefinition, () -> {
                refDataStore.doWithLoaderUnlessComplete(
                        refStreamDefinition, effectiveStream.getEffectiveMs(), refDataLoader -> {

                            refDataLoader.initialise(false);
                            for (Tuple3<String, String, String> mapKeyValueTuple : mapKeyValueTuples) {
                                String mapName = mapKeyValueTuple._1();
                                String key = mapKeyValueTuple._2();
                                String value = mapKeyValueTuple._3();
                                MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                                refDataLoader.put(mapDefinition, key, StringValue.of(value));
                            }
                            refDataLoader.completeProcessing();
                        });
            });
        }
    }

    private void addRangeValueDataToMockReferenceDataLoader(final DocRef pipelineRef,
                                                            final PipelineDoc pipelineDoc,
                                                            final TreeSet<EffectiveStream> effectiveStreams,
                                                            final List<Tuple3<String, Range<Long>, String>> mapRangeValueTuples,
                                                            final Map<RefStreamDefinition, Runnable> mockLoaderActions) {

        for (EffectiveStream effectiveStream : effectiveStreams) {

            RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineRef, pipelineStore.readDocument(pipelineRef).getVersion(), effectiveStream.getStreamId());


            mockLoaderActions.put(refStreamDefinition, () -> {
                refDataStore.doWithLoaderUnlessComplete(
                        refStreamDefinition, effectiveStream.getEffectiveMs(), refDataLoader -> {

                            refDataLoader.initialise(false);
                            for (Tuple3<String, Range<Long>, String> mapKeyValueTuple : mapRangeValueTuples) {
                                String mapName = mapKeyValueTuple._1();
                                Range<Long> range = mapKeyValueTuple._2();
                                String value = mapKeyValueTuple._3();
                                MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                                refDataLoader.put(mapDefinition, range, StringValue.of(value));
                            }
                            refDataLoader.completeProcessing();
                        });
            });
        }
    }

    private StringValue buildValue(MapDefinition mapDefinition, String value) {
        return StringValue.of(
                mapDefinition.getRefStreamDefinition().getPipelineDocRef().getUuid() + "|" +
                        mapDefinition.getRefStreamDefinition().getStreamId() + "|" +
                        mapDefinition.getMapName() + "|" +
                        value
        );
    }

    private void checkData(final ReferenceData data, final List<PipelineReference> pipelineReferences,
                           final String mapName) {
        String expectedValuePart = VALUE_1;

        Optional<String> optFoundValue;

        optFoundValue = lookup(data, pipelineReferences, "2010-01-01T09:47:00.111Z", mapName, USER_1);
        doValueAsserts(optFoundValue, 3, mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2015-01-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, 3, mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2009-10-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, 2, mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, 2, mapName, expectedValuePart);

        optFoundValue = lookup(data, pipelineReferences, "2008-01-01T09:47:00.000Z", mapName, USER_1);
        doValueAsserts(optFoundValue, 1, mapName, expectedValuePart);

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
        assertThat(optFoundValue).isNotEmpty();
        String[] parts = optFoundValue.get().split("\\|");
        assertThat(parts).hasSize(4);
        assertThat(Long.parseLong(parts[1])).isEqualTo(expectedStreamId);
        assertThat(parts[2]).isEqualTo(expectedMapName);
        assertThat(parts[3]).isEqualTo(expectedValuePart);
    }

    @Test
    public void testNestedMaps() {
        pipelineScopeRunnable.scopeRunnable(() -> {

            Feed feed1 = feedService.create("TEST_FEED_V1");
            feed1.setReference(true);
            feed1 = feedService.save(feed1);

            final DocRef pipelineRef = pipelineStore.createDocument("12345");
            final PipelineReference pipelineReference = new PipelineReference(pipelineRef,
                    DocRefUtil.create(feed1), StreamType.REFERENCE.getName());
            final List<PipelineReference> pipelineReferences = Collections.singletonList(pipelineReference);
            final PipelineDoc pipelineDoc = buildPipelineDoc(pipelineReference);

            final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
            streamSet.add(new EffectiveStream(0, 0L));
            try (CacheManager cacheManager = new CacheManager()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                    @Override
                    protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        effectiveStreamCache,
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        getRefDataStoreHolder(),
                        new RefDataLoaderHolder(),
                        new Security(new MockSecurityContext()),
                        pipelineStore);

                Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addKeyValueDataToMockReferenceDataLoader(
                        pipelineRef,
                        pipelineDoc,
                        streamSet,
                        Arrays.asList(
                                Tuple.of("CARD_NUMBER_TO_PF_NUMBER", "011111", "091111"),
                                Tuple.of("NUMBER_TO_SID", "091111", USER_1)
                        ),
                        mockLoaderActionsMap);

                Mockito.doAnswer(invocation -> {
                    RefStreamDefinition refStreamDefinition = invocation.getArgumentAt(0, RefStreamDefinition.class);

                    Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
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
    public void testRange() {
        pipelineScopeRunnable.scopeRunnable(() -> {

            Feed feed1 = feedService.create("TEST_FEED_V1");
            feed1.setReference(true);
            feed1 = feedService.save(feed1);

            final DocRef pipelineRef = pipelineStore.createDocument("12345");
            final PipelineReference pipelineReference = new PipelineReference(pipelineRef,
                    DocRefUtil.create(feed1), StreamType.REFERENCE.getName());
            final List<PipelineReference> pipelineReferences = Collections.singletonList(pipelineReference);
            final PipelineDoc pipelineDoc = buildPipelineDoc(pipelineReference);

            final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
            streamSet.add(new EffectiveStream(0, 0L));
            try (CacheManager cacheManager = new CacheManager()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                    @Override
                    protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };

                final ReferenceData referenceData = new ReferenceData(
                        effectiveStreamCache,
                        new FeedHolder(),
                        null,
                        null,
                        mockDocumentPermissionCache,
                        mockReferenceDataLoader,
                        getRefDataStoreHolder(),
                        new RefDataLoaderHolder(),
                        new Security(new MockSecurityContext()),
                        pipelineStore);

                Map<RefStreamDefinition, Runnable> mockLoaderActionsMap = new HashMap<>();

                // Add multiple reference data items to prove that looping over maps works.
                addRangeValueDataToMockReferenceDataLoader(
                        pipelineRef,
                        pipelineDoc,
                        streamSet,
                        Arrays.asList(
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(2L, 30L), VALUE_1),
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(40L, 41L), VALUE_2),
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(500L, 2000L), VALUE_3),
                                Tuple.of(IP_TO_LOC_MAP_NAME, Range.of(3000L, 3001L), VALUE_4)),
                        mockLoaderActionsMap);

                Mockito.doAnswer(invocation -> {
                    RefStreamDefinition refStreamDefinition = invocation.getArgumentAt(0, RefStreamDefinition.class);

                    Runnable action = mockLoaderActionsMap.get(refStreamDefinition);
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
        Optional<String> optValue = lookup(referenceData, pipelineReferences, DateUtil.parseNormalDateTimeString(time), mapName, key);
        LOGGER.debug("Found {}", optValue.orElse("EMPTY"));
        return optValue;
    }

    private Optional<String> lookup(final ReferenceData referenceData,
                                    final List<PipelineReference> pipelineReferences,
                                    final long time,
                                    final String mapName,
                                    final String key) {
        final ReferenceDataResult result = new ReferenceDataResult();

        referenceData.ensureReferenceDataAvailability(pipelineReferences, LookupIdentifier.of(mapName, key, time), result);

        if (result.getRefDataValueProxy() != null) {
            return result.getRefDataValueProxy()
                    .supplyValue()
                    .flatMap(val -> Optional.of(((StringValue) val).getValue()));
        } else {
            return Optional.empty();
        }
    }
}
