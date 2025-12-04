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

package stroom.processor.impl.db.migration.legacyqd;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "limits", propOrder = {"streamCount", "eventCount", "durationMs"})
@XmlRootElement(name = "limits")
@JsonInclude(Include.NON_NULL)
public class Limits {

    @XmlElement(name = "streamCount")
    @JsonProperty
    private Long streamCount;

    @XmlElement(name = "eventCount")
    @JsonProperty
    private Long eventCount;

    @XmlElement(name = "durationMs")
    @JsonProperty
    private Long durationMs;

    public Limits() {
    }

    @JsonCreator
    public Limits(@JsonProperty("streamCount") final Long streamCount,
                  @JsonProperty("eventCount") final Long eventCount,
                  @JsonProperty("durationMs") final Long durationMs) {
        this.streamCount = streamCount;
        this.eventCount = eventCount;
        this.durationMs = durationMs;
    }

    public Long getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(final Long streamCount) {
        this.streamCount = streamCount;
    }

    public Long getEventCount() {
        return eventCount;
    }

    public void setEventCount(final Long eventCount) {
        this.eventCount = eventCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(final Long durationMs) {
        this.durationMs = durationMs;
    }
}
