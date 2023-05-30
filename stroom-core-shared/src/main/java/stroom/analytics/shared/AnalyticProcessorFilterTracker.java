package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticProcessorFilterTracker {

    @JsonProperty
    private final String filterUuid;
    @JsonProperty
    private final Long lastPollMs;
    @JsonProperty
    private final Integer lastPollTaskCount;
    @JsonProperty
    private final Long lastMetaId;
    @JsonProperty
    private final Long lastEventId;
    @JsonProperty
    private final Long lastEventTime;
    @JsonProperty
    private final Long metaCount;
    @JsonProperty
    private final Long eventCount;
    @JsonProperty
    private final String message;

    @JsonCreator
    public AnalyticProcessorFilterTracker(@JsonProperty("filterUuid") final String filterUuid,
                                          @JsonProperty("lastPollMs") final Long lastPollMs,
                                          @JsonProperty("lastPollTaskCount") final Integer lastPollTaskCount,
                                          @JsonProperty("lastMetaId") final Long lastMetaId,
                                          @JsonProperty("lastEventId") final Long lastEventId,
                                          @JsonProperty("lastEventTime") final Long lastEventTime,
                                          @JsonProperty("metaCount") final Long metaCount,
                                          @JsonProperty("eventCount") final Long eventCount,
                                          @JsonProperty("message") final String message) {
        this.filterUuid = filterUuid;
        this.lastPollMs = lastPollMs;
        this.lastPollTaskCount = lastPollTaskCount;
        this.lastMetaId = lastMetaId;
        this.lastEventId = lastEventId;
        this.lastEventTime = lastEventTime;
        this.metaCount = metaCount;
        this.eventCount = eventCount;
        this.message = message;
    }

    public String getFilterUuid() {
        return filterUuid;
    }

    public Long getLastPollMs() {
        return lastPollMs;
    }

    public Integer getLastPollTaskCount() {
        return lastPollTaskCount;
    }

    public Long getLastMetaId() {
        return lastMetaId;
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public Long getLastEventTime() {
        return lastEventTime;
    }

    public Long getMetaCount() {
        return metaCount;
    }

    public Long getEventCount() {
        return eventCount;
    }

    public String getMessage() {
        return message;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String filterUuid;
        private Long lastPollMs;
        private Integer lastPollTaskCount;
        private Long lastMetaId;
        private Long lastEventId;
        private Long lastEventTime;
        private Long metaCount;
        private Long eventCount;
        private String message;

        private Builder() {
        }

        private Builder(final AnalyticProcessorFilterTracker analyticProcessorFilter) {
            this.filterUuid = analyticProcessorFilter.filterUuid;
            this.lastPollMs = analyticProcessorFilter.lastPollMs;
            this.lastPollTaskCount = analyticProcessorFilter.lastPollTaskCount;
            this.lastMetaId = analyticProcessorFilter.lastMetaId;
            this.lastEventId = analyticProcessorFilter.lastEventId;
            this.lastEventTime = analyticProcessorFilter.lastEventTime;
            this.metaCount = analyticProcessorFilter.metaCount;
            this.eventCount = analyticProcessorFilter.eventCount;
            this.message = analyticProcessorFilter.message;
        }

        public Builder filterUuid(final String filterUuid) {
            this.filterUuid = filterUuid;
            return this;
        }

        public Builder lastPollMs(final Long lastPollMs) {
            this.lastPollMs = lastPollMs;
            return this;
        }

        public Builder lastPollTaskCount(final Integer lastPollTaskCount) {
            this.lastPollTaskCount = lastPollTaskCount;
            return this;
        }

        public Builder lastMetaId(final Long lastMetaId) {
            this.lastMetaId = lastMetaId;
            return this;
        }

        public Builder lastEventId(final Long lastEventId) {
            this.lastEventId = lastEventId;
            return this;
        }

        public Builder lastEventTime(final Long lastEventTime) {
            this.lastEventTime = lastEventTime;
            return this;
        }

        public Builder metaCount(final Long metaCount) {
            this.metaCount = metaCount;
            return this;
        }

        public Builder eventCount(final Long eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        public Builder message(final String message) {
            this.message = message;
            return this;
        }

        public AnalyticProcessorFilterTracker build() {
            return new AnalyticProcessorFilterTracker(
                    filterUuid,
                    lastPollMs,
                    lastPollTaskCount,
                    lastMetaId,
                    lastEventId,
                    lastEventTime,
                    metaCount,
                    eventCount,
                    message);
        }
    }
}
