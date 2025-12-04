/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl;

import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    private Detection(final Builder builder) {
        detectTime = builder.detectTime;
        detectorName = builder.detectorName;
        detectorUuid = builder.detectorUuid;
        detectorVersion = builder.detectorVersion;
        detectorEnvironment = builder.detectorEnvironment;
        headline = builder.headline;
        detailedDescription = builder.detailedDescription;
        fullDescription = builder.fullDescription;
        detectionUniqueId = builder.detectionUniqueId;
        detectionRevision = builder.detectionRevision;
        defunct = builder.defunct;
        executionSchedule = builder.executionSchedule;
        executionTime = builder.executionTime;
        effectiveExecutionTime = builder.effectiveExecutionTime;
        values = builder.values;
        linkedEvents = builder.linkedEvents;
    }

    public static Builder builder(final Detection copy) {
        final Builder builder = new Builder();
        builder.detectTime = copy.getDetectTime();
        builder.detectorName = copy.getDetectorName();
        builder.detectorUuid = copy.getDetectorUuid();
        builder.detectorVersion = copy.getDetectorVersion();
        builder.detectorEnvironment = copy.getDetectorEnvironment();
        builder.headline = copy.getHeadline();
        builder.detailedDescription = copy.getDetailedDescription();
        builder.fullDescription = copy.getFullDescription();
        builder.detectionUniqueId = copy.getDetectionUniqueId();
        builder.detectionRevision = copy.getDetectionRevision();
        builder.defunct = copy.getDefunct();
        builder.values = copy.getValues();
        builder.linkedEvents = copy.getLinkedEvents();
        return builder;
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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

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

        public Builder withDetectTime(final String detectTime) {
            this.detectTime = detectTime;
            return this;
        }

        public Builder withDetectorName(final String detectorName) {
            this.detectorName = detectorName;
            return this;
        }

        public Builder withDetectorUuid(final String detectorUuid) {
            this.detectorUuid = detectorUuid;
            return this;
        }

        public Builder withRandomDetectorUuid() {
            this.detectorUuid = UUID.randomUUID().toString();
            return this;
        }

        public Builder withDetectorVersion(final String detectorVersion) {
            this.detectorVersion = detectorVersion;
            return this;
        }

        public Builder withDetectorEnvironment(final String detectorEnvironment) {
            this.detectorEnvironment = detectorEnvironment;
            return this;
        }

        public Builder withHeadline(final String headline) {
            this.headline = headline;
            return this;
        }

        public Builder withDetailedDescription(final String detailedDescription) {
            this.detailedDescription = detailedDescription;
            return this;
        }

        public Builder withFullDescription(final String fullDescription) {
            this.fullDescription = fullDescription;
            return this;
        }

        public Builder withDetectionUniqueId(final String detectionUniqueId) {
            this.detectionUniqueId = detectionUniqueId;
            return this;
        }

        public Builder withRandomDetectionUniqueId() {
            this.detectionUniqueId = UUID.randomUUID().toString();
            return this;
        }

        public Builder withDetectionRevision(final int detectionRevision) {
            this.detectionRevision = detectionRevision;
            return this;
        }

        public Builder withDefunct(final boolean defunct) {
            this.defunct = defunct;
            return this;
        }

        public Builder defunct() {
            this.defunct = true;
            return this;
        }

        public Builder notDefunct() {
            this.defunct = false;
            return this;
        }

        public Builder withExecutionSchedule(final String executionSchedule) {
            this.executionSchedule = executionSchedule;
            return this;
        }

        public Builder withExecutionTime(final String executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder withExecutionTime(final Instant executionTime) {
            this.executionTime = NullSafe.get(executionTime, DateUtil::createNormalDateTimeString);
            return this;
        }

        public Builder withEffectiveExecutionTime(final String effectiveExecutionTime) {
            this.effectiveExecutionTime = effectiveExecutionTime;
            return this;
        }

        public Builder withEffectiveExecutionTime(final Instant effectiveExecutionTime) {
            this.effectiveExecutionTime = NullSafe.get(effectiveExecutionTime, DateUtil::createNormalDateTimeString);
            return this;
        }

        public Builder withValues(final List<DetectionValue> values) {
            this.values = values;
            return this;
        }

        public Builder withValues(final Map<String, String> values) {
            this.values = NullSafe.map(values)
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey() != null)
                    .map(entry -> new DetectionValue(entry.getKey(), entry.getValue()))
                    .toList();
            return this;
        }

        public Builder addValue(final DetectionValue value) {
            if (values == null) {
                values = new ArrayList<>();
            }
            values.add(value);
            return this;
        }

        public Builder addValue(final String name, final String value) {
            return addValue(new DetectionValue(Objects.requireNonNull(name), value));
        }

        public Builder withLinkedEvents(final List<DetectionLinkedEvent> linkedEvents) {
            this.linkedEvents = linkedEvents;
            return this;
        }

        public Builder addLinkedEvents(final DetectionLinkedEvent linkedEvent) {
            if (linkedEvents == null) {
                linkedEvents = new ArrayList<>();
            }
            linkedEvents.add(linkedEvent);
            return this;
        }

        public Detection build() {
            return new Detection(this);
        }
    }
}
