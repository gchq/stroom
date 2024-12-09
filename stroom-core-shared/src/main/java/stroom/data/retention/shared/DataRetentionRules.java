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
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "rules"})
@JsonInclude(Include.NON_NULL)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataRetentionPolicy", propOrder = {"rules"})
@XmlRootElement(name = "dataRetentionPolicy")
public class DataRetentionRules extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "DataRetentionRules";
    // Seems to use same icon as receive rules
    public static final SvgImage ICON = SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;

    @JsonProperty
    private List<DataRetentionRule> rules;

    public DataRetentionRules() {
    }

    public DataRetentionRules(final List<DataRetentionRule> rules) {
        this.rules = rules;
    }

    @JsonCreator
    public DataRetentionRules(@JsonProperty("uuid") final String uuid,
                              @JsonProperty("name") final String name,
                              @JsonProperty("uniqueName") final String uniqueName,
                              @JsonProperty("version") final String version,
                              @JsonProperty("createTimeMs") final Long createTimeMs,
                              @JsonProperty("updateTimeMs") final Long updateTimeMs,
                              @JsonProperty("createUser") final String createUser,
                              @JsonProperty("updateUser") final String updateUser,
                              @JsonProperty("rules") final List<DataRetentionRule> rules) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.rules = rules;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
    }

    public List<DataRetentionRule> getRules() {
        return rules;
    }

    @JsonIgnore
    public List<DataRetentionRule> getActiveRules() {
        if (rules == null) {
            return Collections.emptyList();
        } else {
            return rules.stream()
                    .filter(DataRetentionRule::isEnabled)
                    .collect(Collectors.toList());
        }
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

