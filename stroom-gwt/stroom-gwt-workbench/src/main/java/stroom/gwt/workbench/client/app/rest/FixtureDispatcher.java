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

import com.google.gwt.http.client.Header;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Timer;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;

import java.util.Objects;
import java.util.function.Consumer;

/// A RestyGWT [Dispatcher] that answers every REST request a screen makes from the story's
/// [RestFixtures] instead of sending it to a server, the in-browser equivalent of msw. Requests
/// still go through Stroom's real `RestFactory`, RestyGWT's generated service proxies and its
/// JSON encoding/decoding, so only the network is faked.
///
/// The reply is always asynchronous (as a real request is), after the reply's delay.
public class FixtureDispatcher implements Dispatcher {

    private final RestFixtures fixtures;
    private final Consumer<RecordedRequest> requestListener;

    /// @param fixtures        The replies to the requests.
    /// @param requestListener Told about each request when it is made, e.g. to spy on them.
    public FixtureDispatcher(final RestFixtures fixtures, final Consumer<RecordedRequest> requestListener) {
        this.fixtures = Objects.requireNonNull(fixtures);
        this.requestListener = Objects.requireNonNull(requestListener);
    }

    @Override
    public Request send(final Method method, final RequestBuilder builder) {
        final RecordedRequest request = RecordedRequest.fromUrl(
                builder.getHTTPMethod(), builder.getUrl(), builder.getRequestData());
        final RestReply reply = fixtures.reply(request);
        requestListener.accept(request);

        final RequestCallback callback = builder.getCallback();
        final FixtureRequest fixtureRequest = new FixtureRequest();
        final Timer timer = new Timer() {
            @Override
            public void run() {
                if (!fixtureRequest.cancelled) {
                    fixtureRequest.pending = false;
                    callback.onResponseReceived(fixtureRequest, new FixtureResponse(reply));
                }
            }
        };
        // Always reply asynchronously, as the screens expect
        timer.schedule(Math.max(1, reply.getDelayMillis()));
        return fixtureRequest;
    }

    // --------------------------------------------------------------------------------


    /// A request that is never sent.
    private static final class FixtureRequest extends Request {

        private boolean cancelled;
        private boolean pending = true;

        @Override
        public void cancel() {
            cancelled = true;
            pending = false;
        }

        @Override
        public boolean isPending() {
            return pending;
        }
    }

    // --------------------------------------------------------------------------------


    /// The response to a request, created from a [RestReply].
    private static final class FixtureResponse extends Response {

        private static final String CONTENT_TYPE = "Content-Type";

        private final RestReply reply;

        private FixtureResponse(final RestReply reply) {
            this.reply = reply;
        }

        @Override
        public String getHeader(final String header) {
            return CONTENT_TYPE.equalsIgnoreCase(header)
                    ? reply.getContentType()
                    : null;
        }

        @Override
        public Header[] getHeaders() {
            return new Header[]{new ContentTypeHeader(reply.getContentType())};
        }

        @Override
        public String getHeadersAsString() {
            return CONTENT_TYPE + ": " + reply.getContentType() + "\r\n";
        }

        @Override
        public int getStatusCode() {
            return reply.getStatus();
        }

        @Override
        public String getStatusText() {
            return reply.getStatus() < 400
                    ? "OK"
                    : "Error";
        }

        @Override
        public String getText() {
            return reply.getBody();
        }
    }

    // --------------------------------------------------------------------------------


    /// The content type header of a [FixtureResponse].
    private static final class ContentTypeHeader extends Header {

        private final String contentType;

        private ContentTypeHeader(final String contentType) {
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return FixtureResponse.CONTENT_TYPE;
        }

        @Override
        public String getValue() {
            return contentType;
        }
    }
}
