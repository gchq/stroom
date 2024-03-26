package stroom.analytics.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "detectTime",
        "detectorName",
        "detectorUuid",
        "detectorVersion",
        "detectorEnvironment",
        "headline",
        "detailedDescription",
        "fullDescription",
        "detectionUniqueId",
        "detectionRevision",
        "defunct",
        "executionSchedule",
        "executionTime",
        "effectiveExecutionTime",
        "values",
        "linkedEvents"
})
@JsonInclude(Include.NON_NULL)
public class Detection {

    @JsonProperty
    private final String detectTime;
    @JsonProperty
    private final String detectorName;
    @JsonProperty
    private final String detectorUuid;
    @JsonProperty
    private final String detectorVersion;
    @JsonProperty
    private final String detectorEnvironment;
    @JsonProperty
    private final String headline;
    @JsonProperty
    private final String detailedDescription;
    @JsonProperty
    private final String fullDescription;
    @JsonProperty
    private final String detectionUniqueId;
    @JsonProperty
    private final Integer detectionRevision;
    @JsonProperty
    private final Boolean defunct;


    @JsonProperty
    private final String executionSchedule;
    @JsonProperty
    private final String executionTime;
    @JsonProperty
    private final String effectiveExecutionTime;


    @JsonProperty
    private final List<DetectionValue> values;
    @JsonProperty
    private final List<DetectionLinkedEvent> linkedEvents;

    @JsonCreator
    public Detection(@JsonProperty("detectTime") final String detectTime,
                     @JsonProperty("detectorName") final String detectorName,
                     @JsonProperty("detectorUuid") final String detectorUuid,
                     @JsonProperty("detectorVersion") final String detectorVersion,
                     @JsonProperty("detectorEnvironment") final String detectorEnvironment,
                     @JsonProperty("headline") final String headline,
                     @JsonProperty("detailedDescription") final String detailedDescription,
                     @JsonProperty("fullDescription") final String fullDescription,
                     @JsonProperty("detectionUniqueId") final String detectionUniqueId,
                     @JsonProperty("detectionRevision") final Integer detectionRevision,
                     @JsonProperty("defunct") final Boolean defunct,
                     @JsonProperty("executionSchedule") final String executionSchedule,
                     @JsonProperty("executionTime") final String executionTime,
                     @JsonProperty("effectiveExecutionTime") final String effectiveExecutionTime,
                     @JsonProperty("values") final List<DetectionValue> values,
                     @JsonProperty("linkedEvents") final List<DetectionLinkedEvent> linkedEvents) {
        this.detectTime = detectTime;
        this.detectorName = detectorName;
        this.detectorUuid = detectorUuid;
        this.detectorVersion = detectorVersion;
        this.detectorEnvironment = detectorEnvironment;
        this.headline = headline;
        this.detailedDescription = detailedDescription;
        this.fullDescription = fullDescription;
        this.detectionUniqueId = detectionUniqueId;
        this.detectionRevision = detectionRevision;
        this.defunct = defunct;
        this.executionSchedule = executionSchedule;
        this.executionTime = executionTime;
        this.effectiveExecutionTime = effectiveExecutionTime;
        this.values = values;
        this.linkedEvents = linkedEvents;
    }

    public String getDetectTime() {
        return detectTime;
    }

    public String getDetectorName() {
        return detectorName;
    }

    public String getDetectorUuid() {
        return detectorUuid;
    }

    public String getDetectorVersion() {
        return detectorVersion;
    }

    public String getDetectorEnvironment() {
        return detectorEnvironment;
    }

