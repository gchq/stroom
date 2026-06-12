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

package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceConfig extends AbstractConfig implements IsStroomConfig {

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of characters of data to display in the Data Preview pane.")
    private final long maxCharactersInPreviewFetch;

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of characters of data to display in the Source View editor at " +
            "at time.")
    private final long maxCharactersPerFetch;

    @Min(0)
    @JsonProperty
    @JsonPropertyDescription("When displaying multi-line data in the Data Preview or Source views, the viewer will " +
            "attempt to always show complete lines. It will go past the requested range by up to this many " +
            "characters in order to complete the line.")
    private final long maxCharactersToCompleteLine;

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of lines of hex dump to display when viewing data as hex. " +
            "A single line displays 32 bytes.")
    private final int maxHexDumpLines;

    public SourceConfig() {
        // TODO @AT Default values may need increasing
        maxCharactersInPreviewFetch = 30_000L;
        maxCharactersPerFetch = 80_000L;
        maxCharactersToCompleteLine = 10_000L;
        maxHexDumpLines = 1_000;
    }

    @JsonCreator
    public SourceConfig(@JsonProperty("maxCharactersInPreviewFetch") final long maxCharactersInPreviewFetch,
                        @JsonProperty("maxCharactersPerFetch") final long maxCharactersPerFetch,
                        @JsonProperty("maxCharactersToCompleteLine") final long maxCharactersToCompleteLine,
                        @JsonProperty("maxHexDumpLines") final int maxHexDumpLines) {

        this.maxCharactersInPreviewFetch = maxCharactersInPreviewFetch;
        this.maxCharactersPerFetch = maxCharactersPerFetch;
        this.maxCharactersToCompleteLine = maxCharactersToCompleteLine;
        this.maxHexDumpLines = maxHexDumpLines;
    }

    public long getMaxCharactersInPreviewFetch() {
        return maxCharactersInPreviewFetch;
    }

    public long getMaxCharactersPerFetch() {
        return maxCharactersPerFetch;
    }

    public long getMaxCharactersToCompleteLine() {
        return maxCharactersToCompleteLine;
    }

    public int getMaxHexDumpLines() {
        return maxHexDumpLines;
    }

    @Override
    public String toString() {
        return "SourceConfig{" +
                "maxCharactersInPreviewFetch=" + maxCharactersInPreviewFetch +
                ", maxCharactersPerFetch=" + maxCharactersPerFetch +
                ", maxCharactersToCompleteLine=" + maxCharactersToCompleteLine +
                ", maxHexDumpLines=" + maxHexDumpLines +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SourceConfig that = (SourceConfig) o;
        return maxCharactersInPreviewFetch == that.maxCharactersInPreviewFetch
                && maxCharactersPerFetch == that.maxCharactersPerFetch
                && maxCharactersToCompleteLine == that.maxCharactersToCompleteLine
                && maxHexDumpLines == that.maxHexDumpLines;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxCharactersInPreviewFetch,
                maxCharactersPerFetch,
                maxCharactersToCompleteLine,
                maxHexDumpLines);
    }

}
