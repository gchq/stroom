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

package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CreateAnnotationTagRequest {

    @JsonProperty
    private final AnnotationTagType type;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String tagText;

    @JsonCreator
    public CreateAnnotationTagRequest(
            @JsonProperty("type") final AnnotationTagType type,
            @JsonProperty("name") final String name,
            @JsonProperty("tagText") final String tagText) {
        this.type = type;
        this.name = name;
        this.tagText = tagText;
    }

    public AnnotationTagType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getTagText() {
        return tagText;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreateAnnotationTagRequest that = (CreateAnnotationTagRequest) o;
        return type == that.type &&
               Objects.equals(name, that.name) &&
               Objects.equals(tagText, that.tagText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return "CreateAnnotationTagRequest{" +
               "type=" + type +
               ", name='" + name + '\'' +
               ", tagText='" + tagText + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private AnnotationTagType type;
        private String name;
        private String tagText;

        public Builder() {
        }

        public Builder(final CreateAnnotationTagRequest doc) {
            this.type = doc.type;
            this.name = doc.name;
            this.tagText = doc.tagText;
        }


        public Builder type(final AnnotationTagType type) {
            this.type = type;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder tagText(final String tagText) {
            this.tagText = tagText;
            return self();
        }

        protected Builder self() {
            return this;
        }

        public CreateAnnotationTagRequest build() {
            return new CreateAnnotationTagRequest(
                    type,
                    name,
                    tagText);
        }
    }
}
