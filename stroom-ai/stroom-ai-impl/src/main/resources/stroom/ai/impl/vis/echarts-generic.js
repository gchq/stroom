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

// ECharts Generic - Stroom's adapter that draws Stroom AI generated visualisations with Apache ECharts.
// stroom-echarts-generic-version: 1
//
// The visualisation's settings say which chart kind to draw and which table columns feed it, so no
// code is ever generated: the AI only chooses a kind, maps columns and sets options.
//
// Settings (all strings, as Stroom passes them):
//   kind     - bar | line | pie | scatter | graph
//   fields   - comma separated ids of the field controls, in the order of the values in each data row
//   options  - optional JSON object of ECharts option overrides; keys that can carry HTML, code or URLs
//              are removed before use
//   plus the field controls themselves (their values are column names) and the style controls below.
//
// After each redraw it sends Stroom a render report (counts and its own messages, never data values).

if (!window.visualisations) {
    window.visualisations = {};
}

(function () {
    "use strict";

    var ADAPTER_VERSION = "1";
    // The oldest ECharts major version the adapter draws with.
    var MIN_ECHARTS_MAJOR = 6;
    var KINDS = ["bar", "line", "pie", "scatter", "graph", "heatmap", "calendar", "histogram", "sankey", "chord",
        "gauge", "sunburst"];
    // A gauge's bands: below the first band's start, then the first band, then above the second.
    var GAUGE_BAND_COLOURS = ["#3ba272", "#fac858", "#ee6666"];
    // The space kept clear at the chart's right edge. Stroom's refresh button and the chart's controls are in the
    // toolbar above the chart, so this is only a margin.
    var STROOM_BUTTON_SPACE = 16;
    // A scatter plot with more points than this is drawn in ECharts' large mode, a batch at a time.
    var LARGE_THRESHOLD = 2000;
    // A selected item's border, and the solid line drawn either side of a selected link (half as thick).
    var SELECTED_BORDER = 3;
    var LINK_OUTLINE = SELECTED_BORDER / 2;
    var PROGRESSIVE_CHUNK = 5000;

    // Keys removed from option overrides anywhere they appear: they can carry HTML, code or URLs.
    var FORBIDDEN_OPTION_KEYS = {
        formatter: true,
        rich: true,
        link: true,
        sublink: true,
        image: true,
        graphic: true,
        renderItem: true,
        extraCssText: true,
        url: true,
        // Keys that would change what the chart shows rather than how.
        data: true,
        dataset: true,
        encode: true,
        type: true,
        series: true
    };

    // The only top-level keys an option override may set.
    // No title: a dashboard shows the vis component's name above the chart, which is its title.
    var ALLOWED_TOP_LEVEL_KEYS = {
        legend: true,
        grid: true,
        xAxis: true,
        yAxis: true,
        tooltip: true,
        color: true,
        textStyle: true,
        animation: true,
        animationDuration: true,
        dataZoom: true,
        seriesDefaults: true
    };

    var DEFAULT_TIME_PATTERN = "YYYY-MM-DD[T]HH:mm:ss.SSS[Z]";
    var DEFAULT_COMPACT_TIME_PATTERN = "YYYY-MM-DD[T]HH:mm";
    var MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December"];
    var DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

    // ------------------------------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------------------------------

    function isBlank(str) {
        return str === undefined || str === null || String(str).trim().length === 0;
    }

    function intSetting(settings, id, defaultValue, min, max) {
        var value = parseInt(settings[id], 10);
        if (isNaN(value)) {
            value = defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    function boolSetting(settings, id, defaultValue) {
        var value = settings[id];
        if (value === undefined || value === null || value === "" || String(value).toLowerCase() === "auto") {
            return defaultValue;
        }
        return String(value).toLowerCase() === "true";
    }

    function textSetting(settings, id) {
        var value = settings[id];
        return isBlank(value) ? null : String(value);
    }

    // A number from a cell, or NaN. Stroom sends numbers and dates as numbers and other values as strings.
    function toNumber(value) {
        if (typeof value === "number") {
            return isFinite(value) ? value : NaN;
        }
        if (typeof value === "string" && /^\s*[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?\s*$/.test(value)) {
            return parseFloat(value);
        }
        return NaN;
    }

    function pad(value, length) {
        var str = String(Math.abs(value));
        while (str.length < length) {
            str = "0" + str;
        }
        return str;
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function formatNumber(value) {
        if (typeof value !== "number" || !isFinite(value)) {
            return String(value);
        }
        if (Math.abs(value) >= 1000 || Number.isInteger(value)) {
            return value.toLocaleString(undefined, {maximumFractionDigits: 2});
        }
        return value.toLocaleString(undefined, {maximumSignificantDigits: 4});
    }

    function addDropped(report, reason, count) {
        if (count > 0) {
            report.droppedRows[reason] = (report.droppedRows[reason] || 0) + count;
        }
    }

    // Scales value from [min, max] to [lo, hi] by square root, so areas grow with the value.
    function scaleSqrt(value, min, max, lo, hi) {
        if (!(max > min)) {
            return (lo + hi) / 2;
        }
        var ratio = (Math.sqrt(Math.max(value, min)) - Math.sqrt(min)) / (Math.sqrt(max) - Math.sqrt(min));
        return lo + ratio * (hi - lo);
    }

    // ------------------------------------------------------------------------------------------
    // Time formatting, following the user's Stroom preferences
    // ------------------------------------------------------------------------------------------

    // The fields of an instant in the user's time zone, plus its offset from UTC in minutes.
    function timeFields(ms, timeZone) {
        var use = timeZone && timeZone.use ? timeZone.use : "UTC";
        var date;

        if (use === "Local") {
            date = new Date(ms);
            return {
                year: date.getFullYear(), month: date.getMonth(), day: date.getDate(),
                weekday: date.getDay(), hour: date.getHours(), minute: date.getMinutes(),
                second: date.getSeconds(), milli: date.getMilliseconds(),
                offset: -date.getTimezoneOffset(), zoneName: null
            };
        }

        if (use === "Id" && timeZone.id) {
            try {
                var parts = new Intl.DateTimeFormat("en-GB", {
                    timeZone: timeZone.id, hourCycle: "h23", year: "numeric", month: "2-digit",
                    day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit"
                }).formatToParts(new Date(ms));
                var map = {};
                parts.forEach(function (part) {
                    map[part.type] = part.value;
                });
                var milli = ((ms % 1000) + 1000) % 1000;
                var asUtc = Date.UTC(+map.year, +map.month - 1, +map.day, +map.hour % 24, +map.minute,
                    +map.second, milli);
                var offset = Math.round((asUtc - ms) / 60000);
                date = new Date(asUtc);
                return {
                    year: date.getUTCFullYear(), month: date.getUTCMonth(), day: date.getUTCDate(),
                    weekday: date.getUTCDay(), hour: date.getUTCHours(), minute: date.getUTCMinutes(),
                    second: date.getUTCSeconds(), milli: milli, offset: offset, zoneName: timeZone.id
                };
            } catch (e) {
                // An unknown zone id falls back to UTC below.
            }
        }

        var offsetMinutes = use === "Offset" ? (timeZone.offsetMinutes || 0) : 0;
        date = new Date(ms + offsetMinutes * 60000);
        return {
            year: date.getUTCFullYear(), month: date.getUTCMonth(), day: date.getUTCDate(),
            weekday: date.getUTCDay(), hour: date.getUTCHours(), minute: date.getUTCMinutes(),
            second: date.getUTCSeconds(), milli: date.getUTCMilliseconds(), offset: offsetMinutes,
            zoneName: use === "Offset" ? null : "UTC"
        };
    }

    function offsetText(offset, separator) {
        var sign = offset < 0 ? "-" : "+";
        var abs = Math.abs(offset);
        return sign + pad(Math.floor(abs / 60), 2) + separator + pad(abs % 60, 2);
    }

    var TIME_TOKEN = /\[[^\]]*]|YYYY|YY|MMMM|MMM|MM|M|DD|D|dddd|ddd|dd|d|HH|H|hh|h|mm|m|ss|s|S{1,9}|A|a|ZZ|Z|zz|z|./g;

    // Formats epoch milliseconds with a Moment.js style pattern (as Stroom's UI uses) in the user's zone.
    function formatTime(ms, pattern, timeZone) {
        if (typeof ms !== "number" || !isFinite(ms)) {
            return String(ms);
        }
        var f = timeFields(ms, timeZone);
        var hour12 = f.hour % 12 === 0 ? 12 : f.hour % 12;
        return String(pattern || DEFAULT_TIME_PATTERN).replace(TIME_TOKEN, function (token) {
            switch (token) {
                case "YYYY": return pad(f.year, 4);
                case "YY": return pad(f.year % 100, 2);
                case "MMMM": return MONTHS[f.month];
                case "MMM": return MONTHS[f.month].substring(0, 3);
                case "MM": return pad(f.month + 1, 2);
                case "M": return String(f.month + 1);
                case "DD": return pad(f.day, 2);
                case "D": return String(f.day);
                case "dddd": return DAYS[f.weekday];
                case "ddd": return DAYS[f.weekday].substring(0, 3);
                case "dd": return DAYS[f.weekday].substring(0, 2);
                case "d": return String(f.weekday);
                case "HH": return pad(f.hour, 2);
                case "H": return String(f.hour);
                case "hh": return pad(hour12, 2);
                case "h": return String(hour12);
                case "mm": return pad(f.minute, 2);
                case "m": return String(f.minute);
                case "ss": return pad(f.second, 2);
                case "s": return String(f.second);
                case "A": return f.hour < 12 ? "AM" : "PM";
                case "a": return f.hour < 12 ? "am" : "pm";
                case "ZZ": return offsetText(f.offset, "");
                case "Z": return offsetText(f.offset, ":");
                case "zz":
                case "z": return f.zoneName || offsetText(f.offset, ":");
                default:
                    if (token.charAt(0) === "[") {
                        return token.substring(1, token.length - 1);
                    }
                    if (token.charAt(0) === "S") {
                        return pad(f.milli, 3).substring(0, Math.min(3, token.length)) +
                            (token.length > 3 ? new Array(token.length - 2).join("0") : "");
                    }
                    return token;
            }
        });
    }

    // The user's preferences from the context Stroom passes, with defaults.
    function readPreferences(context, element) {
        var prefs = context && context.preferences ? context.preferences : {};
        var className = element && element.className ? String(element.className) : "";
        var theme = prefs.theme;
        if (!theme) {
            theme = className.indexOf("stroom-theme-light") >= 0 ? "light" : "dark";
        }
        return {
            theme: theme,
            timePattern: prefs.dateTimePattern || DEFAULT_TIME_PATTERN,
            compactTimePattern: prefs.compactDateTimePattern || DEFAULT_COMPACT_TIME_PATTERN,
            timeZone: prefs.timeZone || {use: "UTC"}
        };
    }

    // Colours and font from Stroom's own theme variables, so a chart matches the rest of the UI.
    function readThemeStyle(element, theme) {
        var dark = theme !== "light";
        var style = window.getComputedStyle ? window.getComputedStyle(element) : null;

        function cssVar(name, fallback) {
            var value = style ? style.getPropertyValue(name) : "";
            return isBlank(value) ? fallback : value.trim();
        }

        return {
            dark: dark,
            text: cssVar("--text-color", dark ? "#dce4e5" : "#1f2328"),
            subtleText: cssVar("--text-color--low-emphasis", dark ? "#9aa0a6" : "#5f6368"),
            border: cssVar("--border-color", dark ? "#353944" : "#d0d7de"),
            tooltipBackground: dark ? "rgba(32, 35, 42, 0.95)" : "rgba(255, 255, 255, 0.95)",
            background: cssVar("--vis__background-color", dark ? "#1e2128" : "#ffffff"),
            fontFamily: cssVar("--regular-font-family", "Roboto, sans-serif")
        };
    }

    // ------------------------------------------------------------------------------------------
    // Saving the chart: its image, and its data as CSV (Stroom's toolbar's Download)
    // ------------------------------------------------------------------------------------------

    // The background a saved image is drawn on: the chart's own, as the theme gives it.
    function backgroundOf(style) {
        return style && style.background ? style.background : (style && !style.dark ? "#ffffff" : "#1e2128");
    }

    // A library's major version, from its version text (e.g. 6 from "6.1.0"); 0 if it has none.
    function majorVersion(version) {
        return parseInt(String(version || "0").split(".")[0], 10) || 0;
    }

    // A field of a CSV file: quoted if it holds a comma, quote or line break, its quotes doubled.
    function csvField(value) {
        var text = value === null || value === undefined ? "" : String(value);
        return /[",\r\n]/.test(text) ? "\"" + text.replace(/"/g, "\"\"") + "\"" : text;
    }

    // The data a chart draws (after any Auto Interval grouping) as CSV: a column per field it was given, named as
    // the table's column, in the order the fields come; dates as ISO 8601 text in UTC.
    function dataCsv(settings, data) {
        var ids = String(settings.fields || "").split(",").map(function (id) {
            return id.trim();
        });
        var types = data && data.types ? data.types : [];
        var columns = [];
        ids.forEach(function (id, index) {
            if (id.length > 0 && !isBlank(settings[id])) {
                columns.push({index: index, name: String(settings[id]), date: types[index] === "DATE_TIME"});
            }
        });
        var lines = [columns.map(function (column) {
            return csvField(column.name);
        }).join(",")];
        dataRows(data).forEach(function (row) {
            lines.push(columns.map(function (column) {
                var value = row[column.index];
                if (column.date && typeof value === "number" && isFinite(value)) {
                    value = new Date(value).toISOString();
                }
                return csvField(value);
            }).join(","));
        });
        return lines.join("\r\n") + "\r\n";
    }

    // Saves a file in the browser: from a URL (e.g. an image's data URL), or text.
    function saveFile(name, url) {
        var link = window.document.createElement("a");
        link.href = url;
        link.download = name;
        link.style.display = "none";
        window.document.body.appendChild(link);
        link.click();
        window.document.body.removeChild(link);
    }

    // Puts what the user is looking at in a chart's report, for the chat's agent (as the dashboard's description
    // gives it): the interval Auto Interval groups by, the part zoomed in on, what is selected and the series hidden
    // from the legend; each left out when there is none. Stroom keeps these short.
    function setView(report, view) {
        ["interval", "shown", "selection", "hiddenSeries"].forEach(function (key) {
            delete report[key];
        });
        if (view.interval) {
            report.interval = view.interval;
        }
        if (view.shown) {
            report.shown = view.shown;
        }
        if (view.selection) {
            report.selection = view.selection;
        }
        if (view.hiddenSeries && view.hiddenSeries.length) {
            report.hiddenSeries = view.hiddenSeries.slice(0, 10);
        }
        return report;
    }

    // Saves text as a file, e.g. CSV.
    function saveText(name, text, type) {
        var blob = new window.Blob([text], {type: type});
        var url = window.URL.createObjectURL(blob);
        saveFile(name, url);
        setTimeout(function () {
            window.URL.revokeObjectURL(url);
        }, 1000);
    }

    // ------------------------------------------------------------------------------------------
    // Option overrides
    // ------------------------------------------------------------------------------------------

    function stripForbidden(value, warnings, path) {
        if (Array.isArray(value)) {
            return value.map(function (item, index) {
                return stripForbidden(item, warnings, path + "[" + index + "]");
            });
        }
        if (value && typeof value === "object") {
            var copy = {};
            Object.keys(value).forEach(function (key) {
                if (FORBIDDEN_OPTION_KEYS[key]) {
                    // Seen in testing: a legend made to scroll (as it already does), taken to be one that can't.
                    warnings.push("Ignored option '" + path + key + "'" + (path + key === "legend.type"
                        ? ": the legend already scrolls, a page at a time, when its entries don't fit" : ""));
                } else {
                    copy[key] = stripForbidden(value[key], warnings, path + key + ".");
                }
            });
            return copy;
        }
        if (typeof value === "string"
            && (/^\s*(image|javascript|data|https?):/i.test(value) || /[;<>{}]|url\s*\(|expression\s*\(/i.test(value))) {
            // URLs, markup and CSS beyond a single value (e.g. "red;background:url(...)") are never needed.
            warnings.push("Ignored a value of option '" + path.replace(/\.$/, "") + "'");
            return undefined;
        }
        return value;
    }

    // Parses and cleans the options setting. Returns {options, warnings}.
    function sanitiseOptions(json) {
        var warnings = [];
        if (isBlank(json)) {
            return {options: {}, warnings: warnings};
        }
        var parsed;
        try {
            parsed = JSON.parse(json);
        } catch (e) {
            warnings.push("Ignored options: not valid JSON");
            return {options: {}, warnings: warnings};
        }
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            warnings.push("Ignored options: not a JSON object");
            return {options: {}, warnings: warnings};
        }
        var options = {};
        Object.keys(parsed).forEach(function (key) {
            if (ALLOWED_TOP_LEVEL_KEYS[key]) {
                options[key] = stripForbidden(parsed[key], warnings, key + ".");
            } else {
                warnings.push("Ignored option '" + key + "'");
            }
        });
        return {options: options, warnings: warnings};
    }

    function isPlainObject(value) {
        return value !== null && typeof value === "object" && !Array.isArray(value);
    }

    // Deep merges source into target; arrays and other values replace.
    function merge(target, source) {
        Object.keys(source).forEach(function (key) {
            var value = source[key];
            if (value === undefined) {
                return;
            }
            if (isPlainObject(value) && isPlainObject(target[key])) {
                merge(target[key], value);
            } else if (isPlainObject(value) && Array.isArray(target[key])) {
                // An object for an axis list applies to every axis.
                target[key].forEach(function (item) {
                    if (isPlainObject(item)) {
                        merge(item, value);
                    }
                });
            } else {
                target[key] = value;
            }
        });
        return target;
    }

    // ------------------------------------------------------------------------------------------
    // Time buckets
    // ------------------------------------------------------------------------------------------

    var SECOND = 1000;
    var MINUTE = 60 * SECOND;
    var HOUR = 60 * MINUTE;
    var DAY = 24 * HOUR;
    // How finely a time can be aligned, coarsest last, with the width of each fixed-width step.
    var ALIGNMENTS = [
        {name: "second", ms: SECOND},
        {name: "minute", ms: MINUTE},
        {name: "hour", ms: HOUR},
        {name: "day", ms: DAY},
        {name: "month"},
        {name: "year"}
    ];

    // How many steps of ALIGNMENTS an instant is aligned to in the time zone: 0 for none (it has milliseconds),
    // up to 6 for the start of a year.
    function alignmentOf(ms, timeZone) {
        var t = timeFields(ms, timeZone);
        var checks = [t.milli === 0, t.second === 0, t.minute === 0, t.hour === 0, t.day === 1, t.month === 0];
        var level = 0;
        while (level < checks.length && checks[level]) {
            level++;
        }
        return level;
    }

    // The time bucket a column of dates stands for, from how its values are aligned in the user's time zone and
    // spaced: {unit: "month"} or {unit: "year"} for calendar months or years, {ms: width} for a fixed width (e.g.
    // a day, or a week of days apart), or null if they aren't aligned (e.g. raw event times), so stand for an
    // instant. A day is a fixed width, so a bucket over a daylight saving change is an hour out at its end.
    function timeBucket(values, timeZone) {
        var times = [];
        values.forEach(function (value) {
            if (typeof value === "number" && isFinite(value) && times.indexOf(value) < 0) {
                times.push(value);
            }
        });
        if (times.length === 0) {
            return null;
        }
        times.sort(function (a, b) {
            return a - b;
        });
        var level = ALIGNMENTS.length;
        times.forEach(function (time) {
            level = Math.min(level, alignmentOf(time, timeZone));
        });
        if (level === 0) {
            return null;
        }
        var gap = Infinity;
        for (var i = 1; i < times.length; i++) {
            gap = Math.min(gap, times[i] - times[i - 1]);
        }
        var alignment = ALIGNMENTS[level - 1];
        if (alignment.name === "year") {
            return {unit: gap >= 365 * DAY ? "year" : "month"};
        }
        if (alignment.name === "month") {
            return {unit: "month"};
        }
        return {ms: isFinite(gap) && gap > alignment.ms && gap % alignment.ms === 0 ? gap : alignment.ms};
    }

    // The start of the day an instant is in, in the time zone.
    function startOfDay(ms, timeZone) {
        var t = timeFields(ms, timeZone);
        return ms - t.hour * HOUR - t.minute * MINUTE - t.second * SECOND - t.milli;
    }

    // The end of the bucket starting at an instant (the start of the next), or the instant itself if it isn't
    // in a bucket.
    function bucketEnd(start, bucket, timeZone) {
        if (!bucket) {
            return start;
        }
        if (bucket.ms) {
            return start + bucket.ms;
        }
        var t = timeFields(start, timeZone);
        var year = bucket.unit === "year" ? t.year + 1 : t.year + (t.month === 11 ? 1 : 0);
        var month = bucket.unit === "year" ? 0 : (t.month + 1) % 12;
        // The next month's start as if in UTC, moved by the zone's offset there.
        var end = Date.UTC(year, month, 1) - t.offset * MINUTE;
        return end - (timeFields(end, timeZone).offset - t.offset) * MINUTE;
    }

    // The intervals Auto Interval chooses from, finest first: a fixed length, or a week, month or year.
    var AUTO_INTERVALS = [
        {ms: SECOND, label: "1 second"}, {ms: 5 * SECOND, label: "5 seconds"}, {ms: 10 * SECOND, label: "10 seconds"},
        {ms: 30 * SECOND, label: "30 seconds"}, {ms: MINUTE, label: "1 minute"}, {ms: 5 * MINUTE, label: "5 minutes"},
        {ms: 10 * MINUTE, label: "10 minutes"}, {ms: 15 * MINUTE, label: "15 minutes"},
        {ms: 30 * MINUTE, label: "30 minutes"}, {ms: HOUR, label: "1 hour"}, {ms: 3 * HOUR, label: "3 hours"},
        {ms: 6 * HOUR, label: "6 hours"}, {ms: 12 * HOUR, label: "12 hours"}, {ms: DAY, label: "1 day"},
        {ms: 7 * DAY, week: true, label: "1 week"}, {unit: "month", label: "1 month"},
        {unit: "year", label: "1 year"}
    ];
    // About how many intervals Auto Interval shows across the time shown: fewer bars than points on a line.
    var AUTO_INTERVAL_TARGETS = {bar: 60, line: 240};

    // About how long an interval (or a time bucket, as timeBucket gives it) is.
    function intervalLength(interval) {
        if (!interval) {
            return 0;
        }
        if (interval.unit === "year") {
            return 365 * DAY;
        }
        if (interval.unit === "month") {
            return 30 * DAY;
        }
        return interval.ms || 0;
    }

    // The start of the interval an instant is in, in the time zone: shorter than a day from the day's start (so
    // hours line up with midnight), a week from its Monday, a month or year from its first day.
    function intervalStart(ms, interval, timeZone) {
        if (interval.unit) {
            var t = timeFields(ms, timeZone);
            var first = Date.UTC(t.year, interval.unit === "year" ? 0 : t.month, 1) - t.offset * MINUTE;
            return first - (timeFields(first, timeZone).offset - t.offset) * MINUTE;
        }
        var day = startOfDay(ms, timeZone);
        if (interval.week) {
            var sinceMonday = (timeFields(day, timeZone).weekday + 6) % 7;
            // From the middle of the day, so a change of clocks between doesn't move it to another day.
            return startOfDay(day - sinceMonday * DAY + 12 * HOUR, timeZone);
        }
        if (interval.ms >= DAY) {
            return day;
        }
        return day + Math.floor((ms - day) / interval.ms) * interval.ms;
    }

    // The interval that suits a span of time: the finest giving at most about the target number across it, and
    // never finer than the data's own (its time bucket), as rows can't be split. Null if that is the data's own.
    function chooseInterval(span, target, finest) {
        var floor = intervalLength(finest);
        for (var i = 0; i < AUTO_INTERVALS.length; i++) {
            var interval = AUTO_INTERVALS[i];
            var length = intervalLength(interval);
            if (length < floor) {
                continue;
            }
            if (span / length <= target || i === AUTO_INTERVALS.length - 1) {
                return length === floor ? null : interval;
            }
        }
        return null;
    }

    // Auto Interval: a line chart's rows (or a bar chart's of dates) grouped into the interval that suits the time
    // shown (the part zoomed in on, a fraction from start to end), or the one chosen in Stroom's toolbar (by its
    // label; Auto or null for the one that suits), their values combined as Interval Combine says (Sum, Average, Min
    // or Max) for each time and series. Gives {data, interval, intervals, choice}: the data as grouped, the interval
    // used (null if not grouped: the setting is off, there is nothing to group, or it would be the data's own), the
    // intervals that can be chosen (Auto first; none if the setting is off), and the one chosen.
    function autoInterval(kind, data, settings, prefs, zoom, chosen) {
        var given = {data: data, interval: null, intervals: [], choice: null};
        if (!boolSetting(settings, "autoInterval", false) || (kind !== "line" && kind !== "bar")
            || (kind === "bar" && !isBlank(settings.sort))) {
            return given;
        }
        var timeZone = prefs ? prefs.timeZone : null;
        var fields = new Fields(settings, data, prefs);
        var timeId = kind === "line" ? "x" : "category";
        var valueId = kind === "line" ? "y" : "value";
        if (fields.type(timeId) !== "DATE_TIME" || !fields.has(valueId)) {
            return given;
        }
        var timeIndex = fields.index(timeId);
        var valueIndex = fields.index(valueId);
        var seriesIndex = fields.index("series");
        var rows = dataRows(data);
        var min = Infinity;
        var max = -Infinity;
        rows.forEach(function (row) {
            var time = row[timeIndex];
            if (typeof time === "number" && isFinite(time)) {
                min = Math.min(min, time);
                max = Math.max(max, time);
            }
        });
        var finest = fields.bucket(timeId);
        if (!isFinite(min)) {
            return given;
        }
        // Those no finer than the data's own, which rows can't be split into.
        var floor = intervalLength(finest);
        var choices = AUTO_INTERVALS.filter(function (interval) {
            return intervalLength(interval) >= floor;
        });
        given.intervals = ["Auto"].concat(choices.map(function (interval) {
            return interval.label;
        }));
        var fixed = choices.filter(function (interval) {
            return interval.label === chosen;
        })[0];
        given.choice = fixed ? fixed.label : "Auto";
        var interval;
        if (fixed) {
            interval = intervalLength(fixed) === floor ? null : fixed;
        } else {
            var shown = zoom ? Math.max(0.001, Math.min(1, zoom.end - zoom.start)) : 1;
            var span = Math.max(1, (max - min + intervalLength(finest)) * shown);
            interval = chooseInterval(span, AUTO_INTERVAL_TARGETS[kind], finest);
        }
        if (!interval) {
            return given;
        }
        var combine = textSetting(settings, "intervalCombine") || "Sum";
        var groups = {};
        var order = [];
        var kept = [];
        rows.forEach(function (row) {
            var time = row[timeIndex];
            var value = toNumber(row[valueIndex]);
            if (typeof time !== "number" || !isFinite(time) || isNaN(value)) {
                // Left as it is, for the chart to report.
                kept.push(row);
                return;
            }
            var start = intervalStart(time, interval, timeZone);
            var key = start + "\u0000" + (seriesIndex >= 0 ? String(row[seriesIndex]) : "");
            var group = groups[key];
            if (!group) {
                group = groups[key] = {row: row.slice(), sum: 0, count: 0, min: Infinity, max: -Infinity};
                group.row[timeIndex] = start;
                order.push(key);
            }
            group.sum += value;
            group.count++;
            group.min = Math.min(group.min, value);
            group.max = Math.max(group.max, value);
        });
        var values = order.map(function (key) {
            var group = groups[key];
            group.row[valueIndex] = combine === "Average" ? group.sum / group.count
                : combine === "Min" ? group.min
                    : combine === "Max" ? group.max
                        : group.sum;
            return group.row;
        }).concat(kept);
        return {data: merge(merge({}, data), {values: values}), interval: interval.label,
            intervals: given.intervals, choice: given.choice};
    }

    // ------------------------------------------------------------------------------------------
    // Reading the data
    // ------------------------------------------------------------------------------------------

    // Works out which value index each field control is at, from the 'fields' setting.
    function fieldIndexes(settings) {
        var indexes = {};
        String(settings.fields || "").split(",").forEach(function (id, index) {
            id = id.trim();
            if (id.length > 0) {
                indexes[id] = index;
            }
        });
        return indexes;
    }

    function Fields(settings, data, prefs) {
        var indexes = fieldIndexes(settings);
        var types = data && data.types ? data.types : [];
        var timeZone = prefs ? prefs.timeZone : null;

        this.has = function (id) {
            return indexes[id] !== undefined && !isBlank(settings[id]);
        };
        this.index = function (id) {
            return this.has(id) ? indexes[id] : -1;
        };
        this.type = function (id) {
            var index = this.index(id);
            return index >= 0 && types[index] ? types[index] : "GENERAL";
        };
        this.column = function (id) {
            return this.has(id) ? String(settings[id]) : null;
        };
        this.value = function (row, id) {
            var index = this.index(id);
            return index >= 0 ? row[index] : null;
        };

        // The values behind the labels drawn, by column id and label, for what a click selects.
        var raws = {};

        // A cell as a label, as labelOf gives it, remembering the value it came from.
        this.label = function (row, id, prefs) {
            var value = this.value(row, id);
            var label = labelOf(value, this.type(id), prefs);
            var forId = raws[id] || (raws[id] = {});
            if (!Object.prototype.hasOwnProperty.call(forId, label)) {
                forId[label] = value;
            }
            return label;
        };

        // The value a label was drawn from, or the label itself if it wasn't drawn from one.
        this.rawOf = function (id, label) {
            var forId = raws[id];
            return forId && Object.prototype.hasOwnProperty.call(forId, label) ? forId[label] : label;
        };

        // The time buckets of each date column, by column id, worked out when first needed.
        var buckets = {};

        // The time bucket a date column's values stand for (e.g. a day, for floorDay() values), or null if they
        // aren't bucketed (e.g. raw event times).
        this.bucket = function (id) {
            if (!Object.prototype.hasOwnProperty.call(buckets, id)) {
                var index = this.index(id);
                var values = index < 0 ? [] : dataRows(data).map(function (row) {
                    return row[index];
                });
                buckets[id] = timeBucket(values, timeZone);
            }
            return buckets[id];
        };

        // What a click selects, for Stroom: each column id given (e.g. category) with its value, and the same
        // value under the name of the table column it is mapped to, so a filter can name either. Dates are given
        // as ISO 8601 text, which Stroom's filters read as dates. A clicked date also gives the time it stands for
        // as from and to (e.g. the whole day, for a day's value), as a range dragged on a time axis does, so a
        // filter keeping a date field within from and to matches what the clicked point counts.
        this.selection = function (pairs) {
            var selection = {};
            var fields = this;
            pairs.forEach(function (pair) {
                var type = pair[0] === "node" ? fields.type("source") : fields.type(pair[0]);
                var value = selectionValue(pair[1], type);
                selection[pair[0]] = value;
                if (type === "DATE_TIME" && typeof pair[1] === "number" && isFinite(pair[1])
                    && selection.from === undefined) {
                    // A calendar's day is the whole day, whatever times the rows have in it.
                    var start = pair[0] === "date" ? startOfDay(pair[1], timeZone) : pair[1];
                    var end = bucketEnd(start, pair[0] === "date" ? {ms: DAY} : fields.bucket(pair[0]), timeZone);
                    selection.from = new Date(start).toISOString();
                    selection.to = new Date(end > start ? end - 1 : start).toISOString();
                }
            });
            pairs.forEach(function (pair) {
                var column = fields.column(pair[0]);
                if (column && selection[column] === undefined) {
                    selection[column] = selection[pair[0]];
                }
            });
            return selection;
        };
    }

    // The ids of the columns a click picks out, in the order the selection bar names them.
    var SELECTION_ROLES = ["category", "name", "series", "x", "y", "date"];

    // What a click selected, for the selection bar: a node, a link's two ends, or the values it picks out.
    function selectionLabel(selection) {
        if (selection.node !== undefined) {
            return String(selection.node);
        }
        if (selection.source !== undefined && selection.target !== undefined) {
            return selection.source + " → " + selection.target;
        }
        var values = [];
        SELECTION_ROLES.forEach(function (role) {
            if (selection[role] !== undefined && selection[role] !== "") {
                values.push(String(selection[role]));
            }
        });
        return values.length ? values.join(", ") : "an item";
    }

    // What a selection is outlined in: white on a dark theme, the theme's text colour on a light one (where white
    // wouldn't show).
    function selectionColour(style) {
        return style.dark ? "#ffffff" : style.text;
    }

    // A link's selection the other way round: its source and target (and the columns mapped to them) swapped.
    function reversed(selection) {
        var swapped = {};
        Object.keys(selection).forEach(function (key) {
            var value = selection[key];
            swapped[key] = value === selection.source
                ? selection.target
                : value === selection.target ? selection.source : value;
        });
        return swapped;
    }

    function selectionValue(value, type) {
        if (value === null || value === undefined) {
            return "";
        }
        if (type === "DATE_TIME" && typeof value === "number" && isFinite(value)) {
            return new Date(value).toISOString();
        }
        return String(value);
    }

    // The data rows. With no nesting Stroom sends the rows as the top level values.
    function dataRows(data) {
        if (!data || !Array.isArray(data.values)) {
            return [];
        }
        var rows = [];
        data.values.forEach(function (item) {
            if (Array.isArray(item)) {
                rows.push(item);
            } else if (item && Array.isArray(item.values)) {
                // Nested data: flatten the leaves.
                dataRows(item).forEach(function (row) {
                    rows.push(row);
                });
            }
        });
        return rows;
    }

    function labelOf(value, type, prefs) {
        if (value === null || value === undefined || value === "") {
            return "(none)";
        }
        if (type === "DATE_TIME" && typeof value === "number") {
            return formatTime(value, prefs.compactTimePattern, prefs.timeZone);
        }
        if (typeof value === "number") {
            return formatNumber(value);
        }
        return String(value);
    }

    function requireFields(fields, ids) {
        var missing = ids.filter(function (id) {
            return !fields.has(id);
        });
        if (missing.length > 0) {
            throw new Error("Map a column to " + missing.join(" and ") + " in the visualisation's settings");
        }
    }

    // Keeps the series with the largest totals, dropping the rest.
    function limitSeries(seriesTotals, maxSeries) {
        var names = Object.keys(seriesTotals);
        if (names.length <= maxSeries) {
            return null;
        }
        names.sort(function (a, b) {
            return Math.abs(seriesTotals[b]) - Math.abs(seriesTotals[a]);
        });
        var keep = {};
        names.slice(0, maxSeries).forEach(function (name) {
            keep[name] = true;
        });
        return keep;
    }

    function axisFor(type) {
        if (type === "DATE_TIME") {
            return "time";
        }
        if (type === "NUMBER") {
            return "value";
        }
        return "category";
    }

    // ------------------------------------------------------------------------------------------
    // Chart models: each turns rows into an ECharts option and fills in the report
    // ------------------------------------------------------------------------------------------

    // Sums values by category and series. Used by bar charts and by line charts on a category axis.
    function categoryMatrix(rows, fields, settings, prefs, report, categoryId, valueId) {
        var categoryType = fields.type(categoryId);
        var categories = [];
        var categoryIndex = {};
        var categoryTotals = {};
        var seriesNames = [];
        var seriesIndex = {};
        var seriesTotals = {};
        var sums = {};
        var nonNumeric = 0;
        var duplicates = 0;

        rows.forEach(function (row) {
            var value = toNumber(fields.value(row, valueId));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var category = fields.label(row, categoryId, prefs);
            var series = fields.has("series") ? fields.label(row, "series", prefs)
                : (fields.column(valueId) || "Value");

            if (categoryIndex[category] === undefined) {
                categoryIndex[category] = categories.length;
                categories.push(category);
                categoryTotals[category] = 0;
            }
            if (seriesIndex[series] === undefined) {
                seriesIndex[series] = seriesNames.length;
                seriesNames.push(series);
                seriesTotals[series] = 0;
            }
            var key = category + "\u0000" + series;
            if (sums[key] !== undefined) {
                duplicates++;
                sums[key] += value;
            } else {
                sums[key] = value;
            }
            categoryTotals[category] += value;
            seriesTotals[series] += value;
        });

        addDropped(report, "value not a number", nonNumeric);
        if (duplicates > 0) {
            report.warnings.push(duplicates + " rows shared a category and series with another row and were summed");
        }

        var keep = limitSeries(seriesTotals, intSetting(settings, "maxSeries", 20, 1, 500));
        if (keep) {
            var droppedSeries = seriesNames.filter(function (name) {
                return !keep[name];
            });
            report.warnings.push(droppedSeries.length + " series beyond Max Series were left out");
            seriesNames = seriesNames.filter(function (name) {
                return keep[name];
            });
        }

        var sort = textSetting(settings, "sort");
        if (sort === "Ascending" || sort === "Descending") {
            var direction = sort === "Ascending" ? 1 : -1;
            categories.sort(function (a, b) {
                return direction * (categoryTotals[a] - categoryTotals[b]);
            });
        } else if (categoryType === "DATE_TIME" || categoryType === "NUMBER") {
            // Keep a time or number category in its natural order.
            var firstValue = {};
            rows.forEach(function (row) {
                var label = labelOf(fields.value(row, categoryId), categoryType, prefs);
                if (firstValue[label] === undefined) {
                    firstValue[label] = toNumber(fields.value(row, categoryId));
                }
            });
            categories.sort(function (a, b) {
                return (firstValue[a] || 0) - (firstValue[b] || 0);
            });
        }

        if (keep) {
            // Leave out categories only the dropped series had.
            categories = categories.filter(function (category) {
                return seriesNames.some(function (name) {
                    return sums[category + "\u0000" + name] !== undefined;
                });
            });
        }

        var seriesData = seriesNames.map(function (name) {
            return {
                name: name,
                data: categories.map(function (category) {
                    var value = sums[category + "\u0000" + name];
                    return value === undefined ? null : value;
                })
            };
        });

        return {categories: categories, series: seriesData};
    }

    // Text cut short with an ellipsis when wider than this, as the D3 adapter shortens it: a long name would
    // otherwise take a legend's, an axis's or a chart's room.
    function shortened(width) {
        return {width: width, overflow: "truncate", ellipsis: "…"};
    }

    function legendOption(settings, show, style) {
        var position = textSetting(settings, "legend") || "top";
        if (!show || position === "none") {
            return {show: false};
        }
        var legend = {show: true, type: "scroll", textStyle: merge({color: style.text}, shortened(160))};
        if (position === "bottom") {
            legend.bottom = 0;
        } else if (position === "right") {
            legend.right = 0;
            legend.top = "middle";
            legend.orient = "vertical";
        } else {
            legend.top = 0;
            legend.left = 8;
            legend.right = STROOM_BUTTON_SPACE;
        }
        return legend;
    }

    function gridOption(settings, legendShown) {
        var position = textSetting(settings, "legend") || "top";
        var top = 16;
        if (legendShown && position === "top") {
            top += 28;
        }
        // The margins are the bounds of the axes' labels and names, which are kept inside them.
        return {
            top: top,
            left: 8,
            right: legendShown && position === "right" ? 140 : STROOM_BUTTON_SPACE,
            bottom: legendShown && position === "bottom" ? 32 : 8,
            outerBoundsMode: "same",
            outerBoundsContain: "all"
        };
    }

    function axisStyle(style) {
        return {
            axisLine: {lineStyle: {color: style.border}},
            axisTick: {lineStyle: {color: style.border}},
            axisLabel: merge({color: style.subtleText, hideOverlap: true}, shortened(120)),
            splitLine: {lineStyle: {color: style.border, opacity: 0.6}},
            nameTextStyle: {color: style.subtleText}
        };
    }

    // An axis's name is centred along it, clear of its labels (at its end, it was cut off at the chart's edge, and at
    // the top of a y axis, drawn over the legend).
    function axisName(name) {
        return {name: name || "", nameLocation: "middle", nameGap: 24, nameMoveOverlap: true};
    }

    function valueAxis(name, style, axisType, prefs) {
        var axis = merge(merge({type: axisType || "value"}, axisName(name)), axisStyle(style));
        if (axisType === "time") {
            axis.axisLabel.formatter = function (value) {
                return formatTime(value, prefs.compactTimePattern, prefs.timeZone);
            };
        } else if (axisType === "value") {
            axis.axisLabel.formatter = function (value) {
                return formatNumber(value);
            };
        }
        return axis;
    }

    function categoryAxis(name, categories, style) {
        return merge(merge({type: "category", data: categories}, axisName(name)), axisStyle(style));
    }

    // What a click on a category chart's bar or point selects: its category, and its series if one is mapped.
    // For a chart whose categories are dates in time order (e.g. a bar per day), what a range dragged across them
    // stands for: from the start of the first to the end of the last (e.g. the whole of its day). Null if they
    // aren't dates in order (e.g. sorted by value), as a range across them would mean nothing.
    function categoryTimes(fields, id, categories, prefs) {
        if (fields.type(id) !== "DATE_TIME" || categories.length === 0) {
            return null;
        }
        var times = categories.map(function (label) {
            return Number(fields.rawOf(id, label));
        });
        var rising = true;
        var falling = true;
        for (var i = 0; i < times.length; i++) {
            if (!isFinite(times[i])) {
                return null;
            }
            if (i > 0) {
                rising = rising && times[i] > times[i - 1];
                falling = falling && times[i] < times[i - 1];
            }
        }
        if (!rising && !falling) {
            return null;
        }
        var bucket = fields.bucket(id);
        var last = times.length - 1;
        return {
            // The time from the first category's start to the last's end, of those between two (by index).
            range: function (first, second) {
                var a = Math.max(0, Math.min(last, Math.round(Math.min(first, second))));
                var b = Math.max(0, Math.min(last, Math.round(Math.max(first, second))));
                var start = Math.min(times[a], times[b]);
                var latest = Math.max(times[a], times[b]);
                var end = bucketEnd(latest, bucket, prefs.timeZone);
                return [start, end > latest ? end - 1 : latest];
            },
            // The indices of the first and last categories within a time range, or null if none are.
            indices: function (start, end) {
                var found = null;
                times.forEach(function (time, index) {
                    if (time >= start && time <= end) {
                        found = found ? [found[0], index] : [index, index];
                    }
                });
                return found;
            }
        };
    }

    function matrixSelectAt(fields, matrix, categoryId) {
        return function (seriesIndex, dataIndex) {
            var category = matrix.categories[dataIndex];
            var series = matrix.series[seriesIndex];
            if (category === undefined || !series || series.data[dataIndex] === null) {
                return null;
            }
            var pairs = [[categoryId, fields.rawOf(categoryId, category)]];
            if (fields.has("series")) {
                pairs.push(["series", fields.rawOf("series", series.name)]);
            }
            return fields.selection(pairs);
        };
    }

    function barModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["category", "value"]);
        var matrix = categoryMatrix(rows, fields, settings, prefs, report, "category", "value");
        var horizontal = boolSetting(settings, "horizontal", false);
        var stack = boolSetting(settings, "stack", false);
        var showLabels = boolSetting(settings, "showLabels", false);
        var legendShown = matrix.series.length > 1;

        report.series = matrix.series.length;
        report.points = matrix.series.reduce(function (total, series) {
            return total + series.data.filter(function (value) {
                return value !== null;
            }).length;
        }, 0);

        var catAxis = categoryAxis(textSetting(settings, "xAxisName") || fields.column("category"),
            matrix.categories, style);
        // A horizontal bar chart's categories read from the top down, in their order (e.g. largest first).
        catAxis.inverse = horizontal;
        var valAxis = valueAxis(textSetting(settings, "yAxisName") || fields.column("value"), style, "value", prefs);

        return {
            legend: legendOption(settings, legendShown, style),
            grid: gridOption(settings, legendShown),
            tooltip: {trigger: "axis", axisPointer: {type: "shadow"}},
            xAxis: horizontal ? valAxis : catAxis,
            yAxis: horizontal ? catAxis : valAxis,
            selectAt: matrixSelectAt(fields, matrix, "category"),
            // Bars of dates in time order: a time range is dragged across them.
            rangeAt: categoryTimes(fields, "category", matrix.categories, prefs),
            series: matrix.series.map(function (series) {
                return {
                    type: "bar",
                    name: series.name,
                    data: series.data,
                    stack: stack ? "total" : undefined,
                    label: {show: showLabels, position: stack ? "inside" : (horizontal ? "right" : "top"),
                        color: style.text}
                };
            })
        };
    }

    function lineModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["x", "y"]);
        var smooth = boolSetting(settings, "smooth", false);
        var stack = boolSetting(settings, "stack", false);
        var area = boolSetting(settings, "area", false);
        var showLabels = boolSetting(settings, "showLabels", false);
        var xType = axisFor(fields.type("x"));
        var xName = textSetting(settings, "xAxisName") || fields.column("x");
        var yName = textSetting(settings, "yAxisName") || fields.column("y");

        function lineSeries(name, data) {
            return {
                type: "line",
                name: name,
                data: data,
                smooth: smooth,
                showSymbol: data.length <= 200,
                stack: stack ? "total" : undefined,
                areaStyle: area ? {opacity: 0.25} : undefined,
                label: {show: showLabels, color: style.text}
            };
        }

        if (xType === "category") {
            var matrix = categoryMatrix(rows, fields, settings, prefs, report, "x", "y");
            report.series = matrix.series.length;
            report.points = matrix.series.reduce(function (total, series) {
                return total + series.data.filter(function (value) {
                    return value !== null;
                }).length;
            }, 0);
            var legendShown = matrix.series.length > 1;
            return {
                legend: legendOption(settings, legendShown, style),
                grid: gridOption(settings, legendShown),
                tooltip: {trigger: "axis"},
                xAxis: merge(categoryAxis(xName, matrix.categories, style), {boundaryGap: false}),
                yAxis: valueAxis(yName, style, "value", prefs),
                selectAt: matrixSelectAt(fields, matrix, "x"),
                series: matrix.series.map(function (series) {
                    return lineSeries(series.name, series.data);
                })
            };
        }

        // A time or value axis: points are [x, y] pairs, sorted by x.
        var bySeries = {};
        var seriesNames = [];
        var seriesTotals = {};
        var badX = 0;
        var badY = 0;
        rows.forEach(function (row) {
            var x = toNumber(fields.value(row, "x"));
            var y = toNumber(fields.value(row, "y"));
            if (isNaN(x)) {
                badX++;
                return;
            }
            if (isNaN(y)) {
                badY++;
                return;
            }
            var series = fields.has("series") ? fields.label(row, "series", prefs)
                : (yName || "Value");
            if (!bySeries[series]) {
                bySeries[series] = [];
                seriesNames.push(series);
                seriesTotals[series] = 0;
            }
            bySeries[series].push([x, y]);
            seriesTotals[series] += y;
        });
        addDropped(report, "x not a number or date", badX);
        addDropped(report, "y not a number", badY);

        var keep = limitSeries(seriesTotals, intSetting(settings, "maxSeries", 20, 1, 500));
        if (keep) {
            report.warnings.push((seriesNames.length - Object.keys(keep).length) +
                " series beyond Max Series were left out");
            seriesNames = seriesNames.filter(function (name) {
                return keep[name];
            });
        }

        report.series = seriesNames.length;
        report.points = 0;
        var series = seriesNames.map(function (name) {
            var points = bySeries[name].sort(function (a, b) {
                return a[0] - b[0];
            });
            report.points += points.length;
            return lineSeries(name, points);
        });
        var legendShown = seriesNames.length > 1;

        return {
            legend: legendOption(settings, legendShown, style),
            grid: gridOption(settings, legendShown),
            tooltip: {
                trigger: "axis",
                axisPointer: {
                    label: {
                        formatter: function (params) {
                            return xType === "time"
                                ? formatTime(params.value, prefs.timePattern, prefs.timeZone)
                                : formatNumber(params.value);
                        }
                    }
                }
            },
            xAxis: valueAxis(xName, style, xType, prefs),
            yAxis: valueAxis(yName, style, "value", prefs),
            selectAt: function (seriesIndex, dataIndex) {
                var line = series[seriesIndex];
                var point = line && line.data[dataIndex];
                if (!point) {
                    return null;
                }
                var pairs = [["x", point[0]]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", line.name)]);
                }
                return fields.selection(pairs);
            },
            series: series
        };
    }

    // ------------------------------------------------------------------------------------------
    // Rings of a whole: sunburst
    // ------------------------------------------------------------------------------------------

    // The rings of a sunburst, inner first: its name, then each further level.
    var RING_ROLES = ["name", "level2", "level3", "level4", "level5", "level6"];

    // A sunburst's tree from the rows: each row's path (its name, then each further level up to the first it leaves
    // empty) adds its value (or 1) to each segment along it. A segment keeps its rows' total, so a row that stops at
    // it leaves part of the ring beyond it empty. Each segment knows what a click on it selects: its path.
    function sunburstTree(rows, fields, prefs, report) {
        var hasValue = fields.has("value");
        var root = {children: [], byName: {}, value: 0};
        var missing = 0;
        var badValue = 0;
        rows.forEach(function (row) {
            var steps = [];
            for (var i = 0; i < RING_ROLES.length; i++) {
                if (!fields.has(RING_ROLES[i])) {
                    continue;
                }
                var raw = fields.value(row, RING_ROLES[i]);
                if (isBlank(raw)) {
                    break;
                }
                steps.push({role: RING_ROLES[i], raw: raw, label: fields.label(row, RING_ROLES[i], prefs)});
            }
            if (steps.length === 0 || steps[0].role !== "name") {
                missing++;
                return;
            }
            var value = hasValue ? toNumber(fields.value(row, "value")) : 1;
            if (isNaN(value) || value < 0) {
                badValue++;
                return;
            }
            var node = root;
            root.value += value;
            var pairs = [];
            steps.forEach(function (step) {
                pairs.push([step.role, step.raw]);
                var child = node.byName[step.label];
                if (!child) {
                    child = {name: step.label, value: 0, children: [], byName: {}, pairs: pairs.slice()};
                    node.byName[step.label] = child;
                    node.children.push(child);
                }
                child.value += value;
                node = child;
            });
        });
        addDropped(report, "name empty", missing);
        addDropped(report, (fields.column("value") || "value") + " not a number or negative", badValue);
        // Largest first, round each ring.
        (function sort(node) {
            node.children.sort(function (a, b) {
                return b.value - a.value;
            });
            node.children.forEach(sort);
        })(root);
        return root;
    }

    // Each segment of a sunburst's tree, depth first: the order ECharts numbers them in (after its root).
    function treeNodes(root) {
        var nodes = [];
        (function walk(node) {
            node.children.forEach(function (child) {
                nodes.push(child);
                walk(child);
            });
        })(root);
        return nodes;
    }

    function sunburstModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["name"]);
        var root = sunburstTree(rows, fields, prefs, report);
        var nodes = treeNodes(root);
        var showLabels = boolSetting(settings, "showLabels", true);
        report.series = root.children.length;
        report.points = nodes.length;
        var colours = distinctColours(root.children.length, style);

        function data(node, colour) {
            return node.children.map(function (child, i) {
                var item = {name: child.name, value: child.value};
                // Each inner segment its own colour, the segments outside it its colour.
                var own = colour || colours[i % colours.length];
                item.itemStyle = {color: own};
                if (child.children.length) {
                    item.children = data(child, own);
                }
                return item;
            });
        }

        return {
            tooltip: {trigger: "item", formatter: function (params) {
                var path = (params.treePathInfo || []).slice(1).map(function (step) {
                    return step.name;
                });
                return escapeHtml(path.join(" › ") + ": " + prefs.formatValue(params.value));
            }},
            // A segment selects its path from the centre: its name and each level out to it.
            selectAt: function (seriesIndex, dataIndex) {
                var node = nodes[dataIndex - 1];
                return node ? fields.selection(node.pairs) : null;
            },
            series: [{
                type: "sunburst",
                // A click selects a segment (for the dashboard's filters), rather than zooming into it.
                nodeClick: false,
                radius: ["12%", "80%"],
                center: ["50%", "52%"],
                sort: null,
                emphasis: {focus: "ancestor"},
                itemStyle: {borderColor: style.dark ? "#1b1e24" : "#ffffff", borderWidth: 1},
                label: merge({show: showLabels, color: style.text, rotate: "radial", minAngle: 8}, shortened(70)),
                data: data(root, null),
                flatData: [null].concat(nodes)
            }]
        };
    }

    function pieModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["name", "value"]);
        var sums = {};
        var names = [];
        var nonNumeric = 0;
        var negative = 0;

        rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            if (value < 0) {
                negative++;
                return;
            }
            var name = fields.label(row, "name", prefs);
            if (sums[name] === undefined) {
                sums[name] = 0;
                names.push(name);
            }
            sums[name] += value;
        });
        addDropped(report, "value not a number", nonNumeric);
        addDropped(report, "value negative", negative);

        names.sort(function (a, b) {
            return sums[b] - sums[a];
        });
        var maxSlices = intSetting(settings, "maxSlices", 12, 1, 100);
        var data = names.slice(0, maxSlices).map(function (name) {
            return {name: name, value: sums[name]};
        });
        if (names.length > maxSlices) {
            var other = names.slice(maxSlices).reduce(function (total, name) {
                return total + sums[name];
            }, 0);
            data.push({name: sums.Other !== undefined ? "Other (combined)" : "Other", value: other, combined: true});
            report.warnings.push((names.length - maxSlices) + " smaller slices were combined as 'Other'");
        }

        report.series = 1;
        report.points = data.length;
        var doughnut = boolSetting(settings, "doughnut", false);
        var showLabels = boolSetting(settings, "showLabels", true);
        var legendShown = textSetting(settings, "legend") !== "none" && data.length > 1;

        return {
            legend: legendOption(settings, legendShown, style),
            tooltip: {trigger: "item"},
            // A slice selects its name; the slices combined as Other select nothing.
            selectAt: function (seriesIndex, dataIndex) {
                var slice = data[dataIndex];
                return !slice || slice.combined ? null : fields.selection([["name", fields.rawOf("name", slice.name)]]);
            },
            series: [{
                type: "pie",
                name: fields.column("value"),
                radius: doughnut ? ["40%", "70%"] : "70%",
                center: ["50%", "52%"],
                data: data,
                label: merge({show: showLabels, color: style.text}, shortened(130)),
                itemStyle: {borderColor: style.dark ? "#1b1e24" : "#fff", borderWidth: 1}
            }]
        };
    }

    // A number from a decimal setting, or the default if it is blank or isn't one.
    function decimalSetting(settings, id, defaultValue) {
        var value = toNumber(settings[id]);
        return isNaN(value) ? defaultValue : value;
    }

    // The smallest round number (1, 2 or 5 times a power of ten) at least as large as a value, for a gauge's top.
    function niceCeil(value) {
        if (!(value > 0)) {
            return 1;
        }
        var power = Math.pow(10, Math.floor(Math.log(value) / Math.LN10));
        var steps = [1, 2, 5, 10];
        for (var i = 0; i < steps.length; i++) {
            if (value <= steps[i] * power) {
                return steps[i] * power;
            }
        }
        return 10 * power;
    }

    // A gauge's coloured bands from the Bands setting, e.g. "70,90": green below 70, amber to 90, red above, as
    // ECharts takes them (each band's end as a fraction of the range). One colour if blank or unreadable.
    function gaugeBands(text, min, max) {
        var cuts = String(text || "").split(",").map(function (part) {
            return toNumber(part);
        }).filter(function (value) {
            return !isNaN(value) && value > min && value < max;
        }).sort(function (a, b) {
            return a - b;
        }).slice(0, GAUGE_BAND_COLOURS.length - 1);
        var bands = cuts.map(function (cut, i) {
            return [(cut - min) / (max - min), GAUGE_BAND_COLOURS[i]];
        });
        bands.push([1, cuts.length ? GAUGE_BAND_COLOURS[cuts.length] : GAUGE_BAND_COLOURS[0]]);
        return bands;
    }

    // A dial per row (or per name, when a name column is mapped: a name's values are added up), laid out in a grid.
    // A gauge's scale's labels: large numbers made short (e.g. 3T), which round a small dial otherwise overlap,
    // unless the Units setting says how they read.
    function gaugeScaleLabel(format, min, max) {
        return function (value) {
            return Math.max(Math.abs(min), Math.abs(max)) >= 10000 && format === formatNumber
                ? compact(value, ["", "k", "M", "B", "T"], 1000)
                : format(value);
        };
    }

    function gaugeModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["value"]);
        var named = fields.has("name");
        var sums = {};
        var names = [];
        var nonNumeric = 0;
        rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var name = named ? fields.label(row, "name", prefs) : fields.column("value");
            if (sums[name] === undefined) {
                sums[name] = 0;
                names.push(name);
            } else if (!named) {
                // Without a name column, a gauge shows one value: the first row's.
                return;
            }
            sums[name] += value;
        });
        addDropped(report, "value not a number", nonNumeric);
        if (!named && rows.length > 1) {
            report.warnings.push("Only the first row is shown: map a name column for a gauge per row, or group "
                + "the table to one row (e.g. max() of the column)");
        }
        var maxGauges = intSetting(settings, "maxGauges", 6, 1, 24);
        if (names.length > maxGauges) {
            report.warnings.push("Only the first " + maxGauges + " of " + names.length + " gauges are drawn "
                + "(Max Gauges)");
        }
        names = names.slice(0, maxGauges);

        var min = decimalSetting(settings, "gaugeMin", 0);
        var largest = names.reduce(function (most, name) {
            return Math.max(most, sums[name]);
        }, min);
        var max = decimalSetting(settings, "gaugeMax", NaN);
        if (isNaN(max) || max <= min) {
            max = min + niceCeil(largest - min);
        }
        var bands = gaugeBands(textSetting(settings, "bands"), min, max);
        var columns = Math.ceil(Math.sqrt(names.length || 1));
        var gridRows = Math.ceil((names.length || 1) / columns);
        // As a percentage of half the chart's smaller side, as ECharts takes it.
        var radius = Math.min(100 / columns, 100 / gridRows) * 0.84;
        var format = function (value) {
            return prefs.formatValue(value);
        };

        report.series = names.length;
        report.points = names.length;
        return {
            tooltip: {trigger: "item", formatter: function (params) {
                return escapeHtml(params.name + ": " + format(params.value));
            }},
            // A gauge selects its name, when a name column is mapped.
            selectAt: function (seriesIndex) {
                var name = names[seriesIndex];
                return named && name !== undefined ? fields.selection([["name", fields.rawOf("name", name)]]) : null;
            },
            series: names.map(function (name, i) {
                var column = i % columns;
                var row = Math.floor(i / columns);
                return {
                    type: "gauge",
                    name: name,
                    min: min,
                    max: max,
                    radius: radius + "%",
                    center: [((column + 0.5) * 100 / columns) + "%", ((row + 0.55) * 100 / gridRows) + "%"],
                    axisLine: {lineStyle: {width: 10, color: bands}},
                    axisTick: {lineStyle: {color: style.subtleText}},
                    splitLine: {lineStyle: {color: style.subtleText}},
                    axisLabel: {color: style.subtleText, distance: 14,
                        formatter: gaugeScaleLabel(prefs.formatValue, min, max)},
                    pointer: {itemStyle: {color: style.text}},
                    title: merge({show: true, color: style.text, offsetCenter: [0, "70%"]}, shortened(140)),
                    detail: {color: style.text, fontSize: 16, offsetCenter: [0, "40%"], formatter: format},
                    data: [{name: name, value: sums[name]}]
                };
            })
        };
    }

    function scatterModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["x", "y"]);
        var xType = axisFor(fields.type("x")) === "time" ? "time" : "value";
        var yType = axisFor(fields.type("y")) === "time" ? "time" : "value";
        var hasSize = fields.has("size");
        var bySeries = {};
        var seriesNames = [];
        var seriesTotals = {};
        var bad = 0;
        var badSize = 0;
        var minSize = Infinity;
        var maxSize = -Infinity;

        rows.forEach(function (row) {
            var x = toNumber(fields.value(row, "x"));
            var y = toNumber(fields.value(row, "y"));
            if (isNaN(x) || isNaN(y)) {
                bad++;
                return;
            }
            var point = [x, y];
            if (hasSize) {
                var size = toNumber(fields.value(row, "size"));
                if (isNaN(size) || size < 0) {
                    badSize++;
                    size = 0;
                }
                minSize = Math.min(minSize, size);
                maxSize = Math.max(maxSize, size);
                point.push(size);
            }
            var series = fields.has("series") ? fields.label(row, "series", prefs)
                : (fields.column("y") || "Value");
            if (!bySeries[series]) {
                bySeries[series] = [];
                seriesNames.push(series);
                seriesTotals[series] = 0;
            }
            bySeries[series].push(point);
            seriesTotals[series] += 1;
        });
        addDropped(report, "x or y not a number or date", bad);
        if (badSize > 0) {
            report.warnings.push(badSize + " rows had no usable size and were drawn smallest");
        }

        var keep = limitSeries(seriesTotals, intSetting(settings, "maxSeries", 20, 1, 500));
        if (keep) {
            report.warnings.push((seriesNames.length - Object.keys(keep).length) +
                " series beyond Max Series were left out");
            seriesNames = seriesNames.filter(function (name) {
                return keep[name];
            });
        }

        report.series = seriesNames.length;
        report.points = 0;
        var legendShown = seriesNames.length > 1;

        function axisText(value, type) {
            return type === "time" ? formatTime(value, prefs.timePattern, prefs.timeZone) : prefs.formatValue(value);
        }

        return {
            legend: legendOption(settings, legendShown, style),
            grid: gridOption(settings, legendShown),
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    // A jittered point's own x and y, kept at its end.
                    var value = boolSetting(settings, "jitter", false) ? params.value.slice(-2) : params.value;
                    var lines = [escapeHtml(params.seriesName)];
                    lines.push(escapeHtml((fields.column("x") || "x") + ": " + axisText(value[0], xType)));
                    lines.push(escapeHtml((fields.column("y") || "y") + ": " + axisText(value[1], yType)));
                    if (hasSize) {
                        lines.push(escapeHtml(fields.column("size") + ": " + formatNumber(params.value[2])));
                    }
                    return lines.join("<br/>");
                }
            },
            xAxis: valueAxis(textSetting(settings, "xAxisName") || fields.column("x"), style, xType, prefs),
            yAxis: valueAxis(textSetting(settings, "yAxisName") || fields.column("y"), style, yType, prefs),
            selectAt: function (seriesIndex, dataIndex) {
                var name = seriesNames[seriesIndex];
                var point = name !== undefined && bySeries[name][dataIndex];
                if (!point) {
                    return null;
                }
                var pairs = [["x", point[0]], ["y", point[1]]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", name)]);
                }
                return fields.selection(pairs);
            },
            series: seriesNames.map(function (name) {
                report.points += bySeries[name].length;
                return {
                    type: "scatter",
                    name: name,
                    data: bySeries[name],
                    symbolSize: hasSize ? function (value) {
                        return scaleSqrt(value[2], minSize, maxSize, 6, 36);
                    } : 10
                };
            })
        };
    }

    // ------------------------------------------------------------------------------------------
    // Clusters in a network (Node Colours cluster)
    // ------------------------------------------------------------------------------------------

    // The most nodes clustered: the most connected, if there are more.
    var MAX_CLUSTER_NODES = 5000;
    // The most clusters coloured and named each its own; the rest are one, Other clusters, in a subtle colour.
    var MAX_NAMED_CLUSTERS = 12;
    var OTHER_CLUSTERS = "Other clusters";
    // The most clusters, and bridges between them, a report gives.
    var MAX_REPORTED_CLUSTERS = 10;
    var MAX_BRIDGES = 5;

    // Finds a network's clusters with the Leiden method (Traag, Waltman and van Eck, 2019), which improves on Louvain
    // by never leaving a cluster badly connected, or in pieces: each node is moved into the neighbouring cluster
    // that most raises the modularity (how much more the clusters are linked within than chance would give); each
    // cluster is then refined into well-connected parts, merged node by node (the best merge each time, rather than
    // Leiden's random one, so the same network always gives the same clusters); and each part becomes one node of
    // a smaller network, starting in its cluster, until no node moves. Links are taken either way round, weighted
    // by their value.
    //
    // names: the nodes, in a fixed order; links: {source, target, value}.
    // Returns each node's cluster (by index in names), numbered from 0 in order of first appearance.
    function leiden(names, links) {
        var index = {};
        names.forEach(function (name, i) {
            index[name] = i;
        });
        // The network at this level: each node's links to others (by index) and the weight within it.
        var graph = {adjacent: names.map(function () {
            return {};
        }), within: names.map(function () {
            return 0;
        })};
        links.forEach(function (link) {
            var a = index[link.source];
            var b = index[link.target];
            var weight = link.value > 0 ? link.value : 0;
            if (a === undefined || b === undefined || a === b || weight === 0) {
                return;
            }
            graph.adjacent[a][b] = (graph.adjacent[a][b] || 0) + weight;
            graph.adjacent[b][a] = (graph.adjacent[b][a] || 0) + weight;
        });
        // Each original node's node at this level, and each node's cluster.
        var membership = names.map(function (name, i) {
            return i;
        });
        var community = names.map(function (name, i) {
            return i;
        });
        for (var level = 0; level < 30; level++) {
            var degree = graph.adjacent.map(function (others, i) {
                return Object.keys(others).reduce(function (sum, other) {
                    return sum + others[other];
                }, 0) + 2 * graph.within[i];
            });
            var twiceTotal = degree.reduce(function (sum, value) {
                return sum + value;
            }, 0);
            if (twiceTotal === 0) {
                break;
            }
            community = moveNodes(graph, degree, twiceTotal, community);
            var sizes = {};
            community.forEach(function (c) {
                sizes[c] = (sizes[c] || 0) + 1;
            });
            if (Object.keys(sizes).length === community.length) {
                break;
            }
            var parts = refineClusters(graph, degree, twiceTotal, community);
            // Each part becomes a node, starting in the cluster its nodes are in.
            var number = {};
            var count = 0;
            parts.forEach(function (part) {
                if (number[part] === undefined) {
                    number[part] = count++;
                }
            });
            // Nothing merged: the next level would be this one again.
            if (count === parts.length) {
                break;
            }
            var next = {adjacent: [], within: []};
            var nextCommunity = [];
            for (var n = 0; n < count; n++) {
                next.adjacent.push({});
                next.within.push(0);
            }
            parts.forEach(function (part, a) {
                var from = number[part];
                nextCommunity[from] = community[a];
                next.within[from] += graph.within[a];
                Object.keys(graph.adjacent[a]).forEach(function (other) {
                    var to = number[parts[other]];
                    if (to === from) {
                        // Seen from both its ends.
                        next.within[from] += graph.adjacent[a][other] / 2;
                    } else {
                        next.adjacent[from][to] = (next.adjacent[from][to] || 0) + graph.adjacent[a][other];
                    }
                });
            });
            membership = membership.map(function (node) {
                return number[parts[node]];
            });
            graph = next;
            community = nextCommunity;
        }
        var numbered = {};
        var total = 0;
        return membership.map(function (node) {
            var c = community[node];
            if (numbered[c] === undefined) {
                numbered[c] = total++;
            }
            return numbered[c];
        });
    }

    // Leiden's fast local moving: each node in turn (from a queue) moves into the neighbouring cluster that most
    // raises the modularity, and its neighbours outside that cluster are visited again, until none moves.
    function moveNodes(graph, degree, twiceTotal, start) {
        var community = start.slice();
        var totals = {};
        community.forEach(function (c, i) {
            totals[c] = (totals[c] || 0) + degree[i];
        });
        var queue = community.map(function (c, i) {
            return i;
        });
        var queued = community.map(function () {
            return true;
        });
        // Read from its head, as shifting a long queue is slow.
        for (var head = 0; head < queue.length; head++) {
            var i = queue[head];
            queued[i] = false;
            var own = community[i];
            var toCommunity = {};
            Object.keys(graph.adjacent[i]).forEach(function (other) {
                var c = community[other];
                toCommunity[c] = (toCommunity[c] || 0) + graph.adjacent[i][other];
            });
            totals[own] -= degree[i];
            var best = own;
            var bestGain = (toCommunity[own] || 0) - totals[own] * degree[i] / twiceTotal;
            Object.keys(toCommunity).map(Number).sort(function (a, b) {
                return a - b;
            }).forEach(function (c) {
                var gain = toCommunity[c] - totals[c] * degree[i] / twiceTotal;
                if (gain > bestGain + 1e-12) {
                    best = c;
                    bestGain = gain;
                }
            });
            totals[best] = (totals[best] || 0) + degree[i];
            if (best !== own) {
                community[i] = best;
                Object.keys(graph.adjacent[i]).map(Number).forEach(function (other) {
                    if (community[other] !== best && !queued[other]) {
                        queued[other] = true;
                        queue.push(other);
                    }
                });
            }
        }
        return community;
    }

    // Leiden's refinement: within each cluster, every node starts as a part of its own; each node still alone, and
    // well connected to the rest of its cluster, merges into the well-connected part of its cluster it raises the
    // modularity most by joining (if any), so each part is connected. Returns each node's part.
    function refineClusters(graph, degree, twiceTotal, community) {
        var part = community.map(function (c, i) {
            return i;
        });
        var alone = community.map(function () {
            return true;
        });
        var partTotal = degree.slice();
        var clusterTotal = {};
        var members = {};
        community.forEach(function (c, i) {
            clusterTotal[c] = (clusterTotal[c] || 0) + degree[i];
            (members[c] = members[c] || []).push(i);
        });
        // Each part's links to the rest of its cluster.
        var outside = community.map(function (c, i) {
            return Object.keys(graph.adjacent[i]).reduce(function (sum, other) {
                return sum + (community[other] === c ? graph.adjacent[i][other] : 0);
            }, 0);
        });
        Object.keys(members).map(Number).sort(function (a, b) {
            return a - b;
        }).forEach(function (c) {
            members[c].forEach(function (v) {
                if (!alone[v] || outside[v] < degree[v] * (clusterTotal[c] - degree[v]) / twiceTotal) {
                    return;
                }
                var toPart = {};
                Object.keys(graph.adjacent[v]).forEach(function (other) {
                    if (community[other] === c) {
                        toPart[part[other]] = (toPart[part[other]] || 0) + graph.adjacent[v][other];
                    }
                });
                var best = -1;
                var bestGain = 0;
                Object.keys(toPart).map(Number).sort(function (a, b) {
                    return a - b;
                }).forEach(function (p) {
                    if (p === part[v]
                        || outside[p] < partTotal[p] * (clusterTotal[c] - partTotal[p]) / twiceTotal) {
                        return;
                    }
                    var gain = toPart[p] - partTotal[p] * degree[v] / twiceTotal;
                    if (gain > bestGain + 1e-12 || (best < 0 && gain >= 0)) {
                        best = p;
                        bestGain = gain;
                    }
                });
                if (best < 0) {
                    return;
                }
                var own = part[v];
                // The merged part's links to the rest of its cluster: both parts' less those between them.
                outside[best] = outside[best] + outside[own] - 2 * toPart[best];
                partTotal[best] += partTotal[own];
                part[v] = best;
                alone[v] = false;
                alone[best] = false;
            });
        });
        return part;
    }

    // A network's clusters: found by Leiden over the whole network (its most connected MAX_CLUSTER_NODES), before
    // Max Nodes leaves any out, so leaving out more or fewer doesn't change them. Largest first, each named after
    // its most connected member and how many others it has (e.g. "mchen +11").
    //
    // names: the nodes; links: {source, target, value}; nodes: each node by name, with its degree and weight.
    // Returns {of: each node's cluster (by index in list), list: [{name, size, members}], modularity}.
    function networkClusters(names, links, nodes) {
        var clustered = names.slice();
        if (clustered.length > MAX_CLUSTER_NODES) {
            clustered.sort(function (a, b) {
                return (nodes[b].degree - nodes[a].degree) || (nodes[b].weight - nodes[a].weight);
            });
            clustered = clustered.slice(0, MAX_CLUSTER_NODES);
        }
        // A fixed order, whatever the rows' order.
        clustered.sort(function (a, b) {
            return a < b ? -1 : (a > b ? 1 : 0);
        });
        var membership = leiden(clustered, links);
        var strength = {};
        links.forEach(function (link) {
            strength[link.source] = (strength[link.source] || 0) + link.value;
            strength[link.target] = (strength[link.target] || 0) + link.value;
        });
        var byId = {};
        clustered.forEach(function (name, i) {
            (byId[membership[i]] = byId[membership[i]] || []).push(name);
        });
        var list = Object.keys(byId).map(function (id) {
            var members = byId[id];
            var top = members.slice().sort(function (a, b) {
                return ((strength[b] || 0) - (strength[a] || 0)) || (a < b ? -1 : (a > b ? 1 : 0));
            })[0];
            return {top: top, size: members.length, members: members};
        });
        list.sort(function (a, b) {
            return (b.size - a.size) || (a.top < b.top ? -1 : (a.top > b.top ? 1 : 0));
        });
        var of = {};
        list.forEach(function (cluster, i) {
            cluster.name = nodeLabel(cluster.top) + (cluster.size > 1 ? " +" + (cluster.size - 1) : "");
            cluster.members.forEach(function (member) {
                of[member] = i;
            });
        });
        // Modularity: the share of the links' weight within clusters, less the share chance would give.
        var total = 0;
        var inside = list.map(function () {
            return 0;
        });
        var ends = list.map(function () {
            return 0;
        });
        links.forEach(function (link) {
            var a = of[link.source];
            var b = of[link.target];
            if (a === undefined || b === undefined || link.source === link.target || !(link.value > 0)) {
                return;
            }
            total += link.value;
            ends[a] += link.value;
            ends[b] += link.value;
            if (a === b) {
                inside[a] += link.value;
            }
        });
        var modularity = 0;
        if (total > 0) {
            list.forEach(function (cluster, i) {
                modularity += inside[i] / total - Math.pow(ends[i] / (2 * total), 2);
            });
        }
        return {of: of, list: list, modularity: modularity};
    }

    // A node's cluster as drawn: its own if one of the MAX_NAMED_CLUSTERS largest, else Other clusters.
    function clusterNameOf(clusters, name) {
        var index = clusters.of[name];
        return index !== undefined && index < MAX_NAMED_CLUSTERS ? clusters.list[index].name : OTHER_CLUSTERS;
    }

    // The clusters of the nodes drawn, in the clusters' order (Other clusters last): the legend's, and the colours'.
    function drawnClusters(clusters, names) {
        var drawn = {};
        names.forEach(function (name) {
            drawn[clusterNameOf(clusters, name)] = true;
        });
        var order = clusters.list.slice(0, MAX_NAMED_CLUSTERS).map(function (cluster) {
            return cluster.name;
        }).concat([OTHER_CLUSTERS]);
        return order.filter(function (name) {
            return drawn[name];
        });
    }

    // Puts a network's clusters in its report: how many, how clear-cut (modularity, from 0 to 1), the largest with
    // their sizes (and, when the nodes have groups, the group most of each is in, with its share), and the bridges:
    // the nodes linked to the most other clusters.
    function reportClusters(report, clusters, links, nodes) {
        var top = clusters.list.slice(0, MAX_REPORTED_CLUSTERS).map(function (cluster) {
            var entry = {name: cluster.name, size: cluster.size};
            var counts = {};
            var grouped = 0;
            cluster.members.forEach(function (member) {
                var group = nodes[member] ? nodes[member].group : null;
                if (group !== null && group !== undefined) {
                    counts[group] = (counts[group] || 0) + 1;
                    grouped++;
                }
            });
            var main = Object.keys(counts).sort(function (a, b) {
                return (counts[b] - counts[a]) || (a < b ? -1 : (a > b ? 1 : 0));
            })[0];
            if (main !== undefined) {
                entry.group = main;
                entry.groupShare = Math.round(100 * counts[main] / grouped);
            }
            return entry;
        });
        var others = {};
        var across = {};
        links.forEach(function (link) {
            var a = clusters.of[link.source];
            var b = clusters.of[link.target];
            if (a === undefined || b === undefined || a === b) {
                return;
            }
            [[link.source, b], [link.target, a]].forEach(function (end) {
                others[end[0]] = others[end[0]] || {};
                others[end[0]][end[1]] = true;
                across[end[0]] = (across[end[0]] || 0) + link.value;
            });
        });
        var bridges = Object.keys(others).map(function (name) {
            return {name: name, clusters: Object.keys(others[name]).length};
        }).sort(function (a, b) {
            return (b.clusters - a.clusters) || (across[b.name] - across[a.name]) ||
                (a.name < b.name ? -1 : (a.name > b.name ? 1 : 0));
        }).slice(0, MAX_BRIDGES).map(function (bridge) {
            return {name: nodeLabel(bridge.name), otherClusters: bridge.clusters};
        });
        report.clusters = {count: clusters.list.length, modularity: Math.round(clusters.modularity * 100) / 100,
            top: top, bridges: bridges};
    }

    function graphModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["source", "target"]);
        var hasWeight = fields.has("weight");
        var nodes = {};
        var nodeNames = [];
        var links = {};
        var linkKeys = [];
        var missing = 0;
        var selfLinks = 0;
        var badWeight = 0;

        var directed = boolSetting(settings, "directed", false);

        function node(name, group) {
            var existing = nodes[name];
            if (!existing) {
                existing = {name: name, neighbours: {}, weight: 0, group: null};
                nodes[name] = existing;
                nodeNames.push(name);
            }
            if (existing.group === null && group !== null) {
                existing.group = group;
            }
            return existing;
        }

        function groupOf(row, id) {
            if (!fields.has(id)) {
                return null;
            }
            var value = fields.value(row, id);
            return value === null || value === undefined || value === "" ? null
                : labelOf(value, fields.type(id), prefs);
        }

        // Each row's path (see pathOf) is a link from each step to the next: its source to its target, then on
        // through any further levels.
        var nodeRoles = {};
        rows.forEach(function (row) {
            var path = pathOf(row, fields, prefs);
            if (!path) {
                missing++;
                return;
            }
            var weight = 1;
            if (hasWeight) {
                weight = toNumber(fields.value(row, "weight"));
                if (isNaN(weight) || weight < 0) {
                    badWeight++;
                    return;
                }
            }
            for (var i = 0; i < path.length - 1; i++) {
                var source = path[i].label;
                var target = path[i + 1].label;
                if (source === target) {
                    selfLinks++;
                    continue;
                }
                var sourceNode = node(source, path[i].role === "source" ? groupOf(row, "sourceGroup") : null);
                var targetNode = node(target, path[i + 1].role === "target" ? groupOf(row, "targetGroup") : null);
                [path[i], path[i + 1]].forEach(function (step) {
                    if (nodeRoles[step.label] === undefined) {
                        nodeRoles[step.label] = step.role;
                    }
                });
                sourceNode.neighbours[target] = true;
                targetNode.neighbours[source] = true;
                sourceNode.weight += weight;
                targetNode.weight += weight;

                // An undirected graph draws A to B and B to A as one link.
                var key = directed || source < target
                    ? source + "\u0000" + target
                    : target + "\u0000" + source;
                if (links[key]) {
                    links[key].value += weight;
                } else {
                    links[key] = {source: source, target: target, value: weight, fromRole: path[i].role,
                        toRole: path[i + 1].role};
                    linkKeys.push(key);
                }
            }
        });
        addDropped(report, "source or target empty", missing);
        addDropped(report, "source and target the same", selfLinks);
        addDropped(report, "weight not a number or negative", badWeight);

        nodeNames.forEach(function (name) {
            nodes[name].degree = Object.keys(nodes[name].neighbours).length;
        });

        // Clusters (Node Colours cluster), over the whole network before Max Nodes leaves any out.
        var byCluster = String(textSetting(settings, "nodeColours") || "group").toLowerCase() === "cluster";
        var allLinks = linkKeys.map(function (key) {
            return links[key];
        });
        var clusters = byCluster ? networkClusters(nodeNames, allLinks, nodes) : null;
        if (clusters) {
            reportClusters(report, clusters, allLinks, nodes);
        }

        // Keep the most connected nodes when there are more than Max Nodes.
        var maxNodes = intSetting(settings, "maxNodes", 300, 2, 5000);
        if (nodeNames.length > maxNodes) {
            nodeNames.sort(function (a, b) {
                return (nodes[b].degree - nodes[a].degree) || (nodes[b].weight - nodes[a].weight);
            });
            var dropped = nodeNames.length - maxNodes;
            nodeNames = nodeNames.slice(0, maxNodes);
            var kept = {};
            nodeNames.forEach(function (name) {
                kept[name] = true;
            });
            var before = linkKeys.length;
            linkKeys = linkKeys.filter(function (key) {
                return kept[links[key].source] && kept[links[key].target];
            });
            report.warnings.push(dropped + " less connected nodes beyond Max Nodes were left out, with " +
                (before - linkKeys.length) + " of their links");

            // Size the nodes drawn by the links drawn, not those left out.
            nodeNames.forEach(function (name) {
                nodes[name].neighbours = {};
                nodes[name].weight = 0;
            });
            linkKeys.forEach(function (key) {
                var link = links[key];
                nodes[link.source].neighbours[link.target] = true;
                nodes[link.target].neighbours[link.source] = true;
                nodes[link.source].weight += link.value;
                nodes[link.target].weight += link.value;
            });
            nodeNames.forEach(function (name) {
                nodes[name].degree = Object.keys(nodes[name].neighbours).length;
            });
        }

        // Each node its own colour, or coloured by its group (one colour if there are none).
        // Each node its own colour (nodeColours node), or by its group: without a sourceGroup or targetGroup
        // column there are no groups, so each node its own then too. Seen in testing: a network without groups
        // was drawn in one colour, its links (in their source's colour) too.
        // By cluster (nodeColours cluster), each its own colour and named in the legend.
        var colourEachNode = !byCluster && (String(textSetting(settings, "nodeColours") || "group").toLowerCase()
            === "node" || !(fields.has("sourceGroup") || fields.has("targetGroup")));
        var groups = [];
        var groupIndex = {};
        var groupOfNode = function (name) {
            return byCluster ? clusterNameOf(clusters, name) : (colourEachNode ? name : nodes[name].group);
        };
        (byCluster ? drawnClusters(clusters, nodeNames) : nodeNames.map(groupOfNode)).forEach(function (group) {
            if (group !== null && groupIndex[group] === undefined) {
                groupIndex[group] = groups.length;
                groups.push(group);
            }
        });
        // Links between clusters grey and thinner (Cluster Links), so the clusters stand out.
        var greyAcross = byCluster && boolSetting(settings, "clusterLinks", true);

        var minDegree = Infinity;
        var maxDegree = -Infinity;
        nodeNames.forEach(function (name) {
            minDegree = Math.min(minDegree, nodes[name].degree);
            maxDegree = Math.max(maxDegree, nodes[name].degree);
        });
        var minWeight = Infinity;
        var maxWeight = -Infinity;
        linkKeys.forEach(function (key) {
            minWeight = Math.min(minWeight, links[key].value);
            maxWeight = Math.max(maxWeight, links[key].value);
        });
        // Node Size: by its connections (how many others it links to), or by its links' total (their weights, or
        // the rows they come from, e.g. the messages a person sent and received).
        var sizeByTotal = String(textSetting(settings, "nodeSize") || "connections").toLowerCase() === "total";
        var minTotal = Infinity;
        var maxTotal = -Infinity;
        nodeNames.forEach(function (name) {
            minTotal = Math.min(minTotal, nodes[name].weight);
            maxTotal = Math.max(maxTotal, nodes[name].weight);
        });

        var labelSetting = String(settings.showLabels || "auto").toLowerCase();
        var labelThreshold;
        if (labelSetting === "false") {
            labelThreshold = -1;
        } else if (labelSetting === "true" || nodeNames.length <= 60) {
            labelThreshold = 0;
        } else {
            labelThreshold = Math.max(1, Math.ceil(nodeNames.length * 0.1));
        }
        var byDegree = nodeNames.slice().sort(function (a, b) {
            return nodes[b].degree - nodes[a].degree;
        });
        var labelled = {};
        if (labelThreshold === 0) {
            nodeNames.forEach(function (name) {
                labelled[name] = true;
            });
        } else if (labelThreshold > 0) {
            byDegree.slice(0, labelThreshold).forEach(function (name) {
                labelled[name] = true;
            });
        }

        report.series = 1;
        report.nodes = nodeNames.length;
        report.links = linkKeys.length;
        report.points = nodeNames.length;

        var nodeCount = nodeNames.length;

        return {
            // Each node's own colour needs no legend: its label names it.
            legend: legendOption(settings, groups.length > 1 && !colourEachNode, style),
            // An overview of the whole graph, marking where it is zoomed to, in the bottom-right corner.
            thumbnail: boolSetting(settings, "thumbnail", false) ? {
                show: true,
                right: 8,
                bottom: 8,
                width: "22%",
                height: "22%",
                itemStyle: {borderColor: style.border}
            } : undefined,
            // A node selects itself (node); a link its two ends (by the columns they are from).
            selectAt: function (seriesIndex, dataIndex, dataType) {
                if (dataType === "edge") {
                    var link = links[linkKeys[dataIndex]];
                    return link ? linkSelection(fields, link) : null;
                }
                var name = nodeNames[dataIndex];
                return name === undefined ? null : nodeSelection(fields, {nodeRoles: nodeRoles}, name);
            },
            color: colourEachNode ? distinctColours(groups.length, style) : undefined,
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    if (params.dataType === "edge") {
                        var text = params.data.source + (directed ? " → " : " — ") + params.data.target;
                        // Its weight, or how many rows it comes from (each counts once without a weight).
                        text += ": " + formatNumber(params.data.value) + (hasWeight ? ""
                            : (params.data.value === 1 ? " row" : " rows"));
                        return escapeHtml(text);
                    }
                    var lines = [escapeHtml(params.data.name),
                        escapeHtml("Connections: " + formatNumber(params.data.degree))];
                    lines.push(escapeHtml((hasWeight ? fields.column("weight") || "Weight" : "Rows") + ": " +
                        formatNumber(params.data.weight)));
                    if (params.data.groupName) {
                        lines.push(escapeHtml(params.data.groupName));
                    }
                    if (params.data.clusterName) {
                        lines.push(escapeHtml("Cluster: " + params.data.clusterName));
                    }
                    return lines.join("<br/>");
                }
            },
            series: [{
                type: "graph",
                layout: "force",
                roam: true,
                draggable: nodeCount <= 1000,
                animation: nodeCount <= 500,
                categories: groups.map(function (group) {
                    return group === OTHER_CLUSTERS && byCluster
                        ? {name: group, itemStyle: {color: style.subtleText}} : {name: group};
                }),
                force: physicsOf(settings, nodeCount),
                edgeSymbol: directed ? ["none", "arrow"] : ["none", "none"],
                edgeSymbolSize: 6,
                emphasis: {focus: "adjacency", lineStyle: {width: 4}},
                lineStyle: {color: "source", opacity: 0.5, curveness: directed ? 0.1 : 0},
                label: merge({color: style.text, position: "right"}, shortened(150)),
                labelLayout: {hideOverlap: true},
                data: nodeNames.map(function (name) {
                    var n = nodes[name];
                    return {
                        name: name,
                        degree: n.degree,
                        weight: n.weight,
                        groupName: n.group,
                        clusterName: clusters && clusters.of[name] !== undefined
                            ? clusters.list[clusters.of[name]].name : undefined,
                        value: n.degree,
                        category: groupOfNode(name) !== null ? groupIndex[groupOfNode(name)] : undefined,
                        symbolSize: sizeByTotal ? scaleSqrt(n.weight, minTotal, maxTotal, 8, 48)
                            : scaleSqrt(n.degree, minDegree, maxDegree, 8, 48),
                        label: {show: labelled[name] === true}
                    };
                }),
                links: linkKeys.map(function (key) {
                    var link = links[key];
                    // As wide as its weight, or (without one) the rows it comes from: e.g. a pair's messages.
                    var width = hasWeight || maxWeight > minWeight
                        ? scaleSqrt(link.value, minWeight, maxWeight, 1, 6) : 1;
                    var across = greyAcross && clusters.of[link.source] !== clusters.of[link.target];
                    return {
                        source: link.source,
                        target: link.target,
                        value: link.value,
                        lineStyle: across ? {width: Math.max(1, width * 0.6), color: style.subtleText, opacity: 0.3}
                            : {width: width}
                    };
                })
            }]
        };
    }

    // The force layout's physics by the Physics setting: settled (as it settles quickly), lively (kept moving longer,
    // with less friction and gravity) or spread (nodes pushed further apart, on longer links).
    function physicsOf(settings, nodeCount) {
        var repulsion = nodeCount > 500 ? 40 : (nodeCount > 100 ? 80 : 160);
        var edgeLength = nodeCount > 100 ? [20, 80] : [40, 140];
        var physics = String(textSetting(settings, "physics") || "settled").toLowerCase();
        if (physics === "lively") {
            return {repulsion: repulsion * 1.5, edgeLength: edgeLength, gravity: 0.05, friction: 0.15,
                layoutAnimation: nodeCount <= 500};
        }
        if (physics === "spread") {
            return {repulsion: repulsion * 3, edgeLength: [edgeLength[0] * 2, edgeLength[1] * 2], gravity: 0.03,
                layoutAnimation: nodeCount <= 500};
        }
        return {repulsion: repulsion, edgeLength: edgeLength, gravity: 0.1, layoutAnimation: nodeCount <= 500};
    }

    // ECharts' own palette, then as many more as are needed, spread round the colour wheel by the golden angle so
    // neighbouring ones differ, lighter on a dark theme.
    var BASE_PALETTE = ["#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de", "#3ba272", "#fc8452", "#9a60b4",
        "#ea7ccc"];
    // How many colours a chart's series are given in turn.
    var MAX_PALETTE = 30;

    function distinctColours(count, style) {
        var colours = BASE_PALETTE.slice(0, Math.min(count, BASE_PALETTE.length));
        for (var i = colours.length; i < count; i++) {
            var hue = Math.round(((i - BASE_PALETTE.length) * 137.508 + 20) % 360);
            colours.push("hsl(" + hue + ", 60%, " + (style.dark ? "62%" : "48%") + ")");
        }
        return colours;
    }

    // ------------------------------------------------------------------------------------------
    // Grids of values: heatmap and calendar
    // ------------------------------------------------------------------------------------------

    // The colours a value runs through, smallest to largest; a dark theme's smallest is dark rather than pale.
    var COLOUR_SCALES = {
        blue: ["#e8f1fb", "#6baed6", "#08519c"],
        green: ["#e8f5e9", "#74c476", "#1b7a3a"],
        red: ["#fdecea", "#fb6a4a", "#a50f15"],
        purple: ["#f3e8fb", "#9e9ac8", "#54278f"],
        orange: ["#fff3e0", "#fd8d3c", "#a63603"],
        heat: ["#ffffcc", "#fd8d3c", "#bd0026"]
    };

    function valueScale(settings, style, min, max, format) {
        var name = String(textSetting(settings, "colourScale") || "blue").toLowerCase();
        var colours = (COLOUR_SCALES[name] || COLOUR_SCALES.blue).slice();
        if (style.dark && name !== "heat") {
            colours[0] = "#26303b";
        }
        // A single value (or all the same) at the top of the scale, so it shows.
        var top = isFinite(max) ? max : (isFinite(min) ? min : 1);
        var bottom = isFinite(min) && top > min ? min : top - 1;
        return {
            type: "continuous",
            min: bottom,
            max: top,
            orient: "horizontal",
            left: "center",
            bottom: 0,
            itemHeight: 120,
            itemWidth: 10,
            calculable: false,
            inRange: {color: colours},
            textStyle: {color: style.subtleText},
            formatter: function (value) {
                return format(value);
            }
        };
    }

    // Puts labels in their natural order when they were drawn from dates or numbers.
    function sortNatural(labels, fields, id) {
        var type = fields.type(id);
        if (type === "DATE_TIME" || type === "NUMBER") {
            labels.sort(function (a, b) {
                return toNumber(fields.rawOf(id, a)) - toNumber(fields.rawOf(id, b));
            });
        }
        return labels;
    }

    function heatmapModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["x", "y", "value"]);
        var xs = [];
        var ys = [];
        var seenX = {};
        var seenY = {};
        var sums = {};
        var nonNumeric = 0;
        var duplicates = 0;
        rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var x = fields.label(row, "x", prefs);
            var y = fields.label(row, "y", prefs);
            if (!seenX[x]) {
                seenX[x] = true;
                xs.push(x);
            }
            if (!seenY[y]) {
                seenY[y] = true;
                ys.push(y);
            }
            var key = x + "\u0000" + y;
            if (sums[key] !== undefined) {
                duplicates++;
                sums[key] += value;
            } else {
                sums[key] = value;
            }
        });
        addDropped(report, "value not a number", nonNumeric);
        if (duplicates > 0) {
            report.warnings.push(duplicates + " rows shared an x and y with another row and were added up");
        }
        sortNatural(xs, fields, "x");
        sortNatural(ys, fields, "y");

        var data = [];
        var min = Infinity;
        var max = -Infinity;
        ys.forEach(function (y) {
            xs.forEach(function (x) {
                var value = sums[x + "\u0000" + y];
                if (value !== undefined) {
                    data.push([x, y, value]);
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            });
        });
        report.series = 1;
        report.points = data.length;
        var showLabels = boolSetting(settings, "showLabels", false);

        return {
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    return escapeHtml(params.value[0] + ", " + params.value[1] + ": " +
                        prefs.formatValue(params.value[2]));
                }
            },
            visualMap: valueScale(settings, style, min, max, prefs.formatValue),
            matrix: {
                x: {data: xs},
                y: {data: ys},
                top: 16,
                bottom: 44,
                left: 16,
                right: STROOM_BUTTON_SPACE
            },
            // A cell selects its x and y.
            selectAt: function (seriesIndex, dataIndex) {
                var cell = data[dataIndex];
                return cell ? fields.selection([["x", fields.rawOf("x", cell[0])], ["y", fields.rawOf("y", cell[1])]])
                    : null;
            },
            series: [{
                type: "heatmap",
                coordinateSystem: "matrix",
                data: data,
                label: {
                    show: showLabels,
                    color: style.text,
                    formatter: function (params) {
                        return prefs.formatValue(params.value[2]);
                    }
                }
            }]
        };
    }

    // The most years a calendar shows, the latest ones.
    var MAX_CALENDAR_YEARS = 5;

    function calendarModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["date", "value"]);
        var sums = {};
        var days = [];
        var dayValue = {};
        var badDate = 0;
        var nonNumeric = 0;
        rows.forEach(function (row) {
            var ms = toNumber(fields.value(row, "date"));
            if (isNaN(ms)) {
                badDate++;
                return;
            }
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var day = formatTime(ms, "YYYY-MM-DD", prefs.timeZone);
            if (sums[day] === undefined) {
                sums[day] = 0;
                days.push(day);
                dayValue[day] = ms;
            }
            sums[day] += value;
        });
        addDropped(report, "date not a date", badDate);
        addDropped(report, "value not a number", nonNumeric);
        days.sort();

        var years = [];
        days.forEach(function (day) {
            var year = day.substring(0, 4);
            if (years.indexOf(year) < 0) {
                years.push(year);
            }
        });
        if (years.length > MAX_CALENDAR_YEARS) {
            report.warnings.push((years.length - MAX_CALENDAR_YEARS) + " earlier years were left out");
            years = years.slice(years.length - MAX_CALENDAR_YEARS);
            days = days.filter(function (day) {
                return years.indexOf(day.substring(0, 4)) >= 0;
            });
        }
        var min = Infinity;
        var max = -Infinity;
        days.forEach(function (day) {
            min = Math.min(min, sums[day]);
            max = Math.max(max, sums[day]);
        });
        report.series = years.length;
        report.points = days.length;

        // Within a year, only the days its data covers; else a calendar a year.
        var calendars = years.length === 1
            ? [{range: [days[0], days[days.length - 1]]}]
            : years.map(function (year) {
                return {range: year};
            });
        var height = 150;
        var series = calendars.map(function (calendar, index) {
            calendar.top = 24 + index * height;
            calendar.left = 40;
            calendar.right = STROOM_BUTTON_SPACE;
            calendar.cellSize = ["auto", 14];
            calendar.itemStyle = {borderColor: style.border, borderWidth: 1, color: "transparent"};
            calendar.splitLine = {lineStyle: {color: style.subtleText}};
            calendar.dayLabel = {color: style.subtleText};
            calendar.monthLabel = {color: style.subtleText};
            calendar.yearLabel = {color: style.subtleText};
            return {
                type: "heatmap",
                coordinateSystem: "calendar",
                calendarIndex: index,
                data: days.filter(function (day) {
                    return years.length === 1 || day.substring(0, 4) === years[index];
                }).map(function (day) {
                    return [day, sums[day]];
                })
            };
        });

        return {
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    return escapeHtml(params.value[0] + ": " + prefs.formatValue(params.value[1]));
                }
            },
            visualMap: valueScale(settings, style, min, max, prefs.formatValue),
            calendar: calendars,
            // A day selects its date, as the table has it.
            selectAt: function (seriesIndex, dataIndex) {
                var day = series[seriesIndex] && series[seriesIndex].data[dataIndex];
                return day ? fields.selection([["date", dayValue[day[0]]]]) : null;
            },
            series: series
        };
    }

    // ------------------------------------------------------------------------------------------
    // Histogram
    // ------------------------------------------------------------------------------------------

    // The most ranges a histogram counts in.
    var MAX_BINS = 200;

    function histogramModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["value"]);
        var values = [];
        var nonNumeric = 0;
        rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var series = fields.has("series") ? fields.label(row, "series", prefs) : (fields.column("value") || "Rows");
            values.push([value, series]);
        });
        addDropped(report, "value not a number", nonNumeric);

        var min = Infinity;
        var max = -Infinity;
        values.forEach(function (value) {
            min = Math.min(min, value[0]);
            max = Math.max(max, value[0]);
        });
        // Sturges' rule when not set.
        var bins = intSetting(settings, "bins", 0, 0, MAX_BINS) ||
            Math.min(50, Math.max(1, Math.ceil(Math.log(Math.max(1, values.length)) / Math.LN2 + 1)));
        // No values, no bars; one value (or all the same), one.
        if (values.length === 0) {
            bins = 0;
        } else if (!(max > min)) {
            bins = 1;
        }
        var width = bins > 0 && max > min ? (max - min) / bins : 1;
        var labels = [];
        for (var i = 0; i < bins; i++) {
            var from = min + i * width;
            labels.push(prefs.formatValue(from) + " – " + prefs.formatValue(i === bins - 1 ? max : from + width));
        }

        var counts = {};
        var seriesNames = [];
        var seriesTotals = {};
        values.forEach(function (value) {
            var bin = max > min ? Math.min(bins - 1, Math.floor((value[0] - min) / width)) : 0;
            if (!counts[value[1]]) {
                counts[value[1]] = [];
                for (var b = 0; b < bins; b++) {
                    counts[value[1]].push(0);
                }
                seriesNames.push(value[1]);
                seriesTotals[value[1]] = 0;
            }
            counts[value[1]][bin]++;
            seriesTotals[value[1]]++;
        });
        var keep = limitSeries(seriesTotals, intSetting(settings, "maxSeries", 20, 1, 500));
        if (keep) {
            report.warnings.push((seriesNames.length - Object.keys(keep).length) +
                " series beyond Max Series were left out");
            seriesNames = seriesNames.filter(function (name) {
                return keep[name];
            });
        }
        report.series = seriesNames.length;
        report.points = values.length;
        var stack = boolSetting(settings, "stack", false);
        var showLabels = boolSetting(settings, "showLabels", false);
        var legendShown = seriesNames.length > 1;

        return {
            legend: legendOption(settings, legendShown, style),
            grid: gridOption(settings, legendShown),
            tooltip: {trigger: "axis", axisPointer: {type: "shadow"}},
            xAxis: categoryAxis(textSetting(settings, "xAxisName") || fields.column("value"), labels, style),
            yAxis: valueAxis(textSetting(settings, "yAxisName") || "Rows", style, "value", prefs),
            series: seriesNames.map(function (name) {
                return {
                    type: "bar",
                    name: name,
                    data: counts[name],
                    stack: stack ? "total" : undefined,
                    barCategoryGap: "4%",
                    label: {show: showLabels, position: stack ? "inside" : "top", color: style.text}
                };
            })
        };
    }

    // ------------------------------------------------------------------------------------------
    // Flows and links: Sankey and chord diagrams
    // ------------------------------------------------------------------------------------------

    // The columns of a network's path, in order: source, target, then the further levels after it (Level 3 to 6).
    var PATH_ROLES = ["source", "target", "level3", "level4", "level5", "level6"];
    // What tells a Sankey diagram's node at one level from the same value at another: a number of zero-width
    // spaces after its name, one for each level after the first, so it reads the same.
    var LEVEL_MARK = "\u200b";

    // A row's path through a network's columns, each step a mapped column's role, label and value: its source and
    // target, then each further level mapped, up to the first the row leaves empty. Null if its source or target
    // is empty.
    function pathOf(row, fields, prefs) {
        var steps = [];
        for (var i = 0; i < PATH_ROLES.length; i++) {
            var role = PATH_ROLES[i];
            if (!fields.has(role)) {
                continue;
            }
            var value = fields.value(row, role);
            if (isBlank(value)) {
                if (i < 2) {
                    return null;
                }
                break;
            }
            steps.push({role: role, level: i, label: fields.label(row, role, prefs), raw: value});
        }
        return steps.length >= 2 ? steps : null;
    }

    // A node's name without the marks that tell its level (see LEVEL_MARK).
    function nodeLabel(name) {
        return String(name).replace(/\u200b+$/, "");
    }

    // The links of a Sankey or chord diagram from the rows, each row's path (see pathOf) a link from each step to
    // the next, added up per pair, with the column (role) each end is from. A Sankey diagram's nodes are per level
    // (perLevel), so a value at two levels is two nodes (and its flows can't loop); a chord diagram's are per value.
    function linkTotals(rows, fields, settings, prefs, report, valueId, perLevel) {
        var hasValue = fields.has(valueId);
        var links = {};
        var linkKeys = [];
        var nodeTotals = {};
        var nodeRoles = {};
        var missing = 0;
        var selfLinks = 0;
        var badValue = 0;
        rows.forEach(function (row) {
            var path = pathOf(row, fields, prefs);
            if (!path) {
                missing++;
                return;
            }
            var value = hasValue ? toNumber(fields.value(row, valueId)) : 1;
            if (isNaN(value) || value < 0) {
                badValue++;
                return;
            }
            var names = path.map(function (step) {
                var name = perLevel ? step.label + new Array(step.level + 1).join(LEVEL_MARK) : step.label;
                if (nodeRoles[name] === undefined) {
                    nodeRoles[name] = step.role;
                }
                return name;
            });
            for (var i = 0; i < path.length - 1; i++) {
                var source = names[i];
                var target = names[i + 1];
                if (source === target) {
                    selfLinks++;
                    continue;
                }
                var key = source + "\u0000" + target;
                if (!links[key]) {
                    links[key] = {source: source, target: target, value: 0, fromRole: path[i].role,
                        toRole: path[i + 1].role};
                    linkKeys.push(key);
                }
                links[key].value += value;
                nodeTotals[source] = (nodeTotals[source] || 0) + value;
                nodeTotals[target] = (nodeTotals[target] || 0) + value;
            }
        });
        addDropped(report, "source or target empty", missing);
        addDropped(report, "source and target the same", selfLinks);
        addDropped(report, (fields.column(valueId) || valueId) + " not a number or negative", badValue);

        // A node or link with nothing flowing through it has nothing to draw.
        var nodeNames = Object.keys(nodeTotals).filter(function (name) {
            return nodeTotals[name] > 0;
        }).sort(function (a, b) {
            return nodeTotals[b] - nodeTotals[a];
        });
        var maxNodes = intSetting(settings, "maxNodes", 300, 2, 5000);
        if (nodeNames.length > maxNodes) {
            report.warnings.push((nodeNames.length - maxNodes) + " nodes with the least flowing through them were " +
                "left out (Max Nodes)");
            nodeNames = nodeNames.slice(0, maxNodes);
        }
        var kept = {};
        nodeNames.forEach(function (name) {
            kept[name] = true;
        });
        return {
            nodeNames: nodeNames,
            nodeRoles: nodeRoles,
            links: linkKeys.map(function (key) {
                return links[key];
            }).filter(function (link) {
                return link.value > 0 && kept[link.source] && kept[link.target];
            })
        };
    }

    // A network's node as a click selects it (node): its value as the column it is from has it.
    function nodeSelection(fields, network, name) {
        var role = (network.nodeRoles && network.nodeRoles[name]) || "source";
        var label = nodeLabel(name);
        var raw = fields.rawOf(role, label);
        if (raw === label && role === "source") {
            raw = fields.rawOf("target", label);
        }
        return fields.selection([["node", raw]]);
    }

    // A network's link as a click selects it: its two ends, by the columns they are from (source and target, or a
    // further level).
    function linkSelection(fields, link) {
        var from = link.fromRole || "source";
        var to = link.toRole || "target";
        return fields.selection([[from, fields.rawOf(from, nodeLabel(link.source))],
            [to, fields.rawOf(to, nodeLabel(link.target))]]);
    }

    // What a click on a node or link selects: the node itself (node), or the link's two ends.
    function networkSelectAt(fields, network) {
        return function (seriesIndex, dataIndex, dataType) {
            if (dataType === "edge") {
                var link = network.links[dataIndex];
                return link ? linkSelection(fields, link) : null;
            }
            var name = network.nodeNames[dataIndex];
            return name === undefined ? null : nodeSelection(fields, network, name);
        };
    }

    function sankeyModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["source", "target"]);
        var network = linkTotals(rows, fields, settings, prefs, report, "value", true);
        // A Sankey diagram can't draw a loop: the strongest flows are kept, and one that would close a loop left
        // out.
        var next = {};

        function reaches(from, to, seen) {
            if (from === to) {
                return true;
            }
            if (seen[from]) {
                return false;
            }
            seen[from] = true;
            return (next[from] || []).some(function (node) {
                return reaches(node, to, seen);
            });
        }

        var looping = 0;
        network.links = network.links.slice().sort(function (a, b) {
            return b.value - a.value;
        }).filter(function (link) {
            if (reaches(link.target, link.source, {})) {
                looping++;
                return false;
            }
            (next[link.source] = next[link.source] || []).push(link.target);
            return true;
        });
        addDropped(report, "flow would close a loop", looping);
        var linked = {};
        network.links.forEach(function (link) {
            linked[link.source] = true;
            linked[link.target] = true;
        });
        network.nodeNames = network.nodeNames.filter(function (name) {
            return linked[name];
        });
        report.series = 1;
        report.nodes = network.nodeNames.length;
        report.links = network.links.length;
        report.points = network.links.length;
        var vertical = String(textSetting(settings, "orient") || "horizontal").toLowerCase() === "vertical";
        var showLabels = boolSetting(settings, "showLabels", true);
        // A Sankey diagram's nodes take their own colours, not the chart's: given the chart's, in turn.
        var colours = distinctColours(network.nodeNames.length, style);

        return {
            tooltip: {trigger: "item"},
            selectAt: networkSelectAt(fields, network),
            series: [{
                type: "sankey",
                orient: vertical ? "vertical" : "horizontal",
                left: 16,
                right: vertical ? STROOM_BUTTON_SPACE : 120,
                top: vertical ? 40 : 16,
                bottom: vertical ? 40 : 16,
                nodeGap: 8,
                emphasis: {focus: "adjacency"},
                lineStyle: {color: "gradient", opacity: 0.35},
                label: merge({show: showLabels, color: style.text, position: vertical ? "bottom" : "right"},
                    shortened(150)),
                data: network.nodeNames.map(function (name, i) {
                    return {name: name, itemStyle: {color: colours[i]}};
                }),
                links: network.links.map(function (link) {
                    return {source: link.source, target: link.target, value: link.value};
                })
            }]
        };
    }

    function chordModel(rows, fields, settings, prefs, style, report) {
        requireFields(fields, ["source", "target"]);
        var network = linkTotals(rows, fields, settings, prefs, report, "weight");
        report.series = 1;
        report.nodes = network.nodeNames.length;
        report.links = network.links.length;
        report.points = network.links.length;
        var showLabels = boolSetting(settings, "showLabels", true);

        return {
            tooltip: {trigger: "item"},
            color: distinctColours(network.nodeNames.length, style),
            selectAt: networkSelectAt(fields, network),
            series: [{
                type: "chord",
                top: 24,
                bottom: 24,
                left: 24,
                right: STROOM_BUTTON_SPACE,
                emphasis: {focus: "adjacency"},
                label: merge({show: showLabels, color: style.text}, shortened(150)),
                lineStyle: {color: "source", opacity: 0.5},
                data: network.nodeNames.map(function (name) {
                    return {name: name};
                }),
                links: network.links.map(function (link) {
                    return {source: link.source, target: link.target, value: link.value};
                })
            }]
        };
    }

    var MODELS = {
        bar: barModel,
        line: lineModel,
        pie: pieModel,
        scatter: scatterModel,
        graph: graphModel,
        heatmap: heatmapModel,
        calendar: calendarModel,
        histogram: histogramModel,
        sankey: sankeyModel,
        chord: chordModel,
        gauge: gaugeModel,
        sunburst: sunburstModel
    };

    function newReport(kind) {
        return {
            status: "ok",
            adapter: "ECharts Generic",
            adapterVersion: ADAPTER_VERSION,
            library: "ECharts",
            libraryVersion: window.echarts ? window.echarts.version : null,
            kind: kind,
            rows: 0,
            series: 0,
            points: 0,
            nodes: null,
            links: null,
            droppedRows: {},
            warnings: [],
            width: 0,
            height: 0
        };
    }

    // Builds the full ECharts option for the data and settings. Throws an Error a user can act on when the
    // data can't make the chart. Exposed for testing.
    // Zooming: the mouse wheel zooms and dragging pans a bar or line chart's category or x axis, both axes of a
    // scatter plot, and a network graph (which roams). The reset zoom button in Stroom's toolbar under the
    // chart puts it back. A pie chart doesn't zoom.
    // ------------------------------------------------------------------------------------------
    // Value settings: units, log scale, threshold and average lines, outlier breaks
    // ------------------------------------------------------------------------------------------

    function compact(value, steps, base) {
        var abs = Math.abs(value);
        for (var i = steps.length - 1; i > 0; i--) {
            var size = Math.pow(base, i);
            if (abs >= size) {
                var scaled = value / size;
                return (Math.abs(scaled) >= 100 ? Math.round(scaled) : Math.round(scaled * 10) / 10) + steps[i];
            }
        }
        return formatNumber(value) + steps[0];
    }

    function duration(ms) {
        var abs = Math.abs(ms);
        if (abs < 1000) {
            return formatNumber(Math.round(ms * 10) / 10) + " ms";
        }
        if (abs < 60000) {
            return Math.round(ms / 100) / 10 + " s";
        }
        if (abs < 3600000) {
            return Math.round(ms / 6000) / 10 + " min";
        }
        if (abs < 86400000) {
            return Math.round(ms / 360000) / 10 + " h";
        }
        return Math.round(ms / 8640000) / 10 + " d";
    }

    // How a value is shown, by the Units setting.
    function unitFormatter(units) {
        switch (String(units || "none").toLowerCase()) {
            case "count":
                return function (value) {
                    return compact(value, ["", "k", "M", "B", "T"], 1000);
                };
            case "bytes":
                return function (value) {
                    return compact(value, [" B", " KB", " MB", " GB", " TB", " PB"], 1024);
                };
            case "percent":
                return function (value) {
                    return formatNumber(Math.round(value * 10) / 10) + " %";
                };
            case "ms":
                return duration;
            case "seconds":
                return function (value) {
                    return duration(value * 1000);
                };
            default:
                return formatNumber;
        }
    }

    // The axis a bar, line or scatter chart's values run along: x for a horizontal bar chart, else y.
    function valueAxisOf(option) {
        if (option.yAxis && option.yAxis.type === "category" && option.xAxis && option.xAxis.type !== "category") {
            return {axis: option.xAxis, dim: "x", index: 0};
        }
        return option.yAxis && option.yAxis.type !== "category" ? {axis: option.yAxis, dim: "y", index: 1} : null;
    }

    // Each series' values along the value axis: as numbers, or the y of [x, y] points.
    function eachValue(series, index, fn) {
        series.forEach(function (one) {
            (one.data || []).forEach(function (item) {
                if (Array.isArray(item)) {
                    fn(item[index]);
                } else if (typeof item === "number") {
                    fn(item);
                }
            });
        });
    }

    // Where to cut the value axis, if a few values dwarf the rest: between the largest jump (at least four times)
    // with no more than a fifth of the values above it.
    function outlierBreak(values) {
        var sorted = values.filter(function (value) {
            return value > 0;
        }).sort(function (a, b) {
            return a - b;
        });
        var best = null;
        for (var i = 0; i < sorted.length - 1; i++) {
            var ratio = sorted[i + 1] / sorted[i];
            var above = sorted.length - i - 1;
            if (ratio >= 4 && above <= Math.max(1, Math.floor(sorted.length / 5)) && (!best || ratio > best.ratio)) {
                best = {ratio: ratio, start: sorted[i] * 1.15, end: sorted[i + 1] * 0.9};
            }
        }
        return best && best.end > best.start ? {start: best.start, end: best.end, gap: "3%"} : null;
    }

    // ------------------------------------------------------------------------------------------
    // Trend lines
    // ------------------------------------------------------------------------------------------

    // A linear trend explaining less of the values' variation than this (its R²) is weak, and drawn fainter.
    var WEAK_TREND = 0.3;
    // A trend changing less than this share of the values' mean size over the time shown is flat.
    var FLAT_TREND = 0.05;
    // The most trends a report gives.
    var MAX_TRENDS = 10;
    // The units a trend's slope is given per over time, largest first: the largest the time shown spans twice.
    var TREND_UNITS = [[7 * DAY, "week"], [DAY, "day"], [HOUR, "hour"], [MINUTE, "minute"], [SECOND, "second"]];

    // The Trend Line setting: linear, moving average, or none.
    function trendMethod(settings) {
        var method = String(textSetting(settings, "trendLine") || "none").toLowerCase();
        return method === "linear" || method === "moving average" ? method : "none";
    }

    // The least-squares line through points ([x, y], x numbers or times): its slope, intercept and R²; null with
    // fewer than two points, or all at one x. Centred on the means first, as times are large numbers.
    function linearFit(points) {
        var n = points.length;
        if (n < 2) {
            return null;
        }
        var meanX = 0;
        var meanY = 0;
        points.forEach(function (point) {
            meanX += point[0] / n;
            meanY += point[1] / n;
        });
        var sxx = 0;
        var sxy = 0;
        var syy = 0;
        points.forEach(function (point) {
            var dx = point[0] - meanX;
            var dy = point[1] - meanY;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        });
        if (sxx === 0) {
            return null;
        }
        var slope = sxy / sxx;
        return {slope: slope, intercept: meanY - slope * meanX, r2: syy === 0 ? 1 : (sxy * sxy) / (sxx * syy)};
    }

    // How many points a moving average takes: Trend Window, else about a tenth of them (at most 500); odd (so it is
    // centred), at least 3, and no more than there are.
    function trendWindow(settings, count) {
        var window = intSetting(settings, "trendWindow", 0, 0, 500) || Math.min(500, Math.round(count / 10));
        window = Math.max(3, window);
        if (window % 2 === 0) {
            window++;
        }
        return Math.min(window, count % 2 === 0 ? count - 1 : count);
    }

    // Each point's mean with the points either side of it, the window centred on it (narrower at the ends), so the
    // line smooths the points without lagging behind them.
    function movingAverage(points, window) {
        var half = Math.floor(window / 2);
        // A running sum of the window: the point coming into it added, the one leaving taken away.
        var sum = 0;
        var from = 0;
        var to = -1;
        return points.map(function (point, i) {
            while (to < Math.min(points.length - 1, i + half)) {
                to++;
                sum += points[to][1];
            }
            while (from < i - half) {
                sum -= points[from][1];
                from++;
            }
            return [point[0], sum / (to - from + 1)];
        });
    }

    // A slope as the report and label give it: per the largest unit the time shown spans twice (e.g. +12/day), or
    // per one along a number axis.
    function trendSlopeText(slope, time, span, format) {
        var per = 1;
        var unit = " per 1";
        if (time) {
            var chosen = TREND_UNITS[TREND_UNITS.length - 1];
            for (var i = 0; i < TREND_UNITS.length; i++) {
                if (TREND_UNITS[i][0] * 2 <= span) {
                    chosen = TREND_UNITS[i];
                    break;
                }
            }
            per = chosen[0];
            unit = "/" + chosen[1];
        }
        var value = slope * per;
        return (value < 0 ? "-" : "+") + format(Math.abs(value)) + unit;
    }

    // The trend of one series' points ([x, y]): the line drawn ([x, value] at each point's x, in x order), its
    // label, whether it is weak, and what the report says of it (from a linear fit, whichever line is drawn). Its
    // slope is per a unit suiting the chart's whole span (unitSpan), so every series' is per the same unit. Null
    // with too few points: two at different x's, or three for a moving average.
    function seriesTrend(name, points, method, settings, time, format, unitSpan) {
        var sorted = points.filter(function (point) {
            return point && isFinite(point[0]) && isFinite(point[1]);
        }).map(function (point) {
            return [point[0], point[1]];
        }).sort(function (a, b) {
            return a[0] - b[0];
        });
        var fit = linearFit(sorted);
        if (!fit || (method === "moving average" && sorted.length < 3)) {
            return null;
        }
        var span = sorted[sorted.length - 1][0] - sorted[0][0];
        var size = sorted.reduce(function (sum, point) {
            return sum + Math.abs(point[1]);
        }, 0) / sorted.length;
        var change = fit.slope * span;
        var direction = change === 0 || Math.abs(change) < FLAT_TREND * size ? "flat"
            : (change > 0 ? "rising" : "falling");
        var slope = trendSlopeText(fit.slope, time, unitSpan || span, format);
        var r2 = Math.round(fit.r2 * 100) / 100;
        var line;
        var label;
        if (method === "moving average") {
            var window = trendWindow(settings, sorted.length);
            line = movingAverage(sorted, window);
            label = window + "-point moving average";
        } else {
            line = sorted.map(function (point) {
                return [point[0], fit.intercept + fit.slope * point[0]];
            });
            label = slope + ", R² " + r2.toFixed(2);
        }
        return {
            name: name,
            line: line,
            label: label,
            weak: method === "linear" && fit.r2 < WEAK_TREND,
            report: {series: name, method: method, direction: direction, slope: slope, r2: r2}
        };
    }

    // The trends a chart's Trend Line setting asks for, put in its report: one per series, or, stacked, one of
    // their total. Series hold values by category (each category's x in xs, null if the categories have no order
    // a trend could follow) or [x, y] points (categorical false). Refused, with a warning, when there is no x to
    // follow, or for a reason the chart gives (e.g. a y of dates).
    function chartTrends(settings, series, categorical, xs, time, stacked, format, report, refusal) {
        var method = trendMethod(settings);
        if (method === "none" || series.length === 0) {
            return [];
        }
        if (refusal || (categorical && !xs)) {
            report.warnings.push(refusal || ("Trend Line needs categories that are dates or numbers, in their own "
                + "order (not sorted by value), so no trend is drawn"));
            return [];
        }
        var inputs = series.map(function (one) {
            return {
                name: one.name,
                points: categorical ? one.data.map(function (value, i) {
                    return value === null || value === undefined ? null : [xs[i], value];
                }).filter(Boolean) : one.data
            };
        });
        if (stacked && inputs.length > 1) {
            var totals = {};
            var order = [];
            inputs.forEach(function (input) {
                input.points.forEach(function (point) {
                    if (totals[point[0]] === undefined) {
                        totals[point[0]] = 0;
                        order.push(point[0]);
                    }
                    totals[point[0]] += point[1];
                });
            });
            inputs = [{name: "Total", points: order.map(function (x) {
                return [x, totals[x]];
            })}];
        }
        var least = Infinity;
        var most = -Infinity;
        inputs.forEach(function (input) {
            input.points.forEach(function (point) {
                if (isFinite(point[0])) {
                    least = Math.min(least, point[0]);
                    most = Math.max(most, point[0]);
                }
            });
        });
        var trends = inputs.map(function (input) {
            return seriesTrend(input.name, input.points, method, settings, time, format, most - least);
        }).filter(Boolean);
        report.trends = trends.slice(0, MAX_TRENDS).map(function (trend) {
            return trend.report;
        });
        return trends;
    }

    // The x of each category, for a trend across them: their times or numbers; null when they are text, or sorted
    // by value, as a trend across them would mean nothing.
    function categoryXs(fields, id, categories, settings) {
        var sort = textSetting(settings, "sort");
        var type = fields.type(id);
        if (sort === "Ascending" || sort === "Descending" || (type !== "DATE_TIME" && type !== "NUMBER")) {
            return null;
        }
        var xs = categories.map(function (label) {
            return Number(fields.rawOf(id, label));
        });
        return xs.every(isFinite) ? xs : null;
    }

    // The trends of a bar, line or scatter chart (see chartTrends), from its series as its model made them, before
    // the value settings change them (e.g. a scatter plot's points jittered): with the x of each category, if its
    // series are by category.
    function trendsFor(kind, option, settings, fields, prefs, report) {
        if ((kind !== "bar" && kind !== "line" && kind !== "scatter") || trendMethod(settings) === "none") {
            return null;
        }
        var categories = option.xAxis && option.xAxis.type === "category" ? option.xAxis
            : (option.yAxis && option.yAxis.type === "category" ? option.yAxis : null);
        var id = kind === "bar" ? "category" : "x";
        var xs = categories ? categoryXs(fields, id, categories.data || [], settings) : null;
        var series = (option.series || []).map(function (one) {
            return {name: one.name, data: one.data || []};
        });
        var refusal = kind === "scatter" && fields.type("y") === "DATE_TIME"
            ? "Trend Line needs a y of numbers, so no trend is drawn" : null;
        var trends = chartTrends(settings, series, !!categories, xs, fields.type(id) === "DATE_TIME",
            kind !== "scatter" && boolSetting(settings, "stack", false), prefs.formatValue, report, refusal);
        return trends.length > 0 ? {trends: trends, xs: categories ? xs : null} : null;
    }

    // Draws trends as dashed lines in their series' colours (a total's in the subtle text colour), fainter when weak,
    // each labelled at its end. Each has its series' name, so hiding a series in the legend hides its trend too; a
    // total's is named Total. They are silent and out of the tooltip, so a click and the tooltip go to the chart's
    // own marks; on a log axis, a trend's values of 0 or less are left out. Added after the options apply, so they
    // take the options' colours but not their series defaults.
    function addTrendSeries(option, found, style, log) {
        if (!found) {
            return;
        }
        var names = (option.series || []).map(function (one) {
            return one.name;
        });
        var colours = option.color || [];
        // Added to a copy: a model's click handling reads its own series by index. The legend keeps to the series;
        // each trend is labelled at its end.
        option.series = (option.series || []).slice();
        if (option.legend) {
            option.legend.data = names.slice();
        }
        found.trends.forEach(function (trend) {
            var index = names.indexOf(trend.name);
            var colour = index >= 0 && colours.length > 0 ? colours[index % colours.length] : style.subtleText;
            var data = trend.line;
            if (found.xs) {
                var at = {};
                trend.line.forEach(function (point) {
                    at[point[0]] = point[1];
                });
                data = found.xs.map(function (x) {
                    return at[x] === undefined ? null : at[x];
                });
            }
            data = data.map(function (item) {
                var value = Array.isArray(item) ? item[1] : item;
                return log && value !== null && value <= 0 ? null : item;
            });
            option.series.push({
                type: "line",
                name: trend.name,
                data: data,
                silent: true,
                tooltip: {show: false},
                symbol: "none",
                showSymbol: false,
                connectNulls: true,
                z: 5,
                selectedMode: "multiple",
                emphasis: {disabled: true},
                lineStyle: {color: colour, type: "dashed", width: 1.5, opacity: trend.weak ? 0.45 : 0.9},
                itemStyle: {color: colour},
                endLabel: {
                    show: true,
                    formatter: function () {
                        return trend.label;
                    },
                    color: colour,
                    fontSize: 11,
                    // A halo in the chart's background, so the label reads over the marks under it.
                    textBorderColor: backgroundOf(style),
                    textBorderWidth: 3,
                    align: "right",
                    verticalAlign: "bottom",
                    offset: [-4, -4]
                }
            });
        });
    }

    function applyValueSettings(kind, option, settings, style, prefs, report) {
        var format = prefs.formatValue;
        var unitsSet = String(textSetting(settings, "units") || "none").toLowerCase() !== "none";
        if (unitsSet) {
            option.tooltip = merge(option.tooltip || {}, {valueFormatter: format});
            (option.series || []).forEach(function (series) {
                if (series.label && series.label.show && (series.type === "bar" || series.type === "line")) {
                    series.label.formatter = function (params) {
                        return format(Array.isArray(params.value) ? params.value[1] : params.value);
                    };
                }
            });
        }
        if (kind !== "bar" && kind !== "line" && kind !== "scatter") {
            return;
        }
        var value = valueAxisOf(option);
        if (!value) {
            return;
        }
        var series = option.series || [];
        if (unitsSet) {
            value.axis.axisLabel = merge(value.axis.axisLabel || {}, {formatter: format});
        }

        var log = boolSetting(settings, "logScale", false);
        if (log) {
            value.axis.type = "log";
            value.axis.logBase = 10;
            // A value of 0 or less has no place on a log axis: a category's is left empty, a point left out.
            var notPositive = 0;
            series.forEach(function (one) {
                var points = (one.data || []).some(Array.isArray);
                one.data = (one.data || []).map(function (item) {
                    var number = Array.isArray(item) ? item[value.index] : item;
                    if (typeof number === "number" && number <= 0) {
                        notPositive++;
                        return null;
                    }
                    return item;
                });
                if (points) {
                    one.data = one.data.filter(function (item) {
                        return item !== null;
                    });
                }
            });
            if (notPositive > 0) {
                report.warnings.push(notPositive + " values of 0 or less can't be shown on a log scale and were " +
                    "left out");
                // Those left out aren't drawn.
                report.points = Math.max(0, report.points - notPositive);
            }
        } else if (boolSetting(settings, "breakOutliers", false)) {
            var values = [];
            eachValue(series, value.index, function (item) {
                if (item !== null && isFinite(item)) {
                    values.push(item);
                }
            });
            var cut = outlierBreak(values);
            if (cut) {
                value.axis.breaks = [cut];
            }
        }

        var lines = [];
        var threshold = toNumber(textSetting(settings, "threshold"));
        if (!isNaN(threshold) && series.length > 0) {
            var label = textSetting(settings, "thresholdLabel") || format(threshold);
            var line = {
                name: label,
                lineStyle: {color: "#e5484d", type: "dashed", width: 1.5},
                label: {formatter: label, color: style.text, position: "insideEndTop"}
            };
            line[value.dim === "x" ? "xAxis" : "yAxis"] = threshold;
            lines.push(line);
        }
        if (lines.length > 0) {
            series[0].markLine = {silent: true, symbol: "none", data: lines};
        }
        if (kind !== "scatter" && boolSetting(settings, "averageLine", false)) {
            series.forEach(function (one) {
                var average = {
                    type: "average",
                    name: "Average",
                    valueDim: value.dim,
                    lineStyle: {color: style.subtleText, type: "dotted", width: 1.5},
                    label: {
                        color: style.subtleText,
                        position: "insideEndBottom",
                        formatter: function (params) {
                            return (series.length > 1 ? one.name + " average " : "Average ") + format(params.value);
                        }
                    }
                };
                one.markLine = one.markLine || {silent: true, symbol: "none", data: []};
                one.markLine.data.push(average);
            });
        }
        if (kind === "scatter" && boolSetting(settings, "jitter", false)) {
            jitterPoints(series);
        }
    }

    // A fraction from -0.5 to 0.5 that is the same for the same index, to spread overlapping points (Jitter).
    function jitterOf(index) {
        var value = Math.sin(index * 12.9898 + 78.233) * 43758.5453;
        return value - Math.floor(value) - 0.5;
    }

    // Moves each scatter point a little (up to 1% of the plot each way), the same each time, so points on the same
    // spot can be told apart. ECharts jitters only a category axis, and a scatter plot's are numbers or times. Each
    // point keeps its own x and y after its other values, for its tooltip (as arrays, which a large scatter plot
    // needs).
    function jitterPoints(series) {
        var extent = [[Infinity, -Infinity], [Infinity, -Infinity]];
        series.forEach(function (one) {
            (one.data || []).forEach(function (point) {
                [0, 1].forEach(function (d) {
                    extent[d][0] = Math.min(extent[d][0], point[d]);
                    extent[d][1] = Math.max(extent[d][1], point[d]);
                });
            });
        });
        var spans = extent.map(function (range) {
            return range[1] > range[0] ? range[1] - range[0] : 1;
        });
        var index = 0;
        series.forEach(function (one) {
            one.data = (one.data || []).map(function (point) {
                index++;
                var moved = point.slice();
                moved[0] = point[0] + jitterOf(index) * spans[0] * 0.02;
                moved[1] = point[1] + jitterOf(index + 7919) * spans[1] * 0.02;
                moved.push(point[0], point[1]);
                return moved;
            });
        });
    }

    // Zooming, put back with the toolbar above the chart: a network graph zooms and pans itself (roam).
    function addZoom(kind, option, style, settings, rangeAt) {
        if (kind !== "graph" && option.grid && option.xAxis && option.yAxis) {
            if (kind === "scatter") {
                option.dataZoom = [
                    {type: "inside", xAxisIndex: 0, filterMode: "none"},
                    {type: "inside", yAxisIndex: 0, filterMode: "none"}
                ];
            } else if (option.yAxis.type === "category") {
                // A horizontal bar chart's categories run down the y axis.
                option.dataZoom = [{type: "inside", yAxisIndex: 0}];
            } else {
                option.dataZoom = [{type: "inside", xAxisIndex: 0}];
            }
            if (boolSetting(settings, "zoomSlider", false)) {
                // A zoom bar along the bottom (or, for a horizontal bar chart, the right), driving the same axis.
                var along = option.dataZoom[0];
                // Along the plot: as wide (or high) as the grid.
                var slider = {
                    type: "slider",
                    height: along.yAxisIndex === 0 ? undefined : 16,
                    width: along.yAxisIndex === 0 ? 16 : undefined,
                    bottom: along.yAxisIndex === 0 ? undefined : 6,
                    right: along.yAxisIndex === 0 ? 8 : option.grid.right,
                    left: along.yAxisIndex === 0 ? undefined : option.grid.left,
                    top: along.yAxisIndex === 0 ? option.grid.top : undefined,
                    borderColor: style.border,
                    textStyle: {color: style.subtleText},
                    filterMode: along.filterMode
                };
                if (along.yAxisIndex === 0) {
                    slider.yAxisIndex = 0;
                    option.grid.right += 28;
                } else {
                    slider.xAxisIndex = 0;
                    option.grid.bottom += 28;
                    // A legend along the bottom goes above the slider.
                    if (option.legend && option.legend.show && option.legend.bottom === 0) {
                        option.legend.bottom = 28;
                    }
                }
                option.dataZoom.push(slider);
            }
            addRangeSelect(kind, option, style, rangeAt);
        }
    }

    // A tool to select a time range by dragging across the chart: along a time axis (a line chart's, or a scatter
    // plot's), or across bars of dates in time order (down them, for a horizontal bar chart). The range is the
    // chart's selection (from and to), for the dashboard's filters.
    function addRangeSelect(kind, option, style, rangeAt) {
        if (!option.xAxis || !option.yAxis) {
            return;
        }
        var timeAxis = (kind === "line" || kind === "scatter") && option.xAxis.type === "time";
        var dateBars = kind === "bar" && rangeAt;
        if (!timeAxis && !dateBars) {
            return;
        }
        var down = dateBars && option.yAxis.type === "category";
        var brushType = down ? "lineY" : "lineX";
        option.brush = {
            brushType: brushType,
            brushMode: "single",
            transformable: true,
            // Kept until cleared with the tool or the selection bar, so what is drawn stays the selection.
            removeOnClick: false,
            throttleType: "debounce",
            throttleDelay: 300,
            brushStyle: {borderWidth: 1, color: "rgba(120, 140, 180, 0.15)", borderColor: style.subtleText},
            outOfBrush: {colorAlpha: 0.35}
        };
        // No buttons of ECharts' own in the chart: for a brush, ECharts adds its toolbox (Box Select, Polygon
        // Select, Keep and Clear) unless there is one, so one is given, hidden. The range tool is turned on and off
        // from Stroom's toolbar above the chart instead.
        option.toolbox = {show: false};
        option.brush[down ? "yAxisIndex" : "xAxisIndex"] = 0;
    }

    // The range tool's area for a selected time range: along the time axis, or across the bars it covers (by
    // index). Null if no bar is in it.
    function brushArea(option, rangeAt, range) {
        if (!option.brush) {
            return null;
        }
        var coords = rangeAt ? rangeAt.indices(range[0], range[1]) : range;
        if (!coords) {
            return null;
        }
        var area = {brushType: option.brush.brushType, coordRange: coords};
        if (option.brush.yAxisIndex !== undefined) {
            area.yAxisIndex = 0;
        } else {
            area.xAxisIndex = 0;
        }
        return area;
    }

    // view: how the chart is shown now, or null: zoom, the window of the zoomed axes (each {start, end} as
    // percentages, by the index of their dataZoom), which Auto Interval suits its interval to; and interval, the
    // interval chosen in Stroom's toolbar (Auto or null for the one that suits).
    function buildOption(context, settings, data, prefs, style, view) {
        settings = settings || {};
        // The preferences, with how values are shown by the Units setting.
        prefs = merge({}, prefs || {});
        prefs.formatValue = unitFormatter(textSetting(settings, "units"));
        var kind = String(settings.kind || "").toLowerCase();
        if (KINDS.indexOf(kind) < 0) {
            throw new Error("Unknown chart kind '" + settings.kind + "'. Expected one of " + KINDS.join(", "));
        }
        var report = newReport(kind);
        var rows = dataRows(data);
        report.rows = rows.length;
        // Stroom sends at most Max Rows rows: at the limit, the rest of the table was left out, not summarised.
        var maxRows = Number(settings.maxValues);
        if (isFinite(maxRows) && maxRows > 0 && rows.length >= maxRows) {
            report.truncated = true;
            report.warnings.push("Showing the first " + maxRows + " rows: the table has more, which were left "
                + "out. Group the table (e.g. by a rounded time, such as floorDay()) or raise Max Rows.");
        }
        // Auto Interval: the rows grouped into an interval suiting the time shown.
        var zoomWindow = view && view.zoom && view.zoom[0]
            ? {start: view.zoom[0].start / 100, end: view.zoom[0].end / 100} : null;
        var grouped = autoInterval(kind, data, settings, prefs, zoomWindow, view ? view.interval : null);
        data = grouped.data;
        rows = dataRows(data);
        var fields = new Fields(settings, data, prefs);

        var option = MODELS[kind](rows, fields, settings, prefs, style, report);
        // What a click selects, kept off the option ECharts is given.
        var selectAt = option.selectAt || function () {
            return null;
        };
        delete option.selectAt;
        // For categories that are dates in order, what a range dragged across them stands for.
        var rangeAt = option.rangeAt || null;
        delete option.rangeAt;
        Object.keys(option).forEach(function (key) {
            if (option[key] === undefined) {
                delete option[key];
            }
        });
        // A clicked item is shown selected; Ctrl-click (or Shift-click) selects more than one. Many points are
        // drawn quickly: a line keeps its shape with fewer points (largest-triangle sampling), and a large scatter
        // plot is drawn in batches.
        (option.series || []).forEach(function (series) {
            if (series.type === "line") {
                series.sampling = "lttb";
            } else if (series.type === "scatter") {
                series.large = true;
                series.largeThreshold = LARGE_THRESHOLD;
                series.progressive = PROGRESSIVE_CHUNK;
            }
            series.selectedMode = "multiple";
            // An item is outlined with a border and a glow (white on dark). A network's link is outlined by the
            // chart class, as ECharts draws no border on a line (see syncLinkOutlines).
            series.select = merge(series.select || {}, {
                itemStyle: {borderColor: selectionColour(style), borderWidth: SELECTED_BORDER,
                    shadowColor: selectionColour(style), shadowBlur: 8}
            });
        });

        // The same colours for every kind, whatever ECharts version draws it (and as the D3 adapter draws them),
        // unless the options give others.
        option.color = option.color || distinctColours(MAX_PALETTE, style);
        // Visualisations generated before titles were dropped may still have a Title setting: it isn't drawn.
        option.title = {show: false};
        option.backgroundColor = "transparent";
        option.textStyle = {color: style.text, fontFamily: style.fontFamily};
        option.tooltip = merge(option.tooltip || {}, {
            confine: true,
            backgroundColor: style.tooltipBackground,
            borderColor: style.border,
            textStyle: {color: style.text}
        });

        // Trends are of the values as the model made them, drawn after the value settings (e.g. a log axis) apply.
        var trends = trendsFor(kind, option, settings, fields, prefs, report);
        applyValueSettings(kind, option, settings, style, prefs, report);
        addZoom(kind, option, style, settings, rangeAt);
        // The zoom kept from before (e.g. across a refresh of the table, or a new interval).
        (option.dataZoom || []).forEach(function (zoom, index) {
            var kept = view && view.zoom ? view.zoom[index] : null;
            if (kept) {
                zoom.start = kept.start;
                zoom.end = kept.end;
            }
        });

        var cleaned = sanitiseOptions(settings.options);
        cleaned.warnings.forEach(function (warning) {
            report.warnings.push(warning);
        });
        var overrides = cleaned.options;
        if (overrides.seriesDefaults) {
            option.series.forEach(function (series) {
                merge(series, JSON.parse(JSON.stringify(overrides.seriesDefaults)));
            });
            delete overrides.seriesDefaults;
        }
        merge(option, overrides);
        // Selection is the chart's own (a click selects an item, Ctrl-click or Shift-click selects more), so an
        // override of it is put back: one item at a time would leave the others selected but not shown.
        var modeOverridden = false;
        (option.series || []).forEach(function (series) {
            if (series.selectedMode !== "multiple") {
                modeOverridden = true;
                series.selectedMode = "multiple";
            }
        });
        if (modeOverridden) {
            report.warnings.push("selectedMode is the chart's own (a click selects an item, Ctrl-click or "
                + "Shift-click selects more), so the override was left out.");
        }
        addTrendSeries(option, trends, style, boolSetting(settings, "logScale", false));

        if (rows.length === 0) {
            report.status = "empty";
        } else if (report.points === 0) {
            report.status = "nothing-drawn";
        }
        return {option: option, report: report, selectAt: selectAt, rangeAt: option.brush ? rangeAt : null,
            interval: grouped.interval, intervals: grouped.intervals, intervalChoice: grouped.choice, data: data};
    }

    // ------------------------------------------------------------------------------------------
    // The visualisation class Stroom creates
    // ------------------------------------------------------------------------------------------

    visualisations.EChartsGeneric = function () {
        var element = window.document.createElement("div");
        element.className = "vis";
        element.style.position = "relative";

        var chartElement = window.document.createElement("div");
        chartElement.style.position = "absolute";
        chartElement.style.left = "0";
        chartElement.style.top = "0";
        chartElement.style.right = "0";
        chartElement.style.bottom = "0";
        element.appendChild(chartElement);

        var messageElement = window.document.createElement("div");
        messageElement.style.position = "absolute";
        messageElement.style.left = "0";
        messageElement.style.right = "0";
        messageElement.style.top = "40%";
        messageElement.style.padding = "0 16px";
        messageElement.style.textAlign = "center";
        messageElement.style.display = "none";
        element.appendChild(messageElement);

        var chart = null;
        var chartTheme = null;
        var lastArgs = null;
        var lastReportJson = null;

        this.element = element;

        function sendReport(report) {
            var json = JSON.stringify(report);
            if (json === lastReportJson) {
                return;
            }
            lastReportJson = json;
            if (window.stroom && typeof window.stroom.report === "function") {
                window.stroom.report(report);
            }
        }

        function showMessage(text, style) {
            messageElement.textContent = text;
            messageElement.style.color = style ? style.subtleText : "";
            messageElement.style.display = "block";
        }

        function hideMessage() {
            messageElement.style.display = "none";
            messageElement.textContent = "";
        }

        // The chart last drawn (its option, and what a click on it selects), and what is selected on it: a time
        // range ({range, label, selection}), or the items clicked ({items}, each with what it selects and where it
        // is drawn).
        var built = null;
        var selected = null;
        var lastStyle = null;
        var lastPrefs = null;
        var lastReport = null;

        // Tells Stroom whether something is selected on the chart (never what), with the chart's report, so the
        // agent can tell a selection is what filters the dashboard.
        function reportSelection() {
            if (lastReport) {
                lastReport.selected = !!selected;
                setView(lastReport, {
                    interval: built ? built.interval : null,
                    shown: zoomShown(),
                    selection: selected ? (selected.items ? selected.items.map(function (item) {
                        return item.label;
                    }).join("; ") : selected.label) : null,
                    hiddenSeries: hiddenNames
                });
                sendReport(lastReport);
            }
        }

        // The part of the chart zoomed in on, as the user sees it, e.g. "Day from 2026-10-14 to 2026-10-16", or
        // null if it isn't zoomed.
        function zoomShown() {
            if (!chart || !built || !zoomed) {
                return null;
            }
            var option = chart.getOption();
            var parts = [];
            (option.dataZoom || []).forEach(function (zoom, index) {
                if (zoom.type !== "inside" || (zoom.start <= 0 && zoom.end >= 100)) {
                    return;
                }
                var model = chart.getModel().getComponent("dataZoom", index);
                var range = model && model.getValueRange ? model.getValueRange() : null;
                var onX = zoom.xAxisIndex !== undefined && [].concat(zoom.xAxisIndex).length > 0;
                var axis = [].concat(onX ? option.xAxis : option.yAxis)[0] || {};
                if (!range) {
                    return;
                }
                var from;
                var to;
                if (axis.type === "time") {
                    var pattern = lastPrefs ? lastPrefs.compactTimePattern : undefined;
                    var timeZone = lastPrefs ? lastPrefs.timeZone : undefined;
                    from = formatTime(range[0], pattern, timeZone);
                    to = formatTime(range[1], pattern, timeZone);
                } else if (axis.type === "category" && Array.isArray(axis.data)) {
                    var first = Math.max(0, Math.round(range[0]));
                    var last = Math.min(axis.data.length - 1, Math.round(range[1]));
                    from = "'" + labelText(axis.data[first]) + "'";
                    to = "'" + labelText(axis.data[last]) + "' (" + (last - first + 1) + " of " + axis.data.length
                        + ")";
                } else {
                    from = formatNumber(range[0]);
                    to = formatNumber(range[1]);
                }
                parts.push((axis.name || (onX ? "x" : "y")) + " from " + from + " to " + to);
            });
            var graph = built.option.series ? graphSeries(built.option) : null;
            if (!parts.length && graph) {
                var series = (option.series || []).filter(function (item) {
                    return item.type === "graph";
                })[0];
                if (series && series.zoom && series.zoom !== 1) {
                    parts.push("zoomed in " + (Math.round(series.zoom * 10) / 10) + "x");
                }
            }
            return parts.length ? parts.join("; ") : null;
        }

        // A category's label, as an axis's data holds it.
        function labelText(item) {
            return item && typeof item === "object" && item.value !== undefined ? String(item.value) : String(item);
        }

        function selectedLabel() {
            if (!selected) {
                return "";
            }
            if (selected.range) {
                return selected.label;
            }
            var items = selected.items;
            return items.length === 1
                ? items[0].label
                : items[0].label + " and " + (items.length - 1) + " more";
        }

        // Whether the chart is zoomed in, the window of each zoomed axis (by its dataZoom's index, as percentages),
        // kept across redraws, and whether its range tool is on (dragging selects a time range).
        var zoomed = false;
        var zoomWindows = null;
        var rangeMode = false;
        // Whether some series are hidden, from the legend: Stroom's toolbar then offers to show them all.
        var hiddenSeries = false;
        var hiddenNames = [];
        // The interval chosen in Stroom's toolbar for Auto Interval (null for Auto), and whether a network graph's
        // layout is paused.
        var chosenInterval = null;
        var layoutPaused = false;
        // Auto Interval groups the rows again as the zoom changes, once it has settled.
        var intervalTimer = null;

        // Tells Stroom which of the chart's controls apply, for the toolbar it shows above the chart (reset zoom,
        // select a time range, clear the selection), with what is selected and whether rows were left out.
        function sendControls() {
            if (!window.stroom || typeof window.stroom.controls !== "function") {
                return;
            }
            var option = built ? built.option : null;
            var report = lastReport || {};
            var notes = [];
            if (built && built.interval && built.intervalChoice === "Auto") {
                notes.push("Interval: " + built.interval);
            }
            if (report.truncated) {
                notes.push("First " + report.rows + " rows (the table has more than Max Rows)");
            }
            window.stroom.controls({
                zoom: !!(option && (option.dataZoom || (option.series || []).some(function (series) {
                    return series.roam;
                }))),
                zoomed: zoomed,
                range: !!(option && option.brush),
                rangeMode: rangeMode && !!(option && option.brush),
                selectable: !!option,
                selected: selected ? selectedLabel() : null,
                download: !!option,
                hiddenSeries: hiddenSeries,
                intervals: built ? built.intervals || [] : [],
                interval: built ? built.intervalChoice : null,
                layout: !!(option && graphSeries(option) && graphSeries(option).layout === "force"
                    && graphSeries(option).force && graphSeries(option).force.layoutAnimation) || layoutPaused,
                layoutPaused: layoutPaused,
                note: notes.length ? notes.join(" · ") : null
            });
            // The agent sees what the user does: the view goes in the chart's report too.
            reportSelection();
        }

        // Turns the range tool on or off: while on, dragging across the chart selects a time range.
        function setRangeMode(on) {
            rangeMode = on;
            if (chart && built && built.option.brush) {
                chart.dispatchAction({type: "takeGlobalCursor", key: "brush", brushOption: {
                    brushType: on ? built.option.brush.brushType : false,
                    brushMode: "single"
                }});
            }
            sendControls();
        }

        // Puts the chart's zoom back: its axes' zoom, or a network graph's zoom and pan.
        function resetZoom() {
            if (!chart || !built) {
                return;
            }
            zoomWindows = null;
            if (built.interval || autoIntervalOn()) {
                // Grouped again for the whole time.
                render();
            } else if (built.option.dataZoom) {
                chart.dispatchAction({type: "dataZoom", start: 0, end: 100});
            } else {
                chart.dispatchAction({type: "restore"});
                reselect();
            }
            zoomed = false;
            sendControls();
        }

        // A network graph's series, or null.
        function graphSeries(option) {
            return (option.series || []).filter(function (series) {
                return series.type === "graph";
            })[0] || null;
        }

        // Zooms in on (or out from) the middle of what is shown: each axis's zoom window narrowed (or widened) by
        // half again, or a network graph's zoom.
        function zoomBy(factor) {
            if (!chart || !built) {
                return;
            }
            var option = chart.getOption();
            if (built.option.dataZoom && option.dataZoom) {
                option.dataZoom.forEach(function (zoom, index) {
                    if (zoom.type !== "inside") {
                        return;
                    }
                    var centre = (zoom.start + zoom.end) / 2;
                    var span = Math.max(1, Math.min(100, (zoom.end - zoom.start) / factor));
                    var start = Math.max(0, Math.min(100 - span, centre - span / 2));
                    chart.dispatchAction({type: "dataZoom", dataZoomIndex: index, start: start, end: start + span});
                });
            } else if (graphSeries(built.option)) {
                var series = (option.series || []).filter(function (item) {
                    return item.type === "graph";
                })[0];
                var zoomNow = series && series.zoom ? series.zoom : 1;
                chart.setOption({series: [{zoom: Math.max(0.2, Math.min(20, zoomNow * factor))}]});
                zoomed = true;
                sendControls();
            }
        }

        // Pauses a network graph's layout where its nodes are now, or lets it move again from there.
        function pauseLayout(pause) {
            var index = built ? (built.option.series || []).indexOf(graphSeries(built.option)) : -1;
            if (!chart || index < 0) {
                return;
            }
            var data = chart.getModel().getSeriesByIndex(index).getData();
            var nodes = built.option.series[index].data.map(function (node, i) {
                var at = data.getItemLayout(i);
                var copy = merge({}, node);
                if (at && isFinite(at[0]) && isFinite(at[1])) {
                    copy.x = at[0];
                    copy.y = at[1];
                }
                return copy;
            });
            var series = [];
            series[index] = {layout: pause ? "none" : "force", data: nodes};
            chart.setOption({series: series});
            layoutPaused = pause;
            sendControls();
        }

        function autoIntervalOn() {
            return !!(lastArgs && lastArgs.settings && boolSetting(lastArgs.settings, "autoInterval", false));
        }

        // The zoom changed: kept for the next redraw, and, for Auto Interval, the rows grouped again for the time
        // now shown, once the zoom has settled.
        function onDataZoom() {
            zoomed = true;
            var option = chart ? chart.getOption() : null;
            zoomWindows = option && option.dataZoom ? option.dataZoom.map(function (zoom) {
                return {start: zoom.start, end: zoom.end};
            }) : null;
            sendControls();
            if (autoIntervalOn() && !chosenInterval) {
                if (intervalTimer) {
                    clearTimeout(intervalTimer);
                }
                intervalTimer = setTimeout(function () {
                    intervalTimer = null;
                    render();
                }, 300);
            }
        }

        // Tells Stroom what is selected, for the dashboard's filters that use this chart's selection: a list of
        // selections, which Stroom's filters match any of.
        function sendSelection(selections) {
            if (window.stroom && typeof window.stroom.select === "function") {
                window.stroom.select(selections);
            }
        }

        function selectionKey(selection) {
            return JSON.stringify(Object.keys(selection).sort().map(function (key) {
                return [key, selection[key]];
            }));
        }

        // True if a link between two things is the same either way round: a chord diagram's, or an undirected
        // network graph's (not a Sankey diagram's flows, nor a directed graph's).
        function linksBothWays() {
            var settings = lastArgs ? lastArgs.settings || {} : {};
            var kind = String(settings.kind || "").toLowerCase();
            return kind === "chord" || (kind === "graph" && String(settings.directed) !== "true");
        }

        // What is selected, as Stroom is told it: a clicked link that is the same either way round is sent both
        // ways, so a filter on its source and target matches it in either direction.
        function selections() {
            if (!selected) {
                return [];
            }
            if (selected.range) {
                return [selected.selection];
            }
            var list = [];
            selected.items.forEach(function (item) {
                list.push(item.selection);
                var link = item.selection.source !== undefined && item.selection.target !== undefined
                    && item.selection.node === undefined;
                if (link && linksBothWays()) {
                    list.push(reversed(item.selection));
                }
            });
            return list;
        }

        function send() {
            sendSelection(selections());
            sendControls();
            reportSelection();
        }

        // Each selected link's outline, by its series and index: a copy of the link's line drawn beneath it, wider
        // by a solid line either side, kept in step with the link as the layout moves.
        var linkOutlines = {};

        // The line ECharts drew for a selected network graph link, or null (another chart, or not drawn).
        function linkLine(item) {
            try {
                var series = chart.getModel().getSeriesByIndex(item.seriesIndex);
                if (!series || series.subType !== "graph") {
                    return null;
                }
                var edge = series.getGraph().getEdgeByIndex(item.dataIndex);
                var el = edge && edge.getGraphicEl();
                return el && el.childOfName ? el.childOfName("line") || null : null;
            } catch (e) {
                return null;
            }
        }

        function copyShape(shape) {
            var copy = {};
            Object.keys(shape || {}).forEach(function (key) {
                if (typeof shape[key] !== "object" && typeof shape[key] !== "function") {
                    copy[key] = shape[key];
                }
            });
            return copy;
        }

        function removeLinkOutline(key) {
            var outline = linkOutlines[key];
            if (outline && outline.clone.parent) {
                outline.clone.parent.remove(outline.clone);
            }
            delete linkOutlines[key];
        }

        // Draws, moves or takes away the outlines of the selected links, after each frame ECharts renders.
        function syncLinkOutlines() {
            var wanted = {};
            if (chart && selected && selected.items) {
                selected.items.forEach(function (item) {
                    if (item.dataType === "edge" && item.seriesIndex !== undefined) {
                        wanted[item.seriesIndex + ":" + item.dataIndex] = item;
                    }
                });
            }
            Object.keys(linkOutlines).forEach(function (key) {
                if (!wanted[key] || linkOutlines[key].line !== linkLine(wanted[key])) {
                    removeLinkOutline(key);
                }
            });
            Object.keys(wanted).forEach(function (key) {
                var line = linkLine(wanted[key]);
                if (!line || !line.parent) {
                    return;
                }
                var shape = copyShape(line.shape);
                var shapeKey = JSON.stringify(shape);
                var outline = linkOutlines[key];
                try {
                    if (!outline) {
                        var clone = new line.constructor({
                            shape: shape,
                            silent: true,
                            z: line.z,
                            z2: (line.z2 || 0) - 1,
                            style: {
                                stroke: selectionColour(lastStyle || {dark: true}),
                                fill: null,
                                lineWidth: ((line.style && line.style.lineWidth) || 1) + 2 * LINK_OUTLINE,
                                lineCap: "round"
                            }
                        });
                        line.parent.add(clone);
                        linkOutlines[key] = {line: line, clone: clone, shapeKey: shapeKey};
                    } else if (outline.shapeKey !== shapeKey) {
                        outline.clone.setShape(shape);
                        outline.shapeKey = shapeKey;
                    }
                } catch (e) {
                    // Without an outline the link still shows as selected in the selection bar.
                }
            });
        }

        function showItem(item, type) {
            if (chart && item.seriesIndex !== undefined) {
                var action = {type: type, seriesIndex: item.seriesIndex, dataIndex: item.dataIndex};
                if (item.dataType) {
                    action.dataType = item.dataType;
                }
                chart.dispatchAction(action);
            }
        }

        function showSelected(type) {
            if (!chart || !selected) {
                return;
            }
            if (selected.range) {
                // A time range is shown as the range tool's area.
                var area = built ? brushArea(built.option, built.rangeAt, selected.range) : null;
                if (built && built.option.brush) {
                    chart.dispatchAction({type: "brush", areas: type === "select" && area ? [area] : []});
                }
            } else {
                selected.items.forEach(function (item) {
                    showItem(item, type);
                });
            }
        }

        function clearSelection() {
            showSelected("unselect");
            selected = null;
            send();
        }

        function indexOfKey(items, key) {
            for (var i = 0; i < items.length; i++) {
                if (items[i].key === key) {
                    return i;
                }
            }
            return -1;
        }

        // A click on an item selects it (or clears the selection, if it was all that was selected). Ctrl-click,
        // Cmd-click or Shift-click adds it to what is selected, or takes it away.
        function onItemClick(params) {
            if (!built || params.componentType !== "series") {
                return;
            }
            var dataType = params.dataType === "edge" ? "edge" : undefined;
            var selection = built.selectAt(params.seriesIndex, params.dataIndex, dataType);
            if (!selection) {
                return;
            }
            var item = {key: selectionKey(selection), label: selectionLabel(selection), selection: selection,
                seriesIndex: params.seriesIndex, dataIndex: params.dataIndex, dataType: dataType};
            var event = params.event && params.event.event;
            var adding = !!(event && (event.ctrlKey || event.metaKey || event.shiftKey));
            var items = selected && selected.items ? selected.items : [];
            var index = indexOfKey(items, item.key);
            if (adding && !(selected && selected.range)) {
                if (index >= 0) {
                    showItem(items[index], "unselect");
                    items = items.slice(0, index).concat(items.slice(index + 1));
                } else {
                    items = items.concat([item]);
                    showItem(item, "select");
                }
            } else if (index >= 0 && items.length === 1) {
                clearSelection();
                return;
            } else {
                showSelected("unselect");
                items = [item];
                showItem(item, "select");
            }
            selected = items.length ? {items: items} : null;
            send();
        }

        // A range selected with the range tool, or the range tool cleared.
        function onBrushEnd(params) {
            var area = params && params.areas && params.areas[0];
            var from = area && area.coordRange ? Number(area.coordRange[0]) : NaN;
            var to = area && area.coordRange ? Number(area.coordRange[1]) : NaN;
            if (!isFinite(from) || !isFinite(to)) {
                if (selected && selected.range) {
                    clearSelection();
                }
                return;
            }
            var start = Math.min(from, to);
            var end = Math.max(from, to);
            if (built && built.rangeAt) {
                // Across bars: from the first bar's time to the end of the last's.
                var times = built.rangeAt.range(from, to);
                start = times[0];
                end = times[1];
            }
            if (selected && selected.items) {
                showSelected("unselect");
            }
            var pattern = lastPrefs ? lastPrefs.compactTimePattern : undefined;
            var timeZone = lastPrefs ? lastPrefs.timeZone : undefined;
            selected = {range: [start, end],
                selection: {from: new Date(start).toISOString(), to: new Date(end).toISOString()},
                label: formatTime(start, pattern, timeZone) + " → " + formatTime(end, pattern, timeZone)};
            send();
        }

        function onBrush(params) {
            // The range tool's clear button clears the range.
            if (params && params.areas && params.areas.length === 0 && selected && selected.range
                && params.command === "clear") {
                clearSelection();
            }
        }

        // After a redraw, shows what is selected selected again, wherever it now is. An item no longer drawn
        // (e.g. filtered out) stays selected, so it can still be cleared.
        function reselect() {
            if (!selected || !built) {
                return;
            }
            if (selected.range) {
                showSelected("select");
                return;
            }
            var series = built.option.series || [];
            selected.items.forEach(function (item) {
                item.seriesIndex = undefined;
                for (var s = 0; s < series.length && item.seriesIndex === undefined; s++) {
                    // A sunburst's items are its tree's nodes, listed as ECharts numbers them (see sunburstModel).
                    var lists = [[series[s].flatData || series[s].data || [], undefined]];
                    if (series[s].links) {
                        lists.push([series[s].links, "edge"]);
                    }
                    for (var l = 0; l < lists.length && item.seriesIndex === undefined; l++) {
                        for (var i = 0; i < lists[l][0].length; i++) {
                            var selection = built.selectAt(s, i, lists[l][1]);
                            if (selection && selectionKey(selection) === item.key) {
                                item.seriesIndex = s;
                                item.dataIndex = i;
                                item.dataType = lists[l][1];
                                break;
                            }
                        }
                    }
                }
                showItem(item, "select");
            });
        }

        function ensureChart(theme) {
            if (chart && chartTheme === theme) {
                return chart;
            }
            if (chart) {
                chart.dispose();
            }
            chart = window.echarts.init(chartElement, null, {renderer: "canvas"});
            chartTheme = theme;
            chart.on("click", onItemClick);
            chart.on("brushEnd", onBrushEnd);
            chart.on("brush", onBrush);
            chart.on("datazoom", onDataZoom);
            chart.on("legendselectchanged", function (params) {
                var shown = params && params.selected ? params.selected : {};
                hiddenNames = Object.keys(shown).filter(function (name) {
                    return shown[name] === false;
                });
                hiddenSeries = hiddenNames.length > 0;
                sendControls();
            });
            chart.on("graphroam", function () {
                zoomed = true;
                sendControls();
            });
            linkOutlines = {};
            chart.on("rendered", syncLinkOutlines);
            chart.getZr().on("click", function (event) {
                // A click on no item clears the selection; a time range is cleared with the toolbar's button, as
                // dragging one ends with a click.
                if (!event.target && selected && !selected.range) {
                    clearSelection();
                }
            });
            return chart;
        }

        function render() {
            if (!lastArgs) {
                return;
            }
            var context = lastArgs.context;
            var settings = lastArgs.settings || {};
            var prefs = readPreferences(context, element);
            var style = readThemeStyle(element, prefs.theme);
            var report;
            lastStyle = style;
            lastPrefs = prefs;

            try {
                if (!window.echarts) {
                    throw new Error("The ECharts library hasn't loaded. Check that the ECharts Script doc is " +
                        "installed and is a dependency of this visualisation's script.");
                }
                if (majorVersion(window.echarts.version) < MIN_ECHARTS_MAJOR) {
                    throw new Error("This chart needs ECharts " + MIN_ECHARTS_MAJOR + " or later, but ECharts " +
                        window.echarts.version + " has loaded. Install a newer ECharts build in its Script doc.");
                }
                built = buildOption(context, settings, lastArgs.data, prefs, style, {zoom: zoomWindows,
                    interval: chosenInterval});
                report = built.report;
                var instance = ensureChart(prefs.theme);
                instance.setOption(built.option, {notMerge: true, lazyUpdate: false});
                // Drawn afresh, every series shown and the layout moving, at the zoom it had, and the range tool on
                // again if it was.
                hiddenSeries = false;
                hiddenNames = [];
                layoutPaused = false;
                zoomed = !!(zoomWindows && zoomWindows.some(function (area) {
                    return area.start > 0 || area.end < 100;
                }));
                if (rangeMode) {
                    setRangeMode(true);
                }
                reselect();
                report.width = instance.getWidth();
                report.height = instance.getHeight();

                if (report.status === "empty") {
                    showMessage("No data", style);
                } else if (report.status === "nothing-drawn") {
                    showMessage("None of the rows could be drawn: " +
                        Object.keys(report.droppedRows).join(", "), style);
                } else {
                    hideMessage();
                }
            } catch (e) {
                built = null;
                if (chart) {
                    chart.clear();
                }
                report = newReport(String(settings.kind || ""));
                report.status = "error";
                report.error = e && e.message ? e.message : String(e);
                showMessage(report.error, style);
            }
            report.selected = !!selected;
            lastReport = report;
            sendReport(report);
            // What is selected is kept while it isn't drawn (e.g. filtered out), so it can still be cleared.
            sendControls();
        }

        this.setData = function (context, settings, data) {
            // The zoom and the interval chosen are kept as the table refreshes, but not for other settings (e.g.
            // another kind of chart).
            if (lastArgs && JSON.stringify(lastArgs.settings) !== JSON.stringify(settings)) {
                zoomWindows = null;
                chosenInterval = null;
            }
            lastArgs = {context: context, settings: settings, data: data};
            render();
        };

        this.resize = function () {
            if (chart) {
                chart.resize();
                // A theme change only changes the element's class, so check for one here too.
                var prefs = readPreferences(lastArgs ? lastArgs.context : null, element);
                if (prefs.theme !== chartTheme) {
                    render();
                }
            }
        };

        // A control in Stroom's toolbar above the chart was used: resetZoom, zoomIn, zoomOut, rangeMode (turns the
        // range tool on or off), clearSelection, showAllSeries, pauseLayout, interval (given the one chosen), or
        // downloadImage or downloadData (given the file's name).
        this.command = function (name, value) {
            if (name === "resetZoom") {
                resetZoom();
            } else if (name === "rangeMode") {
                setRangeMode(!rangeMode);
            } else if (name === "clearSelection") {
                if (selected) {
                    clearSelection();
                }
            } else if (name === "zoomIn") {
                zoomBy(1.5);
            } else if (name === "zoomOut") {
                zoomBy(1 / 1.5);
            } else if (name === "pauseLayout") {
                pauseLayout(!layoutPaused);
            } else if (name === "interval") {
                chosenInterval = value && value !== "Auto" ? value : null;
                render();
            } else if (name === "showAllSeries" && chart) {
                chart.dispatchAction({type: "legendAllSelect"});
                hiddenSeries = false;
                hiddenNames = [];
                sendControls();
            } else if (name === "downloadImage" && chart && built) {
                saveFile((value || "chart") + ".png", chart.getDataURL({type: "png", pixelRatio: 2,
                    backgroundColor: backgroundOf(lastStyle)}));
            } else if (name === "downloadData" && built && lastArgs) {
                saveText((value || "chart") + ".csv", dataCsv(lastArgs.settings || {}, built.data),
                    "text/csv;charset=utf-8");
            }
        };

        this.start = function () {
            // Nothing to do: each setData redraws the whole chart.
        };

        this.end = function () {
        };

        this.teardown = function () {
            if (chart) {
                chart.dispose();
                chart = null;
            }
        };
    };

    // Exposed for testing in a browser.
    visualisations.EChartsGeneric.internals = {
        majorVersion: majorVersion,
        trendWindow: trendWindow,
        linearFit: linearFit,
        leiden: leiden,
        buildOption: buildOption,
        formatTime: formatTime,
        sanitiseOptions: sanitiseOptions,
        setView: setView,
        readPreferences: readPreferences,
        selectionLabel: selectionLabel,
        brushArea: brushArea,
        dataCsv: dataCsv,
        reversed: reversed,
        Fields: Fields,
        version: ADAPTER_VERSION
    };
})();
