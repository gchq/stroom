package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticProcessorFilterRow {

    @JsonProperty
    private final AnalyticProcessorFilter analyticProcessorFilter;
    @JsonProperty
    private final AnalyticProcessorFilterTracker analyticProcessorFilterTracker;

    @JsonCreator
    public AnalyticProcessorFilterRow(
            @JsonProperty("analyticProcessorFilter") final AnalyticProcessorFilter analyticProcessorFilter,
            @JsonProperty("analyticProcessorFilterTracker") final AnalyticProcessorFilterTracker analyticProcessorFilterTracker) {
        this.analyticProcessorFilter = analyticProcessorFilter;
        this.analyticProcessorFilterTracker = analyticProcessorFilterTracker;
    }

    public AnalyticProcessorFilter getAnalyticProcessorFilter() {
        return analyticProcessorFilter;
    }

    public AnalyticProcessorFilterTracker getAnalyticProcessorFilterTracker() {
        return analyticProcessorFilterTracker;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticProcessorFilterRow that = (AnalyticProcessorFilterRow) o;
        return Objects.equals(analyticProcessorFilter.getUuid(), that.analyticProcessorFilter.getUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(analyticProcessorFilter.getUuid());
    }
}
