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
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
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
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.security.api.SecurityContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.Range;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestReferenceDataWithCache extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceDataWithCache.class);
    private static final String TEST_PIPELINE_1 = "TEST_PIPELINE_1";
    private static final String TEST_PIPELINE_2 = "TEST_PIPELINE_2";
    public static final String DUMMY_FEED = "DUMMY_FEED";
    public static final String DUMMY_TYPE = "DummyType";

    @Inject
    private FeedStore feedStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;
    @Inject
    private RefDataStoreFactory refDataStoreFactory;
    // Provider so we get new pipeline scoped deps each time
    @Inject
    private Provider<ReferenceData> referenceDataProvider;
    @Inject
    private EffectiveStreamService effectiveStreamService;
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

    private RefDataStore refDataStore;

    @BeforeEach
    void setup() {
        refDataStore = refDataStoreFactory.getOffHeapStore();
    }

    /**
     * Test.
     */
    @Test
    void testSimple() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final DocRef feed1 = feedStore.createDocument("TEST_FEED_1");
            final DocRef feed2 = feedStore.createDocument("TEST_FEED_2");

            final DocRef pipeline1Ref = pipelineStore.createDocument(TEST_PIPELINE_1);
            final DocRef pipeline2Ref = pipelineStore.createDocument(TEST_PIPELINE_2);

            final PipelineReference pipelineReference1 = new PipelineReference(pipeline1Ref,
                    feed1,
                    StreamTypeNames.REFERENCE);
            final PipelineReference pipelineReference2 = new PipelineReference(pipeline2Ref,
                    feed2,
                    StreamTypeNames.REFERENCE);

            final List<PipelineReference> pipelineReferences = new ArrayList<>();
            pipelineReferences.add(pipelineReference1);
            pipelineReferences.add(pipelineReference2);

            final Map<String, EffectiveMetaSet> effectiveMetasByFeed = new HashMap<>();

            for (final DocRef feedDocRef : List.of(feed1, feed2)) {
                final EffectiveMetaSet streamSet = EffectiveMetaSet.builder(feedDocRef.getName(), DUMMY_TYPE)
                        .add(
                                createMeta(feedDocRef.getName()).getId(),
                                DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z"))
                        .add(
                                createMeta(feedDocRef.getName()).getId(),
                                DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z"))
                        .add(
                                createMeta(feedDocRef.getName()).getId(),
                                DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.000Z"))
                        .build();
                effectiveMetasByFeed.put(feedDocRef.getName(), streamSet);
            }

//            final TreeSet<EffectiveMeta> streamSet = new TreeSet<>();
//            streamSet.add(EFFECTIVE_STREAM_1);
//            streamSet.add(EFFECTIVE_STREAM_2);
//            streamSet.add(EFFECTIVE_STREAM_3);

            try (final CacheManager cacheManager = new CacheManagerImpl()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                        null,
                        null,
                        null,
                        ReferenceDataConfig::new) {
                    @Override
                    public EffectiveMetaSet create(final EffectiveStreamKey key) {
                        return effectiveMetasByFeed.get(key.getFeed());
                    }
                };
                final ReferenceData referenceData = referenceDataProvider.get();
                referenceData.setEffectiveStreamCache(effectiveStreamCache);


                // Add multiple reference data items to prove that looping over maps
                // works.
                addData(
                        pipeline1Ref,
                        effectiveMetasByFeed.get(feed1.getName()).asList(),
                        new String[]{"USERNAME_TO_PAYROLL_NO_1", "USERNAME_TO_PAYROLL_NO_2"});
                addData(pipeline2Ref,
                        effectiveMetasByFeed.get(feed2.getName()).asList(),
                        new String[]{"USERNAME_TO_PAYROLL_NO_3", "USERNAME_TO_PAYROLL_NO_4"});
                checkData(referenceData, pipelineReferences, "USERNAME_TO_PAYROLL_NO_1");
                checkData(referenceData, pipelineReferences, "USERNAME_TO_PAYROLL_NO_2");
                checkData(referenceData, pipelineReferences, "USERNAME_TO_PAYROLL_NO_3");
                checkData(referenceData, pipelineReferences, "USERNAME_TO_PAYROLL_NO_4");
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private RefStreamDefinition getRefStreamDefinition(final DocRef pipelineRef, final long streamId) {
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final String version = pipelineDoc.getVersion();
        return new RefStreamDefinition(pipelineRef, version, streamId);
    }

    private void addData(final DocRef pipelineRef,
                         final List<EffectiveMeta> effectiveMetas,
                         final String[] mapNames) {
        EffectiveMeta effectiveStream = effectiveMetas.get(0);
        final RefStreamDefinition refStreamDefinition1 = getRefStreamDefinition(pipelineRef, effectiveStream.getId());

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition1,
                effectiveStream.getEffectiveMs(),
                refDataLoader -> {
                    refDataLoader.initialise(false);
                    for (final String mapName : mapNames) {
                        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition1, mapName);
                        doLoaderPut(refDataLoader, mapDefinition, "user1", StringValue.of("1111"));
                        doLoaderPut(refDataLoader, mapDefinition, "user2", StringValue.of("2222"));
                    }
                    refDataLoader.completeProcessing();
                });

        effectiveStream = effectiveMetas.get(1);
        final RefStreamDefinition refStreamDefinition2 = getRefStreamDefinition(pipelineRef, effectiveStream.getId());

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition2,
                effectiveStream.getEffectiveMs(),
                refDataLoader -> {
                    refDataLoader.initialise(false);
                    for (final String mapName : mapNames) {
                        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition2, mapName);
                        doLoaderPut(refDataLoader, mapDefinition, "user1", StringValue.of("A1111"));
                        doLoaderPut(refDataLoader, mapDefinition, "user2", StringValue.of("A2222"));
                    }
                    refDataLoader.completeProcessing();
                });

        effectiveStream = effectiveMetas.get(2);
        final RefStreamDefinition refStreamDefinition3 = getRefStreamDefinition(pipelineRef, effectiveStream.getId());

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition3,
                effectiveStream.getEffectiveMs(),
                refDataLoader -> {
                    refDataLoader.initialise(false);
                    for (final String mapName : mapNames) {
                        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition3, mapName);
                        doLoaderPut(refDataLoader, mapDefinition, "user1", StringValue.of("B1111"));
                        doLoaderPut(refDataLoader, mapDefinition, "user2", StringValue.of("B2222"));
                    }
                    refDataLoader.completeProcessing();
                });
    }

    private void checkData(final ReferenceData data,
                           final List<PipelineReference> pipelineReferences,
                           final String mapName) {
        assertThat(lookup(data, pipelineReferences, "2010-01-01T09:47:00.111Z", mapName, "user1"))
                .isEqualTo("B1111");
        assertThat(lookup(data, pipelineReferences, "2015-01-01T09:47:00.000Z", mapName, "user1"))
                .isEqualTo("B1111");
        assertThat(lookup(data, pipelineReferences, "2009-10-01T09:47:00.000Z", mapName, "user1"))
                .isEqualTo("A1111");
        assertThat(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1"))
                .isEqualTo("A1111");
        assertThat(lookup(data, pipelineReferences, "2008-01-01T09:47:00.000Z", mapName, "user1"))
                .isEqualTo("1111");

        assertThat(lookup(data, pipelineReferences, "2006-01-01T09:47:00.000Z", mapName, "user1"))
                .isNull();
        assertThat(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1_X"))
                .isNull();
        assertThat(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", "USERNAME_TO_PF_X", "user1"))
                .isNull();
    }

    private String addSuffix(final String str, final int id) {
        return str + id;
    }

    /**
     * Test.
     */
    @Test
    void testNestedMaps() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            final DocRef feedRef = feedStore.createDocument("TEST_FEED_V3");

            final DocRef pipelineRef = pipelineStore.createDocument(TEST_PIPELINE_1);
            final PipelineReference pipelineReference = new PipelineReference(
                    pipelineRef, feedRef, StreamTypeNames.REFERENCE);
            final List<PipelineReference> pipelineReferences = new ArrayList<>();
            pipelineReferences.add(pipelineReference);


            final EffectiveMeta effectiveStream = buildEffectiveMeta(createMeta(feedRef.getName()).getId(), 0L);
            final EffectiveMetaSet streamSet = EffectiveMetaSet.singleton(effectiveStream);

            try (final CacheManager cacheManager = new CacheManagerImpl()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                        null,
                        null,
                        null,
                        ReferenceDataConfig::new) {
                    @Override
                    public EffectiveMetaSet create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };
                final ReferenceData referenceData = referenceDataProvider.get();
                referenceData.setEffectiveStreamCache(effectiveStreamCache);

                final RefStreamDefinition refStreamDefinition = getRefStreamDefinition(pipelineRef,
                        effectiveStream.getId());

                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition,
                        effectiveStream.getEffectiveMs(),
                        refDataLoader -> {
                            refDataLoader.initialise(false);

                            // load the ref data
                            // cardNo => username => payrollNo => location
                            for (int i = 1; i <= 3; i++) {
                                MapDefinition mapDefinition = new MapDefinition(refStreamDefinition,
                                        "CARD_NUMBER_TO_USERNAME");
                                doLoaderPut(refDataLoader, mapDefinition,
                                        addSuffix("cardNo", i),
                                        StringValue.of(addSuffix("user", i)));

                                mapDefinition = new MapDefinition(refStreamDefinition, "USERNAME_TO_PAYROLL_NUMBER");
                                doLoaderPut(refDataLoader, mapDefinition,
                                        addSuffix("user", i),
                                        StringValue.of(addSuffix("payrollNo", i)));

                                mapDefinition = new MapDefinition(refStreamDefinition, "PAYROLL_NUMBER_TO_LOCATION");
                                doLoaderPut(refDataLoader, mapDefinition,
                                        addSuffix("payrollNo", i),
                                        StringValue.of(addSuffix("location", i)));
                            }

                            refDataLoader.completeProcessing();
                        });


                for (int i = 1; i <= 3; i++) {
                    LOGGER.info("Assertion iteration {}", i);

                    assertThat(
                            lookup(referenceData,
                                    pipelineReferences,
                                    0,
                                    "CARD_NUMBER_TO_USERNAME",
                                    addSuffix("cardNo", i)))
                            .isEqualTo(addSuffix("user", i));

                    assertThat(
                            lookup(referenceData,
                                    pipelineReferences,
                                    0,
                                    "USERNAME_TO_PAYROLL_NUMBER",
                                    addSuffix("user", i)))
                            .isEqualTo(addSuffix("payrollNo", i));

                    assertThat(
                            lookup(referenceData,
                                    pipelineReferences,
                                    0,
                                    "PAYROLL_NUMBER_TO_LOCATION",
                                    addSuffix("payrollNo", i)))
                            .isEqualTo(addSuffix("location", i));

                    // now do a nested lookup
                    assertThat(
                            lookup(referenceData, pipelineReferences, 0,
                                    "CARD_NUMBER_TO_USERNAME/USERNAME_TO_PAYROLL_NUMBER", addSuffix("cardNo", i)))
                            .isEqualTo(addSuffix("payrollNo", i));

                    // now do a double nested lookup
                    assertThat(
                            lookup(referenceData, pipelineReferences, 0,
                                    "CARD_NUMBER_TO_USERNAME/USERNAME_TO_PAYROLL_NUMBER/PAYROLL_NUMBER_TO_LOCATION",
                                    addSuffix("cardNo", i)))
                            .isEqualTo(addSuffix("location", i));
                }

            }
        });
    }

    private String lookup(final ReferenceData data,
                          final List<PipelineReference> pipelineReferences,
                          final String time,
                          final String mapName,
                          final String key) {
        return lookup(data, pipelineReferences, DateUtil.parseNormalDateTimeString(time), mapName, key);
    }

    private String lookup(final ReferenceData data,
                          final List<PipelineReference> pipelineReferences,
                          final long time,
                          final String mapName,
                          final String key) {
        final LookupIdentifier lookupIdentifier = LookupIdentifier.of(mapName, key, time);
        final ReferenceDataResult result = new ReferenceDataResult(lookupIdentifier);
        data.ensureReferenceDataAvailability(pipelineReferences, lookupIdentifier, result);
        if (result.getRefDataValueProxy() == null) {
            return null;
        }
        final RefDataValue refDataValue = result.getRefDataValueProxy()
                .flatMap(RefDataValueProxy::supplyValue)
                .orElse(null);
        if (refDataValue == null) {
            return null;
        } else {
            return ((StringValue) refDataValue).getValue();
        }
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
