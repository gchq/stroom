/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamtask.server;

import stroom.AbstractProcessIntegrationTest;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.streamstore.server.MockStreamStore;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.StreamType;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomZipEntry;
import stroom.util.zip.StroomZipFileType;
import stroom.util.zip.HeaderMap;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Resource;
import java.io.IOException;

public class TestStreamTargetStroomStreamHandler extends AbstractProcessIntegrationTest {
    @Resource
    private StreamStore streamStore;
    @Resource
    private FeedService feedService;

    /**
     * This test is used to check that feeds that are set to be reference feeds
     * do not aggregate streams.
     *
     * @throws IOException
     */
    @Test
    public void testReferenceNonAggregation() throws IOException {
        ((MockStreamStore) streamStore).clear();

        Feed feed = feedService.create(null, "TEST_FEED");
        feed.setReference(true);
        feed = feedService.save(feed);

        final HeaderMap headerMap = new HeaderMap();
        headerMap.put(StroomHeaderArguments.FEED, "TEST_FEED");

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedService, null, feed, StreamType.RAW_REFERENCE);
        streamTargetStroomStreamHandler.handleHeader(headerMap);
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

        Assert.assertEquals(2, ((MockStreamStore) streamStore).getStreamStoreCount());
    }

    /**
     * This test is used to check that separate streams are created if the feed
     * changes.
     *
     * @throws IOException
     */
    @Test
    public void testFeedChange() throws IOException {
        ((MockStreamStore) streamStore).clear();

        final Feed feed1 = feedService.create(null, "TEST_FEED1");
        final Feed feed2 = feedService.create(null, "TEST_FEED2");

        final HeaderMap headerMap1 = new HeaderMap();
        headerMap1.put(StroomHeaderArguments.FEED, "TEST_FEED1");

        final HeaderMap headerMap2 = new HeaderMap();
        headerMap2.put(StroomHeaderArguments.FEED, "TEST_FEED2");

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedService, null, feed1, StreamType.RAW_EVENTS);
        streamTargetStroomStreamHandler.handleHeader(headerMap1);
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "1", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleHeader(headerMap2);
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Meta));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Context));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, "2", StroomZipFileType.Data));
        streamTargetStroomStreamHandler.handleEntryEnd();
        streamTargetStroomStreamHandler.close();

        Assert.assertEquals(2, ((MockStreamStore) streamStore).getStreamStoreCount());
    }

    /**
     * This test is used to check that streams are aggregated if the feed is not
     * reference.
     *
     * @throws IOException
     */
    @Test
    public void testFeedAggregation() throws IOException {
        ((MockStreamStore) streamStore).clear();

        final Feed feed = feedService.create(null, "TEST_FEED");

        final HeaderMap headerMap = new HeaderMap();
        headerMap.put(StroomHeaderArguments.FEED, "TEST_FEED");

        final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(streamStore,
                feedService, null, feed, StreamType.RAW_EVENTS);
        streamTargetStroomStreamHandler.handleHeader(headerMap);
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

        Assert.assertEquals(1, ((MockStreamStore) streamStore).getStreamStoreCount());
    }
}
