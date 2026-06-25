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

package stroom.cluster.lock.impl.db;

import stroom.util.date.DateUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterLockKey {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final long creationTime;

    @JsonCreator
    public ClusterLockKey(@JsonProperty("name") final String name,
                          @JsonProperty("nodeName") final String nodeName,
                          @JsonProperty("creationTime") final long creationTime) {
        this.name = name;
        this.nodeName = nodeName;
        this.creationTime = creationTime;
    }

    public String getName() {
        return name;
    }

    public String getNodeName() {
        return nodeName;
    }

    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ClusterLockKey that = (ClusterLockKey) o;
        return creationTime == that.creationTime &&
                Objects.equals(name, that.name) &&
                Objects.equals(nodeName, that.nodeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nodeName, creationTime);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public void append(final StringBuilder sb) {
        sb.append("name=");
        sb.append(name);
        sb.append(" node=");
        sb.append(nodeName);
        sb.append(" creationTime=");
        sb.append(DateUtil.createNormalDateTimeString(creationTime));
    }
}
