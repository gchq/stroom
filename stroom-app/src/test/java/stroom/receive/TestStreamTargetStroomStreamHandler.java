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

package stroom.receive;


import org.junit.jupiter.api.Test;
import stroom.data.store.impl.mock.MockStore;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.docref.DocRef;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.StandardHeaderArguments;
import stroom.receive.common.StreamTargetStroomStreamHandler;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamTargetStroomStreamHandler extends AbstractProcessIntegrationTest {
    @Inject
    private MockStore streamStore;
    @Inject
    private FeedProperties feedProperties;
    @Inject
    private FeedStore feedStore;

    /**
     * This test is used to check that feeds that are set to be reference feeds
     * do not aggregate streams.
     *
     * @throws IOException
     */
    @Test
    void testReferenceNonAggregation() throws IOException {
        streamStore.clear();

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final DocRef feedRef = feedStore.createDocument(feedName);
        final FeedDoc feedDoc = feedStore.readDocument(feedRef);
        feedDoc.setReference(true);
        feedStore.writeDocument(feedDoc);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedProperties, null, feedName, StreamTypeNames.RAW_REFERENCE);
        streamTargetStroomStreamHandler.handleHeader(attributeMap);
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.close();

        assertThat(streamStore.getStreamStoreCount()).isEqualTo(2);
    }

    /**
     * This test is used to check that separate streams are created if the feed
     * changes.
     *
     * @throws IOException
     */
    @Test
    void testFeedChange() throws IOException {
        streamStore.clear();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.put(StandardHeaderArguments.FEED, feedName1);

        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put(StandardHeaderArguments.FEED, feedName2);

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedProperties, null, feedName1, StreamTypeNames.RAW_EVENTS);
        streamTargetStroomStreamHandler.handleHeader(attributeMap1);
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleHeader(attributeMap2);
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.close();

        assertThat(streamStore.getStreamStoreCount()).isEqualTo(2);
    }

    /**
     * This test is used to check that streams are aggregated if the feed is not
     * reference.
     *
     * @throws IOException
     */
    @Test
    void testFeedAggregation() throws IOException {
        streamStore.clear();

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedProperties, null, feedName, StreamTypeNames.RAW_EVENTS);
        streamTargetStroomStreamHandler.handleHeader(attributeMap);
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.close();

        assertThat(streamStore.getStreamStoreCount()).isEqualTo(1);
    }
}
