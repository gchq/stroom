package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"timeField", "windowSize", "advanceSize"})
@JsonInclude(Include.NON_NULL)
public class HoppingWindow implements Window {

    private final String timeField;
    private final String windowSize;
    private final String advanceSize;

    @JsonCreator
    public HoppingWindow(@JsonProperty("timeField") final String timeField,
                         @JsonProperty("windowSize") final String windowSize,
                         @JsonProperty("advanceSize") final String advanceSize) {
        this.timeField = timeField;
        this.windowSize = windowSize;
        this.advanceSize = advanceSize;
    }

    public String getTimeField() {
        return timeField;
    }

    public String getWindowSize() {
        return windowSize;
    }

    public String getAdvanceSize() {
        return advanceSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HoppingWindow that = (HoppingWindow) o;
        return Objects.equals(timeField, that.timeField) && Objects.equals(windowSize,
                that.windowSize) && Objects.equals(advanceSize, that.advanceSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeField, windowSize, advanceSize);
    }

    @Override
    public String toString() {
        return "HoppingWindow{" +
                "timeField='" + timeField + '\'' +
                ", windowSize='" + windowSize + '\'' +
                ", advanceSize='" + advanceSize + '\'' +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String timeField = "EventTime";
        private String windowSize = "10m";
        private String advanceSize = "1m";

        private Builder() {
        }

        private Builder(final HoppingWindow hoppingWindow) {
            this.timeField = hoppingWindow.timeField;
            this.windowSize = hoppingWindow.windowSize;
            this.advanceSize = hoppingWindow.advanceSize;
        }

        public Builder timeField(final String timeField) {
            this.timeField = timeField;
            return this;
        }

        public Builder windowSize(final String windowSize) {
            this.windowSize = windowSize;
            return this;
        }

        public Builder advanceSize(final String advanceSize) {
            this.advanceSize = advanceSize;
            return this;
        }

        public HoppingWindow build() {
            return new HoppingWindow(timeField, windowSize, advanceSize);
        }
    }
}
