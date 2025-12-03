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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class LifespanInfo {

    @JsonProperty
    private final String timeToIdle;
    @JsonProperty
    private final String timeToLive;
    @JsonProperty
    private final boolean destroyOnTabClose;
    @JsonProperty
    private final boolean destroyOnWindowClose;

    @JsonCreator
    public LifespanInfo(@JsonProperty("timeToIdle") final String timeToIdle,
                        @JsonProperty("timeToLive") final String timeToLive,
                        @JsonProperty("destroyOnTabClose") final boolean destroyOnTabClose,
                        @JsonProperty("destroyOnWindowClose") final boolean destroyOnWindowClose) {
        this.timeToIdle = timeToIdle;
        this.timeToLive = timeToLive;
        this.destroyOnTabClose = destroyOnTabClose;
        this.destroyOnWindowClose = destroyOnWindowClose;
    }

    public String getTimeToIdle() {
        return timeToIdle;
    }

    public String getTimeToLive() {
        return timeToLive;
    }

    public boolean isDestroyOnTabClose() {
        return destroyOnTabClose;
    }

    public boolean isDestroyOnWindowClose() {
        return destroyOnWindowClose;
    }
}
