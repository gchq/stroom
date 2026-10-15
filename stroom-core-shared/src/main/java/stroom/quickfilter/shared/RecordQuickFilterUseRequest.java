/*
 * Copyright 2026 Crown Copyright
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

package stroom.quickfilter.shared;

import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class RecordQuickFilterUseRequest {

    @JsonProperty
    private final QuickFilterHistoryKey key;
    @JsonProperty
    private final String filterText;

    @JsonCreator
    public RecordQuickFilterUseRequest(@JsonProperty("key") final QuickFilterHistoryKey key,
                                       @JsonProperty("filterText") final String filterText) {
        this.key = Objects.requireNonNull(key);
        this.filterText = Objects.requireNonNull(filterText);
    }

    @SerialisationTestConstructor
    private RecordQuickFilterUseRequest() {
        this(new QuickFilterHistoryKey("test", null), "");
    }

    public QuickFilterHistoryKey getKey() {
        return key;
    }

    public String getFilterText() {
        return filterText;
    }

    @Override
    public String toString() {
        return "RecordQuickFilterUseRequest{" +
               "key=" + key +
               ", filterText='" + filterText + '\'' +
               '}';
    }
}
