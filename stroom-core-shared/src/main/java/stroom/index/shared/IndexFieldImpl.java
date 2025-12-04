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

package stroom.index.shared;

import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class IndexFieldImpl implements IndexField {

    @JsonProperty
    private final String fldName;
    @JsonProperty
    private final FieldType fldType;
    @JsonProperty
    private final AnalyzerType analyzerType;
    @JsonProperty
    private final boolean indexed;
    @JsonProperty
    private final boolean stored;
    @JsonProperty
    private final boolean termPositions;
    @JsonProperty
    private final boolean caseSensitive;

    @JsonCreator
    public IndexFieldImpl(@JsonProperty("fldName") final String fldName,
                          @JsonProperty("fldType") final FieldType fldType,
                          @JsonProperty("analyzerType") final AnalyzerType analyzerType,
                          @JsonProperty("indexed") final boolean indexed,
                          @JsonProperty("stored") final boolean stored,
                          @JsonProperty("termPositions") final boolean termPositions,
                          @JsonProperty("caseSensitive") final boolean caseSensitive) {
        this.fldName = fldName;
        this.fldType = fldType;
        this.analyzerType = analyzerType;
        this.stored = stored;
        this.indexed = indexed;
        this.termPositions = termPositions;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String getFldName() {
        return fldName;
    }

    @Override
    public FieldType getFldType() {
        return fldType;
    }

    @Override
    public AnalyzerType getAnalyzerType() {
        if (analyzerType == null) {
            return AnalyzerType.KEYWORD;
        }
        return analyzerType;
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @Override
    public boolean isStored() {
        return stored;
    }

    @Override
    public boolean isIndexed() {
        return indexed;
    }

    @Override
    public boolean isTermPositions() {
        return termPositions;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return fldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IndexFieldImpl that = (IndexFieldImpl) o;
        return indexed == that.indexed &&
               stored == that.stored &&
               termPositions == that.termPositions &&
               caseSensitive == that.caseSensitive &&
               Objects.equals(fldName, that.fldName) &&
               fldType == that.fldType &&
               analyzerType == that.analyzerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fldName, fldType, analyzerType, indexed, stored, termPositions, caseSensitive);
    }

    @Override
    public String toString() {
        return fldName;
    }

    @Override
    public int compareTo(final Field o) {
        return fldName.compareToIgnoreCase(o.getFldName());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String fldName;
        private FieldType fldType = FieldType.TEXT;
        private AnalyzerType analyzerType = AnalyzerType.KEYWORD;
        private boolean indexed = true;
        private boolean stored;
        private boolean termPositions;
        private boolean caseSensitive;

        private Builder() {
        }

        public Builder(final IndexField indexField) {
            this.fldName = indexField.getFldName();
            this.fldType = indexField.getFldType();
            this.analyzerType = indexField.getAnalyzerType();
            this.indexed = indexField.isIndexed();
            this.stored = indexField.isStored();
            this.termPositions = indexField.isTermPositions();
            this.caseSensitive = indexField.isCaseSensitive();
        }

        public Builder fldName(final String fldName) {
            this.fldName = fldName;
            return this;
        }

        public Builder fldType(final FieldType fldType) {
            this.fldType = fldType;
            return this;
        }

        public Builder analyzerType(final AnalyzerType analyzerType) {
            this.analyzerType = analyzerType;
            return this;
        }

        public Builder indexed(final boolean indexed) {
            this.indexed = indexed;
            return this;
        }

        public Builder stored(final boolean stored) {
            this.stored = stored;
            return this;
        }

        public Builder termPositions(final boolean termPositions) {
            this.termPositions = termPositions;
            return this;
        }

        public Builder caseSensitive(final boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public IndexFieldImpl build() {
            return new IndexFieldImpl(
                    fldName,
                    fldType,
                    analyzerType,
                    indexed,
                    stored,
                    termPositions,
                    caseSensitive);
        }
    }
}
