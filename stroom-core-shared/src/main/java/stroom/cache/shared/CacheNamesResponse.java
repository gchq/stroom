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

package stroom.cache.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.util.shared.cache.CacheIdentity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class CacheNamesResponse extends ResultPage<CacheIdentity> {

    public CacheNamesResponse(final List<CacheIdentity> values) {
        super(values);
    }

    @JsonCreator
    public CacheNamesResponse(@JsonProperty("values") final List<CacheIdentity> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }
}
