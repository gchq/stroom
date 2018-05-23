/*
 * Copyright 2018 Crown Copyright
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

package stroom.util;

import org.junit.Test;
import stroom.feed.shared.FeedDoc;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.io.IOException;

public class TestStreamGrepTool extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private StreamStore streamStore;

    @Test
    public void test() throws IOException {
        try {
            final FeedDoc feed = commonTestScenarioCreator.createSimpleFeed("TEST", "12345");

            addData(feed, "This is some test data to match on");
            addData(feed, "This is some test data to not match on");

            final StreamGrepTool streamGrepTool = new StreamGrepTool();
            streamGrepTool.setFeed(feed.getName());
            streamGrepTool.setMatch("to match on");
            streamGrepTool.run();

        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void addData(final FeedDoc feed, final String data) throws IOException {
        Stream stream = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null, System.currentTimeMillis());
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(data.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(streamTarget);
    }
}
