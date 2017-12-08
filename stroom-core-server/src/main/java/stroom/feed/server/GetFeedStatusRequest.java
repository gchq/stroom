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

package stroom.feed.server;

public class GetFeedStatusRequest extends RemoteRequest {
    private static final long serialVersionUID = -4083508707616388035L;

    private String feedName;
    private String senderDn;

    public GetFeedStatusRequest() {
    }

    public GetFeedStatusRequest(final String feedName) {
        this.feedName = feedName;
    }

    public GetFeedStatusRequest(final String feedName, final String senderDn) {
        this.feedName = feedName;
        this.senderDn = senderDn;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getSenderDn() {
        return senderDn;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final GetFeedStatusRequest that = (GetFeedStatusRequest) o;

        if (feedName != null ? !feedName.equals(that.feedName) : that.feedName != null) return false;
        return senderDn != null ? senderDn.equals(that.senderDn) : that.senderDn == null;
    }

    @Override
    public int hashCode() {
        int result = feedName != null ? feedName.hashCode() : 0;
        result = 31 * result + (senderDn != null ? senderDn.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("request ");
        builder.append(feedName);
        if (senderDn != null) {
            builder.append(" - ");
            builder.append(senderDn);
        }
        return builder.toString();
    }

}
