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

package stroom.dashboard.client.vis;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/// Keeps only what Stroom expects of a visualisation's render report (see `stroom.report` in `vis.js`), as any
/// script in the frame can send one: its status, counts, sizes, the library, the reasons rows were left out (from
/// a fixed set) and short messages. Reports reach the chat's agent, so nothing else a script sends, such as data
/// values, gets through.
///
/// The filtering ([#sanitise(Map)]) works on plain Java values, so it can be tested on the JVM; [#sanitise(JSONObject)]
/// only converts to and from the browser's JSON.
public final class VisRenderReports {

    static final int MAX_TEXT = 300;
    static final int MAX_SHORT_TEXT = 40;
    static final int MAX_WARNINGS = 5;

    private static final Set<String> STATUSES = Set.of("ok", "error", "empty", "nothing-drawn");
    private static final List<String> NUMBERS = List.of("rows", "series", "points", "nodes", "links", "width",
            "height", "svgCount", "canvasCount", "elementCount");
    private static final List<String> TEXTS = List.of("source", "adapter", "adapterVersion", "library",
            "libraryVersion", "kind");
    // The reasons the ECharts adapter gives for rows it leaves out.
    private static final Set<String> DROPPED_REASONS = Set.of("value not a number", "value negative",
            "x not a number or date", "y not a number", "x or y not a number or date", "source or target empty",
            "source and target the same", "weight not a number or negative");

    private VisRenderReports() {
        // Static only.
    }

    /// Filters a report as a visualisation sent it in the browser.
    ///
    /// @param report The report.
    /// @return The report with only the fields Stroom expects, as JSON, or null if it has no known status.
    public static String sanitise(final JSONObject report) {
        final Map<String, Object> clean = sanitise(toMap(report));
        return clean == null
                ? null
                : toJson(clean).toString();
    }

    /// Filters a report.
    ///
    /// @param report The report as plain Java: maps, lists, strings, numbers and booleans.
    /// @return The report with only the fields Stroom expects, or null if it has no known status. Numbers are
    /// whole numbers (as longs); texts are on one line and cut short.
    public static Map<String, Object> sanitise(final Map<String, Object> report) {
        if (report == null) {
            return null;
        }
        final String status = text(report.get("status"), MAX_SHORT_TEXT);
        if (status == null || !STATUSES.contains(status)) {
            return null;
        }
        final Map<String, Object> clean = new LinkedHashMap<>();
        clean.put("status", status);
        for (final String name : TEXTS) {
            final String text = text(report.get(name), MAX_SHORT_TEXT);
            if (text != null) {
                clean.put(name, text);
            }
        }
        for (final String name : NUMBERS) {
            if (report.get(name) instanceof final Number number) {
                clean.put(name, Math.round(number.doubleValue()));
            }
        }
        if (report.get("droppedRows") instanceof final Map<?, ?> dropped) {
            final Map<String, Object> cleanDropped = new LinkedHashMap<>();
            for (final Entry<?, ?> entry : dropped.entrySet()) {
                if (DROPPED_REASONS.contains(String.valueOf(entry.getKey()))
                    && entry.getValue() instanceof final Number count) {
                    cleanDropped.put(String.valueOf(entry.getKey()), Math.round(count.doubleValue()));
                }
            }
            clean.put("droppedRows", cleanDropped);
        }
        if (report.get("warnings") instanceof final List<?> warnings) {
            final List<Object> cleanWarnings = new ArrayList<>();
            for (final Object warning : warnings) {
                final String text = text(warning, MAX_TEXT);
                if (text != null && cleanWarnings.size() < MAX_WARNINGS) {
                    cleanWarnings.add(text);
                }
            }
            clean.put("warnings", cleanWarnings);
        }
        final String error = text(report.get("error"), MAX_TEXT);
        if (error != null) {
            clean.put("error", error);
        }
        return clean;
    }

    // A string value on one line, cut short; null if it isn't a string.
    private static String text(final Object value, final int max) {
        if (!(value instanceof final String string)) {
            return null;
        }
        final String text = string.replaceAll("[\\r\\n\\t]+", " ").trim();
        return text.length() <= max
                ? text
                : text.substring(0, max) + "...";
    }

    // The browser's JSON as plain Java.
    private static Map<String, Object> toMap(final JSONObject object) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final String key : object.keySet()) {
            map.put(key, toJava(object.get(key)));
        }
        return map;
    }

    private static Object toJava(final JSONValue value) {
        if (value == null || value.isNull() != null) {
            return null;
        }
        if (value.isObject() != null) {
            return toMap(value.isObject());
        }
        if (value.isArray() != null) {
            final JSONArray array = value.isArray();
            final List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                list.add(toJava(array.get(i)));
            }
            return list;
        }
        if (value.isString() != null) {
            return value.isString().stringValue();
        }
        if (value.isNumber() != null) {
            return value.isNumber().doubleValue();
        }
        if (value.isBoolean() != null) {
            return value.isBoolean().booleanValue();
        }
        return null;
    }

    // Plain Java as the browser's JSON.
    private static JSONValue toJson(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final JSONObject object = new JSONObject();
            map.forEach((key, entry) -> object.put(String.valueOf(key), toJson(entry)));
            return object;
        }
        if (value instanceof final List<?> list) {
            final JSONArray array = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                array.set(i, toJson(list.get(i)));
            }
            return array;
        }
        if (value instanceof final Number number) {
            return new JSONNumber(number.doubleValue());
        }
        if (value instanceof final Boolean bool) {
            return JSONBoolean.getInstance(bool);
        }
        return new JSONString(String.valueOf(value));
    }
}
