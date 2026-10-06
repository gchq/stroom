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
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// A RestyGWT [Dispatcher] that answers every REST request a screen makes from the story's
/// [RestFixtures] instead of sending it to a server, the in-browser equivalent of msw. Requests
/// still go through Stroom's real `RestFactory`, RestyGWT's generated service proxies and its
/// JSON encoding/decoding, so only the network is faked.
///
/// Each rendering of a story has its own dispatcher (and so its own [FixtureSession], so
/// sequences of replies start afresh). The reply is always asynchronous, as a real one is, after
/// the reply's delay. Each request is passed to the [Listener] before its reply is worked out, so
/// it is recorded even if the fixture fails.
///
/// Stroom's `RestFactory` sends each request with the dispatcher it was created with, so a screen
/// of an old rendering, which keeps its `RestFactory`, still sends its requests here;
/// [#dispose()] cancels the replies still pending and makes this dispatcher drop any request that
/// still reaches it, so nothing from an old rendering reaches the new one.
public class FixtureDispatcher implements Dispatcher {

    private final FixtureSession session;
    private final Supplier<String> serviceRoot;
    private final ReplyScheduler scheduler;
    private final Listener listener;
    private final List<FixtureRequest> pendingRequests = new ArrayList<>();
    private boolean disposed;

    /// @param fixtures    The replies to the requests.
    /// @param serviceRoot Gives RestyGWT's service root (`Defaults::getServiceRoot`), whose path
    ///                    is removed from the start of each request's path.
    /// @param scheduler   Runs the replies later.
    /// @param listener    Told about each request and any problem with it.
    public FixtureDispatcher(final RestFixtures fixtures,
                             final Supplier<String> serviceRoot,
                             final ReplyScheduler scheduler,
                             final Listener listener) {
        this.session = Objects.requireNonNull(fixtures).newSession();
        this.serviceRoot = Objects.requireNonNull(serviceRoot);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.listener = Objects.requireNonNull(listener);
    }

    /// Answers a request from the fixtures, after the reply's delay.
    ///
    /// @param method  RestyGWT's method, not used.
    /// @param builder The request.
    /// @return The request, which can be cancelled.
    @Override
    public Request send(final Method method, final RequestBuilder builder) {
        final RecordedRequest request = RecordedRequest.fromUrl(
                builder.getHTTPMethod(), builder.getUrl(), builder.getRequestData(), serviceRoot.get());
        final FixtureRequest fixtureRequest = new FixtureRequest();
        if (disposed) {
            // A request from a screen of an old rendering. It is never answered.
            fixtureRequest.pending = false;
            listener.onDroppedRequest(request);
            return fixtureRequest;
        }

        // Record the request first, so that it is recorded whatever the fixtures do
        listener.onRequest(request);
        final FixtureSession.Exchange exchange = session.exchange(request);
        if (!exchange.isHandled()) {
            listener.onUnhandledRequest(request, exchange.getProblem(), session.getFixtures().isStrict());
        } else if (exchange.isFixtureFailed()) {
            listener.onFixtureError(request, exchange.getProblem());
        }

        // RestReply refuses to make a 401 (which would make RestFactoryImpl reload the page), so
        // every reply here is safe to deliver
        final RestReply reply = exchange.getReply();
        final RequestCallback callback = builder.getCallback();
        pendingRequests.add(fixtureRequest);
        fixtureRequest.scheduled = scheduler.schedule(Math.max(1, reply.getDelayMillis()), () ->
                deliver(fixtureRequest, callback, reply));
        return fixtureRequest;
    }

    /// Cancels the replies still pending and drops any later request. Called when the story
    /// renders again.
    public void dispose() {
        disposed = true;
        for (final FixtureRequest request : new ArrayList<>(pendingRequests)) {
            request.cancel();
        }
        pendingRequests.clear();
    }

    /// @return True if [#dispose()] has been called.
    public boolean isDisposed() {
        return disposed;
    }

    /// @return The number of requests whose replies haven't been delivered yet.
    public int getPendingCount() {
        return pendingRequests.size();
    }

    private void deliver(final FixtureRequest request, final RequestCallback callback, final RestReply reply) {
        pendingRequests.remove(request);
        if (request.cancelled || disposed) {
            return;
        }
        request.pending = false;
        switch (reply.getKind()) {
            case NETWORK_ERROR:
                callback.onError(request, new RequestException(reply.getBody()));
                break;
            case TIMEOUT:
                callback.onError(request, new RequestTimeoutException(request, reply.getDelayMillis()));
                break;
            default:
                callback.onResponseReceived(request, new FixtureResponse(reply));
        }
    }

    // --------------------------------------------------------------------------------


    /// Told about the requests a [FixtureDispatcher] answers.
    public interface Listener {

        /// Called for each request, before it is answered.
        ///
        /// @param request The request.
        void onRequest(RecordedRequest request);

        /// Called when no route matches a request, which gets a `404` reply.
        ///
        /// @param request The request.
        /// @param message What to report.
        /// @param strict  True if the story should fail.
        void onUnhandledRequest(RecordedRequest request, String message, boolean strict);

        /// Called when a fixture fails, e.g. throws, so the request gets a `500` reply.
        ///
        /// @param request The request.
        /// @param message What to report.
        void onFixtureError(RecordedRequest request, String message);

        /// Called for a request reaching the dispatcher after [#dispose()], which is never
        /// answered.
        ///
        /// @param request The request.
        void onDroppedRequest(RecordedRequest request);
    }

    // --------------------------------------------------------------------------------


    /// A request that is never sent.
    private final class FixtureRequest extends Request {

        private boolean cancelled;
        private boolean pending = true;
        private ReplyScheduler.Scheduled scheduled;

        /// Cancels the reply.
        @Override
        public void cancel() {
            cancelled = true;
            pending = false;
            pendingRequests.remove(this);
            if (scheduled != null) {
                scheduled.cancel();
            }
        }

        /// @return True until the reply is delivered or the request is cancelled.
        @Override
        public boolean isPending() {
            return pending;
        }
    }
}
