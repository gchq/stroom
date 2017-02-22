/*
 * Copyright 2016 Crown Copyright
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

package stroom.datasource.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@JsonPropertyOrder({"fields"})
@XmlType(name = "DataSource", propOrder = "fields")
@XmlRootElement(name = "dataSource")
@XmlAccessorType(XmlAccessType.FIELD)
public final class DataSource implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    private List<DataSourceField> fields;

    private DataSource() {
    }

    public DataSource(final List<DataSourceField> fields) {
        this.fields = fields;
    }

    public List<DataSourceField> getFields() {
        return fields;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DataSource that = (DataSource) o;

        return fields != null ? fields.equals(that.fields) : that.fields == null;
    }

    @Override
    public int hashCode() {
        return fields != null ? fields.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "DataSource{" +
                "fields=" + fields +
                '}';
    }
}