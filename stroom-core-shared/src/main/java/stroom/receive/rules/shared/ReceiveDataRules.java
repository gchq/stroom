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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.datasource.api.v2.AbstractField;
import stroom.docstore.shared.Doc;

import java.util.List;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "fields", "rules"})
@JsonInclude(Include.NON_DEFAULT)
public class ReceiveDataRules extends Doc {
    public static final String DOCUMENT_TYPE = "ReceiveDataRuleSet";

    @JsonProperty
    private List<AbstractField> fields;
    @JsonProperty
    private List<ReceiveDataRule> rules;

    public ReceiveDataRules() {
        // Default constructor for GWT serialisation.
    }

    @JsonCreator
    public ReceiveDataRules(@JsonProperty("type") final String type,
                            @JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTime") final Long createTime,
                            @JsonProperty("updateTime") final Long updateTime,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("fields") final List<AbstractField> fields,
                            @JsonProperty("rules") final List<ReceiveDataRule> rules) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.fields = fields;
        this.rules = rules;
    }

    public List<AbstractField> getFields() {
        return fields;
    }

    public void setFields(final List<AbstractField> fields) {
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final ReceiveDataRules ruleSet = (ReceiveDataRules) o;

        if (fields != null ? !fields.equals(ruleSet.fields) : ruleSet.fields != null) return false;
        return rules != null ? rules.equals(ruleSet.rules) : ruleSet.rules == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (fields != null ? fields.hashCode() : 0);
        result = 31 * result + (rules != null ? rules.hashCode() : 0);
        return result;
    }
}
