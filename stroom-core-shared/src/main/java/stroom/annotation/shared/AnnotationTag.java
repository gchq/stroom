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

import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.query.api.ConditionalFormattingStyle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AnnotationTag {

    public static final String TYPE = "AnnotationTag";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ANNOTATION_TAG_DOCUMENT_TYPE;

    @JsonProperty
    private final int id;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final AnnotationTagType type;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final ConditionalFormattingStyle style;

    @JsonCreator
    public AnnotationTag(@JsonProperty("id") final int id,
                         @JsonProperty("uuid") final String uuid,
                         @JsonProperty("type") final AnnotationTagType type,
                         @JsonProperty("name") final String name,
                         @JsonProperty("style") final ConditionalFormattingStyle style) {
        this.id = id;
        this.uuid = uuid;
        this.type = type;
        this.name = name;
        this.style = style;
    }

    public int getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public AnnotationTagType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public ConditionalFormattingStyle getStyle() {
        return style;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnnotationTag that = (AnnotationTag) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AnnotationTag{" +
               "id=" + id +
               ", uuid='" + uuid + '\'' +
               ", type=" + type +
               ", name='" + name + '\'' +
               ", style=" + style +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private int id;
        private String uuid;
        private AnnotationTagType type;
        private String name;
        private ConditionalFormattingStyle style;

        public Builder() {
        }

        public Builder(final AnnotationTag doc) {
            this.id = doc.id;
            this.uuid = doc.uuid;
            this.type = doc.type;
            this.name = doc.name;
            this.style = doc.style;
        }

        public Builder id(final int id) {
            this.id = id;
            return self();
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public Builder type(final AnnotationTagType type) {
            this.type = type;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder style(final ConditionalFormattingStyle style) {
            this.style = style;
            return self();
        }

        protected Builder self() {
            return this;
        }

        public AnnotationTag build() {
            return new AnnotationTag(
                    id,
                    uuid,
                    type,
                    name,
                    style);
        }
    }
}
