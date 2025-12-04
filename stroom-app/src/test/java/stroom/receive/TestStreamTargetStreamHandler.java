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

package stroom.receive;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.mock.MockStore;
import stroom.data.zip.StroomZipFileType;
import stroom.docref.DocRef;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamTargetStreamHandler extends AbstractProcessIntegrationTest {

    @Inject
    private MockStore streamStore;
    @Inject
    private FeedProperties feedProperties;
    @Inject
    private FeedStore feedStore;
    @Inject
    private MetaService metaService;
    @Inject
    private VolumeGroupNameProvider volumeGroupNameProvider;

    /**
     * This test is used to check that feeds that are set to be reference feeds
     * do not aggregate streams.
     *
     * @throws IOException
     */
    @Test
    void testReferenceNonAggregation() {
        streamStore.clear();

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final DocRef feedRef = feedStore.createDocument(feedName);
        final FeedDoc feedDoc = feedStore.readDocument(feedRef);
        feedDoc.setReference(true);
        feedStore.writeDocument(feedDoc);

        final AttributeMap attributeMap = new AttributeMap();
//        attributeMap.put(StandardHeaderArguments.FEED, feedName);
//        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_REFERENCE);

        final StreamTargetStreamHandlers streamTargetStreamHandlers = new StreamTargetStreamHandlers(
                streamStore,
                feedProperties,
                metaService,
                null,
                volumeGroupNameProvider);
        final ProgressHandler progressHandler = new ProgressHandler("Test");
        streamTargetStreamHandlers.handle(feedName, StreamTypeNames.RAW_EVENTS, attributeMap, handler -> {
            try {
                handler.addEntry("1" + StroomZipFileType.META.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("1" + StroomZipFileType.CONTEXT.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("1" + StroomZipFileType.DATA.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.META.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.CONTEXT.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.DATA.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertThat(streamStore.getStreamStoreCount()).isEqualTo(2);
    }

    /**
     * This test is used to check that separate streams are created if the feed
     * changes.
     *
     * @throws IOException
     */
    @Test
    void testFeedChange() {
        streamStore.clear();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.put(StandardHeaderArguments.FEED, feedName1);
        attributeMap1.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);

        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put(StandardHeaderArguments.FEED, feedName2);
        attributeMap2.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);

        final ProgressHandler progressHandler = new ProgressHandler("Test");
        final StreamTargetStreamHandlers streamTargetStreamHandlers = new StreamTargetStreamHandlers(
                streamStore,
                feedProperties,
                metaService,
                null,
                volumeGroupNameProvider);
        streamTargetStreamHandlers.handle(feedName1, StreamTypeNames.RAW_EVENTS, attributeMap1, handler -> {
            try {
                handler.addEntry("1" + StroomZipFileType.META.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("1" + StroomZipFileType.CONTEXT.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("1" + StroomZipFileType.DATA.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        streamTargetStreamHandlers.handle(feedName2, StreamTypeNames.RAW_EVENTS, attributeMap2, handler -> {
            try {
                handler.addEntry("2" + StroomZipFileType.META.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.CONTEXT.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.DATA.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertThat(streamStore.getStreamStoreCount()).isEqualTo(2);
    }

    /**
     * This test is used to check that streams are aggregated if the feed is not
     * reference.
     */
    @Test
    void testFeedAggregation() {
        streamStore.clear();

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);

        final ProgressHandler progressHandler = new ProgressHandler("Test");
        final StreamTargetStreamHandlers streamTargetStreamHandlers = new StreamTargetStreamHandlers(
                streamStore,
                feedProperties,
                metaService,
                null,
                volumeGroupNameProvider);
        streamTargetStreamHandlers.handle(feedName, StreamTypeNames.RAW_EVENTS, attributeMap, handler -> {
            try {
                handler.addEntry("1" + StroomZipFileType.META.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("1" + StroomZipFileType.CONTEXT.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("1" + StroomZipFileType.DATA.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.META.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.CONTEXT.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
                handler.addEntry("2" + StroomZipFileType.DATA.getDotExtension(),
                        new ByteArrayInputStream(new byte[0]), progressHandler);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertThat(streamStore.getStreamStoreCount()).isEqualTo(1);
    }
}
