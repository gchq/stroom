/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

@JsonPropertyOrder({"key", "limit", "nest", "values"})
@JsonInclude(Include.NON_NULL)
public class VisNest implements Serializable {

    @JsonProperty
    private VisField key;
    @JsonProperty
    private VisLimit limit;
    @JsonProperty
    private VisNest nest;
    @JsonProperty
    private VisValues values;

    public VisNest() {
    }

    public VisNest(final VisField key) {
        this.key = key;
    }

    @JsonCreator
    public VisNest(@JsonProperty("key") final VisField key,
                   @JsonProperty("limit") final VisLimit limit,
                   @JsonProperty("nest") final VisNest nest,
                   @JsonProperty("values") final VisValues values) {
        this.key = key;
        this.limit = limit;
        this.nest = nest;
        this.values = values;
    }

    public VisField getKey() {
        return key;
    }

    public void setKey(final VisField key) {
        this.key = key;
    }

    public VisLimit getLimit() {
        return limit;
    }

    public void setLimit(final VisLimit limit) {
        this.limit = limit;
    }

    public VisNest getNest() {
        return nest;
    }

    public void setNest(final VisNest nest) {
        this.nest = nest;
    }

    public VisValues getValues() {
        return values;
    }

    public void setValues(final VisValues values) {
        this.values = values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VisNest)) {
            return false;
        }

        final VisNest visNest = (VisNest) o;

        if (key != null
                ? !key.equals(visNest.key)
                : visNest.key != null) {
            return false;
        }
        if (limit != null
                ? !limit.equals(visNest.limit)
                : visNest.limit != null) {
            return false;
        }
        if (nest != null
                ? !nest.equals(visNest.nest)
                : visNest.nest != null) {
            return false;
        }
        return values != null
                ? values.equals(visNest.values)
                : visNest.values == null;
    }

    @Override
    public int hashCode() {
        int result = key != null
                ? key.hashCode()
                : 0;
        result = 31 * result + (limit != null
                ? limit.hashCode()
                : 0);
        result = 31 * result + (nest != null
                ? nest.hashCode()
                : 0);
        result = 31 * result + (values != null
                ? values.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VisNest{" +
               "key=" + key +
               ", limit=" + limit +
               ", nest=" + nest +
               ", values=" + values +
               '}';
    }
}
