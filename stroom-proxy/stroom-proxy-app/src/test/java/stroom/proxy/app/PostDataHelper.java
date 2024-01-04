package stroom.proxy.app;

import stroom.proxy.app.handler.LocalByteBuffer;
import stroom.proxy.app.handler.NumericFileNameUtil;
import stroom.proxy.app.handler.ZipWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class PostDataHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostDataHelper.class);

    private final Client client;
    private final String url;
    private final LongAdder postToProxyCount = new LongAdder();

    public PostDataHelper(final Client client,
                          final String url) {
        this.client = client;
        this.url = url;
    }

    void sendTestData1() {
        sendData(
                TestConstants.FEED_TEST_EVENTS_1,
                TestConstants.SYSTEM_TEST_SYSTEM,
                TestConstants.ENVIRONMENT_DEV,
                Collections.emptyMap(),
                "Hello");
    }

    void sendTestData2() {
        sendData(
                TestConstants.FEED_TEST_EVENTS_2,
                TestConstants.SYSTEM_TEST_SYSTEM,
                TestConstants.ENVIRONMENT_DEV,
                Collections.emptyMap(),
                "Goodbye");
    }

    void sendZipTestData1(final int entryCount) {
        sendZipData(
                TestConstants.FEED_TEST_EVENTS_1,
                TestConstants.SYSTEM_TEST_SYSTEM,
                TestConstants.ENVIRONMENT_DEV,
                Collections.emptyMap(),
                "Hello",
                entryCount);
    }

    void sendZipTestData2(final int entryCount) {
        sendZipData(
                TestConstants.FEED_TEST_EVENTS_2,
                TestConstants.SYSTEM_TEST_SYSTEM,
                TestConstants.ENVIRONMENT_DEV,
                Collections.emptyMap(),
                "Goodbye",
                entryCount);
    }

    public int sendData(final String feed,
                        final String system,
                        final String environment,
                        final Map<String, String> extraHeaders,
                        final String data) {
        int status;
        try {
            final Builder builder = client.target(url)
                    .request()
                    .header("Feed", feed)
                    .header("System", system)
                    .header("Environment", environment);

            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            LOGGER.info("Sending POST request to {}", url);
            final String responseText;
            try (Response response = builder.post(Entity.text(data))) {
                postToProxyCount.increment();
                status = response.getStatus();
                responseText = response.readEntity(String.class);
            }
            LOGGER.info("datafeed response ({}):\n{}", status, responseText);

        } catch (final Exception e) {
            throw new RuntimeException("Error sending request to " + url, e);
        }
        return status;
    }

    public int sendZipData(final String feed,
                           final String system,
                           final String environment,
                           final Map<String, String> extraHeaders,
                           final String data,
                           final int entryCount) {
        int status;
        try {
            final Builder builder = client.target(url)
                    .request()
                    .header("Feed", feed)
                    .header("System", system)
                    .header("Environment", environment)
                    .header("Compression", "zip");

            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            LOGGER.info("Sending POST request to {}", url);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (final ZipWriter zipWriter = new ZipWriter(outputStream, LocalByteBuffer.get())) {
                for (int i = 1; i <= entryCount; i++) {
                    final String name = NumericFileNameUtil.create(i) + ".dat";
                    zipWriter.writeString(name, data);
                }
            }

            final String responseText;
            try (Response response = builder.post(
                    Entity.entity(outputStream.toByteArray(), MediaType.APPLICATION_JSON_TYPE))) {
                postToProxyCount.increment();
                status = response.getStatus();
                responseText = response.readEntity(String.class);
            }
            LOGGER.info("datafeed response ({}):\n{}", status, responseText);

        } catch (final Exception e) {
            throw new RuntimeException("Error sending request to " + url, e);
        }
        return status;
    }

    public long getPostCount() {
        return postToProxyCount.sum();
    }
}
