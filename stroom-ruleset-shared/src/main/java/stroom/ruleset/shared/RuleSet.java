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

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.datasource.api.v2.DataSourceField;
import stroom.docstore.shared.Document;
import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "fields", "rules"})
@XmlRootElement(name = "dataReceiptPolicy")
@XmlType(name = "DataReceiptPolicy", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "fields", "rules"})
public class RuleSet extends Document implements SharedObject {
    private static final long serialVersionUID = -7268301402378907741L;

    public static final String DOCUMENT_TYPE = "ruleset";

    @XmlElement(name = "fields")
    private List<DataSourceField> fields;
    @XmlElement(name = "rule")
    private List<Rule> rules;

    public RuleSet() {
        // Default constructor for GWT serialisation.
    }

    public List<DataSourceField> getFields() {
        return fields;
    }

    public void setFields(final List<DataSourceField> fields) {
        this.fields = fields;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(final List<Rule> rules) {
        this.rules = rules;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RuleSet that = (RuleSet) o;

        if (fields != null ? !fields.equals(that.fields) : that.fields != null) return false;
        return rules != null ? rules.equals(that.rules) : that.rules == null;
    }

    @Override
    public int hashCode() {
        int result = fields != null ? fields.hashCode() : 0;
        result = 31 * result + (rules != null ? rules.hashCode() : 0);
        return result;
    }
}
