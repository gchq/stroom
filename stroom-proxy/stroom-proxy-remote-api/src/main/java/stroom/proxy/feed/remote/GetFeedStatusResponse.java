/*
 * Copyright 2020 Crown Copyright
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

package stroom.proxy.feed.remote;

import stroom.proxy.StroomStatusCode;
import stroom.proxy.remote.RemoteResponse;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class GetFeedStatusResponse extends RemoteResponse {

    private static final long serialVersionUID = 9221787861812287256L;

    @JsonProperty
    private FeedStatus status;
    @JsonProperty
    private String message;
    @JsonProperty
    private StroomStatusCode stroomStatusCode;

    /**
     * For {@code TestJsonSerialisation}, which round-trips every JSON type through its creator and
     * needs a valid instance to start from; the creator refuses a null status, so it cannot supply one.
     * Not a default: a response with no status is not an answer, and nothing else builds one.
     */
    @SerialisationTestConstructor
    private GetFeedStatusResponse() {
        this(FeedStatus.Receive, null, null);
    }

    private GetFeedStatusResponse(final FeedStatus status,
                                  final StroomStatusCode stroomStatusCode) {
        this.status = status;
        this.stroomStatusCode = stroomStatusCode;
        if (stroomStatusCode != null) {
            this.message = stroomStatusCode.getMessage();
        }
    }

    /**
     * @param status Required. A response that does not say what to do with the feed is not a usable
     *               answer, and the one consumer switches on it, so a null here previously became an
     *               opaque NPE deep in the receive path. Refusing it at the boundary instead turns a
     *               malformed downstream response into a {@code FeedStatusUnavailableException}, which
     *               is what the last-good-response and {@code fallbackReceiveAction} paths exist to
     *               handle - the operator's configured choice rather than an accident.
     */
    @JsonCreator
    public GetFeedStatusResponse(@JsonProperty("status") final FeedStatus status,
                                 @JsonProperty("message") final String message,
                                 @JsonProperty("stroomStatusCode") final StroomStatusCode stroomStatusCode) {
        this.status = Objects.requireNonNull(status, "A feed status response must state a status");
        this.message = message;
        this.stroomStatusCode = stroomStatusCode;
    }

    public static GetFeedStatusResponse createOKResponse(final FeedStatus feedStatus) {
        return new GetFeedStatusResponse(feedStatus, null);
    }

    public static GetFeedStatusResponse createOKReceiveResponse() {
        return createOKResponse(FeedStatus.Receive);
    }

    public static GetFeedStatusResponse createOKDropResponse() {
        return createOKResponse(FeedStatus.Drop);
    }

    public static GetFeedStatusResponse createFeedRequiredResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.FEED_MUST_BE_SPECIFIED);
    }

    public static GetFeedStatusResponse createFeedIsNotDefinedResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.FEED_IS_NOT_DEFINED);
    }

    public static GetFeedStatusResponse createFeedNotSetToReceiveDataResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA);
    }

    public static GetFeedStatusResponse createCertificateRequiredResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED);
    }

    public static GetFeedStatusResponse createCertificateNotAuthorisedResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.CLIENT_CERTIFICATE_NOT_AUTHENTICATED);
    }

    public FeedStatus getStatus() {
        return status;
    }

    public void setStatus(final FeedStatus feedStatus) {
        this.status = feedStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public StroomStatusCode getStroomStatusCode() {
        return stroomStatusCode;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("response ");
        builder.append(status);
        if (message != null) {
            builder.append(" - ");
            builder.append(message);
        }
        return builder.toString();
    }

}
