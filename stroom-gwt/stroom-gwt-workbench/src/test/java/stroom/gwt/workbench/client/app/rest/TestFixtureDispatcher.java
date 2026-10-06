/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.app.rest;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.RequestTimeoutException;
import com.google.gwt.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFixtureDispatcher {

    private static final String ROOT = "http://localhost:6008/api/";

    private final FakeScheduler scheduler = new FakeScheduler();
    private final List<String> events = new ArrayList<>();
    private final List<Response> responses = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        scheduler.tasks.clear();
        events.clear();
        responses.clear();
        errors.clear();
    }

    @Test
    void testReplyIsAsynchronousAndDelayed() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/sessionInfo/v1", RestReply.json("{\"nodeName\":\"node1a\"}").delayed(250))
                .build());

        final Request request = dispatcher.send(null, builder(RequestBuilder.GET, "sessionInfo/v1?x=1", null));

        assertThat(events).containsExactly("request GET /sessionInfo/v1?x=1");
        assertThat(responses).isEmpty();
        assertThat(request.isPending()).isTrue();
        assertThat(dispatcher.getPendingCount()).isOne();
        assertThat(scheduler.tasks.get(0).delayMillis).isEqualTo(250);

        scheduler.runAll();

        assertThat(request.isPending()).isFalse();
        assertThat(dispatcher.getPendingCount()).isZero();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatusCode()).isEqualTo(200);
        assertThat(responses.get(0).getStatusText()).isEqualTo("OK");
        assertThat(responses.get(0).getText()).isEqualTo("{\"nodeName\":\"node1a\"}");
        assertThat(responses.get(0).getHeader("content-type")).isEqualTo(RestReply.JSON);
    }

    @Test
    void testZeroDelayIsStillAsynchronous() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/a", RestReply.json("{}"))
                .build());

        dispatcher.send(null, builder(RequestBuilder.GET, "a", null));

        assertThat(responses).isEmpty();
        assertThat(scheduler.tasks.get(0).delayMillis).isEqualTo(1);
    }

    @Test
    void testHeadersAndText() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/download", RestReply.text(500, "It broke")
                        .withHeader("Content-Disposition", "attachment; filename=a.txt"))
                .build());

        dispatcher.send(null, builder(RequestBuilder.GET, "download", null));
        scheduler.runAll();

        final Response response = responses.get(0);
        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getStatusText()).isEqualTo("Internal Server Error");
        assertThat(response.getHeader("Content-Type")).isEqualTo("text/plain");
        assertThat(response.getHeader("content-disposition")).isEqualTo("attachment; filename=a.txt");
        assertThat(response.getHeaders()).hasSize(2);
        assertThat(response.getHeadersAsString())
                .isEqualTo("Content-Type: text/plain\r\nContent-Disposition: attachment; filename=a.txt\r\n");
    }

    @Test
    void testNetworkErrorAndTimeout() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/down", RestReply.networkError("Connection refused"))
                .get("/slow", RestReply.timeout().delayed(3000))
                .build());

        dispatcher.send(null, builder(RequestBuilder.GET, "down", null));
        dispatcher.send(null, builder(RequestBuilder.GET, "slow", null));
        scheduler.runAll();

        assertThat(responses).isEmpty();
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).isExactlyInstanceOf(RequestException.class).hasMessage("Connection refused");
        assertThat(errors.get(1)).isInstanceOf(RequestTimeoutException.class);
        assertThat(((RequestTimeoutException) errors.get(1)).getTimeoutMillis()).isEqualTo(3000);
    }

    @Test
    void testUnhandledRequest() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.none());

        dispatcher.send(null, builder(RequestBuilder.POST, "explorer/v2/find", "{\"a\":1}"));
        scheduler.runAll();

        assertThat(events).containsExactly(
                "request POST /explorer/v2/find",
                "unhandled POST /explorer/v2/find strict=true");
        assertThat(responses.get(0).getStatusCode()).isEqualTo(404);
    }

    @Test
    void testUnhandledRequest_lenient() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder().lenient().build());

        dispatcher.send(null, builder(RequestBuilder.GET, "x", null));

        assertThat(events).containsExactly("request GET /x", "unhandled GET /x strict=false");
    }

    @Test
    void testFailingFixtureIsRecordedAndReplies500() {
        // Regression: the request used to be recorded only after the fixture replied, so a
        // throwing fixture left no record and no reply (a stuck task spinner)
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/boom", request -> {
                    throw new IllegalStateException("Oops");
                })
                .build());

        dispatcher.send(null, builder(RequestBuilder.GET, "boom", null));
        scheduler.runAll();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo("request GET /boom");
        assertThat(events.get(1)).startsWith("fixtureError GET /boom").contains("Oops");
        assertThat(responses.get(0).getStatusCode()).isEqualTo(500);
    }

    @Test
    void testDispose() {
        // Regression: replies scheduled by an old rendering used to arrive after a re-render
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/slow", RestReply.json("{}").delayed(2000))
                .build());
        final Request request = dispatcher.send(null, builder(RequestBuilder.GET, "slow", null));

        dispatcher.dispose();
        scheduler.runAll();

        assertThat(dispatcher.isDisposed()).isTrue();
        assertThat(responses).isEmpty();
        assertThat(request.isPending()).isFalse();
        assertThat(dispatcher.getPendingCount()).isZero();
        assertThat(scheduler.tasks.get(0).cancelled).isTrue();

        // A later request is dropped, not recorded or answered
        events.clear();
        final Request late = dispatcher.send(null, builder(RequestBuilder.GET, "slow", null));
        scheduler.runAll();
        assertThat(events).containsExactly("dropped GET /slow");
        assertThat(late.isPending()).isFalse();
        assertThat(responses).isEmpty();
    }

    @Test
    void testCancel() {
        final FixtureDispatcher dispatcher = dispatcher(RestFixtures.builder()
                .get("/a", RestReply.json("{}"))
                .build());
        final Request request = dispatcher.send(null, builder(RequestBuilder.GET, "a", null));

        request.cancel();
        scheduler.runAll();

        assertThat(responses).isEmpty();
        assertThat(dispatcher.getPendingCount()).isZero();
    }

    @Test
    void testSequenceRestartsWithEachDispatcher() {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/poll", RestReply.json("\"pending\""), RestReply.json("\"done\""))
                .build();

        final FixtureDispatcher first = dispatcher(fixtures);
        first.send(null, builder(RequestBuilder.GET, "poll", null));
        first.send(null, builder(RequestBuilder.GET, "poll", null));
        first.send(null, builder(RequestBuilder.GET, "poll", null));
        final FixtureDispatcher second = dispatcher(fixtures);
        second.send(null, builder(RequestBuilder.GET, "poll", null));
        scheduler.runAll();

        assertThat(responses).extracting(Response::getText)
                .containsExactly("\"pending\"", "\"done\"", "\"done\"", "\"pending\"");
    }

    private FixtureDispatcher dispatcher(final RestFixtures fixtures) {
        return new FixtureDispatcher(fixtures, () -> ROOT, scheduler, new FixtureDispatcher.Listener() {
            @Override
            public void onRequest(final RecordedRequest request) {
                events.add("request " + request.describe());
            }

            @Override
            public void onUnhandledRequest(final RecordedRequest request, final String message, final boolean strict) {
                events.add("unhandled " + request.describe() + " strict=" + strict);
            }

            @Override
            public void onFixtureError(final RecordedRequest request, final String message) {
                events.add("fixtureError " + request.describe() + " " + message);
            }

            @Override
            public void onDroppedRequest(final RecordedRequest request) {
                events.add("dropped " + request.describe());
            }
        });
    }

    private RequestBuilder builder(final RequestBuilder.Method method, final String path, final String body) {
        final RequestBuilder builder = new RequestBuilder(method, ROOT + path);
        builder.setRequestData(body);
        builder.setCallback(new RequestCallback() {
            @Override
            public void onResponseReceived(final Request request, final Response response) {
                responses.add(response);
            }

            @Override
            public void onError(final Request request, final Throwable exception) {
                errors.add(exception);
            }
        });
        return builder;
    }

    // --------------------------------------------------------------------------------


    /// Runs the scheduled tasks when asked, in the order they were scheduled.
    private static final class FakeScheduler implements ReplyScheduler {

        private final List<Task> tasks = new ArrayList<>();

        @Override
        public Scheduled schedule(final int delayMillis, final Runnable runnable) {
            final Task task = new Task(delayMillis, runnable);
            tasks.add(task);
            return () -> task.cancelled = true;
        }

        private void runAll() {
            for (final Task task : new ArrayList<>(tasks)) {
                if (!task.cancelled && !task.done) {
                    task.done = true;
                    task.runnable.run();
                }
            }
        }
    }

    // --------------------------------------------------------------------------------


    private static final class Task {

        private final int delayMillis;
        private final Runnable runnable;
        private boolean cancelled;
        private boolean done;

        private Task(final int delayMillis, final Runnable runnable) {
            this.delayMillis = delayMillis;
            this.runnable = runnable;
        }
    }
}
