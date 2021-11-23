/*
 * Copyright 2017 Crown Copyright
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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CreateProcessFilterRequest {

    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final QueryData queryData;
    @JsonProperty
    private final int priority;
    @JsonProperty
    private final boolean autoPriority;
    @JsonProperty
    private boolean reprocess;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final Long minMetaCreateTimeMs;
    @JsonProperty
    private final Long maxMetaCreateTimeMs;

    @JsonCreator
    public CreateProcessFilterRequest(@JsonProperty("pipeline") final DocRef pipeline,
                                      @JsonProperty("queryData") final QueryData queryData,
                                      @JsonProperty("priority") final int priority,
                                      @JsonProperty("autoPriority") final boolean autoPriority,
                                      @JsonProperty("reprocess") final boolean reprocess,
                                      @JsonProperty("enabled") final boolean enabled,
                                      @JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                                      @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs) {
        this.pipeline = pipeline;
        this.queryData = queryData;
        this.priority = priority;
        this.autoPriority = autoPriority;
        this.reprocess = reprocess;
        this.enabled = enabled;
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public QueryData getQueryData() {
        return queryData;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAutoPriority() {
        return autoPriority;
    }

    public boolean isReprocess() {
        return reprocess;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreateProcessFilterRequest that = (CreateProcessFilterRequest) o;
        return priority == that.priority &&
                autoPriority == that.autoPriority &&
                reprocess == that.reprocess &&
                enabled == that.enabled &&
                Objects.equals(pipeline, that.pipeline) &&
                Objects.equals(queryData, that.queryData) &&
                Objects.equals(minMetaCreateTimeMs, that.minMetaCreateTimeMs) &&
                Objects.equals(maxMetaCreateTimeMs, that.maxMetaCreateTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipeline,
                queryData,
                priority,
                autoPriority,
                reprocess,
                enabled,
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs);
    }

    @Override
    public String toString() {
        return "CreateProcessFilterRequest{" +
                "pipeline=" + pipeline +
                ", queryData=" + queryData +
                ", priority=" + priority +
                ", autoPriority=" + autoPriority +
                ", reprocess=" + reprocess +
                ", enabled=" + enabled +
                ", minMetaCreateTimeMs=" + minMetaCreateTimeMs +
                ", maxMetaCreateTimeMs=" + maxMetaCreateTimeMs +
                '}';
    }

    public static class Builder {

        private DocRef pipeline;
        private QueryData queryData;
        private int priority = 10;
        private boolean autoPriority;
        private boolean reprocess;
        private boolean enabled = true;
        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;

        public Builder() {
        }

        public Builder(final CreateProcessFilterRequest request) {
            this.pipeline = request.pipeline;
            this.queryData = request.queryData;
            this.priority = request.priority;
            this.autoPriority = request.autoPriority;
            this.reprocess = request.reprocess;
            this.enabled = request.enabled;
            this.minMetaCreateTimeMs = request.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = request.maxMetaCreateTimeMs;
        }

        public Builder pipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder queryData(final QueryData queryData) {
            this.queryData = queryData;
            return this;
        }

        public Builder priority(final int priority) {
            this.priority = priority;
            return this;
        }

        public Builder autoPriority(final boolean autoPriority) {
            this.autoPriority = autoPriority;
            return this;
        }

        public Builder reprocess(final boolean reprocess) {
            this.reprocess = reprocess;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return this;
        }

        public Builder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return this;
        }

        public CreateProcessFilterRequest build() {
            return new CreateProcessFilterRequest(
                    pipeline,
                    queryData,
                    priority,
                    autoPriority,
                    reprocess,
                    enabled,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs);
        }
    }
}
