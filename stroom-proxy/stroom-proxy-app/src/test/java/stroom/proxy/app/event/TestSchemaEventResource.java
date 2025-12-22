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

package stroom.proxy.app.event;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ModelStringUtil;

import event.logging.CreateEventAction;
import event.logging.Event;
import event.logging.EventDetail;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.File;
import event.logging.User;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

public class TestSchemaEventResource {

    public static void main(final String[] args) {
        System.out.println("AVAILABLE PROCESSORS = " + Runtime.getRuntime().availableProcessors());

        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final int threadCount = 10;
            final LongAdder count = new LongAdder();
            final CompletableFuture[] arr = new CompletableFuture[threadCount];

            final long startTime = System.currentTimeMillis();
//        for (int i = 0; i < threadCount; i++) {
//            arr[i] = CompletableFuture.runAsync(() -> {
//                while (true) {
            if (post(httpClient)) {
                count.increment();
            }
//                }
//            });
//        }

            CompletableFuture.runAsync(() -> {
                long lastTime = startTime;
                long lastCount = 0;
                while (true) {
                    ThreadUtil.sleep(10000);

                    final long now = System.currentTimeMillis();
                    final long totalCount = count.longValue();
                    final long deltaCount = totalCount - lastCount;
                    final double totalSeconds = (now - startTime) / 1000D;
                    final double deltaSeconds = (now - lastTime) / 1000D;

                    System.out.println("Posts " +
                                       "Delta: " +
                                       deltaCount +
                                       " in " +
                                       ModelStringUtil.formatDurationString(now - lastTime) +
                                       " " +
                                       (long) (deltaCount / deltaSeconds) + "pps" +
                                       " " +
                                       "Total: " + totalCount +
                                       " in " +
                                       ModelStringUtil.formatDurationString(now - startTime) +
                                       " " +
                                       (long) (totalCount / totalSeconds) + "pps");

                    lastTime = now;
                    lastCount = totalCount;
                }
            });
            CompletableFuture.allOf(arr).join();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean post(final HttpClient httpClient) {
        try {
            final EventTime eventTime = new EventTime();
            eventTime.setTimeCreated(Date.from(Instant.now()));

            final User user = new User();
            user.setName("Fred");

            final EventSource eventSource = new EventSource();
            eventSource.setUser(user);

            final CreateEventAction eventAction = CreateEventAction
                    .builder()
                    .addFile(
                            File
                                    .builder()
                                    .withPath("/tmp/test.txt")
                                    .build())
                    .build();

            final EventDetail eventDetail = new EventDetail();
            eventDetail.setDescription("test");
            eventDetail.setEventAction(eventAction);

            final Event event = new Event();
            event.setEventTime(eventTime);
            event.setEventSource(eventSource);
            event.setEventDetail(eventDetail);

            final String json = JsonUtil.writeValueAsString(event);
            final Event event2 = JsonUtil.readValue(json, Event.class);
            System.out.println(event2);

            final Client client = ClientBuilder.newClient();
            try {
                final WebTarget webTarget = client.target("http://127.0.0.1:8090/api/event/schema_v4_0");
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Feed", "TEST-EVENTS")
                        .header("System", "EXAMPLE_SYSTEM")
                        .header("Environment", "EXAMPLE_ENVIRONMENT")
                        .post(Entity.entity(json, MediaType.APPLICATION_JSON));

                if (response.getStatus() != HttpStatus.SC_OK) {
                    throw new RuntimeException("Not ok: " + response.getEntity().toString());
                }
            } finally {
                if (client != null) {
                    client.close();
                }
            }
//
//
//            final HttpPost httpPost = new HttpPost("http://127.0.0.1:8090/stroom/noauth/event/text");
//            httpPost.addHeader("Feed", "TEST-EVENTS");
//            httpPost.addHeader("System", "EXAMPLE_SYSTEM");
//            httpPost.addHeader("Environment", "EXAMPLE_ENVIRONMENT");
//            httpPost.setEntity(
//                    new InputStreamEntity(
//                            new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))));
//
//            // Execute and get the response.
//            final HttpResponse response = httpClient.execute(httpPost);
//            final HttpEntity entity = response.getEntity();
//
//            if (entity != null) {
//                try (final InputStream inputStream = entity.getContent()) {
//                    // do something useful
////                    System.out.println(StreamUtil.streamToString(inputStream));
//                }
//            }
//
//            return response.getStatusLine().getStatusCode() == 200;
        } catch (final Exception e) {
            System.err.println(e.getMessage());
        }
        return false;
    }
}
