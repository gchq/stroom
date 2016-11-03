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

package stroom.entity.server.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import stroom.util.logging.StroomLogger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamTypeService;
import stroom.util.date.DateUtil;
import stroom.util.zip.HeaderMap;

/**
 * <p>
 * Simple client to send a bunch of matching stream into another server. This is
 * typically used for testing.
 * </p>
 */
public final class SendStreamDataClient {
    static final StroomLogger LOGGER = StroomLogger.getLogger(SendStreamDataClient.class);

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
        // Load up the args
        final HeaderMap argsMap = new HeaderMap();
        argsMap.loadArgs(args);

        // Boot up spring
        final ApplicationContext appContext = new ClassPathXmlApplicationContext(
                new String[] { "classpath:META-INF/spring/stroomCoreServerContext.xml" });

        final FindStreamCriteria criteria = new FindStreamCriteria();

        // Check Args
        if (!argsMap.containsKey(FEED)) {
            throw new RuntimeException("Expecting Feed= Argument");
        }
        if (!argsMap.containsKey(STREAM_TYPE)) {
            throw new RuntimeException("Expecting Stream Type= Argument");
        }
        if (!argsMap.containsKey(SEND_URL)) {
            throw new RuntimeException("Expecting SendUrl= Argument");
        }

        // Set args on filter
        if (argsMap.containsKey(RECEIVED_PERIOD_FROM) || argsMap.containsKey(RECEIVED_PERIOD_TO)) {
            final String createStartTime = argsMap.get(RECEIVED_PERIOD_FROM);
            final String createEndTime = argsMap.get(RECEIVED_PERIOD_TO);

            final long createStartTimeDT = DateUtil.parseNormalDateTimeString(createStartTime);
            final long createEndTimeDT = DateUtil.parseNormalDateTimeString(createEndTime);

            criteria.setCreatePeriod(new Period(createStartTimeDT, createEndTimeDT));
        }
        final URL url = new URL(argsMap.get(SEND_URL));
        final StreamStore streamStore = (StreamStore) appContext.getBean("streamStore");
        final FeedService feedService = appContext.getBean(FeedService.class);
        final Feed definition = feedService.loadByName(argsMap.get(FEED));
        if (definition == null) {
            throw new RuntimeException("Unable to locate Feed " + argsMap.get(FEED));
        }
        final StreamTypeService streamTypeService = appContext.getBean(StreamTypeService.class);
        final StreamType streamType = streamTypeService.loadByName(argsMap.get(STREAM_TYPE));
        if (streamType == null) {
            throw new RuntimeException("Unknown stream type " + argsMap.get(STREAM_TYPE));
        }
        criteria.obtainFeeds().obtainInclude().add(definition.getId());
        criteria.obtainStreamIdSet().add(streamType.getId());

        // Query the stream store
        final List<Stream> results = streamStore.find(criteria);

        for (final Stream stream : results) {
            // Bug in the API ... check we get back what we expect.
            if (stream.getFeed().getId() == definition.getId()) {
                sendStream(url, streamStore, definition, stream.getId());
            } else {
                LOGGER.info("Skipping invalid stream");
            }
        }
    }

    /**
     * Perform the HTTP Post.
     */
    private static void sendStream(final URL url, final StreamStore streamStore, final Feed feed, final long streamId)
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
        final StreamSource meta = streamSource.getChildStream(StreamType.META);
        final Stream stream = streamSource.getStream();

        if (stream.getEffectiveMs() != null) {
            connection.addRequestProperty("effectiveTime",
                    DateUtil.createNormalDateTimeString(stream.getEffectiveMs()));
        }

        if (meta != null) {
            final HeaderMap headerMap = new HeaderMap();
            headerMap.read(meta.getInputStream(), true);
            headerMap.entrySet().stream().filter(entry -> connection.getRequestProperty(entry.getKey()) == null)
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
            if (msg != null && msg.length() > 0) {
                LOGGER.info(msg);
            }
            connection.disconnect();

            streamStore.closeStreamSource(streamSource);
        }
    }
}
