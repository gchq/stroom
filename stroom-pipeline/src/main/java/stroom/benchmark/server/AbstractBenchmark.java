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

package stroom.benchmark.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.util.XMLUtil;
import stroom.entity.shared.BaseResultList;
import stroom.feed.shared.Feed;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.ExpressionUtil;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.StreamUtil;
import stroom.util.task.TaskMonitor;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public abstract class AbstractBenchmark {
    // FIXME : Do something with this....

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBenchmark.class);

    @Resource
    private StreamStore streamStore;
    @Resource
    private TaskMonitor taskMonitor;

    public static int getRandomSkewed() {
        return (int) (Math.exp(Math.random() * 10));
    }

    protected boolean isTerminated() {
        return taskMonitor.isTerminated();
    }

    public void abortDueToTimeout() {
        taskMonitor.terminate();
    }

    protected void info(final Object... args) {
        taskMonitor.info(args);
        Arrays.asList(args).forEach(arg -> LOGGER.info(arg.toString()));
    }

    protected void infoInterval(final Object... args) {
        taskMonitor.info(args);
        //TODO logger in an interval
        Arrays.asList(args).forEach(arg -> LOGGER.info(arg.toString()));
    }

    protected Stream writeData(final Feed feed, final StreamType streamType, final String data) {
        // Add the associated data to the stream store.
        final Stream stream = Stream.createStream(streamType, feed, System.currentTimeMillis());

        final StreamTarget dataTarget = streamStore.openStreamTarget(stream);

        final InputStream dataInputStream = StreamUtil.stringToStream(data);

        final RASegmentOutputStream dataOutputStream = new RASegmentOutputStream(dataTarget);

        final RawInputSegmentWriter dataWriter = new RawInputSegmentWriter();
        dataWriter.write(dataInputStream, dataOutputStream);

        streamStore.closeStreamTarget(dataTarget);

        return dataTarget.getStream();
    }

    protected String readData(final long streamId) throws IOException {
        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final InputStream dataInputStream = streamSource.getInputStream();
        final String data = StreamUtil.streamToString(dataInputStream, true);
        streamStore.closeStreamSource(streamSource);
        return data;
    }

    protected String stripChangingContent(final String s) {
        final StringBuilder output = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.startsWith("<Event Id=\"", i)) {
                output.append("<Event Id=\"XXXXX\">");
                i = s.indexOf('>', i);
                continue;
            }
            if (s.startsWith("<TimeCreated>", i)) {
                output.append("<TimeCreated>XXXXX</TimeCreated>");
                i = s.indexOf("</TimeCreated>", i) + "</TimeCreated>".length() - 1;
                continue;
            }
            if (s.startsWith("<Id>", i)) {
                output.append("<Id>XXXXX</Id>");
                i = s.indexOf("</Id>", i) + "</Id>".length() - 1;
                continue;
            }

            output.append(s.charAt(i));
        }
        return output.toString();
    }

    protected void verifyData(final Feed feed, final String verificationString) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
        builder.addTerm(StreamDataSource.FEED, Condition.EQUALS, feed.getName());
        if (feed.isReference()) {
            builder.addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamType.REFERENCE.getDisplayValue());
        } else {
            builder.addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, StreamType.EVENTS.getDisplayValue());
        }
        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(builder.build());
        final BaseResultList<Stream> streams = streamStore.find(criteria);
        final Stream targetStream = streams.getFirst();

        // Get back translated result.
        final StreamSource target = streamStore.openStreamSource(targetStream.getId());
        String xml = StreamUtil.streamToString(target.getInputStream(), true);
        streamStore.closeStreamSource(target);

        // Pretty print the xml.
        xml = XMLUtil.prettyPrintXML(xml);

        // Get rid of event ids.
        xml = stripChangingContent(xml);

        if (!verificationString.equals(xml)) {
            throw new RuntimeException("Data verification failure!");
        }
    }

    protected void deleteData(final Feed... feeds) {
        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(ExpressionUtil.createFeedsExpression(feeds));
        streamStore.findDelete(criteria);
    }

    protected String createReferenceData(final int recordCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("FileNo,Country,Site,Building,Floor,Room,Desk\n");

        for (int i = 0; i < recordCount && !taskMonitor.isTerminated(); i++) {
            sb.append(i);
            sb.append(",UK,Site ");
            sb.append(i);
            sb.append(",Main,1,A,100\n");
        }

        return sb.toString();
    }

    protected String createEventData(final int recordCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Date,Time,FileNo,LineNo,User,Message\n");

        final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy,HH:mm:ss");

        for (int i = 0; i < recordCount && !taskMonitor.isTerminated(); i++) {
            sb.append(df.format(new Date()));
            sb.append(",");
            sb.append(i);
            sb.append(",1,user");
            sb.append(getRandomSkewed());
            sb.append(",Some message 1\n");
        }

        return sb.toString();
    }

    protected String createReferenceVerificationData(final int recordCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<referenceData xmlns:evt=\"event-logging:3\"\n");
        sb.append("               xmlns:stroom=\"stroom\"\n");
        sb.append("               xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("               xmlns=\"reference-data:2\"\n");
        sb.append(
                "               xsi:schemaLocation=\"reference-data:2 file://reference-data-v2.0.1.xsd event-logging:3 file://event-logging-v3.0.0.xsd\"\n");
        sb.append("               Version=\"2.4.2\">\n");

        for (int i = 0; i < recordCount; i++) {
            sb.append("   <reference>\n");
            sb.append("      <map>FILENO_TO_LOCATION_MAP</map>\n");
            sb.append("      <key>");
            sb.append(i);
            sb.append("</key>\n");
            sb.append("      <value>\n");
            sb.append("         <evt:Location>\n");
            sb.append("            <evt:Country>UK</evt:Country>\n");
            sb.append("            <evt:Site>Site ");
            sb.append(i);
            sb.append("</evt:Site>\n");
            sb.append("            <evt:Building>Main</evt:Building>\n");
            sb.append("            <evt:Floor>1</evt:Floor>\n");
            sb.append("            <evt:Room>A</evt:Room>\n");
            sb.append("            <evt:Desk>100</evt:Desk>\n");
            sb.append("         </evt:Location>\n");
            sb.append("      </value>\n");
            sb.append("   </reference>\n");
        }

        sb.append("</referenceData>");

        return sb.toString();
    }

    protected String createEventVerificationData(final int recordCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Events xpath-default-namespace=\"records:2\"\n");
        sb.append("        xmlns:stroom=\"stroom\"\n");
        sb.append("        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("        xmlns=\"event-logging:3\"\n");
        sb.append(
                "        xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\"\n");
        sb.append("        Version=\"2.4.2\">\n");

        for (int i = 0; i < recordCount; i++) {
            sb.append("   <Event Id=\"XXXXX\">\n");
            sb.append("      <EventTime>\n");
            sb.append("         <TimeCreated>XXXXX</TimeCreated>\n");
            sb.append("      </EventTime>\n");
            sb.append("      <EventSource>\n");
            sb.append("         <Generator>CSV</Generator>\n");
            sb.append("         <Device>\n");
            sb.append("            <IPAddress>1.1.1.1</IPAddress>\n");
            sb.append("            <MACAddress>00-00-00-00-00-00</MACAddress>\n");
            sb.append("            <Location>\n");
            sb.append("               <Country>UK</Country>\n");
            sb.append("               <Site>Site ");
            sb.append(i);
            sb.append("</Site>\n");
            sb.append("               <Building>Main</Building>\n");
            sb.append("               <Floor>1</Floor>\n");
            sb.append("               <Room>A</Room>\n");
            sb.append("               <Desk>100</Desk>\n");
            sb.append("            </Location>\n");
            sb.append("         </Device>\n");
            sb.append("         <User>\n");
            sb.append("            <Id>XXXXX</Id>\n");
            sb.append("         </User>\n");
            sb.append("      </EventSource>\n");
            sb.append("      <EventDetail>\n");
            sb.append("         <Description>Some message 1</Description>\n");
            sb.append("         <Authenticate>\n");
            sb.append("            <Action>Logon</Action>\n");
            sb.append("            <LogonType>Interactive</LogonType>\n");
            sb.append("            <data name=\"FileNo\" value=\"");
            sb.append(i);
            sb.append("\"/>\n");
            sb.append("            <data name=\"LineNo\" value=\"1\"/>\n");
            sb.append("         </Authenticate>\n");
            sb.append("      </EventDetail>\n");
            sb.append("   </Event>\n");
        }

        sb.append("</Events>");

        return sb.toString();
    }
}
