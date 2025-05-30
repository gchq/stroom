package stroom.planb.impl.db.histogram;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Objects;

@JsonPropertyOrder({"tags", "time"})
@JsonInclude(Include.NON_NULL)
public class HistogramKey {

    @JsonProperty
    private final Tags tags;
    @JsonProperty
    private final Instant time;

    @JsonCreator
    public HistogramKey(@JsonProperty("tags") final Tags tags,
                        @JsonProperty("time") final Instant time) {
        this.tags = tags;
        this.time = time;
    }

    public Tags getTags() {
        return tags;
    }

    public Instant getTime() {
        return time;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HistogramKey that = (HistogramKey) o;
        return Objects.equals(tags, that.tags) && Objects.equals(time, that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, time);
    }

    @Override
    public String toString() {
        return "HistogramKey{" +
               "tags=" + tags +
               ", time=" + time +
               '}';
    }
}
