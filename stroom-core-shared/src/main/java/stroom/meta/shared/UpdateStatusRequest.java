/*
 * Copyright 2024 Crown Copyright
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

package stroom.meta.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UpdateStatusRequest {

    @JsonProperty
    private final FindMetaCriteria criteria;
    @JsonProperty
    private final Status currentStatus;
    @JsonProperty
    private final Status newStatus;

    @JsonCreator
    public UpdateStatusRequest(@JsonProperty("criteria") final FindMetaCriteria criteria,
                               @JsonProperty("currentStatus") final Status currentStatus,
                               @JsonProperty("newStatus") final Status newStatus) {
        this.criteria = criteria;
        this.currentStatus = currentStatus;
        this.newStatus = newStatus;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public Status getCurrentStatus() {
        return currentStatus;
    }

    public Status getNewStatus() {
        return newStatus;
    }

    @Override
    public String toString() {
        return "UpdateStatusRequest{" +
                "criteria=" + criteria +
                ", currentStatus=" + currentStatus +
                ", newStatus=" + newStatus +
                '}';
    }
}
