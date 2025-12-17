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

import stroom.proxy.remote.RemoteRequest;
import stroom.util.shared.UserDesc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class GetFeedStatusRequestV2 extends RemoteRequest {

    private static final long serialVersionUID = -4083508707616388035L;

    @JsonProperty
    private String feedName;
    @JsonProperty
    private UserDesc userDesc;
    @JsonProperty
    private Map<String, String> attributeMap;

    /**
     * @param feedName     Name of the feed being checked
     * @param userDesc     The user identity that sent the data that has
     *                     triggered this feed check.
     * @param attributeMap The map of headers from the request. Keys are case-insensitive.
     */
    @JsonCreator
    public GetFeedStatusRequestV2(@JsonProperty("feedName") final String feedName,
                                  @JsonProperty("userDesc") final UserDesc userDesc,
                                  @JsonProperty("attributeMap") final Map<String, String> attributeMap) {
        this.feedName = feedName;
        this.userDesc = userDesc;
        this.attributeMap = attributeMap;
    }

    /**
     * @return The name of
     */
    public String getFeedName() {
        return feedName;
    }

    public UserDesc getUserDesc() {
        return userDesc;
    }

    public Map<String, String> getAttributeMap() {
        return attributeMap;
    }

    // IMPORTANT - if attributeMap is in equals/hashcode then it stops us being able to cache
    // FeedStatusUpdater instances by request. If attributeMap has a bearing on the feed status
    // then we need to re-think. Currently only used for the initial content auto creation.
    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final GetFeedStatusRequestV2 that = (GetFeedStatusRequestV2) object;
        return Objects.equals(feedName, that.feedName)
               && Objects.equals(userDesc, that.userDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, userDesc);
    }

    @Override
    public String toString() {
        return "GetFeedStatusRequestV2{" +
               "feedName='" + feedName + '\'' +
               ", subjectId='" + userDesc + '\'' +
               ", attributeMap=" + attributeMap +
               '}';
    }
}
