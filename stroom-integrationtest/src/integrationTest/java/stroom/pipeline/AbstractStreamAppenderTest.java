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

package stroom.pipeline;

import org.junit.Assert;
import stroom.docref.DocRef;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.data.store.impl.fs.serializable.RASegmentInputStream;
import stroom.data.meta.api.StreamMetaService;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractStreamAppenderTest extends AbstractAppenderTest {
    @Inject
    private StreamStore streamStore;
    @Inject
    private StreamMetaService streamMetaService;

    void test(final DocRef pipelineRef,
              final String dir,
              final String name,
              final String type,
              final String outputReference,
              final String encoding) {
        super.test(pipelineRef, dir, name, type, outputReference, encoding);

        final List<Stream> streams = streamMetaService.find(new FindStreamCriteria());
        Assert.assertEquals(1, streams.size());

        try {
            final long streamId = streams.get(0).getId();
            checkOuterData(streamId, type.equalsIgnoreCase("text"));
            checkInnerData(streamId, type.equalsIgnoreCase("text"));
            checkFull(streamId, outputReference);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void checkInnerData(final long streamId, final boolean text) throws IOException {
        if (text) {
            final String innerRef = "2013-04-09T00:00:50.000ZTestTestApachetest.test.com123.123.123.123firstuser1234/goodGETHTTP/1.0someagent200\n" +
                    "2013-04-09T00:00:50.000ZTestTestApachetest.test.com123.123.123.123lastuser1234/goodGETHTTP/1.0someagent200\n";

            checkInnerData(streamId, 143, innerRef);

        } else {
            final String innerRef = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                    "<Events xmlns=\"event-logging:3\"\n" +
                    "        xmlns:stroom=\"stroom\"\n" +
                    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "        xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\"\n" +
                    "        Version=\"3.0.0\">\n" +
                    "   <Event>\n" +
                    "      <EventTime>\n" +
                    "         <TimeCreated>2013-04-09T00:00:50.000Z</TimeCreated>\n" +
                    "      </EventTime>\n" +
                    "      <EventSource>\n" +
                    "         <System>\n" +
                    "            <Name>Test</Name>\n" +
                    "            <Environment>Test</Environment>\n" +
                    "         </System>\n" +
                    "         <Generator>Apache</Generator>\n" +
                    "         <Device>\n" +
                    "            <HostName>test.test.com</HostName>\n" +
                    "         </Device>\n" +
                    "         <Client>\n" +
                    "            <IPAddress>123.123.123.123</IPAddress>\n" +
                    "         </Client>\n" +
                    "         <User>\n" +
                    "            <Id>firstuser</Id>\n" +
                    "         </User>\n" +
                    "      </EventSource>\n" +
                    "      <EventDetail>\n" +
                    "         <TypeId>1234</TypeId>\n" +
                    "         <View>\n" +
                    "            <Resource>\n" +
                    "               <URL>/good</URL>\n" +
                    "               <HTTPMethod>GET</HTTPMethod>\n" +
                    "               <HTTPVersion>HTTP/1.0</HTTPVersion>\n" +
                    "               <UserAgent>someagent</UserAgent>\n" +
                    "               <ResponseCode>200</ResponseCode>\n" +
                    "            </Resource>\n" +
                    "         </View>\n" +
                    "      </EventDetail>\n" +
                    "   </Event>\n" +
                    "   <Event>\n" +
                    "      <EventTime>\n" +
                    "         <TimeCreated>2013-04-09T00:00:50.000Z</TimeCreated>\n" +
                    "      </EventTime>\n" +
                    "      <EventSource>\n" +
                    "         <System>\n" +
                    "            <Name>Test</Name>\n" +
                    "            <Environment>Test</Environment>\n" +
                    "         </System>\n" +
                    "         <Generator>Apache</Generator>\n" +
                    "         <Device>\n" +
                    "            <HostName>test.test.com</HostName>\n" +
                    "         </Device>\n" +
                    "         <Client>\n" +
                    "            <IPAddress>123.123.123.123</IPAddress>\n" +
                    "         </Client>\n" +
                    "         <User>\n" +
                    "            <Id>lastuser</Id>\n" +
                    "         </User>\n" +
                    "      </EventSource>\n" +
                    "      <EventDetail>\n" +
                    "         <TypeId>1234</TypeId>\n" +
                    "         <View>\n" +
                    "            <Resource>\n" +
                    "               <URL>/good</URL>\n" +
                    "               <HTTPMethod>GET</HTTPMethod>\n" +
                    "               <HTTPVersion>HTTP/1.0</HTTPVersion>\n" +
                    "               <UserAgent>someagent</UserAgent>\n" +
                    "               <ResponseCode>200</ResponseCode>\n" +
                    "            </Resource>\n" +
                    "         </View>\n" +
                    "      </EventDetail>\n" +
                    "   </Event>\n" +
                    "</Events>\n";

            checkInnerData(streamId, 143, innerRef);
        }
    }

    private void checkOuterData(final long streamId, final boolean text) throws IOException {
        if (text) {
            final String outerRef = "";

            checkOuterData(streamId, 143, outerRef);

        } else {
            final String outerRef = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                    "<Events xmlns=\"event-logging:3\"\n" +
                    "        xmlns:stroom=\"stroom\"\n" +
                    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "        xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\"\n" +
                    "        Version=\"3.0.0\">\n" +
                    "</Events>\n";

            checkOuterData(streamId, 143, outerRef);
        }
    }

    private void checkFull(final long streamId, final String outputReference) {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(outputReference);
        final String refData = StreamUtil.fileToString(refFile);
        final String data = StreamUtil.streamToString(streamSource.getInputStream());
        Assert.assertEquals(refData, data);
        streamStore.closeStreamSource(streamSource);
    }

    private void checkOuterData(final long streamId, final int count, final String ref) throws IOException {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(streamSource);

        Assert.assertEquals(count, segmentInputStream.count());

        // Include the first and last segment only.
        segmentInputStream.include(0);
        segmentInputStream.include(segmentInputStream.count() - 1);

        final String data = StreamUtil.streamToString(segmentInputStream);
        Assert.assertEquals(ref, data);

        streamStore.closeStreamSource(streamSource);
    }

    private void checkInnerData(final long streamId, final int count, final String ref) throws IOException {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(streamSource);

        Assert.assertEquals(count, segmentInputStream.count());

        // Include the first and last segment only.
        segmentInputStream.include(0);
        segmentInputStream.include(1);
        segmentInputStream.include(segmentInputStream.count() - 2);
        segmentInputStream.include(segmentInputStream.count() - 1);

        final String data = StreamUtil.streamToString(segmentInputStream);
        Assert.assertEquals(ref, data);

        streamStore.closeStreamSource(streamSource);
    }
}