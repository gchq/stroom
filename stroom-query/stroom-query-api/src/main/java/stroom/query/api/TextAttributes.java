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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "bold",
        "italic"
})
@JsonInclude(Include.NON_NULL)
public class TextAttributes {

    @JsonProperty("bold")
    private final boolean bold;
    @JsonProperty("italic")
    private final boolean italic;

    @JsonCreator
    public TextAttributes(@JsonProperty("bold") final boolean bold,
                          @JsonProperty("italic") final boolean italic) {
        this.bold = bold;
        this.italic = italic;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TextAttributes that = (TextAttributes) o;
        return bold == that.bold &&
               italic == that.italic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bold, italic);
    }

    @Override
    public String toString() {
        return "TextAttributes{" +
               "bold=" + bold +
               ", italic=" + italic +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private boolean bold;
        private boolean italic;

        private Builder() {
        }

        private Builder(final TextAttributes textAttributes) {
            this.bold = textAttributes.bold;
            this.italic = textAttributes.italic;
        }

        public Builder bold(final boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder italic(final boolean italic) {
            this.italic = italic;
            return this;
        }

        public TextAttributes build() {
            return new TextAttributes(
                    bold,
                    italic);
        }
    }
}
