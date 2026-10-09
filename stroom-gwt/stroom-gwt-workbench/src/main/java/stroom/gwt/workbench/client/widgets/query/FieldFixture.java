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
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// A fake of Stroom's data source field search (`POST /dataSource/v1/findFields`), answering a
/// `DynamicFieldSelectionListModel`'s requests from a fixed list of fields, as a data source's field
/// list (`findFields`) would answer them.
///
/// Plain Java, so it is unit tested on the JVM.
public final class FieldFixture {

    /// The field search's path, relative to the REST root.
    public static final String FIND_FIELDS = "/dataSource/v1/findFields";

    // Stroom's DataSourceClient.findFieldByName asks for `==` and the quoted name
    private static final String EQUALS_CASE_SENSITIVE = "==";

    private final List<QueryField> fields;

    /// @param fields The data source's fields.
    public FieldFixture(final List<QueryField> fields) {
        this.fields = fields;
    }

    /// Describes a request, for the stories' `fieldSource` spy to record.
    ///
    /// @param requestJson The request's body, a `FindFieldCriteria`.
    /// @return `findFieldByName:<name>` for a request for one field by name, else `loadFields`.
    public static String describe(final String requestJson) {
        final String name = nameOf(filterOf(requestJson));
        return name == null
                ? "loadFields"
                : "findFieldByName:" + name;
    }

    /// Answers a `findFields` request.
    ///
    /// @param requestJson The request's body, a `FindFieldCriteria`.
    /// @return The reply's body, a `ResultPage` of the fields whose names match the request's filter
    /// (a case-insensitive contains, or an exact name for `==` and a quoted name).
    public String find(final String requestJson) {
        final String filter = filterOf(requestJson);
        final String name = nameOf(filter);
        final List<QueryField> matches = new ArrayList<>();
        for (final QueryField field : fields) {
            final boolean matched;
            if (name != null) {
                matched = field.getFldName().equals(name);
            } else {
                matched = filter == null || field.getFldName().toLowerCase(Locale.ROOT)
                        .contains(filter.toLowerCase(Locale.ROOT));
            }
            if (matched) {
                matches.add(field);
            }
        }

        final StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            final QueryField field = matches.get(i);
            sb.append("{\"fldName\":").append(quote(field.getFldName()))
                    .append(",\"fldType\":").append(quote(field.getFldType().name()))
                    .append(",\"conditionSet\":").append(quote(field.getConditionSet().name()));
            if (field.getDocRefType() != null) {
                sb.append(",\"docRefType\":").append(quote(field.getDocRefType()));
            }
            sb.append(",\"queryable\":").append(field.queryable()).append('}');
        }
        sb.append("],\"pageResponse\":{\"offset\":0,\"length\":").append(matches.size())
                .append(",\"total\":").append(matches.size())
                .append(",\"exact\":true}}");
        return sb.toString();
    }

    private static String filterOf(final String requestJson) {
        final Object request = JsonValues.parse(requestJson);
        if (request instanceof Map) {
            final Object filter = ((Map<?, ?>) request).get("filter");
            if (filter != null && !filter.toString().trim().isEmpty()) {
                return filter.toString().trim();
            }
        }
        return null;
    }

    /// @return The name in an `=="name"` filter, or null for any other filter.
    private static String nameOf(final String filter) {
        if (filter != null
            && filter.startsWith(EQUALS_CASE_SENSITIVE + "\"")
            && filter.endsWith("\"")
            && filter.length() > EQUALS_CASE_SENSITIVE.length() + 1) {
            return filter.substring(EQUALS_CASE_SENSITIVE.length() + 1, filter.length() - 1);
        }
        return null;
    }

    private static String quote(final String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
