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

import java.util.Objects;

public class JSONFactoryConfig {

    private boolean allowComments;
    private boolean allowYamlComments;
    private boolean allowUnquotedFieldNames;
    private boolean allowSingleQuotes;
    private boolean allowUnquotedControlChars;
    private boolean allowBackslashEscapingAnyCharacter;
    private boolean allowNumericLeadingZeros;
    private boolean allowNonNumericNumbers;
    private boolean allowMissingValues;
    private boolean allowTrailingComma;

    public boolean isAllowComments() {
        return allowComments;
    }

    public void setAllowComments(final boolean allowComments) {
        this.allowComments = allowComments;
    }

    public boolean isAllowYamlComments() {
        return allowYamlComments;
    }

    public void setAllowYamlComments(final boolean allowYamlComments) {
        this.allowYamlComments = allowYamlComments;
    }

    public boolean isAllowUnquotedFieldNames() {
        return allowUnquotedFieldNames;
    }

    public void setAllowUnquotedFieldNames(final boolean allowUnquotedFieldNames) {
        this.allowUnquotedFieldNames = allowUnquotedFieldNames;
    }

    public boolean isAllowSingleQuotes() {
        return allowSingleQuotes;
    }

    public void setAllowSingleQuotes(final boolean allowSingleQuotes) {
        this.allowSingleQuotes = allowSingleQuotes;
    }

    public boolean isAllowUnquotedControlChars() {
        return allowUnquotedControlChars;
    }

    public void setAllowUnquotedControlChars(final boolean allowUnquotedControlChars) {
        this.allowUnquotedControlChars = allowUnquotedControlChars;
    }

    public boolean isAllowBackslashEscapingAnyCharacter() {
        return allowBackslashEscapingAnyCharacter;
    }

    public void setAllowBackslashEscapingAnyCharacter(final boolean allowBackslashEscapingAnyCharacter) {
        this.allowBackslashEscapingAnyCharacter = allowBackslashEscapingAnyCharacter;
    }

    public boolean isAllowNumericLeadingZeros() {
        return allowNumericLeadingZeros;
    }

    public void setAllowNumericLeadingZeros(final boolean allowNumericLeadingZeros) {
        this.allowNumericLeadingZeros = allowNumericLeadingZeros;
    }

    public boolean isAllowNonNumericNumbers() {
        return allowNonNumericNumbers;
    }

    public void setAllowNonNumericNumbers(final boolean allowNonNumericNumbers) {
        this.allowNonNumericNumbers = allowNonNumericNumbers;
    }

    public boolean isAllowMissingValues() {
        return allowMissingValues;
    }

    public void setAllowMissingValues(final boolean allowMissingValues) {
        this.allowMissingValues = allowMissingValues;
    }

    public boolean isAllowTrailingComma() {
        return allowTrailingComma;
    }

    public void setAllowTrailingComma(final boolean allowTrailingComma) {
        this.allowTrailingComma = allowTrailingComma;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JSONFactoryConfig that = (JSONFactoryConfig) o;
        return allowComments == that.allowComments &&
                allowYamlComments == that.allowYamlComments &&
                allowUnquotedFieldNames == that.allowUnquotedFieldNames &&
                allowSingleQuotes == that.allowSingleQuotes &&
                allowUnquotedControlChars == that.allowUnquotedControlChars &&
                allowBackslashEscapingAnyCharacter == that.allowBackslashEscapingAnyCharacter &&
                allowNumericLeadingZeros == that.allowNumericLeadingZeros &&
                allowNonNumericNumbers == that.allowNonNumericNumbers &&
                allowMissingValues == that.allowMissingValues &&
                allowTrailingComma == that.allowTrailingComma;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                allowComments,
                allowYamlComments,
                allowUnquotedFieldNames,
                allowSingleQuotes,
                allowUnquotedControlChars,
                allowBackslashEscapingAnyCharacter,
                allowNumericLeadingZeros,
                allowNonNumericNumbers,
                allowMissingValues,
                allowTrailingComma);
    }

    @Override
    public String toString() {
        return "JSONParserConfig{" +
                "allowComments=" + allowComments +
                ", allowYamlComments=" + allowYamlComments +
                ", allowUnquotedFieldNames=" + allowUnquotedFieldNames +
                ", allowSingleQuotes=" + allowSingleQuotes +
                ", allowUnquotedControlChars=" + allowUnquotedControlChars +
                ", allowBackslashEscapingAnyCharacter=" + allowBackslashEscapingAnyCharacter +
                ", allowNumericLeadingZeros=" + allowNumericLeadingZeros +
                ", allowNonNumericNumbers=" + allowNonNumericNumbers +
                ", allowMissingValues=" + allowMissingValues +
                ", allowTrailingComma=" + allowTrailingComma +
                '}';
    }
}
