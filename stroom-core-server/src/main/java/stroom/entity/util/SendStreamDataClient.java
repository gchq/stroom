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

package stroom.entity.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.AttributeMap;
import stroom.feed.shared.FeedDoc;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.data.meta.api.Stream;
import stroom.util.date.DateUtil;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

/**
 * <p>
 * Simple client to send a bunch of matching stream into another server. This is
 * typically used for testing.
 * </p>
 */
public final class SendStreamDataClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendStreamDataClient.class);

    private static final String GZIP = "GZIP";
    private static final String COMPRESSION = "Compression";
    private static final String FEED = "feed";
    private static final String STREAM_TYPE = "streamType";
    private static final String RECEIVED_PERIOD_FROM = "receivedPeriodFrom";
    private static final String RECEIVED_PERIOD_TO = "receivedPeriodTo";
    private static final String SEND_URL = "sendUrl";

    private static final int BUFFER_SIZE = 1024;

    private SendStreamDataClient() {
        // NA Just a runnable class.
    }

    public static void main(final String[] args) throws IOException {
//        // Load up the args
//        final Map<String, String> argsMap = ArgsUtil.parse(args);
//
//        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
//        // Check Args
//        if (!argsMap.containsKey(FEED)) {
//            throw new RuntimeException("Expecting Feed= Argument");
//        }
//        if (!argsMap.containsKey(STREAM_TYPE)) {
//            throw new RuntimeException("Expecting Stream Type= Argument");
//        }
//        if (!argsMap.containsKey(SEND_URL)) {
//            throw new RuntimeException("Expecting SendUrl= Argument");
//        }
//
//        // Set args on filter
//        if (argsMap.containsKey(RECEIVED_PERIOD_FROM) || argsMap.containsKey(RECEIVED_PERIOD_TO)) {
//            final String createStartTime = argsMap.get(RECEIVED_PERIOD_FROM);
//            final String createEndTime = argsMap.get(RECEIVED_PERIOD_TO);
//            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, createStartTime + "," + createEndTime);
//        }
//        final URL url = new URL(argsMap.get(SEND_URL));
//        final StreamStore streamStore = (StreamStore) appContext.getInstance("streamStore");
//        final FeedService feedService = appContext.getInstance(FeedService.class);
//        final Feed definition = feedService.loadByName(argsMap.get(FEED));
//        if (definition == null) {
//            throw new RuntimeException("Unable to locate Feed " + argsMap.get(FEED));
//        }
//        final StreamTypeService streamTypeService = appContext.getInstance(StreamTypeService.class);
//        final StreamType streamType = streamTypeService.loadByName(argsMap.get(STREAM_TYPE));
//        if (streamType == null) {
//            throw new RuntimeException("Unknown stream type " + argsMap.get(STREAM_TYPE));
//        }
//        builder.addTerm(StreamDataSource.FEED, Condition.EQUALS, definition.getName());
//        builder.addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, streamType.getDisplayValue());
//
//        // Query the stream store
//        final FindStreamCriteria criteria = new FindStreamCriteria();
//        criteria.setExpression(builder.build());
//        final List<Stream> results = streamStore.find(criteria);
//
//        for (final Stream stream : results) {
//            // Bug in the API ... check we get back what we expect.
//            if (stream.getFeed().getId() == definition.getId()) {
//                sendStream(url, streamStore, definition, stream.getId());
//            } else {
//                LOGGER.info("Skipping invalid stream");
//            }
//        }
    }

    /**
     * Perform the HTTP Post.
     */
    private static void sendStream(final URL url, final StreamStore streamStore, final FeedDoc feed, final long streamId)
            throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier((arg0, arg1) -> {
                LOGGER.info("HostnameVerifier - " + arg0);
                return true;
            });
        }

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/audit");
        connection.setDoOutput(true);
        connection.setDoInput(true);

        connection.addRequestProperty(COMPRESSION, GZIP);
        connection.addRequestProperty(FEED, feed.getName());

        final StreamSource streamSource = streamStore.openStreamSource(streamId);
        final StreamSource meta = streamSource.getChildStream(StreamTypeNames.META);
        final Stream stream = streamSource.getStream();

        if (stream.getEffectiveMs() != null) {
            connection.addRequestProperty("effectiveTime",
                    DateUtil.createNormalDateTimeString(stream.getEffectiveMs()));
        }

        if (meta != null) {
            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.read(meta.getInputStream(), true);
            attributeMap.entrySet().stream().filter(entry -> connection.getRequestProperty(entry.getKey()) == null)
                    .forEach(entry -> connection.addRequestProperty(entry.getKey(), entry.getValue()));
        }

        connection.connect();

        try (OutputStream out = new GZIPOutputStream(connection.getOutputStream())) {
            final InputStream fis = streamSource.getInputStream();

            // Write the output
            final byte[] buffer = new byte[BUFFER_SIZE];
            int readSize;
            while ((readSize = fis.read(buffer)) != -1) {
                out.write(buffer, 0, readSize);
            }

            out.flush();
            out.close();
            fis.close();

            final int response = connection.getResponseCode();
            final String msg = connection.getResponseMessage();

            LOGGER.info("Client Got Response " + response + " Stream Id " + stream.getId());
            if (msg != null && !msg.isEmpty()) {
                LOGGER.info(msg);
            }
            connection.disconnect();

            streamStore.closeStreamSource(streamSource);
        }
    }
}
