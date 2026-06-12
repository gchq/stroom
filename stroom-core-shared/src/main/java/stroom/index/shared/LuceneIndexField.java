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
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@JsonPropertyOrder({
        "fieldName", // deprecated
        "fieldType", // deprecated

        "fldName",
        "fldType",
        "nativeType",
        "analyzerType",
        "indexed",
        "stored",
        "termPositions",
        "caseSensitive",
        "denseVectorFieldConfig"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LuceneIndexField implements IndexField {

    @Deprecated
    @JsonProperty("fieldType")
    private OldIndexFieldType fieldType;
    @Deprecated
    @JsonProperty("fieldName")
    private String fieldName;

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
    @JsonProperty
    private final DenseVectorFieldConfig denseVectorFieldConfig;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public LuceneIndexField(@Deprecated @JsonProperty("fieldName") final String fieldName,
                            @Deprecated @JsonProperty("fieldType") final OldIndexFieldType fieldType,
                            @JsonProperty("fldName") final String fldName,
                            @JsonProperty("fldType") final FieldType fldType,
                            @JsonProperty("analyzerType") final AnalyzerType analyzerType,
                            @JsonProperty("indexed") final boolean indexed,
                            @JsonProperty("stored") final boolean stored,
                            @JsonProperty("termPositions") final boolean termPositions,
                            @JsonProperty("caseSensitive") final boolean caseSensitive,
                            @JsonProperty("denseVectorFieldConfig") final DenseVectorFieldConfig denseVectorFieldConfig) {
        this.fldName = convertLegacyName(fldName, fieldName);
        this.fldType = convertLegacyType(fldType, fieldType);
        this.analyzerType = analyzerType;
        this.stored = stored;
        this.indexed = indexed;
        this.termPositions = termPositions;
        this.caseSensitive = caseSensitive;
        this.denseVectorFieldConfig = denseVectorFieldConfig;
    }

    public static LuceneIndexField fromIndexField(final IndexField indexField) {
        return LuceneIndexField
                .builder()
                .fldName(indexField.getFldName())
                .fldType(indexField.getFldType())
                .analyzerType(indexField.getAnalyzerType())
                .indexed(indexField.isIndexed())
                .stored(indexField.isStored())
                .caseSensitive(indexField.isCaseSensitive())
                .termPositions(indexField.isTermPositions())
                .build();
    }

    private static String convertLegacyName(final String name, final String fieldName) {
        if (name == null) {
            return fieldName;
        }
        return name;
    }

    private static FieldType convertLegacyType(final FieldType type, final OldIndexFieldType fieldType) {
        if (type == null) {
            if (fieldType != null) {
                switch (fieldType) {
                    case ID: {
                        return FieldType.ID;
                    }
                    case BOOLEAN_FIELD: {
                        return FieldType.BOOLEAN;
                    }
                    case INTEGER_FIELD: {
                        return FieldType.INTEGER;
                    }
                    case LONG_FIELD: {
                        return FieldType.LONG;
                    }
                    case FLOAT_FIELD: {
                        return FieldType.FLOAT;
                    }
                    case DOUBLE_FIELD: {
                        return FieldType.DOUBLE;
                    }
                    case DATE_FIELD: {
                        return FieldType.DATE;
                    }
                    case FIELD: {
                        return FieldType.TEXT;
                    }
                    case NUMERIC_FIELD: {
                        return FieldType.LONG;
                    }
                }
            }
            return FieldType.TEXT;
        }
        return type;
    }

    public static LuceneIndexField createField(final String fieldName) {
        return new Builder()
                .fldName(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName, final AnalyzerType analyzerType) {
        return new Builder()
                .fldName(fieldName)
                .analyzerType(analyzerType)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName, final AnalyzerType analyzerType,
                                               final boolean caseSensitive) {
        return new Builder()
                .fldName(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName,
                                               final AnalyzerType analyzerType,
                                               final boolean caseSensitive,
                                               final boolean stored,
                                               final boolean indexed,
                                               final boolean termPositions) {
        return new Builder()
                .fldName(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .indexed(indexed)
                .stored(stored)
                .termPositions(termPositions)
                .build();
    }

    public static LuceneIndexField createNumericField(final String fieldName) {
        return new Builder()
                .fldType(FieldType.LONG)
                .fldName(fieldName)
                .analyzerType(AnalyzerType.NUMERIC)
                .build();
    }

    public static LuceneIndexField createIdField(final String fieldName) {
        return new Builder()
                .fldType(FieldType.ID)
                .fldName(fieldName)
                .analyzerType(AnalyzerType.KEYWORD)
                .stored(true)
                .build();
    }

    public static LuceneIndexField createDateField(final String fieldName) {
        return new Builder()
                .fldType(FieldType.DATE)
                .fldName(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    @Override
    public String getFldName() {
        return fldName;
    }

    @Override
    public FieldType getFldType() {
        return fldType;
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

    public DenseVectorFieldConfig getDenseVectorFieldConfig() {
        return denseVectorFieldConfig;
    }

    @Override
    @JsonIgnore
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
        final LuceneIndexField that = (LuceneIndexField) o;
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
        private DenseVectorFieldConfig denseVectorFieldConfig;

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
            this.denseVectorFieldConfig = indexField.getDenseVectorFieldConfig();
        }

        private Builder(final LuceneIndexField indexField) {
            this.fldName = indexField.fldName;
            this.fldType = indexField.fldType;
            this.analyzerType = indexField.analyzerType;
            this.indexed = indexField.indexed;
            this.stored = indexField.stored;
            this.termPositions = indexField.termPositions;
            this.caseSensitive = indexField.caseSensitive;
            this.denseVectorFieldConfig = indexField.denseVectorFieldConfig;
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

        public Builder denseVectorFieldConfig(final DenseVectorFieldConfig denseVectorFieldConfig) {
            this.denseVectorFieldConfig = denseVectorFieldConfig;
            return this;
        }

        public LuceneIndexField build() {
            return new LuceneIndexField(
                    null,
                    null,
                    fldName,
                    fldType,
                    analyzerType,
                    indexed,
                    stored,
                    termPositions,
                    caseSensitive,
                    denseVectorFieldConfig);
        }
    }
}
