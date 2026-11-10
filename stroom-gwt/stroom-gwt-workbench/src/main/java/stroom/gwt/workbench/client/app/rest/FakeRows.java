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

package stroom.gwt.workbench.client.app.rest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/// A list a fake server holds for a story, which pages, gains rows and loses them as the real one
/// does: a search's reply is the page its request's `pageRequest` asks for, a `ResultPage` of the
/// rows in the order of their sort keys. Make one per render, as stories change it.
///
/// ```java
/// final FakeRows accounts = new FakeRows();
/// accounts.put("1", "admin", ADMIN_JSON);
/// RestFixtures.builder()
///         .post("/account/v1/search", accounts::page)
///         .delete("/account/v1/1", accounts.removing("1", RestReply.json("true")))
/// ```
public final class FakeRows {

    private static final int DEFAULT_PAGE_LENGTH = 100;

    private final List<Row> rows = new ArrayList<>();

    /// Adds a row, or replaces the row with the same id.
    ///
    /// @param id      The row's id.
    /// @param sortKey Where it sorts among the rows.
    /// @param json    The row as the server sends it.
    /// @return This.
    public FakeRows put(final String id, final String sortKey, final String json) {
        remove(id);
        rows.add(new Row(id, sortKey, json));
        rows.sort(Comparator.comparing(Row::sortKey));
        return this;
    }

    /// @param id A row's id.
    /// @return Whether a row was removed.
    public boolean remove(final String id) {
        return rows.removeIf(row -> row.id().equals(id));
    }

    /// @return How many rows there are.
    public int size() {
        return rows.size();
    }

    /// The page a search asks for: its body's `pageRequest` (offset 0 and length 100 without
    /// one).
    ///
    /// @param request The search request.
    /// @return The page, as a `ResultPage`.
    public RestReply page(final RecordedRequest request) {
        int offset = 0;
        int length = DEFAULT_PAGE_LENGTH;
        final Object body = request.getBody() == null
                ? null
                : JsonValues.parse(request.getBody());
        if (body instanceof final Map<?, ?> criteria
            && criteria.get("pageRequest") instanceof final Map<?, ?> pageRequest) {
            offset = number(pageRequest.get("offset"), offset);
            length = number(pageRequest.get("length"), length);
        }
        final List<String> values = new ArrayList<>();
        for (int i = offset; i < offset + length && i < rows.size(); i++) {
            values.add(rows.get(i).json());
        }
        return RestReply.json("{\"values\": [" + String.join(", ", values) + "], \"pageResponse\": {\"offset\": "
                              + offset + ", \"length\": " + values.size() + ", \"total\": " + rows.size()
                              + ", \"exact\": true}}");
    }

    /// A handler that removes a row, then replies, e.g. for a delete.
    ///
    /// @param id    The row's id.
    /// @param reply The reply.
    /// @return The handler.
    public RestHandler removing(final String id, final RestReply reply) {
        return request -> {
            remove(id);
            return reply;
        };
    }

    /// A handler that adds a row, then replies, e.g. for a create.
    ///
    /// @param id      The row's id.
    /// @param sortKey Where it sorts among the rows.
    /// @param json    The row as the server sends it.
    /// @param reply   The reply.
    /// @return The handler.
    public RestHandler adding(final String id, final String sortKey, final String json, final RestReply reply) {
        return request -> {
            put(id, sortKey, json);
            return reply;
        };
    }

    private static int number(final Object value, final int defaultValue) {
        return value instanceof final Number number
                ? number.intValue()
                : defaultValue;
    }

    private record Row(String id, String sortKey, String json) {

    }
}
