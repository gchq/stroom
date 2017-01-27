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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"fields"})
@XmlType(name = "DataSource", propOrder = "fields")
@XmlRootElement(name = "dataSource")
public class DataSource implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private DataSourceField[] fields;

    public DataSource() {
    }

    public DataSource(final DataSourceField[] fields) {
        this.fields = fields;
    }

    @XmlElementWrapper(name = "fields")
    @XmlElement(name = "field")
    public DataSourceField[] getFields() {
        return fields;
    }

    public void setFields(final DataSourceField[] fields) {
        this.fields = fields;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DataSource that = (DataSource) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(fields);
    }

    @Override
    public String toString() {
        return "DataSource{" +
                "fields=" + Arrays.toString(fields) +
                '}';
    }
}