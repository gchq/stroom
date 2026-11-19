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

package stroom.gwt.workbench.client.widgets.query;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// A fake of Stroom's most common values of a field (`POST /suggest/v1/fieldValues`), answering
/// the value picker's requests from a fixed list of values, as a data source would: the values
/// containing the request's filter, ignoring case, most common first, counted across all time.
///
/// Plain Java, so it is unit tested on the JVM.
public final class FieldValuesFixture {

    /// The field values' path, relative to the REST root.
    public static final String FIELD_VALUES = "/suggest/v1/fieldValues";

    private final Map<String, Long> counts;
    private final long totalCount;

    /// @param counts     How many records have each value, most common first.
    /// @param totalCount How many records the values were counted across.
    public FieldValuesFixture(final Map<String, Long> counts, final long totalCount) {
        this.counts = new LinkedHashMap<>(counts);
        this.totalCount = totalCount;
    }

    /// Answers a `fieldValues` request.
    ///
    /// @param requestJson The request's body, a `FetchFieldValuesRequest`.
    /// @return The reply's body, a `FieldValues` of the values containing the request's filter
    /// (all of them for none), counted across all time.
    public String find(final String requestJson) {
        final String filter = filterOf(requestJson);
        final List<Object> values = new ArrayList<>();
        counts.forEach((value, count) -> {
            if (filter == null || value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) {
                final Map<String, Object> valueCount = new LinkedHashMap<>();
                valueCount.put("value", value);
                valueCount.put("count", count);
                values.add(valueCount);
            }
        });
        final Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("values", values);
        reply.put("totalCount", totalCount);
        reply.put("allTime", true);
        return JsonValues.toJson(reply);
    }

    /// @param requestJson The request's body, a `FetchFieldValuesRequest`.
    /// @return The request's filter, or null for none.
    public static String filterOf(final String requestJson) {
        final Object request = JsonValues.parse(requestJson);
        if (request instanceof Map) {
            final Object filter = ((Map<?, ?>) request).get("filter");
            if (filter != null && !filter.toString().trim().isEmpty()) {
                return filter.toString().trim();
            }
        }
        return null;
    }
}
