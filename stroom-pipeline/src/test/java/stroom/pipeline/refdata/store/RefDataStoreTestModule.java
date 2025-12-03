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

package stroom.pipeline.refdata.store;

import stroom.cache.impl.CacheModule;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RefDataStoreTestModule extends AbstractModule {

    public static final long REF_STREAM_1_ID = 1L;
    public static final long REF_STREAM_3_ID = 3L;
    public static final long REF_STREAM_4_ID = 4L;
    public static final String FEED_1_NAME = "FEED1";
    public static final String FEED_1_UUID = "45a5ea9b-fc80-4685-beff-2fb1e84fb2bd";
    public static final String FEED_2_NAME = "FEED2";
    public static final String FEED_2_UUID = "9388a896-eb74-4474-ad4a-17dc7b8315a6";
    public static final String PIPE_1_UUID = "cd48049c-a7b1-4b64-bd7d-6cdc94159721";
    public static final String PIPE_2_UUID = "143abb82-1d8c-4261-b6dd-e8701fef08ef";
    public static final String PIPE_1_VER_1 = "a741e190-b2be-4b5b-910c-1480a1306b49";
    public static final String PIPE_1_VER_2 = "8c98dcd2-7b2d-4111-b278-961e9cabb885";
    public static final String PIPE_2_VER_1 = "7de88440-6fe3-4bd0-b1d6-c6082eb018db";
    // FEED_1, stream 1, pipe 1
    public static RefStreamDefinition REF_STREAM_1_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_1, REF_STREAM_1_ID);
    // FEED_1, stream 1, pipe 2
    public static RefStreamDefinition REF_STREAM_2_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_2, REF_STREAM_1_ID);
    // FEED_1, stream 3, pipe 1
    public static RefStreamDefinition REF_STREAM_3_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_1, REF_STREAM_3_ID);
    // FEED_2, stream 4, pipe 2
    public static RefStreamDefinition REF_STREAM_4_DEF = new RefStreamDefinition(
            PIPE_2_UUID, PIPE_2_VER_1, REF_STREAM_4_ID);

    public static final List<RefStreamDefinition> DEFAULT_REF_STREAM_DEFINITIONS = List.of(
            REF_STREAM_1_DEF,
            REF_STREAM_2_DEF,
            REF_STREAM_3_DEF,
            REF_STREAM_4_DEF);

    private final Provider<ReferenceDataConfig> referenceDataConfigSupplier;
    private final HomeDirProvider homeDirProvider;
    private final TempDirProvider tempDirProvider;

    private Map<Long, String> metaIdToFeedNameMap = new HashMap<>();
    private Map<DocRef, DocRef> docRefs = new HashMap<>();
    private Map<String, DocRefInfo> uuidToDocRefInfoMap = new HashMap<>();
    private Map<String, DocRef> feedNameToDocRefMap = new HashMap<>();

    public RefDataStoreTestModule(final Provider<ReferenceDataConfig> referenceDataConfigSupplier,
                                  final HomeDirProvider homeDirProvider,
                                  final TempDirProvider tempDirProvider) {
        this.referenceDataConfigSupplier = referenceDataConfigSupplier;
        this.homeDirProvider = homeDirProvider;
        this.tempDirProvider = tempDirProvider;
        addFeeds(FeedDoc.buildDocRef().name(FEED_1_NAME).uuid(FEED_1_UUID).build(),
                FeedDoc.buildDocRef().name(FEED_2_NAME).uuid(FEED_2_UUID).build());
        addMetaFeedAssociations(Map.of(
                // Two streams for the same feed
                REF_STREAM_1_ID, FEED_1_NAME,
                REF_STREAM_3_ID, FEED_1_NAME,
                REF_STREAM_4_ID, FEED_2_NAME));
    }

    @Override
    protected void configure() {
        if (referenceDataConfigSupplier != null) {
            bind(ReferenceDataConfig.class).toProvider(referenceDataConfigSupplier);
        } else {
            bind(ReferenceDataConfig.class).toInstance(new ReferenceDataConfig());
        }
        bind(HomeDirProvider.class).toInstance(homeDirProvider);
        bind(TempDirProvider.class).toInstance(tempDirProvider);

        install(new MockMetricsModule());
        install(new CacheModule());
        install(new MockSecurityContextModule());
        install(new MockTaskModule());
        install(new PipelineScopeModule());
        install(new RefDataStoreModule());

        bind(MetaService.class)
                .toInstance(getMockMetaService());
        bind(FeedStore.class)
                .toInstance(getMockFeedStore());
    }

    private FeedStore getMockFeedStore() {

        final FeedStore mockFeedStore = Mockito.mock(FeedStore.class);

        Mockito.doAnswer(invocation -> {
            final String uuid = invocation.getArgument(0, String.class);
            return uuidToDocRefInfoMap.get(uuid);
        }).when(mockFeedStore).info(Mockito.any());

        Mockito.doAnswer(invocation -> {
            final String feedName = invocation.getArgument(0, String.class);
            return List.of(feedNameToDocRefMap.get(feedName));
        }).when(mockFeedStore).findByName(
                Mockito.anyString());

        return mockFeedStore;
    }

    private MetaService getMockMetaService() {
        final MetaService mockMetaService = Mockito.mock(MetaService.class);

        Mockito.doAnswer(invocation -> {
            final Long metaId = invocation.getArgument(0, Long.class);
            final String feedName = metaIdToFeedNameMap.get(metaId);
            if (feedName == null) {
                throw new RuntimeException(LogUtil.message(
                        "No mapping set up for metaId {}. See addMetaFeedAssociation()", metaId));
            } else {
                final Meta mockMeta = Mockito.mock(Meta.class);
                Mockito.when(mockMeta.getFeedName())
                        .thenReturn(feedName);
                return mockMeta;
            }
        }).when(mockMetaService).getMeta(Mockito.anyLong());

        return mockMetaService;
    }

    public void addMetaFeedAssociations(final Map<Long, String> metaIdToFeedNameMap) {
        if (metaIdToFeedNameMap != null) {
            this.metaIdToFeedNameMap.putAll(metaIdToFeedNameMap);
        }
    }

    public void addMetaFeedAssociation(final long refStreamId, final String feedName) {
        Objects.requireNonNull(feedName);
        metaIdToFeedNameMap.put(refStreamId, feedName);
    }

    public void addFeeds(final DocRef... docRefs) {
        if (docRefs != null) {
            for (final DocRef docRef : docRefs) {
                Objects.requireNonNull(docRef);
                if (!docRef.getType().equals(FeedDoc.TYPE)) {
                    throw new RuntimeException(LogUtil.message("Invalid type " + docRef.getType()));
                }
                this.docRefs.put(docRef, docRef);
                this.uuidToDocRefInfoMap.put(docRef.getUuid(), new DocRefInfo(
                        docRef, 0L, 0L, "user", "user", null));
                this.feedNameToDocRefMap.put(docRef.getName(), docRef);
            }
        }
    }


    // --------------------------------------------------------------------------------


    public record MetaFeedAssociation(
            long metaId,
            String feedName) {

    }
}
