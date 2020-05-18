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
import java.util.Objects;

@JsonPropertyOrder({"includes", "excludes"})
@XmlType(name = "Filter", propOrder = {"includes", "excludes"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A pair of regular expression filters (inclusion and exclusion) to apply to the field.  Either or " +
        "both can be supplied")
public final class Filter implements Serializable {
    private static final long serialVersionUID = 7327802315955158337L;

    @XmlElement
    @ApiModelProperty(
            value = "Only results matching this filter will be included",
            example = "^[0-9]{3}$",
            required = false)
    private String includes;

    @XmlElement
    @ApiModelProperty(
            value = "Only results NOT matching this filter will be included",
            example = "^[0-9]{3}$",
            required = false)
    private String excludes;

    public Filter() {
    }

    public Filter(final String includes, final String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Filter filter = (Filter) o;
        return Objects.equals(includes, filter.includes) &&
                Objects.equals(excludes, filter.excludes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includes, excludes);
    }

    @Override
    public String toString() {
        return "Filter{" +
                "includes='" + includes + '\'' +
                ", excludes='" + excludes + '\'' +
                '}';
    }

    /**
     * Builder for constructing a {@link Filter}
     */
    public static class Builder {
        private String includes;
        private String excludes;

        public Builder() {
        }

        public Builder(final Filter filter) {
            this.includes = filter.includes;
            this.excludes = filter.excludes;
        }

        /**
         * Set the inclusion regex
         * @param value Only results matching this filter will be included
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder includes(final String value) {
            this.includes = value;
            return this;
        }

        /**
         * Set the exclusion regex
         * @param value Only results NOT matching this filter will be included
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder excludes(final String value) {
            this.excludes = value;
            return this;
        }

        public Filter build() {
            return new Filter(includes, excludes);
        }
    }
}