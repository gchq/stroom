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

package stroom.index.shared;

import stroom.datasource.api.v2.Field;
import stroom.datasource.api.v2.FieldType;

import java.util.Objects;

public class IndexFieldImpl implements IndexField {

    private final String name;
    private final FieldType type;
    private final AnalyzerType analyzerType;
    private final boolean indexed;
    private final boolean stored;
    private final boolean termPositions;
    private final boolean caseSensitive;

    public IndexFieldImpl(final String name,
                          final FieldType type,
                          final AnalyzerType analyzerType,
                          final boolean indexed,
                          final boolean stored,
                          final boolean termPositions,
                          final boolean caseSensitive) {
        this.name = name;
        this.type = type;
        this.analyzerType = analyzerType;
        this.stored = stored;
        this.indexed = indexed;
        this.termPositions = termPositions;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FieldType getType() {
        return type;
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

    @Override
    public String getDisplayValue() {
        return name;
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
                Objects.equals(name, that.name) &&
                type == that.type &&
                analyzerType == that.analyzerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, analyzerType, indexed, stored, termPositions, caseSensitive);
    }

    @Override
    public int compareTo(final Field o) {
        return name.compareToIgnoreCase(o.getName());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String name;
        private FieldType type = FieldType.TEXT;
        private AnalyzerType analyzerType = AnalyzerType.KEYWORD;
        private boolean indexed = true;
        private boolean stored;
        private boolean termPositions;
        private boolean caseSensitive;

        private Builder() {
        }

        private Builder(final IndexFieldImpl indexField) {
            this.name = indexField.name;
            this.type = indexField.type;
            this.analyzerType = indexField.analyzerType;
            this.indexed = indexField.indexed;
            this.stored = indexField.stored;
            this.termPositions = indexField.termPositions;
            this.caseSensitive = indexField.caseSensitive;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder type(final FieldType type) {
            this.type = type;
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
                    name,
                    type,
                    analyzerType,
                    indexed,
                    stored,
                    termPositions,
                    caseSensitive);
        }
    }
}
