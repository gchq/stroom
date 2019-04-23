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
import stroom.feed.shared.Feed;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import javax.annotation.Resource;
import java.nio.file.Files;

public class TestStreamDumpTool extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamStore streamStore;

    @Test
    public void test() {
        try {
            final Feed feed = commonTestScenarioCreator.createSimpleFeed("TEST", "12345");

            addData(feed, "This is some test data to dump1");
            addData(feed, "This is some test data to dump2");
            addData(feed, "This is some test data to dump3");

            final StreamDumpTool streamDumpTool = new StreamDumpTool();
            streamDumpTool.setOutputDir(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")));
            streamDumpTool.run();

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void addData(final Feed feed, final String data) throws Exception {
        Stream stream = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null, System.currentTimeMillis());
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(data.getBytes(StreamUtil.DEFAULT_CHARSET));

        final StreamTarget metaStreamTarget = streamTarget.addChildStream(StreamType.META);
        metaStreamTarget.getOutputStream().write(("FEED:" + feed.getName()).getBytes(StreamUtil.DEFAULT_CHARSET));

        final StreamTarget contextStreamTarget = streamTarget.addChildStream(StreamType.CONTEXT);
        contextStreamTarget.getOutputStream().write("context=blah".getBytes(StreamUtil.DEFAULT_CHARSET));

        streamStore.closeStreamTarget(streamTarget);
    }
}
