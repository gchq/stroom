/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A page of traces, optionally carrying the histogram of the window the page was drawn from. The
 * histogram travels with the page so that both come from one read of each archive bucket rather than
 * from two requests that could see different copies; it is null on a page-only query.
 */
@JsonInclude(Include.NON_NULL)
public class TracesResultPage extends ResultPage<TraceRoot> {

    @JsonProperty
    private final TraceHistogram histogram;

    public TracesResultPage(final List<TraceRoot> values,
                            final PageResponse pageResponse) {
        this(values, pageResponse, null);
    }

    @JsonCreator
    public TracesResultPage(@JsonProperty("values") final List<TraceRoot> values,
                            @JsonProperty("pageResponse") final PageResponse pageResponse,
                            @JsonProperty("histogram") final TraceHistogram histogram) {
        super(values, pageResponse);
        this.histogram = histogram;
    }

    public TraceHistogram getHistogram() {
        return histogram;
    }
}
