package stroom.meta.shared;

import stroom.util.shared.Range;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelectionSummary {
    @JsonProperty
    private final long itemCount;
    @JsonProperty
    private final long feedCount;
    @JsonProperty
    private final long typeCount;
    @JsonProperty
    private final long processorCount;
    @JsonProperty
    private final long pipelineCount;
    @JsonProperty
    private final long statusCount;
    @JsonProperty
    private final Range<Long> ageRange;

    @JsonCreator
    public SelectionSummary(@JsonProperty("itemCount") final long itemCount,
                            @JsonProperty("feedCount") final long feedCount,
                            @JsonProperty("typeCount") final long typeCount,
                            @JsonProperty("processorCount") final long processorCount,
                            @JsonProperty("pipelineCount") final long pipelineCount,
                            @JsonProperty("statusCount") final long statusCount,
                            @JsonProperty("ageRange") final Range<Long> ageRange) {
        this.itemCount = itemCount;
        this.feedCount = feedCount;
        this.typeCount = typeCount;
        this.processorCount = processorCount;
        this.pipelineCount = pipelineCount;
        this.statusCount = statusCount;
        this.ageRange = ageRange;
    }

    public long getItemCount() {
        return itemCount;
    }

    public long getFeedCount() {
        return feedCount;
    }

    public long getTypeCount() {
        return typeCount;
    }

    public long getProcessorCount() {
        return processorCount;
    }

    public long getPipelineCount() {
        return pipelineCount;
    }

    public long getStatusCount() {
        return statusCount;
    }

    public Range<Long> getAgeRange() {
        return ageRange;
    }
}
