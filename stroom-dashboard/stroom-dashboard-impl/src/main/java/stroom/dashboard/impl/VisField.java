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

import stroom.query.api.Sort;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

@JsonPropertyOrder({"id", "sort"})
@JsonInclude(Include.NON_NULL)
public class VisField implements Serializable {

    private static final long serialVersionUID = 1272545271946712570L;

    @JsonProperty
    private String id;
    @JsonProperty
    private Sort sort;

    public VisField() {
    }

    public VisField(final String id) {
        this(id, null);
    }

    @JsonCreator
    public VisField(@JsonProperty("id") final String id,
                    @JsonProperty("sort") final Sort sort) {
        this.id = id;
        this.sort = sort;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Sort getSort() {
        return sort;
    }

    public void setSort(final Sort sort) {
        this.sort = sort;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VisField)) {
            return false;
        }

        final VisField visField = (VisField) o;

        if (id != null
                ? !id.equals(visField.id)
                : visField.id != null) {
            return false;
        }
        return sort != null
                ? sort.equals(visField.sort)
                : visField.sort == null;
    }

    @Override
    public int hashCode() {
        int result = id != null
                ? id.hashCode()
                : 0;
        result = 31 * result + (sort != null
                ? sort.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VisField{" +
               "id='" + id + '\'' +
               ", sort=" + sort +
               '}';
    }
}
