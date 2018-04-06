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

package stroom.streamstore.fs;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.feed.shared.Feed;
import stroom.streamstore.MockStreamStore;
import stroom.streamstore.StreamSource;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;

/**
 * <p>
 * Test the mock as it is quite complicated.
 * </p>
 */
@RunWith(StroomJUnit4ClassRunner.class)
public class TestMockStreamStore extends StroomUnitTest {
    @Test
    public void testExample() throws IOException {
        final MockStreamStore mockStreamStore = new MockStreamStore();

        mockStreamStore.clear();

        final Feed feed = new Feed();
        feed.setId(1L);

        final Stream stream1 = Stream.createStream(StreamType.EVENTS, feed, null);

        final StreamTarget streamTarget = mockStreamStore.openStreamTarget(stream1);
        streamTarget.getOutputStream().write("PARENT".getBytes(StreamUtil.DEFAULT_CHARSET));
        streamTarget.addChildStream(StreamType.SEGMENT_INDEX).getOutputStream()
                .write("CHILD".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(0, mockStreamStore.find(FindStreamCriteria.createWithStream(stream1)).size());

        mockStreamStore.closeStreamTarget(streamTarget);

        Assert.assertEquals(1, mockStreamStore.find(FindStreamCriteria.createWithStream(stream1)).size());

        final Stream reload = mockStreamStore.find(FindStreamCriteria.createWithStream(stream1)).get(0);

        final StreamSource streamSource = mockStreamStore.openStreamSource(reload.getId());

        String testMe = StreamUtil.streamToString(streamSource.getInputStream());

        Assert.assertEquals("PARENT", testMe);

        testMe = StreamUtil.streamToString(streamSource.getChildStream(StreamType.SEGMENT_INDEX).getInputStream());

        Assert.assertEquals("CHILD", testMe);
    }
}
