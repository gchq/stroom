package stroom.analytics.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;

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

    public Boolean getDefunct() {
        return defunct;
    }

    public List<DetectionValue> getValues() {
        return values;
    }

    public List<DetectionLinkedEvent> getLinkedEvents() {
        return linkedEvents;
    }
}
