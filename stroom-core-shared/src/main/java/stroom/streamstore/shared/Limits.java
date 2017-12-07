/*
 * Copyright 2017 Crown Copyright
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

package stroom.streamstore.shared;

import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "limits", propOrder = {"streamCount", "eventCount", "durationMs"})
@XmlRootElement(name = "limits")
public class Limits implements SharedObject {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "streamCount")
    private Long streamCount;

    @XmlElement(name = "eventCount")
    private Long eventCount;

    @XmlElement(name = "durationMs")
    private Long durationMs;

    public Limits() {
        // Default constructor necessary for GWT serialisation.
    }

    public Long getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(Long streamCount) {
        this.streamCount = streamCount;
    }

    public Long getEventCount() {
        return eventCount;
    }

    public void setEventCount(Long eventCount) {
        this.eventCount = eventCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public static class Builder {

        private final Limits instance;

        public Builder() {
            this.instance = new Limits();
        }

        public Builder streamCount(final Long value) {
            this.instance.streamCount = value;
            return this;
        }

        public Builder eventCount(final Long value) {
            this.instance.eventCount = value;
            return this;
        }

        public Builder durationMs(final Long value) {
            this.instance.durationMs = value;
            return this;
        }

        public Limits build() {
            return instance;
        }
    }
}
