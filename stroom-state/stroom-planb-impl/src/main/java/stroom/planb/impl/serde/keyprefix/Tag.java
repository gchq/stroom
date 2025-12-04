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

package stroom.planb.impl.serde.keyprefix;

import stroom.query.language.functions.Val;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"tagName", "tagValue"})
@JsonInclude(Include.NON_NULL)
public class Tag {

    @JsonProperty
    private final String tagName;
    @JsonProperty
    private final Val tagValue;

    @JsonCreator
    public Tag(@JsonProperty("tagName") final String tagName,
               @JsonProperty("tagValue") final Val tagValue) {
        this.tagName = tagName;
        this.tagValue = tagValue;
    }

    public String getTagName() {
        return tagName;
    }

    public Val getTagValue() {
        return tagValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Tag tag = (Tag) o;
        return Objects.equals(tagName, tag.tagName) && Objects.equals(tagValue, tag.tagValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagName, tagValue);
    }

    @Override
    public String toString() {
        return "Tag{" +
               "tagName='" + tagName + '\'' +
               ", tagValue=" + tagValue +
               '}';
    }

    public static class Builder {

        private String tagName;
        private Val tagValue;

        public Builder() {
        }

        public Builder(final Tag tag) {
            this.tagName = tag.tagName;
            this.tagValue = tag.tagValue;
        }

        public Builder tagName(final String tagName) {
            this.tagName = tagName;
            return this;
        }

        public Builder tagValue(final Val tagValue) {
            this.tagValue = tagValue;
            return this;
        }

        public Tag build() {
            return new Tag(tagName, tagValue);
        }
    }
}
