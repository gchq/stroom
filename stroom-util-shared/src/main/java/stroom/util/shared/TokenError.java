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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class TokenError {

    @JsonProperty
    private final DefaultLocation from;
    @JsonProperty
    private final DefaultLocation to;
    @JsonProperty
    private final String text;

    @JsonCreator
    public TokenError(@JsonProperty("from") final DefaultLocation from,
                      @JsonProperty("to") final DefaultLocation to,
                      @JsonProperty("text") final String text) {
        this.from = from;
        this.to = to;
        this.text = text;
    }

    public DefaultLocation getFrom() {
        return from;
    }

    public DefaultLocation getTo() {
        return to;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TokenError that = (TokenError) o;
        return Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, text);
    }

    @Override
    public String toString() {
        return "TokenError{" +
                "from=" + from +
                ", to=" + to +
                ", text='" + text + '\'' +
                '}';
    }
}
