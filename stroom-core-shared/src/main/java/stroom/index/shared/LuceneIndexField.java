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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LuceneIndexField extends IndexField {

    @Deprecated
    @JsonProperty("fieldType")
    private OldIndexFieldType fieldType;
    @Deprecated
    @JsonProperty("fieldName")
    private String fieldName;

    @JsonCreator
    public LuceneIndexField(@JsonProperty("name") final String name,
                            @JsonProperty("type") final FieldType type,
                            @Deprecated @JsonProperty("fieldName") final String fieldName,
                            @Deprecated @JsonProperty("fieldType") final OldIndexFieldType fieldType,
                            @JsonProperty("analyzerType") final AnalyzerType analyzerType,
                            @JsonProperty("indexed") final boolean indexed,
                            @JsonProperty("stored") final boolean stored,
                            @JsonProperty("termPositions") final boolean termPositions,
                            @JsonProperty("caseSensitive") final boolean caseSensitive) {
        super(convertLegacyName(name, fieldName),
                convertLegacyType(type, fieldType),
                analyzerType,
                indexed,
                stored,
                termPositions,
                caseSensitive);
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
                .name(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName, final AnalyzerType analyzerType) {
        return new Builder()
                .name(fieldName)
                .analyzerType(analyzerType)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName, final AnalyzerType analyzerType,
                                               final boolean caseSensitive) {
        return new Builder()
                .name(fieldName)
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
                .name(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .stored(stored)
                .indexed(indexed)
                .termPositions(termPositions)
                .build();
    }

    public static LuceneIndexField createNumericField(final String fieldName) {
        return new Builder()
                .type(FieldType.LONG)
                .name(fieldName)
                .analyzerType(AnalyzerType.NUMERIC)
                .build();
    }

    public static LuceneIndexField createIdField(final String fieldName) {
        return new Builder()
                .type(FieldType.ID)
                .name(fieldName)
                .analyzerType(AnalyzerType.KEYWORD)
                .stored(true)
                .build();
    }

    public static LuceneIndexField createDateField(final String fieldName) {
        return new Builder()
                .type(FieldType.DATE)
                .name(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
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

        private Builder(final LuceneIndexField indexField) {
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

        public LuceneIndexField build() {
            return new LuceneIndexField(
                    name,
                    type,
                    null,
                    null,
                    analyzerType,
                    indexed,
                    stored,
                    termPositions,
                    caseSensitive);
        }
    }
}
