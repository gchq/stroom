/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@JsonPropertyOrder({"groupKey", "values", "depth"})
@XmlType(name = "Row", propOrder = {"groupKey", "values", "depth"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class Row implements Serializable {
    private static final long serialVersionUID = 4379892306375080112L;

    @XmlElement
    private String groupKey;
    @XmlElementWrapper(name = "values")
    @XmlElement(name = "value")
    private List<String> values;
    @XmlElement
    private Integer depth;

    private Row() {
    }

    public Row(final String groupKey, final List<String> values, final Integer depth) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
    }


    public String getGroupKey() {
        return groupKey;
    }

    public List<String> getValues() {
        return values;
    }

    public Integer getDepth() {
        return depth;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Row row = (Row) o;

        if (groupKey != null ? !groupKey.equals(row.groupKey) : row.groupKey != null) return false;
        if (values != null ? !values.equals(row.values) : row.values != null) return false;
        return depth != null ? depth.equals(row.depth) : row.depth == null;
    }

    @Override
    public int hashCode() {
        int result = groupKey != null ? groupKey.hashCode() : 0;
        result = 31 * result + (values != null ? values.hashCode() : 0);
        result = 31 * result + (depth != null ? depth.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Row{" +
                "groupKey='" + groupKey + '\'' +
                ", values=" + values +
                ", depth=" + depth +
                '}';
    }
}