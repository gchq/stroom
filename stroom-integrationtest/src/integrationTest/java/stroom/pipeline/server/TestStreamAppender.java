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
 */

package stroom.pipeline.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;

import javax.annotation.Resource;
import java.util.List;

public class TestStreamAppender extends AbstractStreamAppenderTest {
    @Resource
    private StreamStore streamStore;

    @Test
    public void testXML() throws Exception {
        test("TestStreamAppender", "XML");
        validateOuptut("TestStreamAppender/TestStreamAppender_XML.out", "XML");
    }

    @Test
    public void testXMLRolling() throws Exception {
        test("TestStreamAppender", "XML_Rolling");

        final List<Stream> streams = streamStore.find(new FindStreamCriteria());
        final long streamId = streams.get(0).getId();
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(streamSource.getInputStream());
        StreamUtil.streamToString(byteCountInputStream);
        Assert.assertEquals(1198, byteCountInputStream.getCount());
        streamStore.closeStreamSource(streamSource);
    }

    @Test
    public void testText() throws Exception {
        test("TestStreamAppender", "Text");
        validateOuptut("TestStreamAppender/TestStreamAppender_Text.out", "Text");
    }
}