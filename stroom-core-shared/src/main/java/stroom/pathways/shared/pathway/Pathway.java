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

package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Pathway {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final NanoTime createTime;
    @JsonProperty
    private final NanoTime updateTime;
    @JsonProperty
    private final NanoTime lastUsedTime;
    @JsonProperty
    private final PathKey pathKey;
    @JsonProperty
    private final PathNode root;

    @JsonCreator
    public Pathway(@JsonProperty("name") final String name,
                   @JsonProperty("createTime") final NanoTime createTime,
                   @JsonProperty("updateTime") final NanoTime updateTime,
                   @JsonProperty("lastUsedTime") final NanoTime lastUsedTime,
                   @JsonProperty("pathKey") final PathKey pathKey,
                   @JsonProperty("root") final PathNode root) {
        this.name = name;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.lastUsedTime = lastUsedTime;
        this.pathKey = pathKey;
        this.root = root;
    }

    public String getName() {
        return name;
    }

    public NanoTime getCreateTime() {
        return createTime;
    }

    public NanoTime getUpdateTime() {
        return updateTime;
    }

    public NanoTime getLastUsedTime() {
        return lastUsedTime;
    }

    public PathKey getPathKey() {
        return pathKey;
    }

    public PathNode getRoot() {
        return root;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Pathway pathway = (Pathway) o;
        return Objects.equals(name, pathway.name) &&
               Objects.equals(createTime, pathway.createTime) &&
               Objects.equals(updateTime, pathway.updateTime) &&
               Objects.equals(lastUsedTime, pathway.lastUsedTime) &&
               Objects.equals(pathKey, pathway.pathKey) &&
               Objects.equals(root, pathway.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, createTime, updateTime, lastUsedTime, pathKey, root);
    }

    @Override
    public String toString() {
        return "Pathway{" +
               "name='" + name + '\'' +
               ", createTime=" + createTime +
               ", updateTime=" + updateTime +
               ", lastUsedTime=" + lastUsedTime +
               ", pathKey=" + pathKey +
               ", root=" + root +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Pathway, Builder> {

        private String name;
        private NanoTime createTime;
        private NanoTime updateTime;
        private NanoTime lastUsedTime;
        private PathKey pathKey;
        private PathNode root;

        public Builder() {
        }

        public Builder(final Pathway pathway) {
            this.name = pathway.name;
            this.createTime = pathway.createTime;
            this.updateTime = pathway.updateTime;
            this.lastUsedTime = pathway.lastUsedTime;
            this.pathKey = pathway.pathKey;
            this.root = pathway.root;
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder createTime(final NanoTime createTime) {
            this.createTime = createTime;
            return self();
        }

        public Builder updateTime(final NanoTime updateTime) {
            this.updateTime = updateTime;
            return self();
        }

        public Builder lastUsedTime(final NanoTime lastUsedTime) {
            this.lastUsedTime = lastUsedTime;
            return self();
        }

        public Builder pathKey(final PathKey pathKey) {
            this.pathKey = pathKey;
            return self();
        }

        public Builder root(final PathNode root) {
            this.root = root;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Pathway build() {
            return new Pathway(
                    name,
                    createTime,
                    updateTime,
                    lastUsedTime,
                    pathKey,
                    root);
        }
    }
}
