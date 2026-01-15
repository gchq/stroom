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

package stroom.query.common.v2;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class Lifespan {

    @JsonProperty
    private final StroomDuration timeToIdle;
    @JsonProperty
    private final StroomDuration timeToLive;
    @JsonProperty
    private final boolean destroyOnTabClose;
    @JsonProperty
    private final boolean destroyOnWindowClose;

    @JsonCreator
    public Lifespan(@JsonProperty("timeToIdle") final StroomDuration timeToIdle,
                    @JsonProperty("timeToLive") final StroomDuration timeToLive,
                    @JsonProperty("destroyOnTabClose") final boolean destroyOnTabClose,
                    @JsonProperty("destroyOnWindowClose") final boolean destroyOnWindowClose) {
        this.timeToIdle = timeToIdle;
        this.timeToLive = timeToLive;
        this.destroyOnTabClose = destroyOnTabClose;
        this.destroyOnWindowClose = destroyOnWindowClose;
    }

    public StroomDuration getTimeToIdle() {
        return timeToIdle;
    }

    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    public boolean isDestroyOnTabClose() {
        return destroyOnTabClose;
    }

    public boolean isDestroyOnWindowClose() {
        return destroyOnWindowClose;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Lifespan lifespan = (Lifespan) o;
        return destroyOnTabClose == lifespan.destroyOnTabClose &&
               destroyOnWindowClose == lifespan.destroyOnWindowClose &&
               Objects.equals(timeToIdle, lifespan.timeToIdle) &&
               Objects.equals(timeToLive, lifespan.timeToLive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeToIdle, timeToLive, destroyOnTabClose, destroyOnWindowClose);
    }

    @Override
    public String toString() {
        return "Lifespan{" +
               "timeToIdle=" + timeToIdle +
               ", timeToLive=" + timeToLive +
               ", destroyOnTabClose=" + destroyOnTabClose +
               ", destroyOnWindowClose=" + destroyOnWindowClose +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private StroomDuration timeToIdle;
        private StroomDuration timeToLive;
        private boolean destroyOnTabClose;
        private boolean destroyOnWindowClose;

        public Builder() {
        }

        public Builder(final Lifespan lifespan) {
            this.timeToIdle = lifespan.timeToIdle;
            this.timeToLive = lifespan.timeToLive;
            this.destroyOnTabClose = lifespan.destroyOnTabClose;
            this.destroyOnWindowClose = lifespan.destroyOnWindowClose;
        }

        public Builder timeToIdle(final StroomDuration timeToIdle) {
            this.timeToIdle = timeToIdle;
            return this;
        }

        public Builder timeToLive(final StroomDuration timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }

        public Builder destroyOnTabClose(final boolean destroyOnTabClose) {
            this.destroyOnTabClose = destroyOnTabClose;
            return this;
        }

        public Builder destroyOnWindowClose(final boolean destroyOnWindowClose) {
            this.destroyOnWindowClose = destroyOnWindowClose;
            return this;
        }

        public Lifespan build() {
            return new Lifespan(
                    timeToIdle,
                    timeToLive,
                    destroyOnTabClose,
                    destroyOnWindowClose);
        }
    }
}
