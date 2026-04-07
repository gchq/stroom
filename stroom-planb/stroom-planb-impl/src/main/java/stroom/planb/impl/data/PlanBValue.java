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

package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = State.class, name = "state"),
        @JsonSubTypes.Type(value = TemporalState.class, name = "temporalState"),
        @JsonSubTypes.Type(value = RangeState.class, name = "rangeState"),
        @JsonSubTypes.Type(value = TemporalRangeState.class, name = "temporalRangeState"),
        @JsonSubTypes.Type(value = Session.class, name = "session"),
        @JsonSubTypes.Type(value = TemporalValue.class, name = "histogram"),
        @JsonSubTypes.Type(value = SpanKV.class, name = "trace")
})
public sealed interface PlanBValue permits
        State,
        TemporalState,
        RangeState,
        TemporalRangeState,
        Session,
        TemporalValue,
        SpanKV {

}
