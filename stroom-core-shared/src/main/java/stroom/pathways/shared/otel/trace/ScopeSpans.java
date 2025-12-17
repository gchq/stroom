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

package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ScopeSpans {

    @JsonProperty("scope")
    private final InstrumentationScope scope;

    @JsonProperty("spans")
    private final List<Span> spans;

    @JsonProperty("schemaUrl")
    private final String schemaUrl;

    @JsonCreator
    public ScopeSpans(@JsonProperty("scope") final InstrumentationScope scope,
                      @JsonProperty("spans") final List<Span> spans,
                      @JsonProperty("schemaUrl") final String schemaUrl) {
        this.scope = scope;
        this.spans = spans;
        this.schemaUrl = schemaUrl;
    }

    public InstrumentationScope getScope() {
        return scope;
    }

    public List<Span> getSpans() {
        return spans;
    }

    public String getSchemaUrl() {
        return schemaUrl;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ScopeSpans that = (ScopeSpans) o;
        return Objects.equals(scope, that.scope) &&
               Objects.equals(spans, that.spans) &&
               Objects.equals(schemaUrl, that.schemaUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, spans, schemaUrl);
    }

    @Override
    public String toString() {
        return "ScopeSpans{" +
               "scope=" + scope +
               ", spans=" + spans +
               ", schemaUrl='" + schemaUrl + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<ScopeSpans, Builder> {

        private InstrumentationScope scope;
        private List<Span> spans;
        private String schemaUrl;

        private Builder() {
        }

        private Builder(final ScopeSpans scopeSpans) {
            this.scope = scopeSpans.scope;
            this.spans = scopeSpans.spans;
            this.schemaUrl = scopeSpans.schemaUrl;
        }

        public Builder scope(final InstrumentationScope scope) {
            this.scope = scope;
            return self();
        }

        public Builder spans(final List<Span> spans) {
            this.spans = spans;
            return self();
        }

        public Builder schemaUrl(final String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ScopeSpans build() {
            return new ScopeSpans(
                    scope,
                    spans,
                    schemaUrl
            );
        }
    }
}

