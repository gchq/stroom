package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.inject.Singleton;
import javax.validation.constraints.Min;

@JsonPropertyOrder({"maxCharactersInPreviewFetch"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceConfig extends AbstractConfig {

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of characters of data to display in the Data Preview pane.")
    private long maxCharactersInPreviewFetch;

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of characters of data to display in the Source View editor at " +
            "at time.")
    private long maxCharactersPerFetch;

    @Min(0)
    @JsonProperty
    @JsonPropertyDescription("When displaying multi-line data in the Data Preview or Source views, the viewer will " +
            "attempt to always show complete lines. It will go past the requested range by up to this many " +
            "characters in order to complete the line.")
    private long maxCharactersToCompleteLine;

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of lines of hex dump to display when viewing data as hex. " +
            "A single line displays 32 bytes.")
    private int maxHexDumpLines;

    public SourceConfig() {
        setDefaults();
    }

    @JsonCreator
    public SourceConfig(@JsonProperty("maxCharactersInPreviewFetch") final long maxCharactersInPreviewFetch,
                        @JsonProperty("maxCharactersPerFetch") final long maxCharactersPerFetch,
                        @JsonProperty("maxCharactersToCompleteLine") final long maxCharactersToCompleteLine,
                        @JsonProperty("maxHexDumpLines") final int maxHexDumpLines) {

        // TODO @AT Default values may need increasing
        this.maxCharactersInPreviewFetch = maxCharactersInPreviewFetch;
        this.maxCharactersPerFetch = maxCharactersPerFetch;
        this.maxCharactersToCompleteLine = maxCharactersToCompleteLine;
        this.maxHexDumpLines = maxHexDumpLines;

        setDefaults();
    }

    private void setDefaults() {
        maxCharactersInPreviewFetch = setDefaultIfUnset(maxCharactersInPreviewFetch, 30_000L);
        maxCharactersPerFetch = setDefaultIfUnset(maxCharactersPerFetch, 80_000L);
        maxCharactersToCompleteLine = setDefaultIfUnset(maxCharactersToCompleteLine, 10_000L);
        maxHexDumpLines = setDefaultIfUnset(maxHexDumpLines, 1_000);
    }

    private long setDefaultIfUnset(final long value, final long defaultValue) {
        return value > 0
                ? value
                : defaultValue;
    }

    private int setDefaultIfUnset(final int value, final int defaultValue) {
        return value > 0
                ? value
                : defaultValue;
    }

    public long getMaxCharactersInPreviewFetch() {
        return maxCharactersInPreviewFetch;
    }

    public void setMaxCharactersInPreviewFetch(final long maxCharactersInPreviewFetch) {
        this.maxCharactersInPreviewFetch = maxCharactersInPreviewFetch;
    }

    public long getMaxCharactersPerFetch() {
        return maxCharactersPerFetch;
    }

    public void setMaxCharactersPerFetch(final long maxCharactersPerFetch) {
        this.maxCharactersPerFetch = maxCharactersPerFetch;
    }

    public long getMaxCharactersToCompleteLine() {
        return maxCharactersToCompleteLine;
    }

    public void setMaxCharactersToCompleteLine(final long maxCharactersToCompleteLine) {
        this.maxCharactersToCompleteLine = maxCharactersToCompleteLine;
    }

    public int getMaxHexDumpLines() {
        return maxHexDumpLines;
    }

    public void setMaxHexDumpLines(final int maxHexDumpLines) {
        this.maxHexDumpLines = maxHexDumpLines;
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
