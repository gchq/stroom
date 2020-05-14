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

package stroom.legacy.model_6_1;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonPropertyOrder({"key", "value"})
@XmlType(name = "Param", propOrder = {"key", "value"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A key value pair that describes a property of a query")
public final class Param implements Serializable {

    private static final long serialVersionUID = 9055582579670841979L;

    @XmlElement
    @ApiModelProperty(
            value = "The property key",
            required = true)
    private String key;

    @XmlElement
    @ApiModelProperty(
            value = "The property value",
            required = true)
    private String value;

    private Param() {
    }

    public Param(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Param param = (Param) o;

        if (key != null ? !key.equals(param.key) : param.key != null) return false;
        return value != null ? value.equals(param.value) : param.value == null;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Param{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    /**
     * Builder for constructing a {@link Param}
     */
    public static class Builder {
        private String key;
        private String value;

        /**
         * @param value The property key
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder key(final String value) {
            this.key = value;
            return this;
        }

        /**
         * @param value The property value
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder value(final String value) {
            this.value = value;
            return this;
        }

        public Param build() {
            return new Param(key, value);
        }
    }
}