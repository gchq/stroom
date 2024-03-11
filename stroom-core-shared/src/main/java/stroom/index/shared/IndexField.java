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

import stroom.datasource.api.v2.FieldType;
import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexField implements HasDisplayValue, Comparable<IndexField>, Serializable {

    @JsonProperty
    protected final String name;
    @JsonProperty
    protected final FieldType type;
    @JsonProperty
    protected final AnalyzerType analyzerType;
    @JsonProperty
    protected final boolean indexed;
    @JsonProperty
    protected final boolean stored;
    @JsonProperty
    protected final boolean termPositions;
    @JsonProperty
    protected final boolean caseSensitive;

    @JsonCreator
    public IndexField(@JsonProperty("name") final String name,
                      @JsonProperty("type") final FieldType type,
                      @JsonProperty("analyzerType") final AnalyzerType analyzerType,
                      @JsonProperty("indexed") final boolean indexed,
                      @JsonProperty("stored") final boolean stored,
                      @JsonProperty("termPositions") final boolean termPositions,
                      @JsonProperty("caseSensitive") final boolean caseSensitive) {
        this.name = name;
        this.type = type;
        this.analyzerType = analyzerType;
        this.stored = stored;
        this.indexed = indexed;
        this.termPositions = termPositions;
        this.caseSensitive = caseSensitive;
    }

    public static IndexField createField(final String name) {
        return new IndexFieldBuilder()
                .name(name)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public static IndexField createField(final String name,
                                         final AnalyzerType analyzerType) {
        return new IndexFieldBuilder()
                .name(name)
                .analyzerType(analyzerType)
                .build();
    }

    public static IndexField createField(final String name,
                                         final AnalyzerType analyzerType,
                                         final boolean caseSensitive) {
        return new IndexFieldBuilder()
                .name(name)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .build();
    }

    public static IndexField createField(final String name,
                                         final AnalyzerType analyzerType,
                                         final boolean caseSensitive,
                                         final boolean stored,
                                         final boolean indexed,
                                         final boolean termPositions) {
        return new IndexFieldBuilder()
                .name(name)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .stored(stored)
                .indexed(indexed)
                .termPositions(termPositions)
                .build();
    }

    public static IndexField createNumericField(final String name) {
        return new IndexFieldBuilder()
                .name(name)
                .type(FieldType.LONG)
                .analyzerType(AnalyzerType.NUMERIC)
                .build();
    }

    public static IndexField createIdField(final String name) {
        return new IndexFieldBuilder()
                .name(name)
                .type(FieldType.ID)
                .analyzerType(AnalyzerType.KEYWORD)
                .stored(true)
                .build();
    }

    public static IndexField createDateField(final String name) {
        return new IndexFieldBuilder()
                .name(name)
                .type(FieldType.DATE)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public AnalyzerType getAnalyzerType() {
        if (analyzerType == null) {
            return AnalyzerType.KEYWORD;
        }
        return analyzerType;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public boolean isTermPositions() {
        return termPositions;
    }

    @Override
    @JsonIgnore
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
        final IndexField that = (IndexField) o;
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
    public int compareTo(final IndexField o) {
        return name.compareToIgnoreCase(o.name);
    }
}
