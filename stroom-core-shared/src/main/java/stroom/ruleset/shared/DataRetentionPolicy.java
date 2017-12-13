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

package stroom.ruleset.shared;

import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
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

    public int getVersion() {
        return version;
    }

    public void setVersion(final int version) {
        this.version = version;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DataRetentionPolicy that = (DataRetentionPolicy) o;

        return rules != null ? rules.equals(that.rules) : that.rules == null;
    }

    @Override
    public int hashCode() {
        return rules != null ? rules.hashCode() : 0;
    }
}
