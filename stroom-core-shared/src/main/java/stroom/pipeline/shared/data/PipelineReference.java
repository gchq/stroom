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

package stroom.pipeline.shared.data;

import stroom.docref.DocRef;
import stroom.util.shared.CompareBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"element", "name", "pipeline", "feed", "streamType"})
public final class PipelineReference implements Comparable<PipelineReference> {

    @JsonProperty
    private final String element;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final DocRef feed;
    @JsonProperty
    private final String streamType;

    @JsonIgnore
    private int hashCode = -1;

    public PipelineReference(final DocRef pipeline,
                             final DocRef feed,
                             final String streamType) {
        this(null, null, pipeline, feed, streamType);
    }

    @JsonCreator
    public PipelineReference(@JsonProperty("element") final String element,
                             @JsonProperty("name") final String name,
                             @JsonProperty("pipeline") final DocRef pipeline,
                             @JsonProperty("feed") final DocRef feed,
                             @JsonProperty("streamType") final String streamType) {
        this.element = element;
        this.name = name;
        this.pipeline = pipeline;
        this.feed = feed;
        this.streamType = streamType;
    }

    public String getElement() {
        return element;
    }

    public String getName() {
        return name;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public DocRef getFeed() {
        return feed;
    }

    public String getStreamType() {
        return streamType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineReference that = (PipelineReference) o;
        return Objects.equals(element, that.element) &&
               Objects.equals(name, that.name) &&
               Objects.equals(pipeline, that.pipeline) &&
               Objects.equals(feed, that.feed) &&
               Objects.equals(streamType, that.streamType);
    }

    @Override
    public int hashCode() {
        if (hashCode == -1) {
            hashCode = Objects.hash(element, name, pipeline, feed, streamType);
        }
        return hashCode;
    }

    @Override
    public int compareTo(final PipelineReference o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(element, o.element);
        builder.append(name, o.name);
        builder.append(pipeline, o.pipeline);
        builder.append(feed, o.feed);
        builder.append(streamType, o.streamType);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "element=" + element + ", name=" + name + ", pipeline=" + pipeline + ", feed=" + feed + ", streamType="
               + streamType;
    }


    public static class Builder {

        private String element;
        private String name;
        private DocRef pipeline;
        private DocRef feed;
        private String streamType;

        public Builder() {
        }

        public Builder(final PipelineReference reference) {
            if (reference != null) {
                this.element = reference.element;
                this.name = reference.name;
                this.pipeline = reference.pipeline;
                this.feed = reference.feed;
                this.streamType = reference.streamType;
            }
        }

        public Builder element(final String element) {
            this.element = element;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder pipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder feed(final DocRef feed) {
            this.feed = feed;
            return this;
        }

        public Builder streamType(final String streamType) {
            this.streamType = streamType;
            return this;
        }

        public PipelineReference build() {
            return new PipelineReference(element, name, pipeline, feed, streamType);
        }
    }
}
