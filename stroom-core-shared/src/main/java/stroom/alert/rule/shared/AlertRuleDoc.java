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
    private final boolean enabled;
    @JsonProperty
    private final AlertRuleType alertRuleType;
    @JsonProperty
    private final AbstractAlertRule alertRule;

    public AlertRuleDoc() {
        description = null;
        languageVersion = null;
        query = null;
        enabled = false;
        alertRuleType = null;
        alertRule = null;
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
                        @JsonProperty("enabled") final boolean enabled,
                        @JsonProperty("alertRuleType") AlertRuleType alertRuleType,
                        @JsonProperty("alertRule") AbstractAlertRule alertRule) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.languageVersion = languageVersion;
        this.query = query;
        this.enabled = enabled;
        this.alertRuleType = alertRuleType;
        this.alertRule = alertRule;
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

    public boolean isEnabled() {
        return enabled;
    }

    public AlertRuleType getAlertRuleType() {
        return alertRuleType;
    }

    public AbstractAlertRule getAlertRule() {
        return alertRule;
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
        return enabled == that.enabled && Objects.equals(description,
                that.description) && languageVersion == that.languageVersion && Objects.equals(query,
                that.query) && alertRuleType == that.alertRuleType && Objects.equals(alertRule, that.alertRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, languageVersion, query, enabled, alertRuleType, alertRule);
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
        private boolean enabled;
        private AlertRuleType alertRuleType;
        private AbstractAlertRule alertRule;

        public Builder() {
        }

        public Builder(final AlertRuleDoc doc) {
            super(doc);
            this.description = doc.description;
            this.languageVersion = doc.languageVersion;
            this.query = doc.query;
            this.enabled = doc.enabled;
            this.alertRuleType = doc.alertRuleType;
            this.alertRule = doc.alertRule;
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

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public Builder alertRuleType(final AlertRuleType alertRuleType) {
            this.alertRuleType = alertRuleType;
            return self();
        }

        public Builder alertRule(final AbstractAlertRule alertRule) {
            this.alertRule = alertRule;
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
                    enabled,
                    alertRuleType,
                    alertRule);
        }
    }
}
