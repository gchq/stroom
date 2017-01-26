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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Arrays;

@JsonPropertyOrder({"componentId", "types", "nodes", "values", "min", "max", "sum", "size", "error"})
@XmlType(name = "VisResult", propOrder = {"types", "nodes", "XMLValues", "min", "max", "sum", "size", "error"})
public class VisResult extends Result {
    private static final long serialVersionUID = 3826654996795750099L;

    private String[] types;
    private Node[] nodes;
    private Object[][] values;
    private Double[] min;
    private Double[] max;
    private Double[] sum;
    private long size;
    private String error;

    public VisResult() {
    }

    public VisResult(final String error) {
        this.size = 0;
        this.error = error;
    }

    public VisResult(final String componentId, final String[] types, final Node[] nodes, final Object[][] values, final Double[] min, final Double[] max, final Double[] sum, final long size, final String error) {
        super(componentId);
        this.types = types;
        this.nodes = nodes;
        this.values = values;
        this.min = min;
        this.max = max;
        this.sum = sum;
        this.size = size;
        this.error = error;
    }

    @XmlElementWrapper(name = "types")
    @XmlElement(name = "type")
    public String[] getTypes() {
        return types;
    }

    public void setTypes(final String[] types) {
        this.types = types;
    }

    @XmlElementWrapper(name = "nodes")
    @XmlElement(name = "node")
    public Node[] getNodes() {
        return nodes;
    }

    public void setNodes(final Node[] nodes) {
        this.nodes = nodes;
    }

    @XmlTransient
    public Object[][] getValues() {
        return values;
    }

    public void setValues(final Object[][] values) {
        this.values = values;
    }

    @JsonIgnore
    @XmlElementWrapper(name = "values")
    @XmlElement(name = "values")
    public Values[] getXMLValues() {
        if (this.values == null) {
            return null;
        }
        final Values[] values = new Values[this.values.length];
        for (int i = 0; i < this.values.length; i++) {
            values[i] = new Values(this.values[i]);
        }
        return values;
    }

    public void setXMLValues(final Values[] values) {
        if (values != null) {
            this.values = new Object[values.length][];
            for (int i = 0; i < values.length; i++) {
                this.values[i] = values[i].getValues();
            }
        } else {
            this.values = null;
        }
    }

    @XmlElementWrapper(name = "min")
    @XmlElement(name = "val")
    public Double[] getMin() {
        return min;
    }

    public void setMin(final Double[] min) {
        this.min = min;
    }

    @XmlElementWrapper(name = "max")
    @XmlElement(name = "val")
    public Double[] getMax() {
        return max;
    }

    public void setMax(final Double[] max) {
        this.max = max;
    }

    @XmlElementWrapper(name = "sum")
    @XmlElement(name = "val")
    public Double[] getSum() {
        return sum;
    }

    public void setSum(final Double[] sum) {
        this.sum = sum;
    }

    @XmlElement
    public long getSize() {
        return size;
    }

    public void setSize(final long size) {
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
        if (!(o instanceof VisResult)) return false;

        final VisResult visResult = (VisResult) o;

        if (size != visResult.size) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(types, visResult.types)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(nodes, visResult.nodes)) return false;
        if (!Arrays.deepEquals(values, visResult.values)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(min, visResult.min)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(max, visResult.max)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(sum, visResult.sum)) return false;
        return error != null ? error.equals(visResult.error) : visResult.error == null;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(types);
        result = 31 * result + Arrays.hashCode(nodes);
        result = 31 * result + Arrays.deepHashCode(values);
        result = 31 * result + Arrays.hashCode(min);
        result = 31 * result + Arrays.hashCode(max);
        result = 31 * result + Arrays.hashCode(sum);
        result = 31 * result + (int) (size ^ (size >>> 32));
        result = 31 * result + (error != null ? error.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "data points = " + size;
    }
}