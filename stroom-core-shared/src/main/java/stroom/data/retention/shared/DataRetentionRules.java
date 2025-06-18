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
 */

package stroom.data.retention.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
        "rules"})
@JsonInclude(Include.NON_NULL)
public class DataRetentionRules extends Doc {

    public static final String TYPE = "DataRetentionRules";
    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            TYPE,
            "Data Retention Rules",
            SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET);

    @JsonProperty
    private List<DataRetentionRule> rules;

    public DataRetentionRules() {
    }

    public DataRetentionRules(final List<DataRetentionRule> rules) {
        this.rules = rules;
    }

    @JsonCreator
    public DataRetentionRules(@JsonProperty("type") final String type,
                              @JsonProperty("uuid") final String uuid,
                              @JsonProperty("name") final String name,
                              @JsonProperty("version") final String version,
                              @JsonProperty("createTimeMs") final Long createTimeMs,
                              @JsonProperty("updateTimeMs") final Long updateTimeMs,
                              @JsonProperty("createUser") final String createUser,
                              @JsonProperty("updateUser") final String updateUser,
                              @JsonProperty("rules") final List<DataRetentionRule> rules) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
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
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public List<DataRetentionRule> getRules() {
        return rules;
    }

    @JsonIgnore
    public List<DataRetentionRule> getActiveRules() {
        return NullSafe.stream(rules)
                .filter(DataRetentionRule::isEnabled)
                .collect(Collectors.toList());
    }

    public void setRules(final List<DataRetentionRule> rules) {
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
        final DataRetentionRules that = (DataRetentionRules) o;
        return Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rules);
    }
}

