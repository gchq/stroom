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

package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FeedDependency {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final String streamType;

    @JsonCreator
    public FeedDependency(@JsonProperty("uuid") final String uuid,
                          @JsonProperty("feedName") final String feedName,
                          @JsonProperty("streamType") final String streamType) {
        this.uuid = uuid;
        this.feedName = feedName;
        this.streamType = streamType;
    }

    public String getUuid() {
        return uuid;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getStreamType() {
        return streamType;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeedDependency that = (FeedDependency) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public String toString() {
        return "FeedDependency{" +
               "uuid='" + uuid + '\'' +
               ", feedName=" + feedName +
               ", streamType='" + streamType + '\'' +
               '}';
    }
}
