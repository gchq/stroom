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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"key", "nodes", "values", "min", "max", "sum"})
@XmlType(name = "Node", propOrder = {"key", "nodes", "XMLValues", "min", "max", "sum"})
public class Node implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private Key key;
    private Node[] nodes;
    private Object[][] values;
    private Double[] min;
    private Double[] max;
    private Double[] sum;

    public Node() {
    }

    public Node(final Key key, final Node[] nodes, final Object[][] values, final Double[] min, final Double[] max, final Double[] sum) {
        this.key = key;
        this.nodes = nodes;
        this.values = values;
        this.min = min;
        this.max = max;
        this.sum = sum;
    }

    @XmlElement
    public Key getKey() {
        return key;
    }

    public void setKey(final Key key) {
        this.key = key;
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
    @JsonProperty("min")
    public Double[] getMin() {
        return min;
    }

    public void setMin(final Double[] min) {
        this.min = min;
    }

    @XmlElementWrapper(name = "max")
    @XmlElement(name = "val")
    @JsonProperty("max")
    public Double[] getMax() {
        return max;
    }

    public void setMax(final Double[] max) {
        this.max = max;
    }

    @XmlElementWrapper(name = "sum")
    @XmlElement(name = "val")
    @JsonProperty("sum")
    public Double[] getSum() {
        return sum;
    }

    public void setSum(final Double[] sum) {
        this.sum = sum;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Node node = (Node) o;

        if (key != null ? !key.equals(node.key) : node.key != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(nodes, node.nodes)) return false;
        if (!Arrays.deepEquals(values, node.values)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(min, node.min)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(max, node.max)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(sum, node.sum);
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(nodes);
        result = 31 * result + Arrays.deepHashCode(values);
        result = 31 * result + Arrays.hashCode(min);
        result = 31 * result + Arrays.hashCode(max);
        result = 31 * result + Arrays.hashCode(sum);
        return result;
    }

    @Override
    public String toString() {
        return "Node{" +
                "key=" + key +
                ", nodes=" + Arrays.toString(nodes) +
                ", values=" + Arrays.toString(values) +
                ", min=" + Arrays.toString(min) +
                ", max=" + Arrays.toString(max) +
                ", sum=" + Arrays.toString(sum) +
                '}';
    }
}