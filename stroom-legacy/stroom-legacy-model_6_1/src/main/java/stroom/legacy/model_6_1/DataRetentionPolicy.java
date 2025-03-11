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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import java.util.List;
import java.util.Objects;

@Deprecated
@XmlType(name = "DataRetentionPolicy", propOrder = {"rules"})
@XmlRootElement(name = "dataRetentionPolicy")
public class DataRetentionPolicy implements SharedObject {

    @XmlElement(name = "rule")
    private List<DataRetentionRule> rules;
    @XmlTransient
    private int version;

    public DataRetentionPolicy() {
        // Default constructor for GWT serialisation.
    }

    public DataRetentionPolicy(final List<DataRetentionRule> rules) {
        this.rules = rules;
    }

    public List<DataRetentionRule> getRules() {
        return rules;
    }

    public void setRules(final List<DataRetentionRule> rules) {
        this.rules = rules;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(final int version) {
        this.version = version;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DataRetentionPolicy that = (DataRetentionPolicy) o;

        return Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return rules != null
                ? rules.hashCode()
                : 0;
    }
}
