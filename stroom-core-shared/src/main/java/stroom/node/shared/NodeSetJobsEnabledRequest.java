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

package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class NodeSetJobsEnabledRequest {

    @JsonProperty
    private final boolean enabled;

    /*
    List of job names to include in this operation
     */
    @JsonProperty
    private final Set<String> includeJobs;

    /*
    List of job names to exclude from this operation
     */
    @JsonProperty
    private final Set<String> excludeJobs;

    @JsonCreator
    public NodeSetJobsEnabledRequest(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("includeJobs") final Set<String> includeJobs,
            @JsonProperty("excludeJobs") final Set<String> excludeJobs) {

        this.enabled = enabled;
        this.includeJobs = includeJobs != null ? includeJobs : Collections.emptySet();
        this.excludeJobs = excludeJobs != null ? excludeJobs : Collections.emptySet();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getIncludeJobs() {
        return includeJobs;
    }

    public Set<String> getExcludeJobs() {
        return excludeJobs;
    }
}
