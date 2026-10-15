/*
 * Copyright 2020 Crown Copyright
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

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FetchNodeStatusResponse extends ResultPage<NodeStatusResult> {

    public FetchNodeStatusResponse(final List<NodeStatusResult> values) {
        super(values);
    }

    public FetchNodeStatusResponse(final List<NodeStatusResult> values,
                                   final PageResponse pageResponse) {
        this(values, pageResponse, null);
    }

    @JsonCreator
    public FetchNodeStatusResponse(@JsonProperty("values") final List<NodeStatusResult> values,
                                   @JsonProperty("pageResponse") final PageResponse pageResponse,
                                   @JsonProperty("filterError") final TokenError filterError) {
        super(values, pageResponse, filterError);
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
