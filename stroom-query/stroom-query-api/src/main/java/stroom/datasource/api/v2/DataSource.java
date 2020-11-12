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

package stroom.datasource.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class DataSource implements Serializable {
    private static final long serialVersionUID = 1272545271946712570L;

    @JsonProperty
    private List<AbstractField> fields;

    @JsonCreator
    public DataSource(@JsonProperty("fields") final List<AbstractField> fields) {
        this.fields = fields;
    }

    public List<AbstractField> getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataSource that = (DataSource) o;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    public String toString() {
        return "DataSource{" +
                "fields=" + fields +
                '}';
    }

    public static class Builder {

        private final List<AbstractField> fields = new ArrayList<>();

        public Builder addFields(final AbstractField... values) {
            this.fields.addAll(Arrays.asList(values));
            return this;
        }

        public DataSource build() {
            return new DataSource(fields);
        }
    }
}