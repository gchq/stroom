/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.receive.rules.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docref.DocRef.TypedBuilder;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
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
public class ReceiveDataRules extends Doc {

    public static final String TYPE = "ReceiveDataRuleSet";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.RECEIVE_DATA_RULESET_DOCUMENT_TYPE;

//    /**
//     * Conditions that support obfuscation/hashing of the values in rule expression terms.
//     */
//    public static final Set<Condition> OBFUSCATABLE_CONDITIONS = EnumSet.of(
//            Condition.EQUALS,
//            Condition.EQUALS_CASE_SENSITIVE,
//            Condition.NOT_EQUALS,
//            Condition.IN,
//            Condition.BETWEEN,
//            Condition.IN_DICTIONARY
//    );

    /**
     * All conditions supported in receipt policy rules.
     */
//    public static final Set<Condition> ALL_SUPPORTED_CONDITIONS = EnumSet.copyOf(OBFUSCATABLE_CONDITIONS);

//    static {
////        ALL_SUPPORTED_CONDITIONS.addAll(EnumSet.noneOf(
//
//
//        ));
//    }

    @JsonProperty
    private String description;
    @JsonProperty
    private List<QueryField> fields;
    @JsonProperty
    private List<ReceiveDataRule> rules;

    public ReceiveDataRules() {
    }

    @JsonCreator
    public ReceiveDataRules(@JsonProperty("type") final String type,
                            @JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("description") final String description,
                            @JsonProperty("fields") final List<QueryField> fields,
                            @JsonProperty("rules") final List<ReceiveDataRule> rules) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.fields = fields;
        this.rules = rules;
    }

    private ReceiveDataRules(final Builder builder) {
        setType(builder.type);
        setUuid(builder.uuid);
        setName(builder.name);
        setVersion(builder.version);
        setCreateTimeMs(builder.createTimeMs);
        setUpdateTimeMs(builder.updateTimeMs);
        setCreateUser(builder.createUser);
        setUpdateUser(builder.updateUser);
        setDescription(builder.description);
        setFields(builder.fields);
        setRules(builder.rules);
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

    public static Builder builder() {
        return new Builder();
    }

    public static Builder copy(final ReceiveDataRules source) {
        Builder builder = new Builder();
        builder.type = source.getType();
        builder.uuid = source.getUuid();
        builder.name = source.getName();
        builder.version = source.getVersion();
        builder.createTimeMs = source.getCreateTimeMs();
        builder.updateTimeMs = source.getUpdateTimeMs();
        builder.createUser = source.getCreateUser();
        builder.updateUser = source.getUpdateUser();
        builder.description = source.getDescription();
        builder.fields = source.getFields();
        builder.rules = source.getRules();
        return builder;
    }

    @Override
    public String toString() {
        return "ReceiveDataRules{" +
               "description='" + description + '\'' +
               ", fields=" + fields +
               ", rules=" + rules +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String type;
        private String uuid;
        private String name;
        private String version;
        private Long createTimeMs;
        private Long updateTimeMs;
        private String createUser;
        private String updateUser;
        private String description;
        private List<QueryField> fields;
        private List<ReceiveDataRule> rules;

        private Builder() {
        }

        public Builder withType(final String type) {
            this.type = type;
            return this;
        }

        public Builder withUuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder withName(final String name) {
            this.name = name;
            return this;
        }

        public Builder withVersion(final String version) {
            this.version = version;
            return this;
        }

        public Builder withCreateTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return this;
        }

        public Builder withUpdateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return this;
        }

        public Builder withCreateUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        public Builder withUpdateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public Builder withDescription(final String description) {
            this.description = description;
            return this;
        }

        public Builder withFields(final List<QueryField> fields) {
            this.fields = fields;
            return this;
        }

        public Builder addField(final QueryField field) {
            if (this.fields == null) {
                this.fields = new ArrayList<>();
            }
            this.fields.add(field);
            return this;
        }

        public Builder withRules(final List<ReceiveDataRule> rules) {
            this.rules = rules;
            return this;
        }

        public Builder addRule(final ReceiveDataRule rule) {
            if (this.rules == null) {
                this.rules = new ArrayList<>();
            }
            this.rules.add(rule);
            return this;
        }

        public ReceiveDataRules build() {
            return new ReceiveDataRules(this);
        }
    }
}
