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

package stroom.feed;

public class GetFeedStatusResponse extends RemoteResponse {
    private static final long serialVersionUID = 9221787861812287256L;

    private FeedStatus status = FeedStatus.Receive;
    private String message = null;
    private StroomStatusCode stroomStatusCode;

    public GetFeedStatusResponse() {
    }

    private GetFeedStatusResponse(final FeedStatus feedStatus, final StroomStatusCode stroomStatusCode) {
        this.status = feedStatus;
        this.stroomStatusCode = stroomStatusCode;
        if (stroomStatusCode != null) {
            this.message = stroomStatusCode.getMessage();
        }
    }

    public static GetFeedStatusResponse createOKRecieveResponse() {
        return new GetFeedStatusResponse();
    }

    public static GetFeedStatusResponse createOKDropResponse() {
        return new GetFeedStatusResponse(FeedStatus.Drop, null);
    }

    public static GetFeedStatusResponse createFeedRequiredResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.FEED_MUST_BE_SPECIFIED);
    }

    public static GetFeedStatusResponse createFeedIsNotDefinedResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.FEED_IS_NOT_DEFINED);
    }

    public static GetFeedStatusResponse createFeedNotSetToReceiveDataResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
    }

    public static GetFeedStatusResponse createCertificateRequiredResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED);
    }

    public static GetFeedStatusResponse createCertificateNotAuthorisedResponse() {
        return new GetFeedStatusResponse(FeedStatus.Reject, StroomStatusCode.CLIENT_CERTIFICATE_NOT_AUTHORISED);
    }

    public FeedStatus getStatus() {
        return status;
    }

    public void setStatus(FeedStatus feedStatus) {
        this.status = feedStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StroomStatusCode getStroomStatusCode() {
        return stroomStatusCode;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("response ");
        builder.append(status);
        if (message != null) {
            builder.append(" - ");
            builder.append(message);
        }
        return builder.toString();
    }

}
