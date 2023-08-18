package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticProcessTracker {

    @JsonProperty
    private String filterUuid;
    @JsonProperty
    private AnalyticProcessTrackerData analyticProcessTrackerData;

    @JsonCreator
    public AnalyticProcessTracker(@JsonProperty("filterUuid") final String filterUuid,
                                  @JsonProperty("analyticProcessTrackerData")
                                  final AnalyticProcessTrackerData analyticProcessTrackerData) {
        this.filterUuid = filterUuid;
        this.analyticProcessTrackerData = analyticProcessTrackerData;
    }

    public String getFilterUuid() {
        return filterUuid;
    }

    public void setFilterUuid(final String filterUuid) {
        this.filterUuid = filterUuid;
    }

    public AnalyticProcessTrackerData getAnalyticProcessTrackerData() {
        return analyticProcessTrackerData;
    }

    public void setAnalyticProcessTrackerData(final AnalyticProcessTrackerData analyticProcessTrackerData) {
        this.analyticProcessTrackerData = analyticProcessTrackerData;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticProcessTracker that = (AnalyticProcessTracker) o;
        return Objects.equals(filterUuid, that.filterUuid) &&
                Objects.equals(analyticProcessTrackerData, that.analyticProcessTrackerData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filterUuid, analyticProcessTrackerData);
    }
}
