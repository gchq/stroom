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

package stroom.receive.rules.shared;

import stroom.docref.DocRef;
import stroom.docref.DocRef.TypedBuilder;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "fields",
        "rules"})
@JsonInclude(Include.NON_NULL)
public class ReceiveDataRules extends AbstractDoc {

    public static final String TYPE = "ReceiveDataRuleSet";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.RECEIVE_DATA_RULESET_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private List<QueryField> fields;
    @JsonProperty
    private List<ReceiveDataRule> rules;

    @JsonCreator
    public ReceiveDataRules(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("description") final String description,
                            @JsonProperty("fields") final List<QueryField> fields,
                            @JsonProperty("rules") final List<ReceiveDataRule> rules) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.fields = fields;
        this.rules = rules;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public List<QueryField> getFields() {
        return fields;
    }

    public void setFields(final List<QueryField> fields) {
        this.fields = fields;
    }

    public List<ReceiveDataRule> getRules() {
        return rules;
    }

    @JsonIgnore
    public List<ReceiveDataRule> getEnabledRules() {
        return NullSafe.stream(rules)
                .filter(ReceiveDataRule::isEnabled)
                .collect(Collectors.toList());
    }

    public void setRules(final List<ReceiveDataRule> rules) {
        this.rules = rules;
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
        final ReceiveDataRules that = (ReceiveDataRules) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(fields, that.fields) &&
               Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, fields, rules);
    }

    @Override
    public String toString() {
        return "ReceiveDataRules{" +
               "description='" + description + '\'' +
               ", fields=" + fields +
               ", rules=" + rules +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDoc.AbstractBuilder<ReceiveDataRules, ReceiveDataRules.Builder> {

        private String description;
        private List<QueryField> fields;
        private List<ReceiveDataRule> rules;

        private Builder() {
        }

        private Builder(final ReceiveDataRules receiveDataRules) {
            super(receiveDataRules);
            this.description = receiveDataRules.description;
            this.fields = receiveDataRules.fields;
            this.rules = receiveDataRules.rules;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder fields(final List<QueryField> fields) {
            this.fields = fields;
            return self();
        }

        public Builder addField(final QueryField field) {
            if (this.fields == null) {
                this.fields = new ArrayList<>();
            }
            this.fields.add(field);
            return self();
        }

        public Builder rules(final List<ReceiveDataRule> rules) {
            this.rules = rules;
            return self();
        }

        public Builder addRule(final ReceiveDataRule rule) {
            if (this.rules == null) {
                this.rules = new ArrayList<>();
            }
            this.rules.add(rule);
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ReceiveDataRules build() {
            return new ReceiveDataRules(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    fields,
                    rules);
        }
    }
}
