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
/// a fixed set), short messages, each series' trend (direction, slope and fit), a network's clusters, and what
/// the user is looking at (the interval, the part zoomed in on, what is selected and the series hidden, which are
/// cut short). Reports reach the chat's agent, so nothing else a script sends gets through.
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
            "libraryVersion", "kind", "interval");
    // What the user is looking at: the part of the chart zoomed in on, and what is selected, as the chart labels it.
    // These hold data values (e.g. the names of what is selected), shown to the chat's agent so it can talk about
    // what the user sees; kept short, as any script in the frame can send them.
    private static final List<String> VIEW_TEXTS = List.of("shown", "selection");
    // The series hidden from the legend, by name.
    static final int MAX_HIDDEN_SERIES = 10;
    // Each series' trend (Trend Line): its series' name, how it is drawn, which way it goes, its slope as text and
    // its R² (from 0 to 1).
    static final int MAX_TRENDS = 10;
    private static final Set<String> TREND_METHODS = Set.of("linear", "moving average");
    private static final Set<String> TREND_DIRECTIONS = Set.of("rising", "falling", "flat");
    // A network's clusters (Node Colours cluster): how many, how clear-cut (modularity), the largest (named after
    // their most connected member, with their size, and the group most of each is in) and the bridges between them.
    static final int MAX_CLUSTERS = 10;
    static final int MAX_BRIDGES = 5;
    // Whether something is selected on the chart (never what is: that would be a data value), and whether its rows
    // reached its row limit, so the rest were left out.
    private static final List<String> BOOLEANS = List.of("selected", "truncated");
    // The reasons the ECharts adapter gives for rows it leaves out.
    private static final Set<String> DROPPED_REASONS = Set.of("value not a number", "value negative",
            "x not a number or date", "y not a number", "x or y not a number or date", "source or target empty",
            "source and target the same", "weight not a number or negative", "date not a date",
            "flow would close a loop");

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
        for (final String name : BOOLEANS) {
            if (report.get(name) instanceof final Boolean value) {
                clean.put(name, value);
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
        for (final String name : VIEW_TEXTS) {
            final String text = text(report.get(name), MAX_TEXT);
            if (text != null) {
                clean.put(name, text);
            }
        }
        if (report.get("hiddenSeries") instanceof final List<?> hidden) {
            final List<Object> cleanHidden = new ArrayList<>();
            for (final Object name : hidden) {
                final String text = text(name, MAX_SHORT_TEXT);
                if (text != null && cleanHidden.size() < MAX_HIDDEN_SERIES) {
                    cleanHidden.add(text);
                }
            }
            if (!cleanHidden.isEmpty()) {
                clean.put("hiddenSeries", cleanHidden);
            }
        }
        if (report.get("trends") instanceof final List<?> trends) {
            final List<Object> cleanTrends = new ArrayList<>();
            for (final Object trend : trends) {
                if (trend instanceof final Map<?, ?> map && cleanTrends.size() < MAX_TRENDS) {
                    final Map<String, Object> cleanTrend = trend(map);
                    if (cleanTrend != null) {
                        cleanTrends.add(cleanTrend);
                    }
                }
            }
            clean.put("trends", cleanTrends);
        }
        if (report.get("clusters") instanceof final Map<?, ?> clusters) {
            clean.put("clusters", clusters(clusters));
        }
        final String error = text(report.get("error"), MAX_TEXT);
        if (error != null) {
            clean.put("error", error);
        }
        return clean;
    }

    // A series' trend, only what is expected kept; null without a known method and direction.
    private static Map<String, Object> trend(final Map<?, ?> trend) {
        final String method = text(trend.get("method"), MAX_SHORT_TEXT);
        final String direction = text(trend.get("direction"), MAX_SHORT_TEXT);
        if (!TREND_METHODS.contains(method) || !TREND_DIRECTIONS.contains(direction)) {
            return null;
        }
        final Map<String, Object> clean = new LinkedHashMap<>();
        final String series = text(trend.get("series"), MAX_SHORT_TEXT);
        if (series != null) {
            clean.put("series", series);
        }
        clean.put("method", method);
        clean.put("direction", direction);
        final String slope = text(trend.get("slope"), MAX_SHORT_TEXT);
        if (slope != null) {
            clean.put("slope", slope);
        }
        if (trend.get("r2") instanceof final Number r2 && r2.doubleValue() >= 0 && r2.doubleValue() <= 1) {
            clean.put("r2", Math.round(r2.doubleValue() * 100) / 100.0);
        }
        return clean;
    }

    // A network's clusters, only what is expected kept.
    private static Map<String, Object> clusters(final Map<?, ?> clusters) {
        final Map<String, Object> clean = new LinkedHashMap<>();
        if (clusters.get("count") instanceof final Number count) {
            clean.put("count", Math.round(count.doubleValue()));
        }
        if (clusters.get("modularity") instanceof final Number modularity
            && modularity.doubleValue() >= -1 && modularity.doubleValue() <= 1) {
            clean.put("modularity", Math.round(modularity.doubleValue() * 100) / 100.0);
        }
        final List<Object> top = new ArrayList<>();
        if (clusters.get("top") instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> cluster && top.size() < MAX_CLUSTERS) {
                    final Map<String, Object> cleanCluster = new LinkedHashMap<>();
                    putText(cleanCluster, "name", cluster.get("name"));
                    putWhole(cleanCluster, "size", cluster.get("size"));
                    putText(cleanCluster, "group", cluster.get("group"));
                    putWhole(cleanCluster, "groupShare", cluster.get("groupShare"));
                    top.add(cleanCluster);
                }
            }
        }
        clean.put("top", top);
        final List<Object> bridges = new ArrayList<>();
        if (clusters.get("bridges") instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> bridge && bridges.size() < MAX_BRIDGES) {
                    final Map<String, Object> cleanBridge = new LinkedHashMap<>();
                    putText(cleanBridge, "name", bridge.get("name"));
                    putWhole(cleanBridge, "otherClusters", bridge.get("otherClusters"));
                    bridges.add(cleanBridge);
                }
            }
        }
        clean.put("bridges", bridges);
        return clean;
    }

    private static void putText(final Map<String, Object> map, final String name, final Object value) {
        final String text = text(value, MAX_SHORT_TEXT);
        if (text != null) {
            map.put(name, text);
        }
    }

    private static void putWhole(final Map<String, Object> map, final String name, final Object value) {
        if (value instanceof final Number number) {
            map.put(name, Math.round(number.doubleValue()));
        }
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
