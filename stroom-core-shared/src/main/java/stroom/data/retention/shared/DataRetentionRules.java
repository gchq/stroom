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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "rules"})
@JsonInclude(Include.NON_DEFAULT)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataRetentionPolicy", propOrder = {"rules"})
@XmlRootElement(name = "dataRetentionPolicy")
public class DataRetentionRules extends Doc {
    public static final String DOCUMENT_TYPE = "DataRetentionRules";

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
                              @JsonProperty("createTime") final Long createTime,
                              @JsonProperty("updateTime") final Long updateTime,
                              @JsonProperty("createUser") final String createUser,
                              @JsonProperty("updateUser") final String updateUser,
                              @JsonProperty("rules") final List<DataRetentionRule> rules) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.rules = rules;
    }

    public List<DataRetentionRule> getRules() {
        return rules;
    }

    public void setRules(final List<DataRetentionRule> rules) {
        this.rules = rules;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final DataRetentionRules that = (DataRetentionRules) o;
        return Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rules);
    }
}

