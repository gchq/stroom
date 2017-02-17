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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import java.util.Arrays;

@JsonPropertyOrder({"componentId", "structure", "values", "size", "error"})
public class VisResult extends Result {
    private static final long serialVersionUID = 3826654996795750099L;

    private Field[] structure;
    private Object[][] values;
    private Long size;
    private String error;

    public VisResult() {
    }

    public VisResult(final String error) {
        this.size = 0L;
        this.error = error;
    }

    public VisResult(final String componentId, final Field[] structure, final Object[][] values, final Long size, final String error) {
        super(componentId);
        this.structure = structure;
        this.values = values;
        this.size = size;
        this.error = error;
    }

    @JsonProperty
    public Field[] getStructure() {
        return structure;
    }

    public void setStructure(final Field[] structure) {
        this.structure = structure;
    }

    @JsonProperty
    public Object[][] getValues() {
        return values;
    }

    public void setValues(final Object[][] values) {
        this.values = values;
    }

    @XmlElement
    public Long getSize() {
        return size;
    }

    public void setSize(final Long size) {
        this.size = size;
    }

    @XmlElement
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

        final VisResult visResult = (VisResult) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(structure, visResult.structure)) return false;
        if (!Arrays.deepEquals(values, visResult.values)) return false;
        if (size != null ? !size.equals(visResult.size) : visResult.size != null) return false;
        return error != null ? error.equals(visResult.error) : visResult.error == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(structure);
        result = 31 * result + Arrays.deepHashCode(values);
        result = 31 * result + (size != null ? size.hashCode() : 0);
        result = 31 * result + (error != null ? error.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return size + " rows";
    }
}