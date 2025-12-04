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

package stroom.proxy.feed.remote;

import stroom.proxy.StroomStatusCode;
import stroom.proxy.remote.RemoteResponse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class GetFeedStatusResponse extends RemoteResponse {

    private static final long serialVersionUID = 9221787861812287256L;

    @JsonProperty
    private FeedStatus status;
    @JsonProperty
    private String message;
    @JsonProperty
    private StroomStatusCode stroomStatusCode;

    public GetFeedStatusResponse() {
        this.status = FeedStatus.Receive;
    }

    private GetFeedStatusResponse(final FeedStatus status,
                                  final StroomStatusCode stroomStatusCode) {
        this.status = status;
        this.stroomStatusCode = stroomStatusCode;
        if (stroomStatusCode != null) {
            this.message = stroomStatusCode.getMessage();
        }
    }

    @JsonCreator
    public GetFeedStatusResponse(@JsonProperty("status") final FeedStatus status,
                                 @JsonProperty("message") final String message,
                                 @JsonProperty("stroomStatusCode") final StroomStatusCode stroomStatusCode) {
        this.status = status;
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
