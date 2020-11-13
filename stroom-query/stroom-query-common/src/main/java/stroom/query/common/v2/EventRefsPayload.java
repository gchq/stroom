/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class EventRefsPayload implements Payload {
    @JsonProperty
    private final int coprocessorId;
    @JsonProperty
    private final EventRefs eventRefs;

    @JsonCreator
    public EventRefsPayload(@JsonProperty("coprocessorId") final int coprocessorId,
                            @JsonProperty("eventRefs") final EventRefs eventRefs) {
        this.coprocessorId = coprocessorId;
        this.eventRefs = eventRefs;
    }

    @Override
    public int getCoprocessorId() {
        return coprocessorId;
    }

    public EventRefs getEventRefs() {
        return eventRefs;
    }
}
