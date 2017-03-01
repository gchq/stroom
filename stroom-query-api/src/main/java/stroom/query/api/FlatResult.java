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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

@JsonPropertyOrder({"componentId", "structure", "values", "size", "error"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class FlatResult extends Result {
    private static final long serialVersionUID = 3826654996795750099L;

    @XmlElement
    private List<Field> structure;
    @XmlElement
    private List<List<Object>> values;
    @XmlElement
    private Long size;
    @XmlElement
    private String error;

    public FlatResult() {
    }

    public FlatResult(final String error) {
        this.size = 0L;
        this.error = error;
    }

    public FlatResult(final String componentId, final List<Field> structure, final List<List<Object>> values, final Long size, final String error) {
        super(componentId);
        this.structure = structure;
        this.values = values;
        this.size = size;
        this.error = error;
    }

    public List<Field> getStructure() {
        return structure;
    }

    public void setStructure(final List<Field> structure) {
        this.structure = structure;
    }

    public List<List<Object>> getValues() {
        return values;
    }

    public void setValues(final List<List<Object>> values) {
        this.values = values;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(final Long size) {
        this.size = size;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final FlatResult that = (FlatResult) o;

        if (structure != null ? !structure.equals(that.structure) : that.structure != null) return false;
        if (values != null ? !values.equals(that.values) : that.values != null) return false;
        if (size != null ? !size.equals(that.size) : that.size != null) return false;
        return error != null ? error.equals(that.error) : that.error == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (structure != null ? structure.hashCode() : 0);
        result = 31 * result + (values != null ? values.hashCode() : 0);
        result = 31 * result + (size != null ? size.hashCode() : 0);
        result = 31 * result + (error != null ? error.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return size + " rows";
    }
}