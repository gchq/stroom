package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"from", "to"})
public class TimeFilter {

    private final long from;
    private final long to;

    @JsonCreator
    public TimeFilter(@JsonProperty("from") final long from,
                      @JsonProperty("to") final long to) {
        this.from = from;
        this.to = to;
    }

    public long getFrom() {
        return from;
    }

    public long getTo() {
        return to;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeFilter that = (TimeFilter) o;
        return from == that.from && to == that.to;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return "TimeFilter{" +
                "from=" + from +
                ", to=" + to +
                '}';
    }
}
