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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticDataShard {

    @JsonProperty
    private final String node;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final long createTimeMs;
    @JsonProperty
    private final long size;

    @JsonCreator
    public AnalyticDataShard(@JsonProperty("node") final String node,
                             @JsonProperty("path") final String path,
                             @JsonProperty("createTimeMs") final long createTimeMs,
                             @JsonProperty("size") final long size) {
        this.node = node;
        this.path = path;
        this.createTimeMs = createTimeMs;
        this.size = size;
    }

    public String getNode() {
        return node;
    }

    public String getPath() {
        return path;
    }

    public long getCreateTimeMs() {
        return createTimeMs;
    }

    public long getSize() {
        return size;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticDataShard that = (AnalyticDataShard) o;
        return createTimeMs == that.createTimeMs &&
                size == that.size &&
                Objects.equals(node, that.node) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, path, createTimeMs, size);
    }

    @Override
    public String toString() {
        return "AnalyticDataShard{" +
                "node='" + node + '\'' +
                ", path='" + path + '\'' +
                ", createTimeMs=" + createTimeMs +
                ", size=" + size +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String node;
        private String path;
        private long createTimeMs;
        private long size;

        private Builder() {
        }

        private Builder(final AnalyticDataShard shard) {
            this.node = shard.node;
            this.path = shard.path;
            this.createTimeMs = shard.createTimeMs;
            this.size = shard.size;
        }

        public Builder node(final String node) {
            this.node = node;
            return this;
        }

        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return this;
        }

        public Builder size(final long size) {
            this.size = size;
            return this;
        }

        public AnalyticDataShard build() {
            return new AnalyticDataShard(
                    node,
                    path,
                    createTimeMs,
                    size);
        }
    }
}
