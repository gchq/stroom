/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Description(
        "Defines a data generator which can be used to send data into a Stroom Feed.\n" +
        "The data is defined as a String.\n" +
        "The schedule on which the data is sent into the feed can be customised.")
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DataGenDoc extends AbstractDoc {

    public static final String TYPE = "DataGen";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ANALYTIC_RULE_DOCUMENT_TYPE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String template;
    @JsonProperty
    private final DocRef feed;

    @JsonCreator
    public DataGenDoc(@JsonProperty("uuid") final String uuid,
                      @JsonProperty("name") final String name,
                      @JsonProperty("version") final String version,
                      @JsonProperty("createTimeMs") final Long createTimeMs,
                      @JsonProperty("updateTimeMs") final Long updateTimeMs,
                      @JsonProperty("createUser") final String createUser,
                      @JsonProperty("updateUser") final String updateUser,
                      @JsonProperty("description") final String description,
                      @JsonProperty("template") final String template,
                      @JsonProperty("feed") final DocRef feed) {
        super(TYPE, uuid,
                name,
                version,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser);

        this.description = description;
        this.template = template;
        this.feed = feed;
    }

    public String getDescription() {
        return description;
    }

    public String getTemplate() {
        return template;
    }

    public DocRef getFeed() {
        return feed;
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<DataGenDoc, Builder> {

        private String template;
        private String description;
        private DocRef feed;

        public Builder() {
        }

        public Builder(final DataGenDoc doc) {
            super(doc);
            this.template = doc.template;
            this.description = doc.description;
            this.feed = doc.feed;
        }

        public Builder template(final String template) {
            this.template = template;
            return self();
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder feed(final DocRef feed) {
            this.feed = feed;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public DataGenDoc build() {
            return new DataGenDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    template,
                    feed);
        }
    }
}
