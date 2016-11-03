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

package stroom.feed.remote;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import stroom.remote.RemoteRequest;

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
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof GetFeedStatusRequest)) {
            return false;
        }
        GetFeedStatusRequest other = (GetFeedStatusRequest) obj;
        EqualsBuilder equalsBuilder = new EqualsBuilder();
        equalsBuilder.append(this.getFeedName(), other.getFeedName());
        equalsBuilder.append(this.getSenderDn(), other.getSenderDn());
        return equalsBuilder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(this.getFeedName());
        hashCodeBuilder.append(this.getSenderDn());
        return hashCodeBuilder.toHashCode();
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
