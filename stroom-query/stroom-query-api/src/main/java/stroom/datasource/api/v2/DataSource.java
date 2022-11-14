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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class DataSource implements Serializable {

    private static final long serialVersionUID = 1272545271946712570L;

    @JsonProperty
    private final List<AbstractField> fields;
    @JsonProperty
    private final DocRef defaultExtractionPipeline;

    @JsonCreator
    public DataSource(@JsonProperty("fields") final List<AbstractField> fields,
                      @JsonProperty("defaultExtractionPipeline") final DocRef defaultExtractionPipeline) {
        this.fields = fields;
        this.defaultExtractionPipeline = defaultExtractionPipeline;
    }

    public List<AbstractField> getFields() {
        return fields;
    }

    public DocRef getDefaultExtractionPipeline() {
        return defaultExtractionPipeline;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataSource that = (DataSource) o;
        return Objects.equals(fields, that.fields) && Objects.equals(defaultExtractionPipeline,
                that.defaultExtractionPipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, defaultExtractionPipeline);
    }

    @Override
    public String toString() {
        return "DataSource{" +
                "fields=" + fields +
                ", defaultExtractionPipeline=" + defaultExtractionPipeline +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private List<AbstractField> fields = new ArrayList<>();
        private DocRef defaultExtractionPipeline;

        private Builder() {
        }

        private Builder(final DataSource dataSource) {
            fields = dataSource.fields;
        }

        public Builder fields(final List<AbstractField> fields) {
            this.fields = fields;
            return this;
        }

        public Builder defaultExtractionPipeline(final DocRef defaultExtractionPipeline) {
            this.defaultExtractionPipeline = defaultExtractionPipeline;
            return this;
        }

        public DataSource build() {
            return new DataSource(fields, defaultExtractionPipeline);
        }
    }
}
