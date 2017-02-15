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

import java.io.Serializable;
import java.util.Arrays;

@JsonPropertyOrder({"key", "values", "nodes", "min", "max", "sum"})
public class Node implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    private Object[] key;
    private Object[] values;

    public Node() {
    }

    public Node(final Object[] key, final Object[] values, final Double[] min, final Double[] max, final Double[] sum) {
        this.key = key;
        this.values = values;
//        this.nodes = nodes;
//        this.min = min;
//        this.max = max;
//        this.sum = sum;
    }

    //    @XmlTransient
    @JsonProperty
    public Object[] getKey() {
        return key;
    }

    public void setKey(final Object[] key) {
        this.key = key;
    }
//
//    @JsonIgnore
//    @XmlElementWrapper(name = "key")
//    @XmlElement(name = "values")
//    public Values[] getXMLKey() {
//        if (this.key == null) {
//            return null;
//        }
//        final Values[] key = new Values[this.key.length];
//        for (int i = 0; i < this.key.length; i++) {
//            key[i] = new Values(this.key[i]);
//        }
//        return key;
//    }
//
//    public void setXMLKey(final Values[] key) {
//        if (key != null) {
//            this.key = new Object[key.length][];
//            for (int i = 0; i < key.length; i++) {
//                this.key[i] = key[i].getValues();
//            }
//        } else {
//            this.key = null;
//        }
//    }

    //    @XmlTransient
    @JsonProperty
    public Object[] getValues() {
        return values;
    }

    public void setValues(final Object[] values) {
        this.values = values;
    }

//    @JsonIgnore
//    @XmlElementWrapper(name = "values")
//    @XmlElement(name = "values")
//    public Values[] getXMLValues() {
//        if (this.values == null) {
//            return null;
//        }
//        final Values[] values = new Values[this.values.length];
//        for (int i = 0; i < this.values.length; i++) {
//            values[i] = new Values(this.values[i]);
//        }
//        return values;
//    }
//
//    public void setXMLValues(final Values[] values) {
//        if (values != null) {
//            this.values = new Object[values.length][];
//            for (int i = 0; i < values.length; i++) {
//                this.values[i] = values[i].getValues();
//            }
//        } else {
//            this.values = null;
//        }
//    }
//
//    @XmlElementWrapper(name = "nodes")
//    @XmlElement(name = "node")
//    public Node[] getNodes() {
//        return nodes;
//    }
//
//    public void setNodes(final Node[] nodes) {
//        this.nodes = nodes;
//    }
//
//    @XmlElementWrapper(name = "min")
//    @XmlElement(name = "double", nillable = true)
//    @JsonProperty("min")
//    public Double[] getMin() {
//        return min;
//    }
//
//    public void setMin(final Double[] min) {
//        this.min = min;
//    }
//
//    @XmlElementWrapper(name = "max")
//    @XmlElement(name = "double", nillable = true)
//    @JsonProperty("max")
//    public Double[] getMax() {
//        return max;
//    }
//
//    public void setMax(final Double[] max) {
//        this.max = max;
//    }
//
//    @XmlElementWrapper(name = "sum")
//    @XmlElement(name = "double", nillable = true)
//    @JsonProperty("sum")
//    public Double[] getSum() {
//        return sum;
//    }
//
//    public void setSum(final Double[] sum) {
//        this.sum = sum;
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Node node = (Node) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(key, node.key)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(values, node.values);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(key);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    public String toString() {
        if (key == null || key.length == 0) {
            return "";
        }

        final Object[] group = (Object[]) key[key.length - 1];
        if (group.length == 0) {
            return "";
        }

        if (group.length == 1) {
            return String.valueOf(group[0]);
        }
        final StringBuilder sb = new StringBuilder();
        for (final Object obj : group) {
            sb.append(String.valueOf(obj));
            sb.append("|");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}