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

package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class GetRequest {

    @JsonProperty
    private final String mapName;
    @JsonProperty
    private final String keyName;
    @JsonProperty
    private final long eventTime;

    @JsonCreator
    public GetRequest(@JsonProperty("mapName") final String mapName,
                      @JsonProperty("keyName") final String keyName,
                      @JsonProperty("eventTime") final long eventTime) {
        this.mapName = mapName;
        this.keyName = keyName;
        this.eventTime = eventTime;
    }

    public String getMapName() {
        return mapName;
    }

    public String getKeyName() {
        return keyName;
    }

    public long getEventTime() {
        return eventTime;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetRequest that = (GetRequest) o;
        return eventTime == that.eventTime &&
               Objects.equals(mapName, that.mapName) &&
               Objects.equals(keyName, that.keyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapName, keyName, eventTime);
    }

    @Override
    public String toString() {
        return "GetRequest{" +
               "mapName='" + mapName + '\'' +
               ", keyName='" + keyName + '\'' +
               ", eventTime=" + eventTime +
               '}';
    }
}
