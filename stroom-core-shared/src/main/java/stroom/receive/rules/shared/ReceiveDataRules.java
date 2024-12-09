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
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
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

    public static final String DOCUMENT_TYPE = "ReceiveDataRuleSet";
    public static final SvgImage ICON = SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;

    @JsonProperty
    private String description;
    @JsonProperty
    private List<QueryField> fields;
    @JsonProperty
    private List<ReceiveDataRule> rules;

    public ReceiveDataRules() {
    }

    @JsonCreator
    public ReceiveDataRules(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("uniqueName") final String uniqueName,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("description") final String description,
                            @JsonProperty("fields") final List<QueryField> fields,
                            @JsonProperty("rules") final List<ReceiveDataRule> rules) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.fields = fields;
        this.rules = rules;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
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
}
