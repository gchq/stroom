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

package stroom.pipeline.xml.converter.json;

public record JSONFactoryConfig(
        boolean allowComments,
        boolean allowYamlComments,
        boolean allowUnquotedFieldNames,
        boolean allowSingleQuotes,
        boolean allowUnquotedControlChars,
        boolean allowBackslashEscapingAnyCharacter,
        boolean allowNumericLeadingZeros,
        boolean allowNonNumericNumbers,
        boolean allowMissingValues,
        boolean allowTrailingComma,
        int stringTruncateLength,
        int maxStringLength,
        int maxDepth) {

    public static final int DEFAULT_STRING_TRUNCATE_LENGTH = 10_000;
    public static final int DEFAULT_MAX_STRING_LENGTH = 100_000_000;
    public static final int DEFAULT_MAX_DEPTH = 500;

    public JSONFactoryConfig() {
        this(new Builder());
    }

    public JSONFactoryConfig {
        if (stringTruncateLength < 0) {
            stringTruncateLength = 0;
        }
        if (maxStringLength < 0) {
            maxStringLength = 0;
        }
        if (maxDepth < 0) {
            maxDepth = 0;
        }
    }

    private JSONFactoryConfig(final Builder builder) {
        this(builder.allowComments,
                builder.allowYamlComments,
                builder.allowUnquotedFieldNames,
                builder.allowSingleQuotes,
                builder.allowUnquotedControlChars,
                builder.allowBackslashEscapingAnyCharacter,
                builder.allowNumericLeadingZeros,
                builder.allowNonNumericNumbers,
                builder.allowMissingValues,
                builder.allowTrailingComma,
                builder.stringTruncateLength,
                builder.maxStringLength,
                builder.maxDepth);
    }

    public static Builder copy(final JSONFactoryConfig source) {
        final Builder builder = new Builder();
        builder.allowComments = source.allowComments();
        builder.allowYamlComments = source.allowYamlComments();
        builder.allowUnquotedFieldNames = source.allowUnquotedFieldNames();
        builder.allowSingleQuotes = source.allowSingleQuotes();
        builder.allowUnquotedControlChars = source.allowUnquotedControlChars();
        builder.allowBackslashEscapingAnyCharacter = source.allowBackslashEscapingAnyCharacter();
        builder.allowNumericLeadingZeros = source.allowNumericLeadingZeros();
        builder.allowNonNumericNumbers = source.allowNonNumericNumbers();
        builder.allowMissingValues = source.allowMissingValues();
        builder.allowTrailingComma = source.allowTrailingComma();
        builder.stringTruncateLength = source.stringTruncateLength();
        builder.maxStringLength = source.maxStringLength();
        builder.maxDepth = source.maxDepth();
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private boolean allowComments = false;
        private boolean allowYamlComments = false;
        private boolean allowUnquotedFieldNames = false;
        private boolean allowSingleQuotes = false;
        private boolean allowUnquotedControlChars = false;
        private boolean allowBackslashEscapingAnyCharacter = false;
        private boolean allowNumericLeadingZeros = false;
        private boolean allowNonNumericNumbers = false;
        private boolean allowMissingValues = false;
        private boolean allowTrailingComma = false;
        private int stringTruncateLength = DEFAULT_STRING_TRUNCATE_LENGTH;
        private int maxStringLength = DEFAULT_MAX_STRING_LENGTH;
        private int maxDepth = DEFAULT_MAX_DEPTH;

        private Builder() {
        }


        public Builder allowComments(final boolean allowComments) {
            this.allowComments = allowComments;
            return this;
        }

        public Builder allowYamlComments(final boolean allowYamlComments) {
            this.allowYamlComments = allowYamlComments;
            return this;
        }

        public Builder allowUnquotedFieldNames(final boolean allowUnquotedFieldNames) {
            this.allowUnquotedFieldNames = allowUnquotedFieldNames;
            return this;
        }

        public Builder allowSingleQuotes(final boolean allowSingleQuotes) {
            this.allowSingleQuotes = allowSingleQuotes;
            return this;
        }

        public Builder allowUnquotedControlChars(final boolean allowUnquotedControlChars) {
            this.allowUnquotedControlChars = allowUnquotedControlChars;
            return this;
        }

        public Builder allowBackslashEscapingAnyCharacter(final boolean allowBackslashEscapingAnyCharacter) {
            this.allowBackslashEscapingAnyCharacter = allowBackslashEscapingAnyCharacter;
            return this;
        }

        public Builder allowNumericLeadingZeros(final boolean allowNumericLeadingZeros) {
            this.allowNumericLeadingZeros = allowNumericLeadingZeros;
            return this;
        }

        public Builder allowNonNumericNumbers(final boolean allowNonNumericNumbers) {
            this.allowNonNumericNumbers = allowNonNumericNumbers;
            return this;
        }

        public Builder allowMissingValues(final boolean allowMissingValues) {
            this.allowMissingValues = allowMissingValues;
            return this;
        }

        public Builder allowTrailingComma(final boolean allowTrailingComma) {
            this.allowTrailingComma = allowTrailingComma;
            return this;
        }

        public Builder stringTruncateLength(final int stringTruncateLength) {
            this.stringTruncateLength = stringTruncateLength;
            return this;
        }

        public Builder maxStringLength(final int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public Builder maxDepth(final int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public JSONFactoryConfig build() {
            return new JSONFactoryConfig(this);
        }

    }
}
