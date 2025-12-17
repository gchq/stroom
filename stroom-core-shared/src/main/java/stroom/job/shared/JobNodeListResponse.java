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

package stroom.job.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobNodeListResponse extends ResultPage<JobNode> {

    public JobNodeListResponse(final List<JobNode> values) {
        super(values);
    }

    @JsonCreator
    public JobNodeListResponse(@JsonProperty("values") final List<JobNode> values,
                               @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    /**
     * Used for full queries (not bounded).
     */
    public static JobNodeListResponse createUnboundedJobNodeResponse(final List<JobNode> realList) {
        if (realList != null) {
            return new JobNodeListResponse(realList, createPageResponse(realList));
        } else {
            return new JobNodeListResponse(Collections.emptyList());
        }
    }

}
