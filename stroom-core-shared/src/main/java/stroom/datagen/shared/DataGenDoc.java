/*
 * Copyright 2026 Crown Copyright
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

import java.util.Objects;

/**
 * A data generator: a fixed block of text ({@link #getTemplate()}) that is written into a feed
 * ({@link #getFeed()}) as a Raw Events stream every time its schedule fires. Useful for
 * generating test or demonstration data without an external sender.
 * <p>
 * The schedule itself is not held here - it lives in an {@code ExecutionSchedule} keyed on this
 * doc, managed from the Execution tab of the editor.
 * </p>
 * <p>
 * Both the template and the feed are nullable, because a doc is created before it is configured
 * and must be saveable in that state. Anything acting on a {@link DataGenDoc} therefore has to
 * cope with a half-configured one; see {@code ScheduledDataGenExecutable}.
 * </p>
 */
@Description(
        """
        Defines a data generator which can be used to send data into a Stroom Feed.
        The data is defined as a String.
        The schedule on which the data is sent into the feed can be customised.
        """)
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DataGenDoc extends AbstractDoc {

    public static final String TYPE = "DataGen";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.DATA_GENERATOR_DOCUMENT_TYPE;

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

    /**
     * @return The literal data written into the destination feed, or null if not yet configured.
     */
    public String getTemplate() {
        return template;
    }

    /**
     * @return The feed the generated data is written to, or null if not yet configured.
     * <p>
     * Only the type and UUID of the returned {@link DocRef} are dependable. Its name is a
     * decoration captured when the feed was picked in the UI, so it goes stale if the feed is
     * later renamed - resolve the name from the feed itself rather than trusting this copy.
     * </p>
     */
    public DocRef getFeed() {
        return feed;
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final DataGenDoc that = (DataGenDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(template, that.template) &&
               Objects.equals(feed, that.feed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, template, feed);
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
