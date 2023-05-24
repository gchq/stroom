/*
 * Copyright 2016 Crown Copyright
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

package stroom.analytics.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.query.api.v2.QueryKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticRuleDoc extends Doc {

    public static final String DOCUMENT_TYPE = "AnalyticRule";

    @JsonProperty
    private final String description;
    @JsonProperty
    private final QueryLanguageVersion languageVersion;
    @JsonProperty
    private final String query;
    @JsonProperty
    private final AnalyticRuleType analyticRuleType;
    @JsonProperty
    private final DocRef destinationFeed;
    @JsonProperty
    private final AnalyticRuleProcessSettings processSettings;

    public AnalyticRuleDoc() {
        description = null;
        languageVersion = null;
        query = null;
        analyticRuleType = null;
        destinationFeed = null;
        processSettings = null;
    }

    @JsonCreator
    public AnalyticRuleDoc(@JsonProperty("type") final String type,
                           @JsonProperty("uuid") final String uuid,
                           @JsonProperty("name") final String name,
                           @JsonProperty("version") final String version,
                           @JsonProperty("createTimeMs") final Long createTimeMs,
                           @JsonProperty("updateTimeMs") final Long updateTimeMs,
                           @JsonProperty("createUser") final String createUser,
                           @JsonProperty("updateUser") final String updateUser,
                           @JsonProperty("description") final String description,
                           @JsonProperty("languageVersion") final QueryLanguageVersion languageVersion,
                           @JsonProperty("query") final String query,
                           @JsonProperty("analyticRuleType") AnalyticRuleType analyticRuleType,
                           @JsonProperty("destinationFeed") final DocRef destinationFeed,
                           @JsonProperty("processSettings") AnalyticRuleProcessSettings processSettings) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.languageVersion = languageVersion;
        this.query = query;
        this.analyticRuleType = analyticRuleType;
        this.destinationFeed = destinationFeed;
        this.processSettings = processSettings;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(DOCUMENT_TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(DOCUMENT_TYPE);
    }

    public String getDescription() {
        return description;
    }

    public QueryLanguageVersion getLanguageVersion() {
        return languageVersion;
    }

    public String getQuery() {
        return query;
    }

    public AnalyticRuleType getAnalyticRuleType() {
        return analyticRuleType;
    }

    public DocRef getDestinationFeed() {
        return destinationFeed;
    }

    public AnalyticRuleProcessSettings getProcessSettings() {
        return processSettings;
    }

    @JsonIgnore
    public QueryKey getQueryKey() {
        return new QueryKey(getUuid() + " - " + getName());
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
        final AnalyticRuleDoc that = (AnalyticRuleDoc) o;
        return Objects.equals(description, that.description) &&
                languageVersion == that.languageVersion &&
                Objects.equals(query, that.query) &&
                analyticRuleType == that.analyticRuleType &&
                Objects.equals(destinationFeed, that.destinationFeed) &&
                Objects.equals(processSettings, that.processSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                languageVersion,
                query,
                analyticRuleType,
                destinationFeed,
                processSettings);
    }

    @Override
    public String toString() {
        return "AnalyticRuleDoc{" +
                "description='" + description + '\'' +
                ", languageVersion=" + languageVersion +
                ", query='" + query + '\'' +
                ", alertRuleType=" + analyticRuleType +
                ", destinationFeed=" + destinationFeed +
                ", processSettings=" + processSettings +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<AnalyticRuleDoc, Builder> {

        private String description;
        private QueryLanguageVersion languageVersion;
        private String query;
        private AnalyticRuleType analyticRuleType;
        private DocRef destinationFeed;
        private AnalyticRuleProcessSettings processSettings;

        public Builder() {
        }

        public Builder(final AnalyticRuleDoc doc) {
            super(doc);
            this.description = doc.description;
            this.languageVersion = doc.languageVersion;
            this.query = doc.query;
            this.analyticRuleType = doc.analyticRuleType;
            this.destinationFeed = doc.destinationFeed;
            this.processSettings = doc.processSettings;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder languageVersion(final QueryLanguageVersion languageVersion) {
            this.languageVersion = languageVersion;
            return self();
        }

        public Builder query(final String query) {
            this.query = query;
            return self();
        }

        public Builder analyticRuleType(final AnalyticRuleType analyticRuleType) {
            this.analyticRuleType = analyticRuleType;
            return self();
        }

        public Builder destinationFeed(final DocRef destinationFeed) {
            this.destinationFeed = destinationFeed;
            return self();
        }

        public Builder processSettings(final AnalyticRuleProcessSettings processSettings) {
            this.processSettings = processSettings;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AnalyticRuleDoc build() {
            return new AnalyticRuleDoc(
                    type,
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    languageVersion,
                    query,
                    analyticRuleType,
                    destinationFeed,
                    processSettings);
        }
    }
}
