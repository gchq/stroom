/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app;

import stroom.proxy.app.handler.LocalByteBuffer;
import stroom.proxy.app.handler.NumericFileNameUtil;
import stroom.proxy.app.handler.ZipWriter;
import stroom.util.concurrent.UniqueId;

import com.google.common.base.Strings;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Helper class for posting data to /datafeed on a proxy
 */
public class PostDataHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostDataHelper.class);

    private final Client client;
    private final String url;
    private final LongAdder postToProxyCount = new LongAdder();
    private final List<PostResponse> postResponses = new ArrayList<>();
    private final List<String> dataIds = new ArrayList<>();
    private final AtomicLong dataIdCounter = new AtomicLong();

    public PostDataHelper(final Client client,
                          final String url) {
        this.client = client;
        this.url = url;
    }

    void sendFeed1TestData() {
        sendData(
                TestConstants.FEED_TEST_EVENTS_1,
                TestConstants.SYSTEM_TEST_SYSTEM,
                TestConstants.ENVIRONMENT_DEV,
                Collections.emptyMap(),
                "Hello");
    }

    void sendFeed2TestData() {
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
        final int status;
        try {
            final Builder builder = client.target(url)
                    .request()
                    .header("Feed", feed)
                    .header("System", system)
                    .header("Environment", environment);

            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            final String dataId = getDataId();
            final String payload = dataId + "-" + data;
            LOGGER.info("Sending POST request to {}, with payload '{}'", url, payload);
            try (final Response response = builder.post(Entity.text(payload))) {
                postToProxyCount.increment();
                status = consumeResponse(response);
            }

        } catch (final Exception e) {
            throw new RuntimeException("Error sending request to " + url, e);
        }
        return status;
    }

    public String getDataId() {
        final String dataId = Strings.padStart(
                String.valueOf(dataIdCounter.getAndIncrement()),
                10,
                '0');
        dataIds.add(dataId);
        return dataId;
    }

    public int sendZipData(final String feed,
                           final String system,
                           final String environment,
                           final Map<String, String> extraHeaders,
                           final String data,
                           final int entryCount) {
        final int status;
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
                    final String dataId = getDataId();
                    final String payload = dataId + "-" + data;
                    final String name = NumericFileNameUtil.create(i) + ".dat";
                    zipWriter.writeString(name, payload);
                }
            }

            try (final Response response = builder.post(
                    Entity.entity(outputStream.toByteArray(), MediaType.APPLICATION_JSON_TYPE))) {
                postToProxyCount.increment();
                status = consumeResponse(response);
            }

        } catch (final Exception e) {
            throw new RuntimeException("Error sending request to " + url, e);
        }
        return status;
    }

    private int consumeResponse(final Response response) {
        final int status = response.getStatus();
        final String responseText = response.readEntity(String.class);
        UniqueId receiptId = null;
        try {
            receiptId = UniqueId.parse(responseText);
        } catch (final Exception ignored) {
            // Ignore
        }
        LOGGER.info("datafeed response ({}):\n{}", status, responseText);
        final PostResponse postResponse = new PostResponse(receiptId, status, responseText);
        postResponses.add(postResponse);
        return status;
    }

    public long getPostCount() {
        return postToProxyCount.sum();
    }

    public List<PostResponse> getPostResponses() {
        return Collections.unmodifiableList(postResponses);
    }

    public List<UniqueId> getReceiptIds() {
        return postResponses.stream()
                .map(PostResponse::receiptId)
                .toList();
    }

    public List<String> getDataIdsSent() {
        return Collections.unmodifiableList(dataIds);
    }


    // --------------------------------------------------------------------------------


    public record PostResponse(UniqueId receiptId,
                               int status,
                               String responseText) {

    }
}
