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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "backgroundColour",
        "textColour"})
@JsonInclude(Include.NON_NULL)
public class CustomRowStyle {

    @JsonProperty("backgroundColour")
    private final String backgroundColour;
    @JsonProperty("textColour")
    private final String textColour;

    @JsonCreator
    public CustomRowStyle(@JsonProperty("backgroundColour") final String backgroundColour,
                          @JsonProperty("textColour") final String textColour) {
        this.backgroundColour = backgroundColour;
        this.textColour = textColour;
    }

    public static CustomRowStyle create(final String backgroundColour, final String textColour) {
        final String backgroundColourTrimmed = NullSafe.trim(backgroundColour);
        final String textColourTrimmed = NullSafe.trim(textColour);
        if (backgroundColourTrimmed.length() > 0 || textColourTrimmed.length() > 0) {
            return new CustomRowStyle(
                    backgroundColourTrimmed.length() == 0
                            ? null
                            : backgroundColourTrimmed,
                    textColourTrimmed.length() == 0
                            ? null
                            : textColourTrimmed);
        }
        return null;
    }

    public String getBackgroundColour() {
        return backgroundColour;
    }

    public String getTextColour() {
        return textColour;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CustomRowStyle that = (CustomRowStyle) o;
        return Objects.equals(backgroundColour, that.backgroundColour) &&
               Objects.equals(textColour, that.textColour);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backgroundColour, textColour);
    }

    @Override
    public String toString() {
        return "CustomRowStyle{" +
               "backgroundColour='" + backgroundColour + '\'' +
               ", textColour='" + textColour + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String backgroundColour;
        private String textColour;


        private Builder() {
        }

        private Builder(final CustomRowStyle rule) {
            this.backgroundColour = rule.backgroundColour;
            this.textColour = rule.textColour;
        }

        public Builder backgroundColour(final String backgroundColour) {
            this.backgroundColour = null;
            if (backgroundColour != null && backgroundColour.trim().length() > 0) {
                this.backgroundColour = backgroundColour.trim();
            }
            return this;
        }

        public Builder textColour(final String textColour) {
            this.textColour = null;
            if (textColour != null && textColour.trim().length() > 0) {
                this.textColour = textColour.trim();
            }
            return this;
        }

        public CustomRowStyle build() {
            return new CustomRowStyle(
                    backgroundColour,
                    textColour);
        }
    }
}