    public String getHeadline() {
        return headline;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public String getFullDescription() {
        return fullDescription;
    }

    public String getDetectionUniqueId() {
        return detectionUniqueId;
    }

    public Integer getDetectionRevision() {
        return detectionRevision;
    }

    public String getExecutionSchedule() {
        return executionSchedule;
    }

    public String getExecutionTime() {
        return executionTime;
    }

    public String getEffectiveExecutionTime() {
        return effectiveExecutionTime;
    }

    public Boolean getDefunct() {
        return defunct;
    }

    public List<DetectionValue> getValues() {
        return values;
    }

    public List<DetectionLinkedEvent> getLinkedEvents() {
        return linkedEvents;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Detection detection = (Detection) o;
        return Objects.equals(detectTime, detection.detectTime) &&
                Objects.equals(detectorName, detection.detectorName) &&
                Objects.equals(detectorUuid, detection.detectorUuid) &&
                Objects.equals(detectorVersion, detection.detectorVersion) &&
                Objects.equals(detectorEnvironment, detection.detectorEnvironment) &&
                Objects.equals(headline, detection.headline) &&
                Objects.equals(detailedDescription, detection.detailedDescription) &&
                Objects.equals(fullDescription, detection.fullDescription) &&
                Objects.equals(detectionUniqueId, detection.detectionUniqueId) &&
                Objects.equals(detectionRevision, detection.detectionRevision) &&
                Objects.equals(defunct, detection.defunct) &&
                Objects.equals(executionSchedule, detection.executionSchedule) &&
                Objects.equals(executionTime, detection.executionTime) &&
                Objects.equals(effectiveExecutionTime, detection.effectiveExecutionTime) &&
                Objects.equals(values, detection.values) &&
                Objects.equals(linkedEvents, detection.linkedEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(detectTime,
                detectorName,
                detectorUuid,
                detectorVersion,
                detectorEnvironment,
                headline,
                detailedDescription,
                fullDescription,
                detectionUniqueId,
                detectionRevision,
                defunct,
                executionSchedule,
                executionTime,
                effectiveExecutionTime,
                values,
                linkedEvents);
    }

    @Override
    public String toString() {
        return "Detection{" +
                "detectTime='" + detectTime + '\'' +
                ", detectorName='" + detectorName + '\'' +
                ", detectorUuid='" + detectorUuid + '\'' +
                ", detectorVersion='" + detectorVersion + '\'' +
                ", detectorEnvironment='" + detectorEnvironment + '\'' +
                ", headline='" + headline + '\'' +
                ", detailedDescription='" + detailedDescription + '\'' +
                ", fullDescription='" + fullDescription + '\'' +
                ", detectionUniqueId='" + detectionUniqueId + '\'' +
                ", detectionRevision=" + detectionRevision +
                ", defunct=" + defunct +
                ", executionSchedule='" + executionSchedule + '\'' +
                ", executionTime=" + executionTime +
                ", effectiveExecutionTime=" + effectiveExecutionTime +
                ", values=" + values +
                ", linkedEvents=" + linkedEvents +
                '}';
    }


    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String detectTime;
        private String detectorName;
        private String detectorUuid;
        private String detectorVersion;
        private String detectorEnvironment;
        private String headline;
        private String detailedDescription;
        private String fullDescription;
        private String detectionUniqueId;
        private Integer detectionRevision;
        private Boolean defunct;
        private String executionSchedule;
        private String executionTime;
        private String effectiveExecutionTime;
        private List<DetectionValue> values;
        private List<DetectionLinkedEvent> linkedEvents;

        private Builder() {
        }

        private Builder(final Detection detection) {
            this.detectTime = detection.detectTime;
            this.detectorName = detection.detectorName;
            this.detectorUuid = detection.detectorUuid;
            this.detectorVersion = detection.detectorVersion;
            this.detectorEnvironment = detection.detectorEnvironment;
            this.headline = detection.headline;
            this.detailedDescription = detection.detailedDescription;
            this.fullDescription = detection.fullDescription;
            this.detectionUniqueId = detection.detectionUniqueId;
            this.detectionRevision = detection.detectionRevision;
            this.defunct = detection.defunct;
            this.executionSchedule = detection.executionSchedule;
            this.executionTime = detection.executionTime;
            this.effectiveExecutionTime = detection.effectiveExecutionTime;
            this.values = detection.values;
            this.linkedEvents = detection.linkedEvents;
        }

        public Builder detectTime(final String detectTime) {
            this.detectTime = detectTime;
            return this;
        }

        public Builder detectorName(final String detectorName) {
            this.detectorName = detectorName;
            return this;
        }

        public Builder detectorUuid(final String detectorUuid) {
            this.detectorUuid = detectorUuid;
            return this;
        }

        public Builder detectorVersion(final String detectorVersion) {
            this.detectorVersion = detectorVersion;
            return this;
        }

        public Builder detectorEnvironment(final String detectorEnvironment) {
            this.detectorEnvironment = detectorEnvironment;
            return this;
        }

        public Builder headline(final String headline) {
            this.headline = headline;
            return this;
        }

        public Builder detailedDescription(final String detailedDescription) {
            this.detailedDescription = detailedDescription;
            return this;
        }

        public Builder fullDescription(final String fullDescription) {
            this.fullDescription = fullDescription;
            return this;
        }

        public Builder detectionUniqueId(final String detectionUniqueId) {
            this.detectionUniqueId = detectionUniqueId;
            return this;
        }

        public Builder detectionRevision(final Integer detectionRevision) {
            this.detectionRevision = detectionRevision;
            return this;
        }

        public Builder defunct(final Boolean defunct) {
            this.defunct = defunct;
            return this;
        }

        public Builder executionSchedule(final String executionSchedule) {
            this.executionSchedule = executionSchedule;
            return this;
        }

        public Builder executionTime(final String executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder effectiveExecutionTime(final String effectiveExecutionTime) {
            this.effectiveExecutionTime = effectiveExecutionTime;
            return this;
        }

        public Builder values(final List<DetectionValue> values) {
            this.values = values;
            return this;
        }

        public Builder linkedEvents(final List<DetectionLinkedEvent> linkedEvents) {
            this.linkedEvents = linkedEvents;
            return this;
        }

        public Detection build() {
            return new Detection(
                    detectTime,
                    detectorName,
                    detectorUuid,
                    detectorVersion,
                    detectorEnvironment,
                    headline,
                    detailedDescription,
                    fullDescription,
                    detectionUniqueId,
                    detectionRevision,
                    defunct,
                    executionSchedule,
                    executionTime,
                    effectiveExecutionTime,
                    values,
                    linkedEvents);
        }
    }
}
