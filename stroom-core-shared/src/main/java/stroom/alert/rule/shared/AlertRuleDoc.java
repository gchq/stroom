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

package stroom.alert.rule.shared;

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
public class AlertRuleDoc extends Doc {

    public static final String DOCUMENT_TYPE = "AlertRule";

    @JsonProperty
    private final String description;
    @JsonProperty
    private final QueryLanguageVersion languageVersion;
    @JsonProperty
    private final String query;
    @JsonProperty
    private final AlertRuleType alertRuleType;
    @JsonProperty
    private final String timeField;
    @JsonProperty
    private final DocRef destinationFeed;
    @JsonProperty
    private final AlertRuleProcessSettings processSettings;

    public AlertRuleDoc() {
        description = null;
        languageVersion = null;
        query = null;
        alertRuleType = null;
        timeField = null;
        destinationFeed = null;
        processSettings = null;
    }

    @JsonCreator
    public AlertRuleDoc(@JsonProperty("type") final String type,
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
                        @JsonProperty("alertRuleType") AlertRuleType alertRuleType,
                        @JsonProperty("timeField") final String timeField,
                        @JsonProperty("destinationFeed") final DocRef destinationFeed,
                        @JsonProperty("processSettings") AlertRuleProcessSettings processSettings) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.languageVersion = languageVersion;
        this.query = query;
        this.alertRuleType = alertRuleType;
        this.timeField = timeField;
        this.destinationFeed = destinationFeed;
        this.processSettings = processSettings;
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

    public AlertRuleType getAlertRuleType() {
        return alertRuleType;
    }

    public String getTimeField() {
        return timeField;
    }

    public DocRef getDestinationFeed() {
        return destinationFeed;
    }

    public AlertRuleProcessSettings getProcessSettings() {
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
        final AlertRuleDoc that = (AlertRuleDoc) o;
        return Objects.equals(description,
                that.description) && languageVersion == that.languageVersion && Objects.equals(query,
                that.query) && alertRuleType == that.alertRuleType && Objects.equals(timeField,
                that.timeField) && Objects.equals(destinationFeed,
                that.destinationFeed) && Objects.equals(processSettings, that.processSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                languageVersion,
                query,
                alertRuleType,
                timeField,
                destinationFeed,
                processSettings);
    }

    @Override
    public String toString() {
        return "AlertRuleDoc{" +
                "description='" + description + '\'' +
                ", languageVersion=" + languageVersion +
                ", query='" + query + '\'' +
                ", alertRuleType=" + alertRuleType +
                ", timeField='" + timeField + '\'' +
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

    public static class Builder extends AbstractBuilder<AlertRuleDoc, Builder> {

        private String description;
        private QueryLanguageVersion languageVersion;
        private String query;
        private AlertRuleType alertRuleType;
        private String timeField;
        private DocRef destinationFeed;
        private AlertRuleProcessSettings processSettings;

        public Builder() {
        }

        public Builder(final AlertRuleDoc doc) {
            super(doc);
            this.description = doc.description;
            this.languageVersion = doc.languageVersion;
            this.query = doc.query;
            this.alertRuleType = doc.alertRuleType;
            this.timeField = doc.timeField;
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

        public Builder alertRuleType(final AlertRuleType alertRuleType) {
            this.alertRuleType = alertRuleType;
            return self();
        }

        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return self();
        }

        public Builder destinationFeed(final DocRef destinationFeed) {
            this.destinationFeed = destinationFeed;
            return self();
        }

        public Builder processSettings(final AlertRuleProcessSettings processSettings) {
            this.processSettings = processSettings;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AlertRuleDoc build() {
            return new AlertRuleDoc(
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
                    alertRuleType,
                    timeField,
                    destinationFeed,
                    processSettings);
        }
    }
}
