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

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanStatus {

    @JsonProperty("message")
    private final String message;

    @JsonProperty("code")
    private final StatusCode code;

    @JsonCreator
    public SpanStatus(@JsonProperty("message") final String message,
                      @JsonProperty("code") final StatusCode code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public StatusCode getCode() {
        return code;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SpanStatus that = (SpanStatus) o;
        return Objects.equals(message, that.message) &&
               code == that.code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, code);
    }

    @Override
    public String toString() {
        return "SpanStatus{" +
               "message='" + message + '\'' +
               ", code=" + code +
               '}';
    }


    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<SpanStatus, Builder> {

        private String message;
        private StatusCode code;

        private Builder() {
        }

        private Builder(final SpanStatus spanStatus) {
            this.message = spanStatus.message;
            this.code = spanStatus.code;
        }

        public Builder message(final String message) {
            this.message = message;
            return self();
        }

        public Builder code(final StatusCode code) {
            this.code = code;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SpanStatus build() {
            return new SpanStatus(
                    message,
                    code
            );
        }
    }
}
