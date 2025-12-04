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

package stroom.meta.shared;

import stroom.security.shared.DocumentPermission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SelectionSummaryRequest {

    @JsonProperty
    private final FindMetaCriteria findMetaCriteria;
    @JsonProperty
    private final DocumentPermission requiredPermission;

    @JsonCreator
    public SelectionSummaryRequest(@JsonProperty("findMetaCriteria") final FindMetaCriteria findMetaCriteria,
                                   @JsonProperty("requiredPermission") final DocumentPermission requiredPermission) {
        this.findMetaCriteria = findMetaCriteria;
        this.requiredPermission = requiredPermission;
    }

    public FindMetaCriteria getFindMetaCriteria() {
        return findMetaCriteria;
    }

    public DocumentPermission getRequiredPermission() {
        return requiredPermission;
    }

    @Override
    public String toString() {
        return "SelectionSummaryRequest{" +
                "findMetaCriteria=" + findMetaCriteria +
                ", requiredPermission='" + requiredPermission + '\'' +
                '}';
    }
}
