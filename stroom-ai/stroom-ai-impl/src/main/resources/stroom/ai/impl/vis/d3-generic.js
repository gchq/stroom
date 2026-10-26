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

// D3 Generic - Stroom's adapter that draws Stroom AI generated visualisations with D3, versions 3 to 7.
// stroom-d3-generic-version: 1
//
// The visualisation's settings say which chart kind to draw and which table columns feed it, so no
// code is ever generated: the AI only chooses a kind, maps columns and sets a few style settings.
//
// Settings (all strings, as Stroom passes them):
//   kind     - bar | line | pie | scatter | graph | heatmap | calendar | histogram | sankey | chord | gauge
//   fields   - comma separated ids of the field controls, in the order of the values in each data row
//   options  - optional JSON object; with D3 only its color list is used
//   plus the field controls themselves (their values are column names) and the style controls.
//
// The D3 library is a separate Script doc this adapter depends on; the global d3 is read at each draw, so
// whichever version is installed is used.
//
// After each redraw it sends Stroom a render report (counts and its own messages, never data values).

if (!window.visualisations) {
    window.visualisations = {};
}

(function () {
    "use strict";

    var ADAPTER_VERSION = "1";

    var KINDS = ["bar", "line", "pie", "scatter", "graph", "heatmap", "calendar", "histogram", "sankey", "chord",
        "gauge", "sunburst"];

    // A gauge's bands: below the first band's start, then the first band, then above the second.
    var GAUGE_BAND_COLOURS = ["#3ba272", "#fac858", "#ee6666"];

    // The space kept clear at the chart's right edge. Stroom's refresh button and the chart's controls are in the
    // toolbar above the chart, so this is only a margin.
    var STROOM_BUTTON_SPACE = 16;

    // A selected item's border, and the solid line drawn either side of a selected link (half as thick).
    var SELECTED_BORDER = 3;

    var LINK_OUTLINE = SELECTED_BORDER / 2;

    var DEFAULT_TIME_PATTERN = "YYYY-MM-DD[T]HH:mm:ss.SSS[Z]";

    var DEFAULT_COMPACT_TIME_PATTERN = "YYYY-MM-DD[T]HH:mm";

    var MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December"];

    var DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

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

    // The background a saved image is drawn on: the chart's own, as the theme gives it.
    function backgroundOf(style) {
        return style && style.background ? style.background : (style && !style.dark ? "#ffffff" : "#1e2128");
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

    // ECharts' own palette, then as many more as are needed, spread round the colour wheel by the golden angle so
    // neighbouring ones differ, lighter on a dark theme.
    var BASE_PALETTE = ["#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de", "#3ba272", "#fc8452", "#9a60b4",
        "#ea7ccc"];

    function distinctColours(count, style) {
        var colours = BASE_PALETTE.slice(0, Math.min(count, BASE_PALETTE.length));
        for (var i = colours.length; i < count; i++) {
            var hue = Math.round(((i - BASE_PALETTE.length) * 137.508 + 20) % 360);
            colours.push("hsl(" + hue + ", 60%, " + (style.dark ? "62%" : "48%") + ")");
        }
        return colours;
    }

    // The colours a value runs through, smallest to largest; a dark theme's smallest is dark rather than pale.
    var COLOUR_SCALES = {
        blue: ["#e8f1fb", "#6baed6", "#08519c"],
        green: ["#e8f5e9", "#74c476", "#1b7a3a"],
        red: ["#fdecea", "#fb6a4a", "#a50f15"],
        purple: ["#f3e8fb", "#9e9ac8", "#54278f"],
        orange: ["#fff3e0", "#fd8d3c", "#a63603"],
        heat: ["#ffffcc", "#fd8d3c", "#bd0026"]
    };

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

    // The most years a calendar shows, the latest ones.
    var MAX_CALENDAR_YEARS = 5;

    // The most ranges a histogram counts in.
    var MAX_BINS = 200;

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

    // A fraction from -0.5 to 0.5 that is the same for the same index, to spread overlapping points (Jitter).
    function jitterOf(index) {
        var value = Math.sin(index * 12.9898 + 78.233) * 43758.5453;
        return value - Math.floor(value) - 0.5;
    }

    // ------------------------------------------------------------------------------------------
    // D3 across its versions
    // ------------------------------------------------------------------------------------------

    // The few D3 calls whose names differ between D3 3 (d3.scale.linear, d3.svg.line, ...) and D3 4 to 7
    // (d3.scaleLinear, d3.line, ...). Everything else the adapter uses (selections, scales' domain, range, ticks,
    // nice and invert, and the path generators' accessors) is the same in all of them. Events are the browser's own,
    // as D3 6 changed how its listeners are called.
    function compat(d3) {
        var major = parseInt(String(d3.version || "0").split(".")[0], 10) || 0;
        var v3 = major < 4;
        return {
            major: major,
            linear: function () {
                return v3 ? d3.scale.linear() : d3.scaleLinear();
            },
            log: function () {
                return v3 ? d3.scale.log() : d3.scaleLog();
            },
            time: function () {
                return v3 ? d3.time.scale.utc() : d3.scaleUtc();
            },
            line: function (smooth) {
                var line = v3 ? d3.svg.line() : d3.line();
                if (smooth) {
                    if (v3) {
                        line.interpolate("monotone");
                    } else {
                        line.curve(d3.curveMonotoneX);
                    }
                }
                return line;
            },
            area: function (smooth) {
                var area = v3 ? d3.svg.area() : d3.area();
                if (smooth) {
                    if (v3) {
                        area.interpolate("monotone");
                    } else {
                        area.curve(d3.curveMonotoneX);
                    }
                }
                return area;
            },
            arc: function () {
                return v3 ? d3.svg.arc() : d3.arc();
            }
        };
    }

    // ------------------------------------------------------------------------------------------
    // Colours and text
    // ------------------------------------------------------------------------------------------

    // The colours of the series or categories: the Options' color list if given, else the palette.
    function paletteOf(options, count, style) {
        var given = options && Array.isArray(options.color) ? options.color.filter(function (colour) {
            return typeof colour === "string" && SAFE_COLOUR.test(colour);
        }) : [];
        if (given.length === 0) {
            return distinctColours(count, style);
        }
        var colours = [];
        for (var i = 0; i < count; i++) {
            colours.push(given[i % given.length]);
        }
        return colours;
    }

    // A colour as a CSS value: a hex, rgb(a) or hsl(a) colour or a colour name, nothing that could carry a URL.
    var SAFE_COLOUR = /^(#[0-9a-fA-F]{3,8}|(rgb|rgba|hsl|hsla)\([0-9.,%\s]+\)|[a-zA-Z]{3,20})$/;

    function hexToRgb(hex) {
        var value = hex.replace("#", "");
        if (value.length === 3) {
            value = value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) +
                value.charAt(2);
        }
        return [parseInt(value.substring(0, 2), 16), parseInt(value.substring(2, 4), 16),
            parseInt(value.substring(4, 6), 16)];
    }

    // A colour part way along a colour scale's colours (each a hex colour), as rgb().
    function colourAt(colours, fraction) {
        var f = Math.max(0, Math.min(1, isFinite(fraction) ? fraction : 0)) * (colours.length - 1);
        var i = Math.min(colours.length - 2, Math.floor(f));
        var a = hexToRgb(colours[i]);
        var b = hexToRgb(colours[i + 1]);
        var t = f - i;
        return "rgb(" + [0, 1, 2].map(function (c) {
            return Math.round(a[c] + (b[c] - a[c]) * t);
        }).join(",") + ")";
    }

    // The colours a value runs through for a heatmap or calendar, by the Colour Scale setting.
    function scaleColours(settings, style) {
        var name = String(textSetting(settings, "colourScale") || "blue").toLowerCase();
        var colours = (COLOUR_SCALES[name] || COLOUR_SCALES.blue).slice();
        if (style.dark && name !== "heat") {
            colours[0] = "#26303b";
        }
        return colours;
    }

    // The width text is drawn at, roughly: SVG text can't be measured before it is drawn in every browser.
    function textWidth(text, fontSize) {
        return String(text).length * fontSize * 0.58;
    }

    function shorten(text, max) {
        var value = String(text);
        return value.length > max ? value.substring(0, max - 1) + "…" : value;
    }

    // ------------------------------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------------------------------

    // ECharts' sizes, so the two draw alike: text, legend items and the gaps between them.
    var FONT_SIZE = 12;
    var SMALL_FONT_SIZE = 12;
    var LEGEND_ICON_WIDTH = 25;
    var LEGEND_ICON_HEIGHT = 14;
    var LEGEND_ITEM_GAP = 10;
    var LEGEND_ROW = 28;
    var LEGEND_RIGHT_WIDTH = 140;
    // ECharts' threshold line colour.
    var THRESHOLD_COLOUR = "#e5484d";

    function svgText(parent, text, x, y, style, options) {
        options = options || {};
        var node = parent.append("text")
            .attr("x", x)
            .attr("y", y)
            .attr("fill", options.colour || style.text)
            .attr("font-size", options.size || FONT_SIZE)
            .attr("font-family", style.fontFamily)
            .attr("text-anchor", options.anchor || "start")
            .attr("dominant-baseline", options.baseline || "auto")
            .text(text);
        if (options.rotate) {
            node.attr("transform", "rotate(" + options.rotate + "," + x + "," + y + ")");
        }
        if (options.weight) {
            node.attr("font-weight", options.weight);
        }
        return node;
    }

    // A round interval for an axis's ticks, as ECharts picks one: 1, 2, 3 or 5 times a power of ten.
    function niceInterval(raw) {
        if (!(raw > 0) || !isFinite(raw)) {
            return 1;
        }
        var power = Math.pow(10, Math.floor(Math.log(raw) / Math.LN10));
        var fraction = raw / power;
        var nice = fraction < 1.5 ? 1 : fraction < 2.5 ? 2 : fraction < 4 ? 3 : fraction < 7 ? 5 : 10;
        return nice * power;
    }

    // A linear scale over values, rounded out to whole intervals with about five of them, as ECharts' value axis
    // is; its ticks are those intervals.
    function niceLinear(C, min, max, range) {
        var interval = niceInterval((max - min) / 5);
        // Rounded out to whole intervals: a value a hair from one (by rounding error) is on it.
        var round = function (value, out) {
            var steps = value / interval;
            // (+ 0 so that minus zero reads as 0.)
            return (Math.abs(steps - Math.round(steps)) < 1e-12 ? Math.round(steps) : out(steps)) * interval + 0;
        };
        var from = round(min, Math.floor);
        var to = round(max, Math.ceil);
        if (!(to > from)) {
            to = from + interval;
        }
        var scale = C.linear().domain([from, to]).range(range);
        var ticks = [];
        for (var tick = from; tick <= to + interval / 2; tick += interval) {
            ticks.push(Math.round(tick / interval) * interval + 0);
        }
        scale.niceTicks = ticks;
        return scale;
    }

    // The ticks of a value axis: about five, as ECharts splits one; on a log axis, its powers of ten.
    function axisTicks(scale, count) {
        // Fewer, if there isn't room for them all: every other one, or every third, ...
        if (scale.niceTicks) {
            // About five ticks may be a few more, as ECharts' are.
            var every = scale.niceTicks.length <= count * 1.6 + 1 ? 1 :
                Math.max(1, Math.ceil((scale.niceTicks.length - 1) / Math.max(1, count)));
            return scale.niceTicks.filter(function (tick, i) {
                return i % every === 0;
            });
        }
        // An axis cut for outliers is ticked either side of the cut.
        if (scale.cutTicks) {
            return scale.cutTicks;
        }
        var ticks = scale.ticks(count);
        if (scale.isLog) {
            var powers = ticks.filter(function (tick) {
                var power = Math.log(tick) / Math.LN10;
                return Math.abs(power - Math.round(power)) < 1e-9;
            });
            if (powers.length >= 2) {
                return powers;
            }
        }
        return ticks;
    }

    // A value (or time) axis along the bottom (horizontal) or left (vertical) of the plot area, drawn as ECharts
    // draws one: a value axis with grid lines across the plot and no axis line, a time axis with an axis line and
    // no grid lines. Along the bottom, as many ticks as their labels have room for, and no label that would overlap
    // the last.
    function drawValueAxis(g, scale, horizontal, plot, format, style, name, time) {
        var ticks = axisTicks(scale, 5);
        if (horizontal) {
            var widest = ticks.reduce(function (most, tick) {
                return Math.max(most, textWidth(format(tick), SMALL_FONT_SIZE));
            }, 0);
            var fits = Math.max(2, Math.floor(plot.width / (widest + 16)));
            if (fits < ticks.length) {
                ticks = axisTicks(scale, fits);
            }
        }
        var axis = g.append("g").attr("class", "axis");
        var labelEnd = -Infinity;
        ticks.forEach(function (tick) {
            var at = scale(tick);
            if (!isFinite(at)) {
                return;
            }
            if (horizontal) {
                if (!time) {
                    gridLine(g.gridLayer || axis, at, 0, at, plot.height, style);
                }
                var label = format(tick);
                var half = textWidth(label, SMALL_FONT_SIZE) / 2;
                // Kept within the chart: an end label is moved in rather than cut off.
                var centre = Math.max(half - plot.left + 2, Math.min(plot.width + 14 - half, at));
                if (centre - half >= labelEnd + 6) {
                    svgText(axis, label, centre, plot.height + 16, style,
                        {anchor: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
                    labelEnd = centre + half;
                }
            } else {
                gridLine(g.gridLayer || axis, 0, at, plot.width, at, style);
                // No label too close to the last (as either side of an axis cut can be).
                if (Math.abs(at - labelEnd) >= 14 || labelEnd === -Infinity) {
                    svgText(axis, format(tick), -8, at, style,
                        {anchor: "end", baseline: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
                    labelEnd = at;
                }
            }
        });
        if (time) {
            axisLine(axis, horizontal, plot, style);
        }
        if (scale.cut) {
            drawAxisBreak(axis, scale.cut, horizontal, plot, style);
        }
        axisName(axis, name, horizontal, plot, style);
    }

    // Where an axis is cut for outliers, a zigzag band across the plot, as ECharts draws its axis breaks.
    function drawAxisBreak(axis, cut, horizontal, plot, style) {
        var a = Math.min(cut.at[0], cut.at[1]);
        var b = Math.max(cut.at[0], cut.at[1]);
        var across = horizontal ? plot.height : plot.width;
        var edge = function (at, flip) {
            var points = [];
            for (var i = 0; i <= Math.ceil(across / 5); i++) {
                var offset = (i % 2 === 0 ? -2 : 2) * (flip ? -1 : 1);
                var along = Math.min(across, i * 5);
                points.push(horizontal ? [at + offset, along] : [along, at + offset]);
            }
            return points;
        };
        var points = edge(a, false).concat(edge(b, true).reverse());
        axis.append("path").attr("d", "M" + points.join("L") + "Z").attr("fill", style.subtleText)
            .attr("fill-opacity", 0.5).attr("stroke", style.subtleText).attr("stroke-width", 1)
            .attr("pointer-events", "none");
    }

    function gridLine(axis, x1, y1, x2, y2, style) {
        axis.append("line").attr("x1", x1).attr("y1", y1).attr("x2", x2).attr("y2", y2)
            .attr("stroke", style.border).attr("stroke-opacity", 0.6);
    }

    // The line of a category or time axis, along the bottom or left of the plot area.
    function axisLine(axis, horizontal, plot, style) {
        axis.append("line")
            .attr("x1", 0).attr("y1", horizontal ? plot.height : 0)
            .attr("x2", horizontal ? plot.width : 0).attr("y2", plot.height)
            .attr("stroke", style.border);
    }

    // An axis's name, centred along it, beyond its labels.
    function axisName(axis, name, horizontal, plot, style) {
        if (!name) {
            return;
        }
        if (horizontal) {
            svgText(axis, name, plot.width / 2, plot.height + 34, style,
                {anchor: "middle", colour: style.subtleText});
        } else {
            svgText(axis, name, -plot.left + 12, plot.height / 2, style,
                {anchor: "middle", colour: style.subtleText, rotate: -90});
        }
    }

    // A category axis: its line, a tick between bands and a label under (or beside) each band, thinned out when
    // they would overlap.
    function drawCategoryAxis(g, categories, band, horizontal, plot, style, name) {
        var axis = g.append("g").attr("class", "axis");
        var step = band.step;
        var widest = categories.reduce(function (most, category) {
            return Math.max(most, textWidth(shorten(category, 20), SMALL_FONT_SIZE));
        }, 0);
        var every = horizontal ? Math.max(1, Math.ceil((Math.min(widest, 120) + 8) / Math.max(1, step))) :
            Math.max(1, Math.ceil(16 / Math.max(1, step)));
        var length = horizontal ? plot.width : plot.height;
        categories.forEach(function (category, i) {
            var at = band.centre ? band.centre(i) : band.position(i) + band.width / 2;
            var edge = band.edge ? band.edge(i) : at - step / 2;
            if (edge >= -0.5 && edge <= length + 0.5) {
                if (horizontal) {
                    axis.append("line").attr("x1", edge).attr("x2", edge).attr("y1", plot.height)
                        .attr("y2", plot.height + 5).attr("stroke", style.border);
                } else {
                    axis.append("line").attr("y1", edge).attr("y2", edge).attr("x1", -5).attr("x2", 0)
                        .attr("stroke", style.border);
                }
            }
            if (i % every !== 0 || at < -1 || at > length + 1) {
                return;
            }
            if (horizontal) {
                svgText(axis, shorten(category, 20), at, plot.height + 16, style,
                    {anchor: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            } else {
                svgText(axis, shorten(category, 20), -8, at, style,
                    {anchor: "end", baseline: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            }
        });
        axisLine(axis, horizontal, plot, style);
        axisName(axis, name, horizontal, plot, style);
    }

    // Bands along an axis of a given length for a number of categories, and the part of them zoomed in on.
    function bands(count, length, padding, zoom) {
        var start = zoom ? zoom.start : 0;
        var end = zoom ? zoom.end : 1;
        var span = Math.max(1e-6, end - start);
        var step = length / Math.max(1, count) / span;
        var offset = -start * count * step;
        return {
            step: step,
            width: Math.max(1, step * (1 - padding)),
            position: function (i) {
                return offset + i * step + step * padding / 2;
            },
            visible: function (i) {
                var at = offset + i * step;
                return at + step > 0 && at < length;
            }
        };
    }

    // An axis a time range is dragged along (see the range tool), by pixels from the plot's start along it: a
    // time scale's (a line chart's, or a scatter plot's).
    function timeRangeAxis(scale, plot, group) {
        return {
            plot: plot,
            group: group,
            down: false,
            range: function (a, b) {
                var from = scale.invert(Math.min(a, b));
                var to = scale.invert(Math.max(a, b));
                return [from instanceof Date ? from.getTime() : Number(from),
                    to instanceof Date ? to.getTime() : Number(to)];
            },
            pixels: function (start, end) {
                return [scale(new Date(start)), scale(new Date(end))];
            }
        };
    }

    // Bars of dates in time order, a time range dragged across them (down them, for a horizontal bar chart): from
    // the first bar's time to the end of the last's (see categoryTimes).
    function barRangeAxis(times, band, count, plot, group, down) {
        function indexAt(at) {
            return Math.max(0, Math.min(count - 1, Math.floor((at - band.position(0) + band.step * 0.1) /
                band.step)));
        }
        return {
            plot: plot,
            group: group,
            down: down,
            range: function (a, b) {
                return times.range(indexAt(a), indexAt(b));
            },
            pixels: function (start, end) {
                var indices = times.indices(start, end);
                return indices ? [band.position(indices[0]) - band.step * 0.1,
                    band.position(indices[1]) + band.width + band.step * 0.1] : null;
            }
        };
    }

    // Points along an axis for a number of categories, the first at its start and the last at its end (as ECharts
    // places a line chart's), and the part of them zoomed in on.
    function pointPositions(count, length, zoom) {
        var start = zoom ? zoom.start : 0;
        var end = zoom ? zoom.end : 1;
        var span = Math.max(1e-6, end - start);
        var step = count > 1 ? length / (count - 1) / span : length;
        var offset = count > 1 ? -start * (count - 1) * step : length / 2;
        return {
            step: step,
            width: 0,
            centre: function (i) {
                return offset + i * step;
            },
            position: function (i) {
                return offset + i * step;
            },
            edge: function (i) {
                return offset + i * step;
            },
            visible: function (i) {
                var at = offset + i * step;
                return at >= -1 && at <= length + 1;
            }
        };
    }

    // The ticks of a value axis's labels, as the Units setting shows them, and how wide the widest is.
    function labelWidth(scale, format) {
        return axisTicks(scale, 5).reduce(function (most, tick) {
            return Math.max(most, textWidth(format(tick), SMALL_FONT_SIZE));
        }, 0);
    }

    // A legend item's icon, as ECharts draws it for the kind of series: a rounded bar, a line through a hollow
    // point, or a point.
    function legendIcon(item, icon, colour, off, style) {
        var fill = off ? style.border : colour;
        if (icon === "line") {
            item.append("line").attr("x1", 0).attr("x2", LEGEND_ICON_WIDTH).attr("y1", 0).attr("y2", 0)
                .attr("stroke", fill).attr("stroke-width", 2);
            item.append("circle").attr("cx", LEGEND_ICON_WIDTH / 2).attr("cy", 0).attr("r", 4)
                .attr("fill", style.dark ? "#ffffff" : "#ffffff").attr("stroke", fill).attr("stroke-width", 2);
        } else if (icon === "circle") {
            item.append("circle").attr("cx", LEGEND_ICON_HEIGHT / 2).attr("cy", 0).attr("r", LEGEND_ICON_HEIGHT / 2)
                .attr("fill", fill);
            return LEGEND_ICON_HEIGHT;
        } else {
            item.append("rect").attr("x", 0).attr("y", -LEGEND_ICON_HEIGHT / 2).attr("width", LEGEND_ICON_WIDTH)
                .attr("height", LEGEND_ICON_HEIGHT).attr("rx", 4).attr("fill", fill);
        }
        return LEGEND_ICON_WIDTH;
    }

    // A legend of series (or categories) and their colours, as ECharts lays one out: along the top (from the
    // left), along the bottom (centred) or down the right (centred). Clicking an entry hides or shows it. When
    // the entries don't fit, they are shown a page at a time, with arrows to page through them.
    function drawLegend(ctx, names, colours, position, icon) {
        var svg = ctx.svg;
        var size = ctx.size;
        var style = ctx.style;
        var hidden = ctx.hidden;
        if (position === "none" || names.length < 2) {
            return 0;
        }
        var vertical = position === "right";
        var items = names.map(function (name, i) {
            var label = shorten(name, 24);
            var iconWidth = icon === "circle" ? LEGEND_ICON_HEIGHT : LEGEND_ICON_WIDTH;
            return {name: name, label: label, colour: colours[i % colours.length],
                width: iconWidth + 5 + textWidth(label, SMALL_FONT_SIZE)};
        });
        var pagerWidth = 70;
        var available = vertical ? size.height - 16 : size.width - 16 - STROOM_BUTTON_SPACE;
        // Pages of entries, each as many as fit (with the pager, if there is more than one).
        var pages = [[]];
        var used = 0;
        var total = items.reduce(function (sum, item) {
            return sum + (vertical ? 20 : item.width + LEGEND_ITEM_GAP);
        }, 0);
        var room = total > available ? available - (vertical ? 24 : pagerWidth) : available;
        items.forEach(function (item) {
            var length = vertical ? 20 : item.width + LEGEND_ITEM_GAP;
            if (used + length > room && pages[pages.length - 1].length > 0) {
                pages.push([]);
                used = 0;
            }
            pages[pages.length - 1].push(item);
            used += length;
        });
        var page = Math.max(0, Math.min(pages.length - 1, ctx.legendPage || 0));
        var shown = pages[page];
        var legend = svg.append("g").attr("class", "legend");
        var widest = shown.reduce(function (most, item) {
            return Math.max(most, item.width);
        }, 0);
        var length = shown.reduce(function (sum, item) {
            return sum + (vertical ? 20 : item.width + LEGEND_ITEM_GAP);
        }, 0) - (vertical ? 0 : LEGEND_ITEM_GAP);
        var x;
        var y;
        if (vertical) {
            x = size.width - 8 - Math.min(LEGEND_RIGHT_WIDTH - 16, widest);
            y = Math.max(10, (size.height - length - (pages.length > 1 ? 24 : 0)) / 2 + 10);
        } else {
            x = position === "bottom" ? Math.max(8, (size.width - length - (pages.length > 1 ? pagerWidth : 0)) / 2) :
                8;
            // Along the bottom, above a zoom slider there.
            y = position === "bottom" ? size.height - 12 - (hasZoomSlider(ctx) && !ctx.categoriesDown ? 28 : 0) : 12;
        }
        shown.forEach(function (entry) {
            var item = legend.append("g").attr("class", "legend-item")
                .attr("transform", "translate(" + x + "," + y + ")").style("cursor", "pointer");
            var off = hidden && hidden[entry.name];
            var iconWidth = legendIcon(item, icon, entry.colour, off, style);
            svgText(item, entry.label, iconWidth + 5, 0, style, {colour: off ? style.subtleText : style.text,
                size: SMALL_FONT_SIZE, baseline: "middle"});
            item.node().addEventListener("click", function (event) {
                event.stopPropagation();
                ctx.toggleSeries(entry.name);
            });
            if (vertical) {
                y += 20;
            } else {
                x += entry.width + LEGEND_ITEM_GAP;
            }
        });
        if (pages.length > 1) {
            drawPager(ctx, legend, page, pages.length, vertical ? x : x + 4, vertical ? y + 4 : y);
        }
        if (vertical) {
            return LEGEND_RIGHT_WIDTH;
        }
        return LEGEND_ROW;
    }

    // Arrows to page through a legend's entries, with the page shown, e.g. ◀ 2/3 ▶.
    function drawPager(ctx, legend, page, pageCount, x, y) {
        var style = ctx.style;
        var pager = legend.append("g").attr("class", "legend-pager")
            .attr("transform", "translate(" + x + "," + y + ")");
        [[-1, "\u25c0", 0], [1, "\u25b6", 48]].forEach(function (arrow) {
            var next = page + arrow[0];
            var enabled = next >= 0 && next < pageCount;
            var button = svgText(pager, arrow[1], arrow[2], 0, style, {colour: enabled ? style.text : style.border,
                size: SMALL_FONT_SIZE, baseline: "middle"});
            if (enabled) {
                button.style("cursor", "pointer");
                button.node().addEventListener("click", function (event) {
                    event.stopPropagation();
                    ctx.setLegendPage(next);
                });
            }
        });
        svgText(pager, (page + 1) + "/" + pageCount, 30, 0, style, {anchor: "middle", colour: style.subtleText,
            size: SMALL_FONT_SIZE, baseline: "middle"});
    }

    // A horizontal gradient bar showing a colour scale's range, along the bottom.
    function drawScaleBar(svg, colours, min, max, format, size, style) {
        var width = 140;
        var x = (size.width - width) / 2;
        var y = size.height - 22;
        var bar = svg.append("g").attr("class", "scale");
        for (var i = 0; i < 28; i++) {
            bar.append("rect").attr("x", x + i * width / 28).attr("y", y).attr("width", width / 28 + 0.5)
                .attr("height", 8).attr("fill", colourAt(colours, i / 27));
        }
        svgText(bar, format(min), x - 6, y + 8, style, {anchor: "end", colour: style.subtleText,
            size: SMALL_FONT_SIZE});
        svgText(bar, format(max), x + width + 6, y + 8, style, {colour: style.subtleText, size: SMALL_FONT_SIZE});
        return 30;
    }

    // A line across the plot at a value, as ECharts marks one: a threshold dashed in red, labelled above its end;
    // an average dotted, labelled below its end. Along a horizontal value axis the line is upright, labelled along
    // it at the top.
    function drawValueLine(g, scale, horizontalValues, plot, value, label, style, average) {
        var at = scale(value);
        if (!isFinite(at) || at < -1 || at > (horizontalValues ? plot.width : plot.height) + 1) {
            return;
        }
        var colour = average ? style.subtleText : THRESHOLD_COLOUR;
        var line = g.append("line").attr("stroke", colour).attr("stroke-width", 1.5)
            .attr("stroke-dasharray", average ? "2,3" : "6,4");
        var labelColour = average ? style.subtleText : style.text;
        if (horizontalValues) {
            // Labelled at the bottom, as ECharts labels it, its categories reading down from the top.
            line.attr("x1", at).attr("x2", at).attr("y1", 0).attr("y2", plot.height);
            var bottom = plot.height - 4 - textWidth(label, SMALL_FONT_SIZE);
            svgText(g, label, average ? at - 4 : at + 4, bottom, style, {colour: labelColour, size: SMALL_FONT_SIZE,
                rotate: 90, baseline: average ? "auto" : "hanging"});
        } else {
            line.attr("x1", 0).attr("x2", plot.width).attr("y1", at).attr("y2", at);
            svgText(g, label, plot.width - 4, average ? at + 4 : at - 5, style, {colour: labelColour, anchor: "end",
                size: SMALL_FONT_SIZE, baseline: average ? "hanging" : "auto"});
        }
    }

    // ------------------------------------------------------------------------------------------
    // Charts on axes: bar, line, scatter, histogram
    // ------------------------------------------------------------------------------------------

    // The plot area: the space left for the chart once the legend and the axes' labels have theirs, as ECharts
    // leaves it (room on the right for Stroom's buttons).
    function plotArea(ctx, legend, left, bottomExtra) {
        var top = 16 + (legend.position === "top" ? legend.space : 0);
        var bottom = 26 + (bottomExtra || 0) + (legend.position === "bottom" ? legend.space : 0);
        var right = legend.position === "right" ? legend.space : STROOM_BUTTON_SPACE;
        // Room for the zoom slider: along the bottom, or down the right of a horizontal bar chart.
        if (hasZoomSlider(ctx)) {
            if (ctx.categoriesDown) {
                right += 28;
            } else {
                bottom += 28;
            }
        }
        return {
            left: left,
            top: top,
            width: Math.max(10, ctx.size.width - left - right),
            height: Math.max(10, ctx.size.height - top - bottom)
        };
    }

    // True if the chart has a zoom slider: a chart on axes, with the Zoom Slider setting on.
    function hasZoomSlider(ctx) {
        var kind = String(ctx.settings.kind || "").toLowerCase();
        return boolSetting(ctx.settings, "zoomSlider", false) && ["bar", "line", "scatter", "histogram"]
            .indexOf(kind) >= 0;
    }

    // The legend's position and the space it takes, once drawn, with an icon for the kind of series (see
    // legendIcon).
    function legendOf(ctx, names, colours, icon) {
        var position = textSetting(ctx.settings, "legend") || "top";
        var space = drawLegend(ctx, names, colours, position, icon);
        return {position: space > 0 ? position : "none", space: space};
    }

    // A value scale for values (linear, or log when the Log Scale setting is on and the values allow), its domain
    // padded and, as ECharts' is, including zero.
    function valueScaleOf(ctx, values, range) {
        var log = boolSetting(ctx.settings, "logScale", false);
        var finite = values.filter(function (value) {
            return isFinite(value) && (!log || value > 0);
        });
        if (log && finite.length < values.filter(isFinite).length) {
            ctx.report.warnings.push((values.filter(isFinite).length - finite.length) + " values of 0 or less " +
                "can't be shown on a log scale and were left out");
        }
        var min = finite.length ? Math.min.apply(null, finite) : 0;
        var max = finite.length ? Math.max.apply(null, finite) : 1;
        var threshold = decimalSetting(ctx.settings, "threshold", NaN);
        if (isFinite(threshold) && (!log || threshold > 0)) {
            min = Math.min(min, threshold);
            max = Math.max(max, threshold);
        }
        var scale;
        var cut = !log && boolSetting(ctx.settings, "breakOutliers", false) ? outlierBreak(finite) : null;
        if (log) {
            scale = ctx.C.log().domain([min > 0 ? min : 1, max > min ? max : min * 10 || 10]).range(range).nice();
        } else if (cut && Math.min(0, min) < cut.start && cut.end < max) {
            scale = brokenScale(ctx, Math.min(0, min), max, cut, range);
        } else {
            min = Math.min(0, min);
            max = Math.max(0, max);
            if (!(max > min)) {
                max = min + 1;
            }
            scale = niceLinear(ctx.C, min, max, range);
        }
        scale.isLog = log;
        return scale;
    }

    // A value scale cut where a few values dwarf the rest (see outlierBreak), as ECharts cuts an axis: the cut
    // shrunk to a sliver (3% of the axis), each side as long as the values it spans.
    function brokenScale(ctx, min, max, cut, range) {
        var below = ctx.C.linear().domain([min, cut.start]).nice(3);
        var above = ctx.C.linear().domain([cut.end, max]).nice(3);
        var low = below.domain();
        var high = above.domain();
        var lowEnd = Math.min(low[1], cut.start * 1.2);
        var highStart = Math.max(high[0], lowEnd * 1.0001);
        var spans = (lowEnd - low[0]) + (high[1] - highStart);
        var length = range[1] - range[0];
        var first = range[0] + length * 0.97 * (lowEnd - low[0]) / spans;
        var second = first + length * 0.03;
        var scale = ctx.C.linear().domain([low[0], lowEnd, highStart, high[1]])
            .range([range[0], first, second, range[1]]);
        scale.cut = {start: lowEnd, end: highStart, at: [first, second]};
        scale.cutTicks = below.ticks(3).filter(function (tick) {
            return tick <= lowEnd;
        }).concat(above.ticks(3).filter(function (tick) {
            return tick >= highStart;
        }));
        return scale;
    }

    // The threshold and average lines a chart's settings ask for, across the plot: an average for each series
    // (named, if there are several).
    function drawValueLines(ctx, g, scale, valuesHorizontal, plot, averages) {
        var threshold = decimalSetting(ctx.settings, "threshold", NaN);
        if (isFinite(threshold)) {
            drawValueLine(g, scale, valuesHorizontal, plot, threshold,
                textSetting(ctx.settings, "thresholdLabel") || ctx.format(threshold), ctx.style, false);
        }
        (averages || []).forEach(function (average) {
            if (isFinite(average.value)) {
                drawValueLine(g, scale, valuesHorizontal, plot, average.value, ((averages.length > 1 ?
                    average.name + " average " : "Average ") + ctx.format(average.value)), ctx.style, true);
            }
        });
    }

    // Outlines a mark (a bar, point, slice or cell) when it is selected.
    function outline(node, ctx, selected) {
        if (selected) {
            node.attr("stroke", selectionColour(ctx.style)).attr("stroke-width", SELECTED_BORDER);
        }
        return node;
    }

    function plotGroup(ctx, plot) {
        var clipId = "stroom-d3-clip-" + (ctx.clipCount = (ctx.clipCount || 0) + 1) + "-" + ctx.id;
        ctx.svg.append("defs").append("clipPath").attr("id", clipId).append("rect")
            .attr("x", 0).attr("y", -2).attr("width", plot.width).attr("height", plot.height + 4);
        var g = ctx.svg.append("g").attr("transform", "translate(" + plot.left + "," + plot.top + ")");
        // Grid lines behind the marks, and points and labels in front of them, not cut off at the plot's edge (as
        // ECharts draws a point whose middle is in the plot whole).
        g.gridLayer = g.append("g").attr("class", "grid");
        var marks = g.append("g").attr("clip-path", "url(#" + clipId + ")");
        var points = g.append("g").attr("class", "points");
        var labels = g.append("g").attr("class", "labels");
        return {axes: g, marks: marks, points: points, labels: labels, inPlot: function (x, y) {
            return x >= -1 && x <= plot.width + 1 && y >= -1 && y <= plot.height + 1;
        }};
    }

    function drawBar(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["category", "value"]);
        var matrix = categoryMatrix(ctx.rows, fields, settings, ctx.prefs, ctx.report, "category", "value");
        var horizontal = boolSetting(settings, "horizontal", false);
        ctx.categoriesDown = horizontal;
        var stack = boolSetting(settings, "stack", false);
        var showLabels = boolSetting(settings, "showLabels", false);
        var colours = paletteOf(ctx.options, matrix.series.length, ctx.style);
        var shown = matrix.series.filter(function (series) {
            return !ctx.hidden[series.name];
        });
        var legend = legendOf(ctx, matrix.series.map(function (series) {
            return series.name;
        }), colours, "rect");

        // The values the axis must reach: each bar's, or each stack's top and bottom.
        var values = [];
        matrix.categories.forEach(function (category, i) {
            var up = 0;
            var down = 0;
            shown.forEach(function (series) {
                var value = series.data[i];
                if (value === null) {
                    return;
                }
                if (stack) {
                    if (value >= 0) {
                        up += value;
                    } else {
                        down += value;
                    }
                } else {
                    values.push(value);
                }
            });
            if (stack) {
                values.push(up, down);
            }
        });
        var probe = valueScaleOf(ctx, values, [0, 1]);
        var left = horizontal ? Math.min(140, matrix.categories.reduce(function (most, category) {
            return Math.max(most, textWidth(shorten(category, 20), SMALL_FONT_SIZE));
        }, 0) + 14) : labelWidth(probe, ctx.format) + 14;
        // Room for each axis's name, which is its column's if not given.
        var plot = plotArea(ctx, legend, Math.max(36, left + 16), 16);
        var scale = valueScaleOf(ctx, values, horizontal ? [0, plot.width] : [plot.height, 0]);
        var groups = plotGroup(ctx, plot);
        // ECharts' gaps: a fifth of each category's space between categories, and a fifth of a bar's width
        // between a category's bars.
        var band = bands(matrix.categories.length, horizontal ? plot.height : plot.width, 0.2, ctx.zoom);
        var base = scale.isLog ? scale.domain()[0] : 0;

        var points = 0;
        matrix.categories.forEach(function (category, i) {
            if (!band.visible(i)) {
                return;
            }
            var up = base;
            var down = base;
            shown.forEach(function (series, s) {
                var value = series.data[i];
                if (value === null || (scale.isLog && value <= 0)) {
                    return;
                }
                points++;
                var from;
                var to;
                if (stack) {
                    from = value >= 0 ? up : down;
                    to = from + value;
                    if (value >= 0) {
                        up = to;
                    } else {
                        down = to;
                    }
                } else {
                    from = base;
                    to = value;
                }
                var width = stack ? band.width : band.width / (shown.length + (shown.length - 1) * 0.2);
                var at = band.position(i) + (stack ? 0 : s * width * 1.2);
                var a = scale(from);
                var b = scale(to);
                var colour = colours[matrix.series.indexOf(series) % colours.length];
                var rect = groups.marks.append("rect").attr("fill", colour);
                if (horizontal) {
                    rect.attr("x", Math.min(a, b)).attr("y", at).attr("width", Math.max(1, Math.abs(b - a)))
                        .attr("height", Math.max(1, width - 1));
                } else {
                    rect.attr("x", at).attr("y", Math.min(a, b)).attr("width", Math.max(1, width - 1))
                        .attr("height", Math.max(1, Math.abs(b - a)));
                }
                var pairs = [["category", fields.rawOf("category", category)]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", series.name)]);
                }
                var selection = fields.selection(pairs);
                outline(rect, ctx, ctx.isSelected(selection));
                ctx.mark(rect, selection, category + (fields.has("series") ? " · " + series.name : "") + ": " +
                    ctx.format(value));
                // As ECharts places them: inside a stack's parts, else beyond the bar's end.
                if (showLabels && stack) {
                    svgText(groups.labels, ctx.format(value), horizontal ? (a + b) / 2 : at + width / 2,
                        horizontal ? at + width / 2 : (a + b) / 2, ctx.style,
                        {anchor: "middle", baseline: "middle", size: SMALL_FONT_SIZE});
                } else if (showLabels) {
                    if (horizontal) {
                        svgText(groups.labels, ctx.format(value), Math.max(a, b) + 5, at + width / 2, ctx.style,
                            {baseline: "middle", size: SMALL_FONT_SIZE});
                    } else {
                        svgText(groups.labels, ctx.format(value), at + width / 2, Math.min(a, b) - 5, ctx.style,
                            {anchor: "middle", size: SMALL_FONT_SIZE});
                    }
                }
            });
        });
        drawCategoryAxis(groups.axes, matrix.categories, band, !horizontal, plot, ctx.style,
            horizontal ? textSetting(settings, "yAxisName") || fields.column("category") :
                textSetting(settings, "xAxisName") || fields.column("category"));
        drawValueAxis(groups.axes, scale, horizontal, plot, ctx.format, ctx.style,
            (horizontal ? textSetting(settings, "xAxisName") : textSetting(settings, "yAxisName")) ||
            fields.column("value"));
        drawValueLines(ctx, groups.axes, scale, horizontal, plot, boolSetting(settings, "averageLine", false) ?
            shown.map(function (series) {
                return {value: mean(series.data), name: series.name};
            }) : []);
        ctx.report.series = shown.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        // Bars of dates in time order: a time range is dragged across them.
        var times = categoryTimes(fields, "category", matrix.categories, ctx.prefs);
        if (times) {
            ctx.timeAxis = barRangeAxis(times, band, matrix.categories.length, plot, groups.axes, horizontal);
        }
        ctx.sliderShadow = matrix.categories.map(function (category, i) {
            return [matrix.categories.length > 1 ? i / (matrix.categories.length - 1) : 0.5,
                shown.reduce(function (sum, series) {
                    return sum + Math.abs(series.data[i] || 0);
                }, 0)];
        });
        // ECharts' axis tooltip: every series' value in the category under the pointer, which is shaded.
        ctx.axisTooltip = function (px, py) {
            var along = horizontal ? py : px;
            var i = Math.floor((along - band.position(0) + band.step * 0.1) / band.step);
            if (i < 0 || i >= matrix.categories.length) {
                return null;
            }
            return {
                title: matrix.categories[i],
                rows: shown.filter(function (series) {
                    return series.data[i] !== null;
                }).map(function (series) {
                    return {colour: colours[matrix.series.indexOf(series) % colours.length], name: series.name,
                        value: ctx.format(series.data[i])};
                }),
                shadow: {at: band.position(i) - band.step * 0.1, width: band.step, down: horizontal}
            };
        };
    }

    // The nearest of some sorted numbers to a value, by binary search, or null if there are none.
    function nearest(sorted, value) {
        if (!sorted.length) {
            return null;
        }
        var low = 0;
        var high = sorted.length - 1;
        while (high - low > 1) {
            var middle = Math.floor((low + high) / 2);
            if (sorted[middle] < value) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return Math.abs(sorted[low] - value) <= Math.abs(sorted[high] - value) ? sorted[low] : sorted[high];
    }

    function mean(values) {
        var numbers = values.filter(function (value) {
            return value !== null && isFinite(value);
        });
        return numbers.length ? numbers.reduce(function (sum, value) {
            return sum + value;
        }, 0) / numbers.length : NaN;
    }

    // Largest-triangle-three-buckets: keeps a line's shape with fewer points, as many as the plot is wide.
    function lttb(points, threshold) {
        if (threshold >= points.length || threshold < 3) {
            return points;
        }
        var sampled = [points[0]];
        var every = (points.length - 2) / (threshold - 2);
        var a = 0;
        for (var i = 0; i < threshold - 2; i++) {
            var rangeStart = Math.floor((i + 1) * every) + 1;
            var rangeEnd = Math.min(Math.floor((i + 2) * every) + 1, points.length);
            var avgX = 0;
            var avgY = 0;
            for (var j = rangeStart; j < rangeEnd; j++) {
                avgX += points[j][0];
                avgY += points[j][1];
            }
            avgX /= Math.max(1, rangeEnd - rangeStart);
            avgY /= Math.max(1, rangeEnd - rangeStart);
            var from = Math.floor(i * every) + 1;
            var to = Math.floor((i + 1) * every) + 1;
            var largest = -1;
            var next = from;
            for (var k = from; k < to; k++) {
                var area = Math.abs((points[a][0] - avgX) * (points[k][1] - points[a][1]) -
                    (points[a][0] - points[k][0]) * (avgY - points[a][1]));
                if (area > largest) {
                    largest = area;
                    next = k;
                }
            }
            sampled.push(points[next]);
            a = next;
        }
        sampled.push(points[points.length - 1]);
        return sampled;
    }

    // Points by series from the rows, each [x, y, row's x value], sorted by x: for a line chart or scatter plot
    // on a time or value x axis.
    function seriesPoints(ctx, xId, yId, extra) {
        var fields = ctx.fields;
        var bySeries = {};
        var seriesNames = [];
        var seriesTotals = {};
        var bad = 0;
        var badX = 0;
        var badY = 0;
        ctx.rows.forEach(function (row) {
            var x = toNumber(fields.value(row, xId));
            var y = toNumber(fields.value(row, yId));
            if (isNaN(x) || isNaN(y)) {
                bad++;
                if (isNaN(x)) {
                    badX++;
                } else {
                    badY++;
                }
                return;
            }
            var series = fields.has("series") ? fields.label(row, "series", ctx.prefs) : (fields.column(yId) || "y");
            if (!bySeries[series]) {
                bySeries[series] = [];
                seriesNames.push(series);
                seriesTotals[series] = 0;
            }
            var point = [x, y, fields.value(row, xId), fields.value(row, yId)];
            if (extra) {
                point.push(extra(row));
            }
            bySeries[series].push(point);
            seriesTotals[series] += Math.abs(y);
        });
        var keep = limitSeries(seriesTotals, intSetting(ctx.settings, "maxSeries", 20, 1, 500));
        if (keep) {
            ctx.report.warnings.push((seriesNames.length - Object.keys(keep).length) +
                " series beyond Max Series were left out");
            seriesNames = seriesNames.filter(function (name) {
                return keep[name];
            });
        }
        seriesNames.forEach(function (name) {
            bySeries[name].sort(function (a, b) {
                return a[0] - b[0];
            });
        });
        return {names: seriesNames, bySeries: bySeries, bad: bad, badX: badX, badY: badY};
    }

    // An x scale for a time or value axis, over the part of the data's range zoomed in on.
    function xScaleOf(ctx, extent, time, plot) {
        var span = extent[1] - extent[0] || 1;
        var start = extent[0] + span * (ctx.zoom ? ctx.zoom.start : 0);
        var end = extent[0] + span * (ctx.zoom ? ctx.zoom.end : 1);
        var scale = (time ? ctx.C.time() : ctx.C.linear()).domain(time ? [new Date(start), new Date(end)] :
            [start, end]).range([0, plot.width]);
        if (!ctx.zoom || (ctx.zoom.start === 0 && ctx.zoom.end === 1)) {
            scale.nice();
        }
        return scale;
    }

    function timeFormat(ctx) {
        return function (value) {
            return formatTime(value instanceof Date ? value.getTime() : value, ctx.prefs.compactTimePattern,
                ctx.prefs.timeZone);
        };
    }

    function drawLine(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["x", "y"]);
        var xType = axisFor(fields.type("x"));
        if (xType === "category") {
            drawCategoryLine(ctx);
            return;
        }
        var time = xType === "time";
        var data = seriesPoints(ctx, "x", "y");
        // As ECharts' adapter says them: an x that isn't one first, else a y.
        addDropped(ctx.report, "x not a number or date", data.badX);
        addDropped(ctx.report, "y not a number", data.badY);
        var colours = paletteOf(ctx.options, data.names.length, ctx.style);
        var shown = data.names.filter(function (name) {
            return !ctx.hidden[name];
        });
        var legend = legendOf(ctx, data.names, colours, "line");
        var stack = boolSetting(settings, "stack", false);
        var area = boolSetting(settings, "area", false);
        var smooth = boolSetting(settings, "smooth", false);

        // Stacked: each series' y on top of those before it at the same x (not on its own other rows there).
        var tops = {};
        var lines = shown.map(function (name) {
            var before = {};
            Object.keys(tops).forEach(function (x) {
                before[x] = tops[x];
            });
            return {
                name: name,
                points: data.bySeries[name].map(function (point) {
                    var base = stack ? before[point[0]] || 0 : 0;
                    if (stack) {
                        tops[point[0]] = base + point[1];
                    }
                    return [point[0], base + point[1], base, point[2], point[1]];
                })
            };
        });
        var xs = [];
        var ys = [];
        lines.forEach(function (line) {
            line.points.forEach(function (point) {
                xs.push(point[0]);
                ys.push(point[1]);
                if (area || stack) {
                    ys.push(point[2]);
                }
            });
        });
        var probe = valueScaleOf(ctx, ys, [0, 1]);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, ctx.format) + 30), 16);
        var extent = xs.length ? [Math.min.apply(null, xs), Math.max.apply(null, xs)] : [0, 1];
        var x = xScaleOf(ctx, extent, time, plot);
        var y = valueScaleOf(ctx, ys, [plot.height, 0]);
        var groups = plotGroup(ctx, plot);
        var showLabels = boolSetting(settings, "showLabels", false);
        var points = 0;
        var xOf = function (value) {
            return x(time ? new Date(value) : value);
        };

        lines.forEach(function (line) {
            var colour = colours[data.names.indexOf(line.name) % colours.length];
            var drawn = line.points.filter(function (point) {
                return !y.isLog || point[1] > 0;
            });
            points += drawn.length;
            var sampled = lttb(drawn, Math.max(3, Math.floor(plot.width)));
            if (area || stack) {
                var areaPath = ctx.C.area(smooth)
                    .x(function (point) {
                        return xOf(point[0]);
                    })
                    .y0(function (point) {
                        return y(y.isLog ? y.domain()[0] : point[2]);
                    })
                    .y1(function (point) {
                        return y(point[1]);
                    });
                groups.marks.append("path").attr("d", areaPath(sampled)).attr("fill", colour)
                    .attr("fill-opacity", 0.25).attr("stroke", "none");
            }
            var linePath = ctx.C.line(smooth)
                .x(function (point) {
                    return xOf(point[0]);
                })
                .y(function (point) {
                    return y(point[1]);
                });
            groups.marks.append("path").attr("d", linePath(sampled)).attr("fill", "none").attr("stroke", colour)
                .attr("stroke-width", 2);
            // Points to click, when there are few enough to tell apart: drawn, as ECharts draws them, while there
            // are no more than 200.
            if (sampled.length <= 400) {
                var showPoints = sampled.length <= 200;
                sampled.forEach(function (point) {
                    var pairs = [["x", point[3]]];
                    if (ctx.fields.has("series")) {
                        pairs.push(["series", fields.rawOf("series", line.name)]);
                    }
                    var selection = fields.selection(pairs);
                    var selected = ctx.isSelected(selection);
                    if (!groups.inPlot(xOf(point[0]), y(point[1]))) {
                        return;
                    }
                    var dot = linePoint(groups.points, xOf(point[0]), y(point[1]), colour, selected, showPoints);
                    outline(dot, ctx, selected);
                    ctx.mark(dot, selection, (time ? timeFormat(ctx)(point[0]) : ctx.format(point[0])) +
                        (ctx.fields.has("series") ? " · " + line.name : "") + ": " + ctx.format(point[4]));
                    if (showLabels && xOf(point[0]) >= -1 && xOf(point[0]) <= plot.width + 1) {
                        svgText(groups.labels, ctx.format(point[4]), xOf(point[0]), y(point[1]) - 8, ctx.style,
                            {anchor: "middle", size: SMALL_FONT_SIZE});
                    }
                });
            }
        });
        drawValueAxis(groups.axes, x, true, plot, time ? timeFormat(ctx) : ctx.format, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("x"), time);
        drawValueAxis(groups.axes, y, false, plot, ctx.format, ctx.style, textSetting(settings, "yAxisName") ||
            fields.column("y"));
        drawValueLines(ctx, groups.axes, y, false, plot, boolSetting(settings, "averageLine", false) ?
            lines.map(function (line) {
                return {value: mean(line.points.map(function (point) {
                    return point[4];
                })), name: line.name};
            }) : []);
        ctx.report.series = lines.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        if (time) {
            ctx.timeAxis = timeRangeAxis(x, plot, groups.axes);
        }
        var span = extent[1] - extent[0] || 1;
        ctx.sliderShadow = lines.length ? lines[0].points.map(function (point) {
            return [(point[0] - extent[0]) / span, point[4]];
        }) : [];
        // ECharts' axis tooltip: every line's value at the x nearest the pointer, marked with a line.
        var byX = lines.map(function (line) {
            var at = {};
            line.points.forEach(function (point) {
                at[point[0]] = point;
            });
            return at;
        });
        var allX = [];
        lines.forEach(function (line) {
            line.points.forEach(function (point) {
                allX.push(point[0]);
            });
        });
        allX.sort(function (a, b) {
            return a - b;
        });
        ctx.axisTooltip = function (px) {
            var wanted = x.invert(px);
            var value = nearest(allX, wanted instanceof Date ? wanted.getTime() : wanted);
            if (value === null) {
                return null;
            }
            return {
                title: time ? formatTime(value, ctx.prefs.timePattern, ctx.prefs.timeZone) : ctx.format(value),
                rows: lines.filter(function (line, l) {
                    return byX[l][value] !== undefined;
                }).map(function (line) {
                    return {colour: colours[data.names.indexOf(line.name) % colours.length], name: line.name,
                        value: ctx.format(byX[lines.indexOf(line)][value][4])};
                }),
                line: {at: xOf(value)}
            };
        };
    }

    // A line chart whose x is a category (text): one point per category and series, summed.
    function drawCategoryLine(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        var matrix = categoryMatrix(ctx.rows, fields, settings, ctx.prefs, ctx.report, "x", "y");
        var colours = paletteOf(ctx.options, matrix.series.length, ctx.style);
        var shown = matrix.series.filter(function (series) {
            return !ctx.hidden[series.name];
        });
        var legend = legendOf(ctx, matrix.series.map(function (series) {
            return series.name;
        }), colours, "line");
        var stack = boolSetting(settings, "stack", false);
        var area = boolSetting(settings, "area", false);
        var smooth = boolSetting(settings, "smooth", false);
        var showLabels = boolSetting(settings, "showLabels", false);
        var tops = matrix.categories.map(function () {
            return 0;
        });
        var lines = shown.map(function (series) {
            return {
                series: series,
                points: series.data.map(function (value, i) {
                    if (value === null) {
                        return null;
                    }
                    var base = stack ? tops[i] : 0;
                    if (stack) {
                        tops[i] = base + value;
                    }
                    return [i, base + value, base, value];
                })
            };
        });
        var ys = [];
        lines.forEach(function (line) {
            line.points.forEach(function (point) {
                if (point) {
                    ys.push(point[1], point[2]);
                }
            });
        });
        var probe = valueScaleOf(ctx, ys, [0, 1]);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, ctx.format) + 30), 16);
        var y = valueScaleOf(ctx, ys, [plot.height, 0]);
        var groups = plotGroup(ctx, plot);
        var band = pointPositions(matrix.categories.length, plot.width, ctx.zoom);
        var xOf = band.centre;
        var points = 0;
        lines.forEach(function (line) {
            var colour = colours[matrix.series.indexOf(line.series) % colours.length];
            var drawn = line.points.filter(function (point) {
                return point && (!y.isLog || point[1] > 0);
            });
            points += drawn.length;
            // A category the series has no value for is a gap in its line, as ECharts leaves one.
            var defined = function (point) {
                return !!point && (!y.isLog || point[1] > 0);
            };
            if (area || stack) {
                groups.marks.append("path").attr("d", ctx.C.area(smooth)
                    .defined(defined)
                    .x(function (point) {
                        return xOf(point[0]);
                    })
                    .y0(function (point) {
                        return y(y.isLog ? y.domain()[0] : point[2]);
                    })
                    .y1(function (point) {
                        return y(point[1]);
                    })(line.points)).attr("fill", colour).attr("fill-opacity", 0.25);
            }
            groups.marks.append("path").attr("d", ctx.C.line(smooth)
                .defined(defined)
                .x(function (point) {
                    return xOf(point[0]);
                })
                .y(function (point) {
                    return y(point[1]);
                })(line.points)).attr("fill", "none").attr("stroke", colour).attr("stroke-width", 2);
            drawn.forEach(function (point) {
                var category = matrix.categories[point[0]];
                var pairs = [["x", fields.rawOf("x", category)]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", line.series.name)]);
                }
                var selection = fields.selection(pairs);
                var selected = ctx.isSelected(selection);
                if (!groups.inPlot(xOf(point[0]), y(point[1]))) {
                    return;
                }
                var dot = linePoint(groups.points, xOf(point[0]), y(point[1]), colour, selected,
                    drawn.length <= 200);
                outline(dot, ctx, selected);
                ctx.mark(dot, selection, category + (fields.has("series") ? " · " + line.series.name : "") +
                    ": " + ctx.format(point[3]));
                if (showLabels && band.visible(point[0])) {
                    svgText(groups.labels, ctx.format(point[3]), xOf(point[0]), y(point[1]) - 8, ctx.style,
                        {anchor: "middle", size: SMALL_FONT_SIZE});
                }
            });
        });
        drawCategoryAxis(groups.axes, matrix.categories, band, true, plot, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("x"));
        drawValueAxis(groups.axes, y, false, plot, ctx.format, ctx.style, textSetting(settings, "yAxisName") ||
            fields.column("y"));
        drawValueLines(ctx, groups.axes, y, false, plot, boolSetting(settings, "averageLine", false) ?
            lines.map(function (line) {
                return {value: mean(line.series.data), name: line.series.name};
            }) : []);
        ctx.report.series = lines.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        ctx.sliderShadow = lines.length ? lines[0].points.filter(Boolean).map(function (point) {
            return [matrix.categories.length > 1 ? point[0] / (matrix.categories.length - 1) : 0.5, point[3]];
        }) : [];
        ctx.axisTooltip = function (px) {
            var i = Math.round((px - band.centre(0)) / band.step);
            if (i < 0 || i >= matrix.categories.length) {
                return null;
            }
            return {
                title: matrix.categories[i],
                rows: lines.filter(function (line) {
                    return line.points[i];
                }).map(function (line) {
                    return {colour: colours[matrix.series.indexOf(line.series) % colours.length],
                        name: line.series.name, value: ctx.format(line.points[i][3])};
                }),
                line: {at: band.centre(i)}
            };
        };
    }

    // A line's point, as ECharts draws one: hollow (filled white), or, where there are too many to draw, there only
    // to be clicked.
    function linePoint(parent, x, y, colour, selected, shown) {
        var dot = parent.append("circle").attr("cx", x).attr("cy", y).attr("r", selected ? 5 : 3)
            .attr("fill", "#ffffff").attr("stroke", colour).attr("stroke-width", 1.5);
        if (!shown && !selected) {
            dot.attr("fill-opacity", 0).attr("stroke-opacity", 0);
        }
        return dot;
    }

    function drawScatter(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["x", "y"]);
        var time = axisFor(fields.type("x")) === "time";
        // A y of dates is on a time axis, as ECharts draws it.
        var yTime = axisFor(fields.type("y")) === "time";
        var hasSize = fields.has("size");
        var data = seriesPoints(ctx, "x", "y", hasSize ? function (row) {
            var size = toNumber(fields.value(row, "size"));
            return isNaN(size) || size < 0 ? 0 : size;
        } : null);
        addDropped(ctx.report, "x or y not a number or date", data.bad);
        var colours = paletteOf(ctx.options, data.names.length, ctx.style);
        var legend = legendOf(ctx, data.names, colours, "circle");
        var shown = data.names.filter(function (name) {
            return !ctx.hidden[name];
        });
        var xs = [];
        var ys = [];
        var sizes = [];
        shown.forEach(function (name) {
            data.bySeries[name].forEach(function (point) {
                xs.push(point[0]);
                ys.push(point[1]);
                if (hasSize) {
                    sizes.push(point[4]);
                }
            });
        });
        var yFormat = yTime ? timeFormat(ctx) : ctx.format;
        var yExtent = ys.length ? [Math.min.apply(null, ys), Math.max.apply(null, ys)] : [0, 1];
        var timeY = function (range) {
            return ctx.C.time().domain([new Date(yExtent[0]), new Date(yExtent[1] > yExtent[0] ? yExtent[1] :
                yExtent[0] + 1)]).range(range).nice();
        };
        var probe = yTime ? timeY([0, 1]) : valueScaleOf(ctx, ys, [0, 1]);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, yFormat) + 30), 16);
        var extent = xs.length ? [Math.min.apply(null, xs), Math.max.apply(null, xs)] : [0, 1];
        // A value x axis includes zero, as ECharts' does.
        if (!time) {
            extent = [Math.min(0, extent[0]), Math.max(0, extent[1])];
        }
        var x = xScaleOf(ctx, extent, time, plot);
        var y = yTime ? timeY([plot.height, 0]) : valueScaleOf(ctx, ys, [plot.height, 0]);
        var yOf = function (value) {
            return y(yTime ? new Date(value) : value);
        };
        var groups = plotGroup(ctx, plot);
        var jitter = boolSetting(settings, "jitter", false);
        var minSize = sizes.length ? Math.min.apply(null, sizes) : 0;
        var maxSize = sizes.length ? Math.max.apply(null, sizes) : 0;
        var index = 0;
        var points = 0;
        shown.forEach(function (name) {
            var colour = colours[data.names.indexOf(name) % colours.length];
            data.bySeries[name].forEach(function (point) {
                if (y.isLog && point[1] <= 0) {
                    return;
                }
                index++;
                points++;
                // In the plot by where it is, before it is jittered.
                var px = x(time ? new Date(point[0]) : point[0]);
                var py = yOf(point[1]);
                if (!groups.inPlot(px, py)) {
                    return;
                }
                var cx = px + (jitter ? jitterOf(index) * plot.width * 0.02 : 0);
                var cy = py + (jitter ? jitterOf(index + 7919) * plot.height * 0.02 : 0);
                var radius = hasSize ? scaleSqrt(point[4], minSize, maxSize, 3, 18) : 5;
                var pairs = [["x", point[2]], ["y", point[3]]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", name)]);
                }
                var selection = fields.selection(pairs);
                var dot = groups.points.append("circle").attr("cx", cx).attr("cy", cy).attr("r", radius)
                    .attr("fill", colour).attr("fill-opacity", 0.8);
                outline(dot, ctx, ctx.isSelected(selection));
                ctx.mark(dot, selection, (fields.has("series") ? name + ": " : "") +
                    (time ? timeFormat(ctx)(point[0]) : ctx.format(point[0])) + ", " + yFormat(point[1]));
            });
        });
        drawValueAxis(groups.axes, x, true, plot, time ? timeFormat(ctx) : ctx.format, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("x"), time);
        drawValueAxis(groups.axes, y, false, plot, yFormat, ctx.style,
            textSetting(settings, "yAxisName") || fields.column("y"), yTime);
        if (!yTime) {
            drawValueLines(ctx, groups.axes, y, false, plot, []);
        }
        ctx.report.series = shown.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        var xSpan = extent[1] - extent[0] || 1;
        ctx.sliderShadow = shown.length ? data.bySeries[shown[0]].map(function (point) {
            return [(point[0] - extent[0]) / xSpan, point[1]];
        }) : [];
        if (time) {
            ctx.timeAxis = timeRangeAxis(x, plot, groups.axes);
        }
    }

    function drawHistogram(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["value"]);
        var values = [];
        var nonNumeric = 0;
        ctx.rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            values.push([value, fields.has("series") ? fields.label(row, "series", ctx.prefs) :
                (fields.column("value") || "Rows")]);
        });
        addDropped(ctx.report, "value not a number", nonNumeric);
        var min = Infinity;
        var max = -Infinity;
        values.forEach(function (value) {
            min = Math.min(min, value[0]);
            max = Math.max(max, value[0]);
        });
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
            labels.push(ctx.format(from) + " – " + ctx.format(i === bins - 1 ? max : from + width));
        }
        var counts = {};
        var names = [];
        var totals = {};
        values.forEach(function (value) {
            var bin = max > min ? Math.min(bins - 1, Math.floor((value[0] - min) / width)) : 0;
            if (!counts[value[1]]) {
                counts[value[1]] = labels.map(function () {
                    return 0;
                });
                names.push(value[1]);
                totals[value[1]] = 0;
            }
            counts[value[1]][bin]++;
            totals[value[1]]++;
        });
        var keep = limitSeries(totals, intSetting(settings, "maxSeries", 20, 1, 500));
        if (keep) {
            ctx.report.warnings.push((names.length - Object.keys(keep).length) +
                " series beyond Max Series were left out");
            names = names.filter(function (name) {
                return keep[name];
            });
        }
        var colours = paletteOf(ctx.options, names.length, ctx.style);
        var legend = legendOf(ctx, names, colours, "rect");
        var shown = names.filter(function (name) {
            return !ctx.hidden[name];
        });
        var stack = boolSetting(settings, "stack", false);
        var heights = labels.map(function (label, b) {
            return shown.reduce(function (sum, name) {
                return stack ? sum + counts[name][b] : Math.max(sum, counts[name][b]);
            }, 0);
        });
        var probe = niceLinear(ctx.C, 0, Math.max(1, Math.max.apply(null, heights.concat([1]))), [0, 1]);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, formatNumber) + 30), 16);
        var y = niceLinear(ctx.C, 0, Math.max(1, Math.max.apply(null, heights.concat([1]))), [plot.height, 0]);
        var groups = plotGroup(ctx, plot);
        var band = bands(bins, plot.width, 0.04, ctx.zoom);
        var showLabels = boolSetting(settings, "showLabels", false);
        labels.forEach(function (label, b) {
            if (!band.visible(b)) {
                return;
            }
            var top = 0;
            shown.forEach(function (name, s) {
                var count = counts[name][b];
                if (count === 0) {
                    return;
                }
                var barWidth = stack ? band.width : band.width / shown.length;
                var at = band.position(b) + (stack ? 0 : s * barWidth);
                var from = stack ? top : 0;
                top += count;
                var rect = groups.marks.append("rect").attr("x", at).attr("y", y(from + count))
                    .attr("width", Math.max(1, barWidth - 0.5)).attr("height", Math.max(1, y(from) - y(from + count)))
                    .attr("fill", colours[names.indexOf(name) % colours.length]);
                // A bar is a range of values, so a click on it selects nothing.
                ctx.mark(rect, null, label + (shown.length > 1 ? " · " + name : "") + ": " + count + " rows");
                if (showLabels) {
                    svgText(groups.labels, String(count), at + barWidth / 2, y(from + count) - 4, ctx.style,
                        {anchor: "middle", size: SMALL_FONT_SIZE});
                }
            });
        });
        drawCategoryAxis(groups.axes, labels, band, true, plot, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("value"));
        drawValueAxis(groups.axes, y, false, plot, formatNumber, ctx.style, textSetting(settings, "yAxisName") ||
            "Rows");
        ctx.report.series = shown.length;
        ctx.report.points = values.length;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        ctx.sliderShadow = heights.map(function (height, b) {
            return [bins > 1 ? b / (bins - 1) : 0.5, height];
        });
        ctx.axisTooltip = function (px) {
            var b = Math.floor((px - band.position(0) + band.step * 0.02) / band.step);
            if (b < 0 || b >= bins) {
                return null;
            }
            return {
                title: labels[b],
                rows: shown.map(function (name) {
                    return {colour: colours[names.indexOf(name) % colours.length], name: name,
                        value: formatNumber(counts[name][b])};
                }),
                shadow: {at: band.position(b) - band.step * 0.02, width: band.step, down: false}
            };
        };
    }

    // ------------------------------------------------------------------------------------------
    // Parts of a whole and single values: pie, gauge
    // ------------------------------------------------------------------------------------------

    function drawPie(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["name", "value"]);
        var sums = {};
        var names = [];
        var nonNumeric = 0;
        var negative = 0;
        ctx.rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            if (value < 0) {
                negative++;
                return;
            }
            var name = fields.label(row, "name", ctx.prefs);
            if (sums[name] === undefined) {
                sums[name] = 0;
                names.push(name);
            }
            sums[name] += value;
        });
        addDropped(ctx.report, "value not a number", nonNumeric);
        addDropped(ctx.report, "value negative", negative);
        names.sort(function (a, b) {
            return sums[b] - sums[a];
        });
        var maxSlices = intSetting(settings, "maxSlices", 12, 1, 100);
        var slices = names.slice(0, maxSlices).map(function (name) {
            return {name: name, value: sums[name]};
        });
        if (names.length > maxSlices) {
            slices.push({name: sums.Other !== undefined ? "Other (combined)" : "Other", combined: true,
                value: names.slice(maxSlices).reduce(function (total, name) {
                    return total + sums[name];
                }, 0)});
            ctx.report.warnings.push((names.length - maxSlices) + " smaller slices were combined as 'Other'");
        }
        var colours = paletteOf(ctx.options, slices.length, ctx.style);
        legendOf(ctx, slices.map(function (slice) {
            return slice.name;
        }), colours, "rect");
        var shown = slices.filter(function (slice) {
            return !ctx.hidden[slice.name];
        });
        var total = shown.reduce(function (sum, slice) {
            return sum + slice.value;
        }, 0);
        // Where ECharts draws one: centred, 70% of the way to the nearer edge (a doughnut from 40%).
        var radius = Math.max(10, Math.min(ctx.size.width, ctx.size.height) / 2 * 0.7);
        var doughnut = boolSetting(settings, "doughnut", false);
        var showLabels = boolSetting(settings, "showLabels", true);
        var centreX = ctx.size.width / 2;
        var centreY = ctx.size.height * 0.52;
        var centre = [centreX, centreY];
        var g = ctx.svg.append("g").attr("transform", "translate(" + centre[0] + "," + centre[1] + ")");
        ctx.geo = g;
        ctx.geoOrigin = centre;
        var arc = ctx.C.arc().innerRadius(doughnut ? radius * 4 / 7 : 0).outerRadius(radius);
        // A slice under the pointer stands out, as ECharts' does: 10 pixels larger.
        var hoverArc = ctx.C.arc().innerRadius(doughnut ? radius * 4 / 7 : 0).outerRadius(radius + 10);
        var labels = [];
        var angle = 0;
        // Nothing to show: an empty circle, as ECharts draws one.
        if (shown.length === 0) {
            g.append("path").attr("d", arc({startAngle: 0, endAngle: Math.PI * 2})).attr("fill", "lightgray");
        }
        shown.forEach(function (slice) {
            // All of nothing: equal slices, as ECharts draws them.
            var sweep = total > 0 ? slice.value / total * Math.PI * 2 : Math.PI * 2 / shown.length;
            var part = {startAngle: angle, endAngle: angle + sweep};
            angle += sweep;
            var selection = slice.combined ? null : fields.selection([["name", fields.rawOf("name", slice.name)]]);
            var path = g.append("path").attr("d", arc(part))
                .attr("fill", colours[slices.indexOf(slice) % colours.length])
                .attr("stroke", ctx.style.dark ? "#1b1e24" : "#fff").attr("stroke-width", 1);
            outline(path, ctx, selection && ctx.isSelected(selection));
            path.node().addEventListener("mouseenter", function () {
                path.attr("d", hoverArc(part));
            });
            path.node().addEventListener("mouseleave", function () {
                path.attr("d", arc(part));
            });
            ctx.mark(path, selection, slice.name + ": " + ctx.format(slice.value) + " (" +
                (total > 0 ? Math.round(slice.value / total * 1000) / 10 : 0) + "%)");
            if (showLabels && sweep > 0) {
                var middle = (part.startAngle + part.endAngle) / 2;
                labels.push({name: shorten(slice.name, 18), angle: middle, right: Math.sin(middle) >= 0,
                    y: -Math.cos(middle) * (radius + 15), colour: colours[slices.indexOf(slice) % colours.length]});
            }
        });
        drawPieLabels(g, labels, radius, ctx.style);
        ctx.report.series = 1;
        ctx.report.points = shown.length;
        ctx.zoomKind = "geo";
    }

    // Each slice's name outside the pie, on a line from the slice as ECharts draws it, those on each side moved
    // apart so none overlap.
    function drawPieLabels(g, labels, radius, style) {
        [true, false].forEach(function (right) {
            var side = labels.filter(function (label) {
                return label.right === right;
            }).sort(function (a, b) {
                return a.y - b.y;
            });
            var last = -Infinity;
            side.forEach(function (label) {
                label.at = Math.max(label.y, last + 14);
                last = label.at;
            });
            side.forEach(function (label) {
                var sx = Math.sin(label.angle) * radius;
                var sy = -Math.cos(label.angle) * radius;
                var bendX = Math.sin(label.angle) * (radius + 15);
                var endX = bendX + (right ? 20 : -20);
                g.append("path").attr("d", "M" + sx + "," + sy + "L" + bendX + "," + label.at + "L" + endX + "," +
                    label.at).attr("fill", "none").attr("stroke", label.colour).attr("stroke-width", 1);
                svgText(g, label.name, endX + (right ? 4 : -4), label.at, style,
                    {anchor: right ? "start" : "end", baseline: "middle", size: SMALL_FONT_SIZE});
            });
        });
    }

    // A sunburst, as ECharts draws one: a ring per level round a hole, each segment as wide as its rows' total,
    // in its inner segment's colour, labelled along its radius where it has room.
    function drawSunburst(ctx) {
        var fields = ctx.fields;
        requireFields(fields, ["name"]);
        var root = sunburstTree(ctx.rows, fields, ctx.prefs, ctx.report);
        var nodes = treeNodes(root);
        var depth = 0;
        (function measure(node, level) {
            depth = Math.max(depth, level);
            node.children.forEach(function (child) {
                measure(child, level + 1);
            });
        })(root, 0);
        var colours = paletteOf(ctx.options, root.children.length, ctx.style);
        var showLabels = boolSetting(ctx.settings, "showLabels", true);
        var half = Math.min(ctx.size.width, ctx.size.height) / 2;
        var hole = half * 0.12;
        var ring = depth > 0 ? (half * 0.8 - hole) / depth : 0;
        var centreX = ctx.size.width / 2;
        var centreY = ctx.size.height * 0.52;
        var g = ctx.svg.append("g").attr("transform", "translate(" + centreX + "," + centreY + ")");
        ctx.geo = g;
        ctx.geoOrigin = [centreX, centreY];
        var arc = ctx.C.arc();
        var segments = [];
        (function place(node, start, end, level, colour, ancestors) {
            var angle = start;
            node.children.forEach(function (child, i) {
                var sweep = node.value > 0 ? (end - start) * child.value / node.value : 0;
                var own = colour || colours[i % colours.length];
                var inner = hole + level * ring;
                var part = {startAngle: angle, endAngle: angle + sweep, innerRadius: inner, outerRadius: inner + ring};
                var path = g.append("path").attr("d", arc(part)).attr("fill", own)
                    .attr("stroke", ctx.style.dark ? "#1b1e24" : "#ffffff").attr("stroke-width", 1);
                var selection = fields.selection(child.pairs);
                outline(path, ctx, ctx.isSelected(selection));
                var names = ancestors.concat([child.name]);
                ctx.mark(path, selection, names.join(" › ") + ": " + ctx.format(child.value));
                var segment = {element: path.node(), node: child, ancestors: ancestors.length};
                segments.push(segment);
                if (showLabels && sweep * 180 / Math.PI >= 8) {
                    sunburstLabel(g, child.name, part, ctx.style);
                }
                place(child, angle, angle + sweep, level + 1, own, names);
                angle += sweep;
            });
        })(root, 0, Math.PI * 2, 0, null, []);
        // ECharts' ancestor focus: under the pointer, a segment and those it is within stand out.
        segments.forEach(function (segment) {
            segment.element.addEventListener("mouseenter", function () {
                segments.forEach(function (other) {
                    var within = other === segment || other.node.pairs.length < segment.node.pairs.length &&
                        segment.node.pairs.slice(0, other.node.pairs.length).every(function (pair, i) {
                            return pair[1] === other.node.pairs[i][1];
                        });
                    other.element.style.opacity = within ? "" : "0.3";
                });
            });
            segment.element.addEventListener("mouseleave", function () {
                segments.forEach(function (other) {
                    other.element.style.opacity = "";
                });
            });
        });
        ctx.report.series = root.children.length;
        ctx.report.points = nodes.length;
        ctx.zoomKind = "geo";
    }

    // A segment's name along its radius, centred in it, read the right way up on either side.
    function sunburstLabel(g, name, part, style) {
        var middle = (part.startAngle + part.endAngle) / 2;
        var radius = (part.innerRadius + part.outerRadius) / 2;
        var x = Math.sin(middle) * radius;
        var y = -Math.cos(middle) * radius;
        var degrees = middle * 180 / Math.PI - 90;
        if (degrees > 90) {
            degrees -= 180;
        }
        var room = Math.max(2, Math.floor((part.outerRadius - part.innerRadius - 2) / (SMALL_FONT_SIZE * 0.55)));
        svgText(g, shorten(name, room), x, y, style, {anchor: "middle", baseline: "middle", size: SMALL_FONT_SIZE})
            .attr("transform", "rotate(" + degrees + "," + x + "," + y + ")").attr("pointer-events", "none");
    }

    function drawGauge(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["value"]);
        var named = fields.has("name");
        var sums = {};
        var names = [];
        var nonNumeric = 0;
        ctx.rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var name = named ? fields.label(row, "name", ctx.prefs) : fields.column("value");
            if (sums[name] === undefined) {
                sums[name] = 0;
                names.push(name);
            } else if (!named) {
                // Without a name column, a gauge shows one value: the first row's.
                return;
            }
            sums[name] += value;
        });
        addDropped(ctx.report, "value not a number", nonNumeric);
        if (!named && ctx.rows.length > 1) {
            ctx.report.warnings.push("Only the first row is shown: map a name column for a gauge per row, or " +
                "group the table to one row (e.g. max() of the column)");
        }
        var maxGauges = intSetting(settings, "maxGauges", 6, 1, 24);
        if (names.length > maxGauges) {
            ctx.report.warnings.push("Only the first " + maxGauges + " of " + names.length + " gauges are drawn " +
                "(Max Gauges)");
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
        var bandStops = gaugeBands(textSetting(settings, "bands"), min, max);
        // Laid out as ECharts lays them out: a grid of dials, each centred in its cell a little below the middle.
        var columns = Math.ceil(Math.sqrt(names.length || 1));
        var gridRows = Math.ceil((names.length || 1) / columns);
        var cellWidth = ctx.size.width / columns;
        var cellHeight = ctx.size.height / gridRows;
        var radius = Math.max(16, Math.min(ctx.size.width, ctx.size.height) / 2 * Math.min(1 / columns,
            1 / gridRows) * 0.84);
        var start = -Math.PI * 0.75;
        var sweep = Math.PI * 1.5;
        var angleOf = function (value) {
            return start + sweep * Math.max(0, Math.min(1, (value - min) / (max - min)));
        };
        var arc = ctx.C.arc().innerRadius(radius - 10).outerRadius(radius);
        var g = ctx.svg.append("g");
        ctx.geo = g;
        ctx.geoOrigin = [0, 0];
        names.forEach(function (name, i) {
            var cx = (i % columns + 0.5) * cellWidth;
            var cy = (Math.floor(i / columns) + 0.55) * cellHeight;
            var dial = g.append("g").attr("transform", "translate(" + cx + "," + cy + ")");
            var from = 0;
            bandStops.forEach(function (stop) {
                dial.append("path").attr("d", arc({startAngle: start + sweep * from, endAngle: start + sweep * stop[0]}))
                    .attr("fill", stop[1]);
                from = stop[0];
            });
            drawGaugeScale(dial, ctx, radius, start, sweep, min, max);
            var angle = angleOf(sums[name]);
            var selection = named ? fields.selection([["name", fields.rawOf("name", name)]]) : null;
            var selected = selection && ctx.isSelected(selection);
            // A pointer tapering from the centre to 60% of the way to the dial.
            var tip = radius * 0.6;
            var base = selected ? 5 : 3;
            var needle = dial.append("path").attr("d", "M" + (-base) + ",0L0," + (-tip) + "L" + base + ",0Z")
                .attr("transform", "rotate(" + (angle * 180 / Math.PI) + ")").attr("fill", ctx.style.text);
            outline(needle, ctx, selected);
            svgText(dial, ctx.format(sums[name]), 0, radius * 0.4, ctx.style,
                {anchor: "middle", baseline: "middle", size: 16, weight: "bold"});
            svgText(dial, shorten(name, 24), 0, radius * 0.7, ctx.style,
                {anchor: "middle", baseline: "middle", size: 14});
            // The whole dial can be clicked.
            var target = dial.append("circle").attr("r", radius).attr("fill", "transparent");
            ctx.mark(target, selection, name + ": " + ctx.format(sums[name]));
            ctx.mark(needle, selection, name + ": " + ctx.format(sums[name]));
        });
        ctx.report.series = names.length;
        ctx.report.points = names.length;
        ctx.zoomKind = null;
    }

    // A gauge's scale, as ECharts draws one inside the dial: ten splits, each with four ticks between, and a label at
    // each split.
    function drawGaugeScale(dial, ctx, radius, start, sweep, min, max) {
        var style = ctx.style;
        var inner = radius - 10 - 10;
        for (var i = 0; i <= 50; i++) {
            var at = start + sweep * i / 50;
            var split = i % 5 === 0;
            var length = split ? 10 : 6;
            var sin = Math.sin(at);
            var cos = -Math.cos(at);
            dial.append("line").attr("x1", sin * inner).attr("y1", cos * inner)
                .attr("x2", sin * (inner - length)).attr("y2", cos * (inner - length))
                .attr("stroke", style.subtleText).attr("stroke-width", split ? 2 : 1);
            if (split) {
                var labelAt = inner - 10 - 15;
                svgText(dial, gaugeScaleLabel(ctx.format, min, max)(min + (max - min) * i / 50), sin * labelAt,
                    cos * labelAt, style,
                    {anchor: "middle", baseline: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Grids of values: heatmap, calendar
    // ------------------------------------------------------------------------------------------

    function drawHeatmap(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["x", "y", "value"]);
        var xs = [];
        var ys = [];
        var sums = {};
        var nonNumeric = 0;
        ctx.rows.forEach(function (row) {
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            var x = fields.label(row, "x", ctx.prefs);
            var y = fields.label(row, "y", ctx.prefs);
            if (xs.indexOf(x) < 0) {
                xs.push(x);
            }
            if (ys.indexOf(y) < 0) {
                ys.push(y);
            }
            var key = x + "\u0000" + y;
            sums[key] = (sums[key] || 0) + value;
        });
        addDropped(ctx.report, "value not a number", nonNumeric);
        sortNatural(xs, fields, "x");
        sortNatural(ys, fields, "y");
        var values = Object.keys(sums).map(function (key) {
            return sums[key];
        });
        var min = values.length ? Math.min.apply(null, values) : 0;
        var max = values.length ? Math.max.apply(null, values) : 1;
        var colours = scaleColours(settings, ctx.style);
        // A table, as ECharts draws a heatmap: a header row of the x values over the cells, and a header column of
        // the y values beside them, all bordered.
        var headerWidth = Math.min(140, ys.reduce(function (most, y) {
            return Math.max(most, textWidth(shorten(y, 20), SMALL_FONT_SIZE));
        }, 0) + 16);
        var table = {left: 16, top: 16, width: Math.max(10, ctx.size.width - 16 - STROOM_BUTTON_SPACE),
            height: Math.max(10, ctx.size.height - 16 - 44)};
        var headerHeight = Math.max(20, table.height / (ys.length + 1));
        var plot = {left: table.left + headerWidth, top: table.top + headerHeight,
            width: Math.max(10, table.width - headerWidth), height: Math.max(10, table.height - headerHeight)};
        var groups = plotGroup(ctx, plot);
        var xBand = bands(xs.length, plot.width, 0, null);
        var yBand = bands(ys.length, plot.height, 0, null);
        var showLabels = boolSetting(settings, "showLabels", false);
        var cells = 0;
        xs.forEach(function (x, i) {
            ys.forEach(function (y, j) {
                var value = sums[x + "\u0000" + y];
                if (value === undefined) {
                    return;
                }
                cells++;
                var selection = fields.selection([["x", fields.rawOf("x", x)], ["y", fields.rawOf("y", y)]]);
                var rect = groups.marks.append("rect").attr("x", xBand.position(i)).attr("y", yBand.position(j))
                    .attr("width", xBand.width).attr("height", yBand.width)
                    .attr("fill", colourAt(colours, max > min ? (value - min) / (max - min) : 1));
                outline(rect, ctx, ctx.isSelected(selection));
                ctx.mark(rect, selection, x + " · " + y + ": " + ctx.format(value));
                if (showLabels && xBand.width > 16 && yBand.width > 12) {
                    svgText(groups.labels, ctx.format(value), xBand.position(i) + xBand.width / 2,
                        yBand.position(j) + yBand.width / 2, ctx.style,
                        {anchor: "middle", baseline: "middle", size: SMALL_FONT_SIZE});
                }
            });
        });
        drawMatrixHeaders(groups.axes, xs, ys, xBand, yBand, plot, headerWidth, headerHeight, ctx.style);
        drawScaleBar(ctx.svg, colours, min, max, ctx.format, ctx.size, ctx.style);
        ctx.geo = groups.marks;
        ctx.geoOrigin = [0, 0];
        ctx.report.series = 1;
        ctx.report.points = cells;
        ctx.zoomKind = "geo";
    }

    // A heatmap's header row and column, and the borders of every cell, as ECharts draws its matrix.
    function drawMatrixHeaders(g, xs, ys, xBand, yBand, plot, headerWidth, headerHeight, style) {
        var lines = g.append("g").attr("class", "matrix");
        var border = function (x1, y1, x2, y2) {
            lines.append("line").attr("x1", x1).attr("y1", y1).attr("x2", x2).attr("y2", y2)
                .attr("stroke", style.border);
        };
        xs.forEach(function (x, i) {
            var at = xBand.position(i);
            border(at, -headerHeight, at, plot.height);
            if (xBand.width >= 8) {
                svgText(lines, shorten(x, Math.max(2, Math.floor(xBand.width / 7))), at + xBand.width / 2,
                    -headerHeight / 2, style, {anchor: "middle", baseline: "middle", colour: style.subtleText,
                        size: SMALL_FONT_SIZE});
            }
        });
        ys.forEach(function (y, j) {
            var at = yBand.position(j);
            border(-headerWidth, at, plot.width, at);
            if (yBand.width >= 10) {
                svgText(lines, shorten(y, 20), -headerWidth / 2, at + yBand.width / 2, style,
                    {anchor: "middle", baseline: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            }
        });
        border(-headerWidth, -headerHeight, plot.width, -headerHeight);
        border(-headerWidth, plot.height, plot.width, plot.height);
        border(-headerWidth, -headerHeight, -headerWidth, plot.height);
        border(plot.width, -headerHeight, plot.width, plot.height);
    }

    function drawCalendar(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["date", "value"]);
        var sums = {};
        var raws = {};
        var badDate = 0;
        var nonNumeric = 0;
        ctx.rows.forEach(function (row) {
            var date = toNumber(fields.value(row, "date"));
            var value = toNumber(fields.value(row, "value"));
            if (isNaN(date)) {
                badDate++;
                return;
            }
            if (isNaN(value)) {
                nonNumeric++;
                return;
            }
            // The day in the user's time zone.
            var day = formatTime(date, "YYYY-MM-DD", ctx.prefs.timeZone);
            sums[day] = (sums[day] || 0) + value;
            if (raws[day] === undefined) {
                raws[day] = fields.value(row, "date");
            }
        });
        addDropped(ctx.report, "date not a date", badDate);
        addDropped(ctx.report, "value not a number", nonNumeric);
        var days = Object.keys(sums).sort();
        var years = [];
        days.forEach(function (day) {
            var year = day.substring(0, 4);
            if (years.indexOf(year) < 0) {
                years.push(year);
            }
        });
        if (years.length > MAX_CALENDAR_YEARS) {
            ctx.report.warnings.push("Only the last " + MAX_CALENDAR_YEARS + " of " + years.length +
                " years are drawn");
            years = years.slice(years.length - MAX_CALENDAR_YEARS);
        }
        var values = days.map(function (day) {
            return sums[day];
        });
        var min = values.length ? Math.min.apply(null, values) : 0;
        var max = values.length ? Math.max.apply(null, values) : 1;
        var colours = scaleColours(settings, ctx.style);
        // As ECharts lays a calendar out: a week to a column, as wide as fits, its days (from Sunday) 14 pixels
        // high, the months above and outlined, the year to the left. A single year's data shows only the weeks it
        // covers; several years show each whole year, one under another.
        var whole = years.length > 1;
        var dayMs = 86400000;
        var utc = function (day) {
            return Date.UTC(parseInt(day.substring(0, 4), 10), parseInt(day.substring(5, 7), 10) - 1,
                parseInt(day.substring(8, 10), 10));
        };
        var blocks = years.map(function (year) {
            var first = whole || !days.length ? utc(year + "-01-01") : utc(days[0]);
            var last = whole || !days.length ? utc(year + "-12-31") : utc(days[days.length - 1]);
            var firstSunday = first - new Date(first).getUTCDay() * dayMs;
            return {year: year, first: first, last: last, firstSunday: firstSunday,
                weeks: Math.floor((last - firstSunday) / dayMs / 7) + 1};
        });
        var maxWeeks = blocks.reduce(function (most, block) {
            return Math.max(most, block.weeks);
        }, 1);
        var left = 40;
        var cellWidth = Math.max(3, (ctx.size.width - left - STROOM_BUTTON_SPACE) / maxWeeks);
        var cellHeight = 14;
        var g = ctx.svg.append("g").attr("transform", "translate(" + left + ",24)");
        var selectedDays = 0;
        blocks.forEach(function (block, b) {
            var calendar = g.append("g").attr("transform", "translate(0," + b * 150 + ")");
            svgText(calendar, block.year, -30, cellHeight * 3.5, ctx.style, {anchor: "middle",
                colour: ctx.style.subtleText, size: SMALL_FONT_SIZE, rotate: -90});
            DAYS.forEach(function (name, i) {
                svgText(calendar, name.charAt(0), -6, (i + 0.5) * cellHeight, ctx.style,
                    {anchor: "end", baseline: "middle", colour: ctx.style.subtleText, size: SMALL_FONT_SIZE});
            });
            var cellOf = function (t) {
                return {week: Math.floor((t - block.firstSunday) / dayMs / 7), weekday: new Date(t).getUTCDay()};
            };
            var monthStarts = [];
            var lastMonth = -1;
            for (var t = block.first; t <= block.last; t += dayMs) {
                var date = new Date(t);
                var at = cellOf(t);
                var key = date.toISOString().substring(0, 10);
                if (date.getUTCMonth() !== lastMonth) {
                    lastMonth = date.getUTCMonth();
                    monthStarts.push(t);
                    svgText(calendar, MONTHS[lastMonth].substring(0, 3), (at.week + 0.5) * cellWidth, -8, ctx.style,
                        {colour: ctx.style.subtleText, size: SMALL_FONT_SIZE});
                }
                var value = sums[key];
                var rect = calendar.append("rect").attr("x", at.week * cellWidth).attr("y", at.weekday * cellHeight)
                    .attr("width", cellWidth).attr("height", cellHeight)
                    .attr("fill", value === undefined ? "transparent" :
                        colourAt(colours, max > min ? (value - min) / (max - min) : 1))
                    .attr("stroke", ctx.style.border).attr("stroke-width", 1);
                if (value !== undefined) {
                    selectedDays++;
                    var selection = fields.selection([["date", raws[key]]]);
                    outline(rect, ctx, ctx.isSelected(selection));
                    ctx.mark(rect, selection, key + ": " + ctx.format(value));
                }
            }
            // Each month outlined: the line between it and the month before, round the weeks' columns.
            monthStarts.forEach(function (first) {
                var at = cellOf(first);
                var x = at.week * cellWidth;
                var y = at.weekday * cellHeight;
                var path = at.weekday === 0 ? "M" + x + ",0L" + x + "," + 7 * cellHeight :
                    "M" + (x + cellWidth) + ",0L" + (x + cellWidth) + "," + y + "L" + x + "," + y + "L" + x + "," +
                    7 * cellHeight;
                calendar.append("path").attr("d", path).attr("fill", "none").attr("stroke", ctx.style.subtleText)
                    .attr("stroke-width", 1);
            });
        });
        drawScaleBar(ctx.svg, colours, min, max, ctx.format, ctx.size, ctx.style);
        ctx.geo = g;
        ctx.geoOrigin = [left, 24];
        // A calendar a year, as ECharts counts them.
        ctx.report.series = years.length;
        ctx.report.points = selectedDays;
        ctx.zoomKind = "geo";
    }

    // ------------------------------------------------------------------------------------------
    // Networks and flows: network graph, Sankey, chord
    // ------------------------------------------------------------------------------------------

    // The nodes and links of a network graph from the rows: a node's degree is how many others it links to, links
    // between the same pair are added up (either way round unless the graph is directed), and only the Max Nodes
    // most connected nodes are kept.
    function networkOf(ctx) {
        var fields = ctx.fields;
        var directed = boolSetting(ctx.settings, "directed", false);
        var hasWeight = fields.has("weight");
        var nodes = {};
        var names = [];
        var links = {};
        var linkKeys = [];
        var missing = 0;
        var selfLinks = 0;
        var badWeight = 0;

        function groupOf(row, id) {
            if (!fields.has(id)) {
                return null;
            }
            var value = fields.value(row, id);
            return isBlank(value) ? null : labelOf(value, fields.type(id), ctx.prefs);
        }

        function node(name, group) {
            if (!nodes[name]) {
                nodes[name] = {name: name, neighbours: {}, weight: 0, group: null};
                names.push(name);
            }
            if (nodes[name].group === null && group !== null) {
                nodes[name].group = group;
            }
            return nodes[name];
        }

        // Each row's path (see pathOf) is a link from each step to the next: its source to its target, then on
        // through any further levels.
        var nodeRoles = {};
        ctx.rows.forEach(function (row) {
            var path = pathOf(row, fields, ctx.prefs);
            if (!path) {
                missing++;
                return;
            }
            var weight = hasWeight ? toNumber(fields.value(row, "weight")) : 1;
            if (isNaN(weight) || weight < 0) {
                badWeight++;
                return;
            }
            for (var i = 0; i < path.length - 1; i++) {
                var source = path[i].label;
                var target = path[i + 1].label;
                if (source === target) {
                    selfLinks++;
                    continue;
                }
                var from = node(source, path[i].role === "source" ? groupOf(row, "sourceGroup") : null);
                var to = node(target, path[i + 1].role === "target" ? groupOf(row, "targetGroup") : null);
                [path[i], path[i + 1]].forEach(function (step) {
                    if (nodeRoles[step.label] === undefined) {
                        nodeRoles[step.label] = step.role;
                    }
                });
                from.neighbours[target] = true;
                to.neighbours[source] = true;
                from.weight += weight;
                to.weight += weight;
                var key = directed || source < target ? source + "\u0000" + target : target + "\u0000" + source;
                if (links[key]) {
                    links[key].value += weight;
                } else {
                    links[key] = {source: source, target: target, value: weight, fromRole: path[i].role,
                        toRole: path[i + 1].role};
                    linkKeys.push(key);
                }
            }
        });
        addDropped(ctx.report, "source or target empty", missing);
        addDropped(ctx.report, "source and target the same", selfLinks);
        addDropped(ctx.report, "weight not a number or negative", badWeight);
        names.forEach(function (name) {
            nodes[name].degree = Object.keys(nodes[name].neighbours).length;
        });
        var maxNodes = intSetting(ctx.settings, "maxNodes", 300, 2, 5000);
        if (names.length > maxNodes) {
            names.sort(function (a, b) {
                return (nodes[b].degree - nodes[a].degree) || (nodes[b].weight - nodes[a].weight);
            });
            ctx.report.warnings.push((names.length - maxNodes) + " least connected nodes were left out (Max Nodes)");
            names = names.slice(0, maxNodes);
        }
        var kept = {};
        names.forEach(function (name) {
            kept[name] = true;
        });
        return {
            directed: directed,
            nodeRoles: nodeRoles,
            nodes: names.map(function (name) {
                return nodes[name];
            }),
            links: linkKeys.map(function (key) {
                return links[key];
            }).filter(function (link) {
                return kept[link.source] && kept[link.target];
            })
        };
    }

    // How the layout settles, by the Physics setting: how far apart linked nodes sit and how hard nodes push apart.
    var PHYSICS = {
        settled: {edge: 1, repulsion: 1},
        lively: {edge: 0.85, repulsion: 1.5},
        spread: {edge: 1.7, repulsion: 1.2}
    };

    // Places a network's nodes with a force-directed layout (Fruchterman and Reingold's), from the same start each
    // time, so the same data and size give the same picture (a click doesn't move the nodes).
    function forceLayout(nodes, links, width, height, physics) {
        var n = nodes.length;
        var index = {};
        var positions = nodes.map(function (node, i) {
            index[node.name] = i;
            // A spiral from the centre, by the golden angle.
            var radius = Math.sqrt((i + 0.5) / Math.max(1, n)) * Math.min(width, height) * 0.45;
            var angle = i * 2.399963;
            return [width / 2 + Math.cos(angle) * radius, height / 2 + Math.sin(angle) * radius];
        });
        if (n < 2) {
            return positions;
        }
        var k = Math.sqrt(width * height / n) * 0.6 * physics.edge;
        var maxWeight = links.reduce(function (most, link) {
            return Math.max(most, link.value);
        }, 1);
        var iterations = n > 200 ? 150 : 250;
        var temperature = Math.min(width, height) / 8;
        for (var it = 0; it < iterations; it++) {
            var moves = positions.map(function () {
                return [0, 0];
            });
            for (var a = 0; a < n; a++) {
                for (var b = a + 1; b < n; b++) {
                    var dx = positions[a][0] - positions[b][0];
                    var dy = positions[a][1] - positions[b][1];
                    var distance = Math.max(0.01, Math.sqrt(dx * dx + dy * dy));
                    var push = k * k / distance * physics.repulsion;
                    moves[a][0] += dx / distance * push;
                    moves[a][1] += dy / distance * push;
                    moves[b][0] -= dx / distance * push;
                    moves[b][1] -= dy / distance * push;
                }
            }
            links.forEach(function (link) {
                var s = index[link.source];
                var t = index[link.target];
                var dx = positions[s][0] - positions[t][0];
                var dy = positions[s][1] - positions[t][1];
                var distance = Math.max(0.01, Math.sqrt(dx * dx + dy * dy));
                var pull = distance * distance / k * (0.5 + 0.5 * link.value / maxWeight);
                moves[s][0] -= dx / distance * pull;
                moves[s][1] -= dy / distance * pull;
                moves[t][0] += dx / distance * pull;
                moves[t][1] += dy / distance * pull;
            });
            for (var i = 0; i < n; i++) {
                // A little gravity keeps unlinked nodes from drifting away.
                moves[i][0] += (width / 2 - positions[i][0]) * 0.02 * k;
                moves[i][1] += (height / 2 - positions[i][1]) * 0.02 * k;
                var length = Math.max(0.01, Math.sqrt(moves[i][0] * moves[i][0] + moves[i][1] * moves[i][1]));
                var step = Math.min(length, temperature);
                positions[i][0] = Math.max(20, Math.min(width - 20, positions[i][0] + moves[i][0] / length * step));
                positions[i][1] = Math.max(20, Math.min(height - 20, positions[i][1] + moves[i][1] / length * step));
            }
            temperature = Math.max(0.5, temperature * 0.97);
        }
        return positions;
    }

    // ECharts' adjacency focus: while the pointer is over a node (or a link), everything not joined to it fades, and
    // its links widen. Each item is {element, names: the nodes it is or joins, width: a link's width or null}.
    function focusAdjacency(items) {
        items.forEach(function (item) {
            item.element.addEventListener("mouseenter", function () {
                var near = {};
                item.names.forEach(function (name) {
                    near[name] = true;
                });
                // A node's neighbours are near too.
                if (item.width === null) {
                    items.forEach(function (other) {
                        if (other.width !== null && other.names.indexOf(item.names[0]) >= 0) {
                            other.names.forEach(function (name) {
                                near[name] = true;
                            });
                        }
                    });
                }
                items.forEach(function (other) {
                    var joined = other.width !== null
                        ? (item.width === null ? other.names.indexOf(item.names[0]) >= 0 : other === item)
                        : near[other.names[0]] === true;
                    other.element.style.opacity = joined ? "" : "0.1";
                    if (other.width !== null && joined && other.widen) {
                        other.element.setAttribute("stroke-width", String(Math.max(other.width, 4)));
                    }
                });
            });
            item.element.addEventListener("mouseleave", function () {
                items.forEach(function (other) {
                    other.element.style.opacity = "";
                    if (other.width !== null && other.widen) {
                        other.element.setAttribute("stroke-width", String(other.width));
                    }
                });
            });
        });
    }

    function drawGraph(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["source", "target"]);
        var network = networkOf(ctx);
        var byGroup = String(textSetting(settings, "nodeColours") || "group").toLowerCase() !== "node";
        var groups = [];
        network.nodes.forEach(function (node) {
            var group = node.group === null ? "(none)" : node.group;
            if (groups.indexOf(group) < 0) {
                groups.push(group);
            }
        });
        var hasGroups = fields.has("sourceGroup") || fields.has("targetGroup");
        var colours = paletteOf(ctx.options, byGroup ? Math.max(1, groups.length) : network.nodes.length,
            ctx.style);
        var legend = legendOf(ctx, byGroup && hasGroups ? groups : [], colours, "rect");
        var shownNodes = network.nodes.filter(function (node) {
            return !ctx.hidden[node.group === null ? "(none)" : node.group];
        });
        var shown = {};
        shownNodes.forEach(function (node) {
            shown[node.name] = true;
        });
        var shownLinks = network.links.filter(function (link) {
            return shown[link.source] && shown[link.target];
        });
        var plot = plotArea(ctx, legend, 4, -22);
        var physics = PHYSICS[String(textSetting(settings, "physics") || "settled").toLowerCase()] ||
            PHYSICS.settled;
        var cacheKey = [plot.width, plot.height, textSetting(settings, "physics"), shownNodes.map(function (node) {
            return node.name;
        }).join("\u0001"), shownLinks.map(function (link) {
            return link.source + "\u0002" + link.target + "\u0002" + link.value;
        }).join("\u0001")].join("\u0003");
        if (!ctx.cache.layout || ctx.cache.layoutKey !== cacheKey) {
            ctx.cache.layout = forceLayout(shownNodes, shownLinks, plot.width, plot.height, physics);
            ctx.cache.layoutKey = cacheKey;
        }
        var positions = ctx.cache.layout;
        var at = {};
        shownNodes.forEach(function (node, i) {
            at[node.name] = positions[i];
        });
        var degrees = shownNodes.map(function (node) {
            return node.degree;
        });
        var minDegree = degrees.length ? Math.min.apply(null, degrees) : 0;
        var maxDegree = degrees.length ? Math.max.apply(null, degrees) : 1;
        var values = shownLinks.map(function (link) {
            return link.value;
        });
        var minValue = values.length ? Math.min.apply(null, values) : 0;
        var maxValue = values.length ? Math.max.apply(null, values) : 1;
        // ECharts' node sizes: from 8 to 48 pixels across, by how many others each links to, or (Node Size total)
        // by its links' total (their weights, or the rows they come from).
        var totals = {};
        shownNodes.forEach(function (node) {
            totals[node.name] = 0;
        });
        shownLinks.forEach(function (link) {
            totals[link.source] += link.value;
            totals[link.target] += link.value;
        });
        var totalValues = shownNodes.map(function (node) {
            return totals[node.name];
        });
        var minTotal = totalValues.length ? Math.min.apply(null, totalValues) : 0;
        var maxTotal = totalValues.length ? Math.max.apply(null, totalValues) : 1;
        var sizeByTotal = String(textSetting(settings, "nodeSize") || "connections").toLowerCase() === "total";
        var radiusOf = function (node) {
            return sizeByTotal ? scaleSqrt(totals[node.name], minTotal, maxTotal, 4, 24)
                : scaleSqrt(node.degree, minDegree, maxDegree, 4, 24);
        };
        var colourOf = function (node, i) {
            return byGroup ? colours[groups.indexOf(node.group === null ? "(none)" : node.group) % colours.length] :
                colours[i % colours.length];
        };
        var g = ctx.svg.append("g").attr("transform", "translate(" + plot.left + "," + plot.top + ")");
        var content = g.append("g");
        ctx.geo = content;
        // Zoomed within the group already placed at the plot.
        ctx.geoOrigin = [0, 0];
        var nodeByName = {};
        var colourByName = {};
        shownNodes.forEach(function (node, i) {
            nodeByName[node.name] = node;
            colourByName[node.name] = colourOf(node, i);
        });
        var focusItems = [];
        var defs = ctx.svg.append("defs");
        // As ECharts draws a graph's links: each in its source node's colour, half see-through, as wide as its
        // weight; a directed graph's curved a little, with an arrow at the target.
        shownLinks.forEach(function (link, l) {
            var from = at[link.source];
            var to = at[link.target];
            // As wide as its weight, or (without one) the rows it comes from, as ECharts draws it.
            var width = fields.has("weight") || maxValue > minValue ? scaleSqrt(link.value, minValue, maxValue, 1, 6)
                : 1;
            var colour = colourByName[link.source];
            var d;
            if (network.directed) {
                // Curved, and stopping at the target node's edge, so the arrow shows.
                var dx = to[0] - from[0];
                var dy = to[1] - from[1];
                var distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                var control = [(from[0] + to[0]) / 2 - dy * 0.1, (from[1] + to[1]) / 2 + dx * 0.1];
                var cdx = to[0] - control[0];
                var cdy = to[1] - control[1];
                var cDistance = Math.max(1, Math.sqrt(cdx * cdx + cdy * cdy));
                var back = radiusOf(nodeByName[link.target]) + 1;
                var end = [to[0] - cdx / cDistance * back, to[1] - cdy / cDistance * back];
                d = "M" + from + "Q" + control + " " + end;
                var markerId = "stroom-d3-arrow-" + ctx.id + "-" + l;
                defs.append("marker").attr("id", markerId).attr("viewBox", "0 0 10 10")
                    .attr("refX", 10).attr("refY", 5).attr("markerWidth", 6).attr("markerHeight", 6)
                    .attr("markerUnits", "userSpaceOnUse").attr("orient", "auto").append("path")
                    .attr("d", "M0,0L10,5L0,10Z").attr("fill", colour);
            } else {
                d = "M" + from + "L" + to;
            }
            var selection = linkSelection(fields, link);
            if (ctx.isSelected(selection)) {
                // A solid line either side of a selected link.
                content.append("path").attr("d", d).attr("fill", "none")
                    .attr("stroke", selectionColour(ctx.style)).attr("stroke-width", width + 2 * LINK_OUTLINE)
                    .attr("stroke-linecap", "round");
            }
            var line = content.append("path").attr("d", d).attr("fill", "none").attr("stroke", colour)
                .attr("stroke-opacity", 0.5).attr("stroke-width", width);
            if (network.directed) {
                line.attr("marker-end", "url(#stroom-d3-arrow-" + ctx.id + "-" + l + ")");
            }
            ctx.mark(line, selection, link.source + (network.directed ? " → " : " – ") + link.target +
                ": " + ctx.format(link.value));
            focusItems.push({element: line.node(), names: [link.source, link.target], width: width, widen: true});
        });
        var showLabels = String(textSetting(settings, "showLabels") || "auto").toLowerCase();
        var labelled = {};
        if (showLabels === "true" || (showLabels !== "false" && shownNodes.length <= 60)) {
            shownNodes.forEach(function (node) {
                labelled[node.name] = true;
            });
        } else if (showLabels !== "false") {
            shownNodes.slice().sort(function (a, b) {
                return b.degree - a.degree;
            }).slice(0, Math.ceil(shownNodes.length / 10)).forEach(function (node) {
                labelled[node.name] = true;
            });
        }
        shownNodes.forEach(function (node) {
            var position = at[node.name];
            var selection = nodeSelection(fields, network, node.name);
            var nodeGroup = content.append("g");
            var circle = nodeGroup.append("circle").attr("cx", position[0]).attr("cy", position[1])
                .attr("r", radiusOf(node)).attr("fill", colourByName[node.name]);
            outline(circle, ctx, ctx.isSelected(selection));
            ctx.mark(circle, selection, node.name + (node.group !== null ? " (" + node.group + ")" : "") + ": " +
                node.degree + " connections, " + (fields.column("weight") || "rows") + " " +
                ctx.format(totals[node.name]));
            if (labelled[node.name]) {
                svgText(nodeGroup, shorten(node.name, 24), position[0] + radiusOf(node) + 5, position[1], ctx.style,
                    {baseline: "middle", size: SMALL_FONT_SIZE});
            }
            focusItems.push({element: nodeGroup.node(), names: [node.name], width: null});
        });
        focusAdjacency(focusItems);
        ctx.report.series = 1;
        ctx.report.nodes = shownNodes.length;
        ctx.report.links = shownLinks.length;
        ctx.report.points = shownNodes.length;
        ctx.zoomKind = "geo";
        ctx.plot = plot;
        if (boolSetting(settings, "thumbnail", false)) {
            ctx.thumbnail = {
                nodes: shownNodes.map(function (node) {
                    return {at: at[node.name], r: radiusOf(node), colour: colourByName[node.name]};
                }),
                links: shownLinks.map(function (link) {
                    return {from: at[link.source], to: at[link.target], colour: colourByName[link.source]};
                })
            };
        }
    }

    // Leaves out the weakest flows that would close a loop (a to b, b to a, ...), which a Sankey diagram can't
    // draw: the strongest flows are kept first.
    function withoutLoops(links, report) {
        var kept = [];
        var next = {};
        function reaches(from, to, seen) {
            if (from === to) {
                return true;
            }
            seen[from] = true;
            return (next[from] || []).some(function (node) {
                return !seen[node] && reaches(node, to, seen);
            });
        }
        var looping = 0;
        links.slice().sort(function (a, b) {
            return b.value - a.value;
        }).forEach(function (link) {
            if (reaches(link.target, link.source, {})) {
                looping++;
                return;
            }
            (next[link.source] = next[link.source] || []).push(link.target);
            kept.push(link);
        });
        addDropped(report, "flow would close a loop", looping);
        if (looping > 0) {
            report.warnings.push(looping + " flows that would close a loop were left out");
        }
        return links.filter(function (link) {
            return kept.indexOf(link) >= 0;
        });
    }

    // Orders each column's nodes to cross fewer flows, as ECharts' layout does: a few sweeps right then left, each
    // node placed by the middle of the nodes it flows from (or to) in the column before (or after).
    function orderColumns(columns, links, valueOf) {
        var position = {};
        var number = function () {
            columns.forEach(function (column) {
                var across = 0;
                column.forEach(function (name) {
                    position[name] = across + valueOf(name) / 2;
                    across += valueOf(name);
                });
            });
        };
        // The middle of the nodes a node flows from (sources), to (targets), or both, weighted by the flows.
        var centreOf = function (name, ends) {
            var total = 0;
            var weighted = 0;
            links.forEach(function (link) {
                var other = null;
                if (link.target === name && ends !== "targets") {
                    other = link.source;
                } else if (link.source === name && ends !== "sources") {
                    other = link.target;
                }
                if (other !== null && position[other] !== undefined) {
                    total += link.value;
                    weighted += position[other] * link.value;
                }
            });
            return total > 0 ? weighted / total : position[name];
        };
        number();
        for (var sweep = 0; sweep < 24; sweep++) {
            var forward = sweep % 2 === 0;
            for (var c = forward ? 1 : columns.length - 2; forward ? c < columns.length : c >= 0;
                c += forward ? 1 : -1) {
                var centres = {};
                // The first and last columns follow their one neighbour; those between, both, once settled.
                var ends = c === 0 ? "targets" : c === columns.length - 1 ? "sources" :
                    sweep >= 12 ? "both" : forward ? "sources" : "targets";
                columns[c].forEach(function (name) {
                    centres[name] = centreOf(name, ends);
                });
                columns[c].sort(function (a, b) {
                    return centres[a] - centres[b];
                });
                number();
            }
        }
    }

    function drawSankey(ctx) {
        var fields = ctx.fields;
        requireFields(fields, ["source", "target"]);
        var network = linkTotals(ctx.rows, fields, ctx.settings, ctx.prefs, ctx.report, "value", true);
        var links = withoutLoops(network.links, ctx.report);
        var names = network.nodeNames.filter(function (name) {
            return links.some(function (link) {
                return link.source === name || link.target === name;
            });
        });
        // Each node's column: the longest run of flows into it.
        var depth = {};
        names.forEach(function (name) {
            depth[name] = 0;
        });
        for (var pass = 0; pass < names.length; pass++) {
            var changed = false;
            links.forEach(function (link) {
                if (depth[link.target] < depth[link.source] + 1) {
                    depth[link.target] = depth[link.source] + 1;
                    changed = true;
                }
            });
            if (!changed) {
                break;
            }
        }
        var columns = [];
        names.forEach(function (name) {
            (columns[depth[name]] = columns[depth[name]] || []).push(name);
        });
        columns = columns.filter(Boolean);
        var inflow = {};
        var outflow = {};
        links.forEach(function (link) {
            outflow[link.source] = (outflow[link.source] || 0) + link.value;
            inflow[link.target] = (inflow[link.target] || 0) + link.value;
        });
        var valueOf = function (name) {
            return Math.max(inflow[name] || 0, outflow[name] || 0);
        };
        // Laid out as ECharts lays one out: nodes 20 pixels wide and 8 apart, room on the right (or below) for
        // the labels.
        var vertical = String(textSetting(ctx.settings, "orient") || "horizontal").toLowerCase() === "vertical";
        var box = vertical ? {left: 16, top: 40, right: STROOM_BUTTON_SPACE, bottom: 40} :
            {left: 16, top: 16, right: 120, bottom: 16};
        var length = vertical ? ctx.size.height - box.top - box.bottom : ctx.size.width - box.left - box.right;
        var breadth = vertical ? ctx.size.width - box.left - box.right : ctx.size.height - box.top - box.bottom;
        var nodeWidth = 20;
        var gap = 8;
        orderColumns(columns, links, valueOf);
        // One scale for all columns: the fullest column fills the breadth.
        var scale = columns.reduce(function (least, column) {
            var total = column.reduce(function (sum, name) {
                return sum + valueOf(name);
            }, 0);
            return Math.min(least, (breadth - gap * (column.length - 1)) / Math.max(1e-9, total));
        }, Infinity);
        if (!isFinite(scale)) {
            scale = 1;
        }
        var place = {};
        columns.forEach(function (column, c) {
            var along = columns.length > 1 ? c * (length - nodeWidth) / (columns.length - 1) : length / 2;
            var across = 0;
            column.forEach(function (name) {
                var size = Math.max(1, valueOf(name) * scale);
                place[name] = {along: along, across: across, size: size, out: 0, in: 0};
                across += size + gap;
            });
        });
        var colours = paletteOf(ctx.options, names.length, ctx.style);
        var colourOf = function (name) {
            return colours[names.indexOf(name) % colours.length];
        };
        var g = ctx.svg.append("g").attr("transform", "translate(" + box.left + "," + box.top + ")");
        ctx.geo = g;
        ctx.geoOrigin = [box.left, box.top];
        var defs = ctx.svg.append("defs");
        var focusItems = [];
        var point = function (along, across) {
            return vertical ? [across, along] : [along, across];
        };
        links.slice().sort(function (a, b) {
            return place[a.target].across - place[b.target].across;
        }).forEach(function (link) {
            var from = place[link.source];
            var to = place[link.target];
            var width = Math.max(1, link.value * scale);
            var start = point(from.along + nodeWidth, from.across + from.out + width / 2);
            var end = point(to.along, to.across + to.in + width / 2);
            from.out += width;
            to.in += width;
            var mid = (from.along + nodeWidth + to.along) / 2;
            var c1 = point(mid, from.across + from.out - width / 2);
            var c2 = point(mid, to.across + to.in - width / 2);
            var d = "M" + start + "C" + c1 + " " + c2 + " " + end;
            var selection = linkSelection(fields, link);
            if (ctx.isSelected(selection)) {
                g.append("path").attr("d", d).attr("fill", "none").attr("stroke", selectionColour(ctx.style))
                    .attr("stroke-width", width + 2 * LINK_OUTLINE);
            }
            // Shaded from the source's colour to the target's, as ECharts shades a flow.
            var gradientId = "stroom-d3-flow-" + ctx.id + "-" + focusItems.length;
            var gradient = defs.append("linearGradient").attr("id", gradientId).attr("gradientUnits", "userSpaceOnUse")
                .attr("x1", start[0] + box.left).attr("y1", start[1] + box.top)
                .attr("x2", end[0] + box.left).attr("y2", end[1] + box.top);
            gradient.append("stop").attr("offset", "0%").attr("stop-color", colourOf(link.source));
            gradient.append("stop").attr("offset", "100%").attr("stop-color", colourOf(link.target));
            var path = g.append("path").attr("d", d).attr("fill", "none")
                .attr("stroke", "url(#" + gradientId + ")").attr("stroke-opacity", 0.35)
                .attr("stroke-width", width);
            ctx.mark(path, selection, link.source + " → " + link.target + ": " + ctx.format(link.value));
            focusItems.push({element: path.node(), names: [link.source, link.target], width: width});
        });
        var showLabels = boolSetting(ctx.settings, "showLabels", true);
        names.forEach(function (name) {
            var node = place[name];
            var corner = point(node.along, node.across);
            var nodeGroup = g.append("g");
            var rect = nodeGroup.append("rect").attr("x", corner[0]).attr("y", corner[1])
                .attr("width", vertical ? node.size : nodeWidth).attr("height", vertical ? nodeWidth : node.size)
                .attr("fill", colourOf(name));
            var selection = nodeSelection(fields, network, name);
            outline(rect, ctx, ctx.isSelected(selection));
            ctx.mark(rect, selection, name + ": " + ctx.format(valueOf(name)));
            // Beside each node (below, when vertical), as ECharts places them.
            if (showLabels) {
                var label = point(node.along + nodeWidth + 5, node.across + node.size / 2);
                svgText(nodeGroup, shorten(name, 24), label[0], label[1], ctx.style, vertical ?
                    {anchor: "middle", baseline: "hanging", size: SMALL_FONT_SIZE} :
                    {anchor: "start", baseline: "middle", size: SMALL_FONT_SIZE});
            }
            focusItems.push({element: nodeGroup.node(), names: [name], width: null});
        });
        focusAdjacency(focusItems);
        ctx.report.series = 1;
        ctx.report.nodes = names.length;
        ctx.report.links = links.length;
        ctx.report.points = links.length;
        ctx.zoomKind = "geo";
    }

    function drawChord(ctx) {
        var fields = ctx.fields;
        requireFields(fields, ["source", "target"]);
        var network = linkTotals(ctx.rows, fields, ctx.settings, ctx.prefs, ctx.report, "weight");
        var names = network.nodeNames;
        var links = network.links;
        var totals = {};
        links.forEach(function (link) {
            totals[link.source] = (totals[link.source] || 0) + link.value;
            totals[link.target] = (totals[link.target] || 0) + link.value;
        });
        names = names.filter(function (name) {
            return totals[name] > 0;
        });
        var total = names.reduce(function (sum, name) {
            return sum + totals[name];
        }, 0);
        // ECharts' proportions: the ring from 70% to 80% of the way to the nearer edge of the space left for it, the
        // ribbons inside it.
        var box = {left: 24, top: 24, width: ctx.size.width - 24 - STROOM_BUTTON_SPACE, height: ctx.size.height - 48};
        var outer = Math.max(24, Math.min(box.width, box.height) / 2 * 0.8);
        var radius = outer * 7 / 8;
        var padding = Math.min(0.05, Math.PI / Math.max(1, names.length));
        var available = Math.PI * 2 - padding * names.length;
        var arcs = {};
        var angle = 0;
        names.forEach(function (name) {
            var sweep = total > 0 ? totals[name] / total * available : 0;
            arcs[name] = {start: angle, end: angle + sweep, used: angle};
            angle += sweep + padding;
        });
        var colours = paletteOf(ctx.options, names.length, ctx.style);
        var centre = [box.left + box.width / 2, box.top + box.height / 2];
        var g = ctx.svg.append("g").attr("transform", "translate(" + centre[0] + "," + centre[1] + ")");
        ctx.geo = g;
        ctx.geoOrigin = centre;
        var focusItems = [];
        var at = function (a) {
            return [Math.sin(a) * radius, -Math.cos(a) * radius];
        };
        // Each link takes a share of both its ends' arcs, as wide as its weight.
        links.forEach(function (link) {
            if (!arcs[link.source] || !arcs[link.target] || total <= 0) {
                return;
            }
            var share = link.value / total * available;
            var s0 = arcs[link.source].used;
            var s1 = s0 + share;
            arcs[link.source].used = s1;
            var t0 = arcs[link.target].used;
            var t1 = t0 + share;
            arcs[link.target].used = t1;
            var big = function (from, to) {
                return to - from > Math.PI ? 1 : 0;
            };
            var d = "M" + at(s0) + "A" + radius + "," + radius + " 0 " + big(s0, s1) + ",1 " + at(s1) + "Q0,0 " +
                at(t0) + "A" + radius + "," + radius + " 0 " + big(t0, t1) + ",1 " + at(t1) + "Q0,0 " + at(s0) + "Z";
            var selection = linkSelection(fields, link);
            var ribbon = g.append("path").attr("d", d)
                .attr("fill", colours[names.indexOf(link.source) % colours.length]).attr("fill-opacity", 0.5)
                .attr("stroke", "none");
            outline(ribbon, ctx, ctx.isSelected(selection));
            ctx.mark(ribbon, selection, link.source + " – " + link.target + ": " + ctx.format(link.value));
            focusItems.push({element: ribbon.node(), names: [link.source, link.target], width: 0});
        });
        // Rounded at the corners as ECharts' are (D3 3's arcs can't be).
        var arc = ctx.C.arc().innerRadius(radius).outerRadius(outer);
        if (typeof arc.cornerRadius === "function") {
            arc.cornerRadius(3);
        }
        var showLabels = boolSetting(ctx.settings, "showLabels", true);
        names.forEach(function (name, i) {
            var part = arcs[name];
            var selection = nodeSelection(fields, network, name);
            var nodeGroup = g.append("g");
            var path = nodeGroup.append("path").attr("d", arc({startAngle: part.start, endAngle: part.end}))
                .attr("fill", colours[i % colours.length]);
            outline(path, ctx, ctx.isSelected(selection));
            ctx.mark(path, selection, name + ": " + ctx.format(totals[name]));
            if (showLabels && part.end - part.start > 0.05) {
                var middle = (part.start + part.end) / 2;
                svgText(nodeGroup, shorten(name, 18), Math.sin(middle) * (outer + 5), -Math.cos(middle) * (outer + 5),
                    ctx.style, {anchor: Math.sin(middle) >= 0 ? "start" : "end", baseline: "middle",
                        size: SMALL_FONT_SIZE});
            }
            focusItems.push({element: nodeGroup.node(), names: [name], width: null});
        });
        focusAdjacency(focusItems);
        ctx.report.series = 1;
        ctx.report.nodes = names.length;
        ctx.report.links = links.length;
        ctx.report.points = links.length;
        ctx.zoomKind = "geo";
    }

    var DRAW = {
        bar: drawBar,
        line: drawLine,
        pie: drawPie,
        scatter: drawScatter,
        graph: drawGraph,
        heatmap: drawHeatmap,
        calendar: drawCalendar,
        histogram: drawHistogram,
        sankey: drawSankey,
        chord: drawChord,
        gauge: drawGauge,
        sunburst: drawSunburst
    };

    // ------------------------------------------------------------------------------------------
    // Drawing a chart
    // ------------------------------------------------------------------------------------------

    function newReport(kind, d3) {
        return {
            status: "ok",
            adapter: "D3 Generic",
            adapterVersion: ADAPTER_VERSION,
            library: "D3",
            libraryVersion: d3 && d3.version ? String(d3.version) : null,
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

    // The Options setting: with D3 only the colours (color, a list) are used; anything else is left out and said so.
    function optionsOf(json, report) {
        if (isBlank(json)) {
            return {};
        }
        var parsed;
        try {
            parsed = JSON.parse(json);
        } catch (e) {
            report.warnings.push("Options aren't valid JSON, so they were left out");
            return {};
        }
        if (!isPlainObject(parsed)) {
            report.warnings.push("Options must be a JSON object, so they were left out");
            return {};
        }
        var ignored = Object.keys(parsed).filter(function (key) {
            return key !== "color";
        });
        if (ignored.length > 0) {
            report.warnings.push("With D3 only the color option is used; left out: " + ignored.join(", "));
        }
        return {color: parsed.color};
    }

    function selectionKey(selection) {
        return JSON.stringify(Object.keys(selection).sort().map(function (key) {
            return [key, selection[key]];
        }));
    }

    // Draws the chart for the data and settings into an element, and says what was drawn. Throws an Error a user
    // can act on when the data can't make the chart. Exposed for testing.
    //
    // state: what the chart remembers between draws: the series hidden from the legend, the zoom, the keys of
    // what is selected, a cache (e.g. a network's layout) and an id unique to the chart.
    function drawChart(d3, host, settings, data, prefs, style, state) {
        settings = settings || {};
        state = state || {};
        prefs = merge({}, prefs || {});
        prefs.formatValue = unitFormatter(textSetting(settings, "units"));
        var kind = String(settings.kind || "").toLowerCase();
        if (KINDS.indexOf(kind) < 0) {
            throw new Error("Unknown chart kind '" + settings.kind + "'. Expected one of " + KINDS.join(", "));
        }
        var report = newReport(kind, d3);
        var rows = dataRows(data);
        report.rows = rows.length;
        // Auto Interval: the rows grouped into an interval suiting the time shown (the part zoomed in on).
        var grouped = autoInterval(kind, data, settings, prefs, state.zoom || null, state.interval || null);
        // Stroom sends at most Max Rows rows: at the limit, the rest of the table was left out, not summarised.
        var maxRows = Number(settings.maxValues);
        if (isFinite(maxRows) && maxRows > 0 && rows.length >= maxRows) {
            report.truncated = true;
            report.warnings.push("Showing the first " + maxRows + " rows: the table has more, which were left " +
                "out. Group the table (e.g. by a rounded time, such as floorDay()) or raise Max Rows.");
        }
        data = grouped.data;
        rows = dataRows(data);
        var size = {
            width: host.clientWidth > 0 ? host.clientWidth : 600,
            height: host.clientHeight > 0 ? host.clientHeight : 400
        };
        var root = d3.select(host);
        root.selectAll("svg").remove();
        var svg = root.append("svg")
            .attr("width", size.width)
            .attr("height", size.height)
            .attr("font-family", style.fontFamily)
            .style("display", "block");
        var marks = [];
        var ctx = {
            d3: d3,
            C: compat(d3),
            svg: svg,
            size: size,
            style: style,
            prefs: prefs,
            settings: settings,
            options: optionsOf(settings.options, report),
            fields: new Fields(settings, data, prefs),
            rows: rows,
            report: report,
            format: prefs.formatValue,
            hidden: state.hidden || {},
            zoom: state.zoom || null,
            cache: state.cache || {},
            id: state.id || "chart",
            toggleSeries: state.toggleSeries || function () {
            },
            // The legend's page shown, when its entries don't all fit, and how to show another.
            legendPage: state.legendPage || 0,
            setLegendPage: state.setLegendPage || function () {
            },
            isSelected: function (selection) {
                return !!selection && !!(state.selectedKeys && state.selectedKeys[selectionKey(selection)]);
            },
            // Remembers what a drawn mark is: what a click on it selects (null for nothing) and its tooltip.
            mark: function (node, selection, tooltip) {
                var element = node.node();
                element.__stroomMark = {selection: selection, tooltip: tooltip};
                marks.push(element);
                return node;
            }
        };
        DRAW[kind](ctx);
        // A chart that zooms as a picture: the zoom applied about its own origin.
        if (ctx.zoomKind === "geo" && ctx.geo && state.geoZoom) {
            var origin = ctx.geoOrigin || [0, 0];
            ctx.geo.attr("transform", "translate(" + (origin[0] + state.geoZoom.x) + "," + (origin[1] +
                state.geoZoom.y) + ") scale(" + state.geoZoom.k + ")");
        }
        var slider = ctx.zoomKind === "axis" && ctx.plot && hasZoomSlider(ctx) ? drawZoomSlider(ctx) : null;
        var thumbnail = ctx.thumbnail && ctx.plot ? drawThumbnail(ctx, state.geoZoom) : null;
        // Where the class draws the axis tooltip's pointer: over the plot, ignoring the mouse.
        var pointerLayer = ctx.axisTooltip && ctx.plot ? svg.append("g").attr("class", "pointer")
            .attr("transform", "translate(" + ctx.plot.left + "," + ctx.plot.top + ")")
            .attr("pointer-events", "none") : null;
        report.width = size.width;
        report.height = size.height;
        if (rows.length === 0) {
            report.status = "empty";
        } else if (report.points === 0) {
            report.status = "nothing-drawn";
        }
        return {report: report, marks: marks, svg: svg, zoomKind: ctx.zoomKind || null, plot: ctx.plot || null,
            timeAxis: ctx.timeAxis || null, size: size, slider: slider, thumbnail: thumbnail,
            axisTooltip: ctx.axisTooltip || null, pointerLayer: pointerLayer,
            categoriesDown: !!ctx.categoriesDown, interval: grouped.interval, intervals: grouped.intervals,
            intervalChoice: grouped.choice, data: data};
    }

    // A zoom slider along the plot, as ECharts draws one: the data's shape in a track, and the part zoomed in on
    // as a window with a handle at each end, to drag. Along the bottom, or down the right of a horizontal bar chart.
    function drawZoomSlider(ctx) {
        var plot = ctx.plot;
        var style = ctx.style;
        var down = !!ctx.categoriesDown;
        var thickness = 16;
        var x0 = down ? ctx.size.width - 8 - thickness : plot.left;
        var y0 = down ? plot.top : ctx.size.height - 6 - thickness;
        var length = down ? plot.height : plot.width;
        var zoom = ctx.zoom || {start: 0, end: 1};
        var g = ctx.svg.append("g").attr("class", "zoom-slider")
            .attr("transform", "translate(" + x0 + "," + y0 + ")");
        var track = g.append("rect").attr("width", down ? thickness : length).attr("height", down ? length : thickness)
            .attr("fill", "transparent").attr("stroke", style.border);
        track.node().__stroomSlider = {part: "track"};
        var shadow = (ctx.sliderShadow || []).filter(function (point) {
            return isFinite(point[0]) && isFinite(point[1]);
        });
        var most = shadow.reduce(function (max, point) {
            return Math.max(max, Math.abs(point[1]));
        }, 0);
        if (shadow.length > 1 && most > 0) {
            var path = shadow.map(function (point) {
                var along = point[0] * length;
                var height = Math.abs(point[1]) / most * (thickness - 2);
                return down ? [thickness - height, along] : [along, thickness - height];
            });
            var closed = down ? [[thickness, length]].concat(path, [[thickness, 0]]) :
                [[0, thickness]].concat(path, [[length, thickness]]);
            g.append("path").attr("d", "M" + closed.join("L") + "Z").attr("fill", style.subtleText)
                .attr("fill-opacity", 0.3).attr("pointer-events", "none");
        }
        var from = zoom.start * length;
        var to = zoom.end * length;
        var shown = g.append("rect").attr("x", down ? 0 : from).attr("y", down ? from : 0)
            .attr("width", down ? thickness : Math.max(2, to - from)).attr("height", down ? Math.max(2, to - from) :
                thickness).attr("fill", style.subtleText).attr("fill-opacity", 0.2).style("cursor", "move");
        shown.node().__stroomSlider = {part: "window"};
        [["start", from], ["end", to]].forEach(function (end) {
            var handle = g.append("rect").attr("x", down ? -2 : end[1] - 3).attr("y", down ? end[1] - 3 : -2)
                .attr("width", down ? thickness + 4 : 6).attr("height", down ? 6 : thickness + 4).attr("rx", 2)
                .attr("fill", style.subtleText).style("cursor", down ? "ns-resize" : "ew-resize");
            handle.node().__stroomSlider = {part: end[0]};
        });
        return {x: x0, y: y0, length: length, down: down};
    }

    // An overview of a whole network graph in the bottom-right corner, as ECharts' thumbnail shows one, with the
    // part zoomed to marked. Clicking or dragging in it moves the view there.
    function drawThumbnail(ctx, geoZoom) {
        var plot = ctx.plot;
        var style = ctx.style;
        var width = ctx.size.width * 0.22;
        var height = ctx.size.height * 0.22;
        var x = ctx.size.width - 8 - width;
        var y = ctx.size.height - 8 - height;
        var scale = Math.min(width / Math.max(1, plot.width), height / Math.max(1, plot.height));
        var dx = (width - plot.width * scale) / 2;
        var dy = (height - plot.height * scale) / 2;
        var g = ctx.svg.append("g").attr("class", "thumbnail").attr("transform", "translate(" + x + "," + y + ")");
        var back = g.append("rect").attr("width", width).attr("height", height).attr("fill", style.tooltipBackground)
            .attr("stroke", style.border).style("cursor", "pointer");
        var content = g.append("g").attr("transform", "translate(" + dx + "," + dy + ") scale(" + scale + ")")
            .attr("pointer-events", "none");
        ctx.thumbnail.links.forEach(function (link) {
            content.append("line").attr("x1", link.from[0]).attr("y1", link.from[1]).attr("x2", link.to[0])
                .attr("y2", link.to[1]).attr("stroke", link.colour).attr("stroke-opacity", 0.5)
                .attr("stroke-width", 1 / scale);
        });
        ctx.thumbnail.nodes.forEach(function (node) {
            content.append("circle").attr("cx", node.at[0]).attr("cy", node.at[1]).attr("r", node.r)
                .attr("fill", node.colour);
        });
        // The part of the graph in view.
        var zoom = geoZoom || {k: 1, x: 0, y: 0};
        var left = Math.max(0, dx + (-zoom.x / zoom.k) * scale);
        var top = Math.max(0, dy + (-zoom.y / zoom.k) * scale);
        var right = Math.min(width, dx + ((plot.width - zoom.x) / zoom.k) * scale);
        var bottom = Math.min(height, dy + ((plot.height - zoom.y) / zoom.k) * scale);
        if (right > left && bottom > top) {
            g.append("rect").attr("x", left).attr("y", top).attr("width", right - left).attr("height", bottom - top)
                .attr("fill", style.subtleText).attr("fill-opacity", 0.15).attr("stroke", style.subtleText)
                .attr("pointer-events", "none");
        }
        var info = {x: x + dx, y: y + dy, scale: scale, plot: plot};
        back.node().__stroomThumbnail = info;
        return info;
    }

    // ------------------------------------------------------------------------------------------
    // The visualisation class Stroom creates
    // ------------------------------------------------------------------------------------------

    var chartCount = 0;

    visualisations.D3Generic = function () {
        var element = window.document.createElement("div");
        element.className = "vis";
        element.style.position = "relative";
        element.style.overflow = "hidden";

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

        var tooltip = window.document.createElement("div");
        tooltip.style.position = "absolute";
        tooltip.style.pointerEvents = "none";
        tooltip.style.padding = "4px 8px";
        tooltip.style.borderRadius = "4px";
        tooltip.style.fontSize = "12px";
        tooltip.style.whiteSpace = "nowrap";
        tooltip.style.zIndex = "3";
        tooltip.style.display = "none";
        element.appendChild(tooltip);

        var lastArgs = null;
        var lastReportJson = null;
        var lastReport = null;
        var lastStyle = null;
        var lastPrefs = null;
        var drawn = null;
        // What is selected: a time range ({range, label, selection}) or the items clicked ({items}).
        var selected = null;
        // What the chart remembers between draws (see drawChart).
        var state = {
            hidden: {},
            zoom: null,
            geoZoom: null,
            cache: {},
            selectedKeys: {},
            id: "c" + (++chartCount)
        };
        var rangeMode = false;

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

        // Tells Stroom whether something is selected on the chart, with the chart's report, and what the user is
        // looking at (see setView), as the ECharts adapter does.
        function reportSelection() {
            if (lastReport) {
                lastReport.selected = !!selected;
                setView(lastReport, {
                    interval: drawn ? drawn.interval : null,
                    shown: zoomShown(),
                    selection: selected ? (selected.items ? selected.items.map(function (item) {
                        return item.label;
                    }).join("; ") : selected.label) : null,
                    hiddenSeries: Object.keys(state.hidden || {})
                });
                sendReport(lastReport);
            }
        }

        // The part of the chart zoomed in on, as the user sees it: the time shown, the part of the axis, or how far
        // a network graph is zoomed in; null if it isn't zoomed.
        function zoomShown() {
            if (!drawn) {
                return null;
            }
            if (drawn.zoomKind === "geo") {
                return state.geoZoom && state.geoZoom.k !== 1
                    ? "zoomed in " + (Math.round(state.geoZoom.k * 10) / 10) + "x" : null;
            }
            if (!state.zoom) {
                return null;
            }
            var axis = drawn.timeAxis;
            if (axis) {
                var times = axis.range(0, axis.down ? axis.plot.height : axis.plot.width);
                var pattern = lastPrefs ? lastPrefs.compactTimePattern : undefined;
                var timeZone = lastPrefs ? lastPrefs.timeZone : undefined;
                if (isFinite(times[0]) && isFinite(times[1])) {
                    return "time from " + formatTime(times[0], pattern, timeZone) + " to "
                        + formatTime(times[1], pattern, timeZone);
                }
            }
            return "zoomed to " + Math.round(state.zoom.start * 100) + "%-" + Math.round(state.zoom.end * 100)
                + "% of the " + (drawn.categoriesDown ? "y" : "x") + " axis";
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

        function selectedLabel() {
            if (!selected) {
                return "";
            }
            if (selected.range) {
                return selected.label;
            }
            var items = selected.items;
            return items.length === 1 ? items[0].label : items[0].label + " and " + (items.length - 1) + " more";
        }

        // Tells Stroom which of the chart's controls apply, for the toolbar it shows above the chart (reset zoom,
        // select a time range, clear the selection), with what is selected and whether rows were left out, as the
        // ECharts adapter does.
        function sendControls() {
            if (!window.stroom || typeof window.stroom.controls !== "function") {
                return;
            }
            var report = lastReport || {};
            var notes = [];
            if (drawn && drawn.interval && drawn.intervalChoice === "Auto") {
                notes.push("Interval: " + drawn.interval);
            }
            if (report.truncated) {
                notes.push("First " + report.rows + " rows (the table has more than Max Rows)");
            }
            window.stroom.controls({
                zoom: !!(drawn && drawn.zoomKind),
                zoomed: !!(state.zoom || state.geoZoom),
                range: !!(drawn && drawn.timeAxis),
                rangeMode: rangeMode && !!(drawn && drawn.timeAxis),
                selectable: !!drawn,
                selected: selected ? selectedLabel() : null,
                download: !!drawn,
                hiddenSeries: Object.keys(state.hidden || {}).length > 0,
                intervals: drawn ? drawn.intervals || [] : [],
                interval: drawn ? drawn.intervalChoice : null,
                // Its network layout is worked out before it is drawn, so never moves.
                layout: false,
                layoutPaused: false,
                note: notes.length ? notes.join(" · ") : null
            });
            // The agent sees what the user does: the view goes in the chart's report too.
            reportSelection();
        }

        // True if a link between two things is the same either way round: a chord diagram's, or an undirected
        // network graph's (not a Sankey diagram's flows, nor a directed graph's).
        function linksBothWays() {
            var settings = lastArgs ? lastArgs.settings || {} : {};
            var kind = String(settings.kind || "").toLowerCase();
            return kind === "chord" || (kind === "graph" && String(settings.directed) !== "true");
        }

        // What is selected, as Stroom is told it: a list of selections, which Stroom's filters match any of. A
        // clicked link that is the same either way round is sent both ways.
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
                var link = item.selection.source !== undefined && item.selection.target !== undefined &&
                    item.selection.node === undefined;
                if (link && linksBothWays()) {
                    list.push(reversed(item.selection));
                }
            });
            return list;
        }

        function send() {
            state.selectedKeys = {};
            if (selected && selected.items) {
                selected.items.forEach(function (item) {
                    state.selectedKeys[item.key] = true;
                });
            }
            if (window.stroom && typeof window.stroom.select === "function") {
                window.stroom.select(selections());
            }
            render();
            reportSelection();
        }

        function clearSelection() {
            selected = null;
            send();
        }

        // A control in Stroom's toolbar above the chart was used: resetZoom, rangeMode (turns the range tool on
        // or off) or clearSelection.
        // A control in Stroom's toolbar above the chart was used: resetZoom, rangeMode, clearSelection,
        // showAllSeries, or downloadImage or downloadData (given the file's name), as the ECharts adapter takes them.
        this.command = function (name, value) {
            if (name === "resetZoom") {
                state.zoom = null;
                state.geoZoom = null;
                render();
            } else if (name === "rangeMode") {
                rangeMode = !rangeMode;
                sendControls();
            } else if (name === "clearSelection") {
                if (selected) {
                    clearSelection();
                }
            } else if (name === "zoomIn" || name === "zoomOut") {
                // On the middle of the plot, or of the whole chart for a network graph.
                var middle = drawn && drawn.plot && drawn.zoomKind !== "geo"
                    ? [drawn.plot.left + drawn.plot.width / 2, drawn.plot.top + drawn.plot.height / 2]
                    : [drawn ? drawn.size.width / 2 : 0, drawn ? drawn.size.height / 2 : 0];
                zoomAt(name === "zoomIn" ? 1.5 : 1 / 1.5, middle);
            } else if (name === "interval") {
                state.interval = value && value !== "Auto" ? value : null;
                render();
            } else if (name === "showAllSeries") {
                state.hidden = {};
                render();
            } else if (name === "downloadImage" && drawn) {
                saveImage((value || "chart") + ".png");
            } else if (name === "downloadData" && drawn && lastArgs) {
                saveText((value || "chart") + ".csv", dataCsv(lastArgs.settings || {}, drawn.data),
                    "text/csv;charset=utf-8");
            }
        };

        // Saves the chart as a PNG image, twice its size on screen, on its background: its SVG drawn on a canvas.
        function saveImage(name) {
            var svg = chartElement.querySelector("svg");
            if (!svg) {
                return;
            }
            var width = svg.getAttribute("width") || chartElement.clientWidth;
            var height = svg.getAttribute("height") || chartElement.clientHeight;
            var copy = svg.cloneNode(true);
            copy.setAttribute("xmlns", "http://www.w3.org/2000/svg");
            var source = new window.XMLSerializer().serializeToString(copy);
            var image = new window.Image();
            image.onload = function () {
                var canvas = window.document.createElement("canvas");
                canvas.width = width * 2;
                canvas.height = height * 2;
                var context = canvas.getContext("2d");
                context.fillStyle = backgroundOf(lastStyle);
                context.fillRect(0, 0, canvas.width, canvas.height);
                context.scale(2, 2);
                context.drawImage(image, 0, 0);
                saveFile(name, canvas.toDataURL("image/png"));
            };
            image.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(source);
        }

        // A click on a mark selects it (or clears the selection, if it was all that was selected). Ctrl-click,
        // Cmd-click or Shift-click adds it to what is selected, or takes it away.
        function onMarkClick(mark, event) {
            if (!mark.selection) {
                return;
            }
            var item = {key: selectionKey(mark.selection), label: selectionLabel(mark.selection),
                selection: mark.selection};
            var adding = !!(event && (event.ctrlKey || event.metaKey || event.shiftKey));
            var items = selected && selected.items ? selected.items : [];
            var index = -1;
            items.forEach(function (existing, i) {
                if (existing.key === item.key) {
                    index = i;
                }
            });
            if (adding && !(selected && selected.range)) {
                items = index >= 0 ? items.slice(0, index).concat(items.slice(index + 1)) : items.concat([item]);
            } else if (index >= 0 && items.length === 1) {
                clearSelection();
                return;
            } else {
                items = [item];
            }
            selected = items.length ? {items: items} : null;
            send();
        }

        function markOf(target) {
            var node = target;
            while (node && node !== element) {
                if (node.__stroomMark) {
                    return node.__stroomMark;
                }
                node = node.parentNode;
            }
            return null;
        }

        function sliderOf(target) {
            var node = target;
            while (node && node !== element) {
                if (node.__stroomSlider) {
                    return node.__stroomSlider;
                }
                if (node.__stroomThumbnail) {
                    return {thumbnail: node.__stroomThumbnail};
                }
                node = node.parentNode;
            }
            return null;
        }

        // The tooltip as ECharts shows an axis tooltip: a title, then each series' colour, name and value.
        function showRows(title, rows, at) {
            tooltip.textContent = "";
            var heading = window.document.createElement("div");
            heading.textContent = title;
            tooltip.appendChild(heading);
            rows.forEach(function (row) {
                var line = window.document.createElement("div");
                var dot = window.document.createElement("span");
                dot.style.display = "inline-block";
                dot.style.width = "10px";
                dot.style.height = "10px";
                dot.style.borderRadius = "5px";
                dot.style.marginRight = "6px";
                dot.style.background = SAFE_COLOUR.test(row.colour) ? row.colour : "";
                line.appendChild(dot);
                line.appendChild(window.document.createTextNode(row.name));
                var value = window.document.createElement("span");
                value.style.marginLeft = "16px";
                value.style.float = "right";
                value.style.fontWeight = "bold";
                value.textContent = row.value;
                line.appendChild(value);
                tooltip.appendChild(line);
            });
            tooltip.style.left = Math.min(at[0] + 12, Math.max(0, element.clientWidth - 220)) + "px";
            tooltip.style.top = (at[1] + 12) + "px";
            tooltip.style.display = "block";
        }

        // The axis tooltip's pointer: the category under the pointer shaded, or a line at the x nearest it.
        function showPointer(found) {
            var layer = drawn.pointerLayer;
            layer.selectAll("*").remove();
            if (!found) {
                return;
            }
            var plot = drawn.plot;
            if (found.shadow) {
                layer.append("rect").attr("x", found.shadow.down ? 0 : found.shadow.at)
                    .attr("y", found.shadow.down ? found.shadow.at : 0)
                    .attr("width", found.shadow.down ? plot.width : found.shadow.width)
                    .attr("height", found.shadow.down ? found.shadow.width : plot.height)
                    .attr("fill", lastStyle ? lastStyle.subtleText : "#999").attr("fill-opacity", 0.15);
            } else if (found.line) {
                layer.append("line").attr("x1", found.line.at).attr("x2", found.line.at).attr("y1", 0)
                    .attr("y2", plot.height).attr("stroke", lastStyle ? lastStyle.subtleText : "#999")
                    .attr("stroke-dasharray", "4,3");
            }
        }

        // Moves the network graph's view so a point clicked in its thumbnail is in the middle.
        function centreOn(info, at) {
            var zoom = state.geoZoom || {k: 1, x: 0, y: 0};
            var x = (at[0] - info.x) / info.scale;
            var y = (at[1] - info.y) / info.scale;
            state.geoZoom = {k: zoom.k, x: info.plot.width / 2 - x * zoom.k, y: info.plot.height / 2 - y * zoom.k};
            render();
        }

        // Moves the zoom slider's window (or an end of it) as dragged, by the fraction of the slider moved.
        function slide(part, from, moved) {
            var start = from.start;
            var end = from.end;
            if (part === "start") {
                start = Math.max(0, Math.min(end - 0.01, start + moved));
            } else if (part === "end") {
                end = Math.min(1, Math.max(start + 0.01, end + moved));
            } else {
                var shift = Math.max(-start, Math.min(1 - end, moved));
                start += shift;
                end += shift;
            }
            state.zoom = start <= 0 && end >= 1 ? null : {start: start, end: end};
            render();
        }

        function pointerIn(event) {
            var box = element.getBoundingClientRect ? element.getBoundingClientRect() : {left: 0, top: 0};
            return [event.clientX - box.left, event.clientY - box.top];
        }

        // Dragging pans (or, with the range tool, selects a time range); the wheel zooms.
        var drag = null;

        function onMouseDown(event) {
            if (event.button !== 0 || !drawn) {
                return;
            }
            var at = pointerIn(event);
            drag = {start: at, last: at, moved: false};
            var control = sliderOf(event.target);
            if (control && control.thumbnail) {
                drag.thumbnail = control.thumbnail;
                drag.moved = true;
                centreOn(control.thumbnail, at);
            } else if (control && drawn.slider) {
                var from = state.zoom || {start: 0, end: 1};
                drag.moved = true;
                if (control.part === "track") {
                    // A click on the track moves the window there.
                    var span = from.end - from.start;
                    var fraction = ((drawn.slider.down ? at[1] - drawn.slider.y : at[0] - drawn.slider.x) /
                        drawn.slider.length);
                    var start = Math.max(0, Math.min(1 - span, fraction - span / 2));
                    state.zoom = span >= 1 ? null : {start: start, end: start + span};
                    from = state.zoom || {start: 0, end: 1};
                    render();
                }
                drag.slider = {part: control.part === "track" ? "window" : control.part, from: from};
            } else if (rangeMode && drawn.timeAxis) {
                drag.range = true;
            }
        }

        function onMouseMove(event) {
            var mark = markOf(event.target);
            var at = pointerIn(event);
            var plot = drawn && drawn.plot;
            var inPlot = plot && at[0] >= plot.left && at[0] <= plot.left + plot.width && at[1] >= plot.top &&
                at[1] <= plot.top + plot.height;
            if (drawn && drawn.axisTooltip && !drag) {
                // A chart on axes shows every series at the pointer, as ECharts' axis tooltip does.
                var found = inPlot ? drawn.axisTooltip(at[0] - plot.left, at[1] - plot.top) : null;
                showPointer(found);
                if (found && found.rows.length) {
                    showRows(found.title, found.rows, at);
                } else {
                    tooltip.style.display = "none";
                }
            } else if (mark && mark.tooltip && !drag) {
                tooltip.textContent = mark.tooltip;
                tooltip.style.left = Math.min(at[0] + 12, Math.max(0, element.clientWidth - 200)) + "px";
                tooltip.style.top = (at[1] + 12) + "px";
                tooltip.style.display = "block";
            } else {
                tooltip.style.display = "none";
            }
            if (!drag) {
                return;
            }
            var now = at;
            if (drag.thumbnail) {
                centreOn(drag.thumbnail, now);
                return;
            }
            if (drag.slider) {
                var moved = ((drawn.slider.down ? now[1] - drag.start[1] : now[0] - drag.start[0]) /
                    drawn.slider.length);
                slide(drag.slider.part, drag.slider.from, moved);
                return;
            }
            var dx = now[0] - drag.last[0];
            var dy = now[1] - drag.last[1];
            if (Math.abs(now[0] - drag.start[0]) + Math.abs(now[1] - drag.start[1]) > 3) {
                drag.moved = true;
            }
            drag.last = now;
            if (drag.range) {
                var along = drawn.timeAxis.down ? 1 : 0;
                showRangeDrag(drag.start[along], now[along]);
                return;
            }
            if (drawn.zoomKind === "geo") {
                var zoom = state.geoZoom || {k: 1, x: 0, y: 0};
                state.geoZoom = {k: zoom.k, x: zoom.x + dx, y: zoom.y + dy};
                render();
            } else if (drawn.zoomKind === "axis" && state.zoom && drawn.plot) {
                var span = state.zoom.end - state.zoom.start;
                // A horizontal bar chart's categories run down it.
                var shift = drawn.categoriesDown ? -dy / drawn.plot.height * span : -dx / drawn.plot.width * span;
                shift = Math.max(-state.zoom.start, Math.min(1 - state.zoom.end, shift));
                state.zoom = {start: state.zoom.start + shift, end: state.zoom.end + shift};
                render();
            }
        }

        // Set when a drag ends, so the click that follows it isn't taken as a click on what is under the pointer.
        var dragged = false;

        function onMouseUp(event) {
            if (!drag) {
                return;
            }
            var ended = drag;
            drag = null;
            dragged = ended.moved;
            if (ended.range && ended.moved && drawn && drawn.timeAxis) {
                var down = drawn.timeAxis.down ? 1 : 0;
                selectRange(ended.start[down], pointerIn(event)[down]);
            }
        }

        function onClick(event) {
            if ((drag && drag.moved) || dragged) {
                dragged = false;
                return;
            }
            var mark = markOf(event.target);
            if (mark) {
                onMarkClick(mark, event);
            } else if (selected && !selected.range && !rangeMode) {
                // A click on no item clears the selection; a time range is cleared with its bar's button.
                clearSelection();
            }
        }

        function onWheel(event) {
            if (!drawn || !drawn.zoomKind) {
                return;
            }
            event.preventDefault();
            zoomAt(event.deltaY < 0 ? 1.2 : 1 / 1.2, pointerIn(event));
        }

        // Zooms in (a factor over 1) or out, keeping the point at a position where it is: a network graph's zoom, or
        // the axis's zoom window.
        function zoomAt(factor, at) {
            if (!drawn || !drawn.zoomKind) {
                return;
            }
            if (drawn.zoomKind === "geo") {
                var zoom = state.geoZoom || {k: 1, x: 0, y: 0};
                var k = Math.max(0.2, Math.min(20, zoom.k * factor));
                // Keep the point under the pointer where it is.
                state.geoZoom = {k: k, x: at[0] - (at[0] - zoom.x) * k / zoom.k, y: at[1] - (at[1] - zoom.y) * k / zoom.k};
            } else if (drawn.plot) {
                var current = state.zoom || {start: 0, end: 1};
                var span = current.end - current.start;
                // Along the categories: down a horizontal bar chart.
                var fraction = drawn.categoriesDown ?
                    Math.max(0, Math.min(1, (at[1] - drawn.plot.top) / drawn.plot.height)) :
                    Math.max(0, Math.min(1, (at[0] - drawn.plot.left) / drawn.plot.width));
                var centre = current.start + span * fraction;
                var next = Math.max(0.01, Math.min(1, span / factor));
                var start = Math.max(0, Math.min(1 - next, centre - next * fraction));
                state.zoom = next >= 1 ? null : {start: start, end: start + next};
            }
            render();
        }

        // The time range being dragged out, shown over the plot until the mouse is let go: along the time axis,
        // or across (or down) bars of dates.
        function showRangeDrag(from, to) {
            var axis = drawn.timeAxis;
            axis.group.selectAll(".range-drag").remove();
            drawRangeRect(axis, from - (axis.down ? axis.plot.top : axis.plot.left),
                to - (axis.down ? axis.plot.top : axis.plot.left), "range-drag", 0.2);
        }

        // A band across the plot between two pixels along its range axis, clamped to the plot.
        function drawRangeRect(axis, a, b, className, opacity) {
            var length = axis.down ? axis.plot.height : axis.plot.width;
            var from = Math.max(0, Math.min(length, Math.min(a, b)));
            var to = Math.max(0, Math.min(length, Math.max(a, b)));
            var rect = axis.group.append("rect")
                .attr("x", axis.down ? 0 : from).attr("y", axis.down ? from : 0)
                .attr("width", axis.down ? axis.plot.width : Math.max(1, to - from))
                .attr("height", axis.down ? Math.max(1, to - from) : axis.plot.height)
                .attr("fill", "rgba(120, 140, 180, " + opacity + ")")
                .attr("stroke", lastStyle ? lastStyle.subtleText : "#999").attr("pointer-events", "none");
            if (className) {
                rect.attr("class", className);
            }
        }

        function selectRange(fromAt, toAt) {
            var axis = drawn.timeAxis;
            var origin = axis.down ? axis.plot.top : axis.plot.left;
            var length = axis.down ? axis.plot.height : axis.plot.width;
            var times = axis.range(Math.max(0, Math.min(length, fromAt - origin)),
                Math.max(0, Math.min(length, toAt - origin)));
            var start = times[0];
            var end = times[1];
            if (!isFinite(start) || !isFinite(end) || end <= start) {
                return;
            }
            var pattern = lastPrefs ? lastPrefs.compactTimePattern : undefined;
            var timeZone = lastPrefs ? lastPrefs.timeZone : undefined;
            selected = {range: [start, end],
                selection: {from: new Date(start).toISOString(), to: new Date(end).toISOString()},
                label: formatTime(start, pattern, timeZone) + " → " + formatTime(end, pattern, timeZone)};
            send();
        }

        // The selected time range, drawn over the plot (over the bars it covers, for bars of dates).
        function showRange() {
            if (!selected || !selected.range || !drawn || !drawn.timeAxis) {
                return;
            }
            var pixels = drawn.timeAxis.pixels(selected.range[0], selected.range[1]);
            if (pixels) {
                drawRangeRect(drawn.timeAxis, pixels[0], pixels[1], null, 0.15);
            }
        }

        element.addEventListener("mousedown", onMouseDown);
        element.addEventListener("mousemove", onMouseMove);
        element.addEventListener("mouseup", onMouseUp);
        element.addEventListener("mouseleave", function () {
            tooltip.style.display = "none";
            if (drawn && drawn.pointerLayer) {
                drawn.pointerLayer.selectAll("*").remove();
            }
            drag = null;
        });
        element.addEventListener("click", onClick);
        element.addEventListener("wheel", onWheel, {passive: false});

        state.setLegendPage = function (page) {
            state.legendPage = page;
            render();
        };

        state.toggleSeries = function (name) {
            if (state.hidden[name]) {
                delete state.hidden[name];
            } else {
                state.hidden[name] = true;
            }
            render();
        };

        function render() {
            if (!lastArgs) {
                return;
            }
            var context = lastArgs.context;
            var settings = lastArgs.settings || {};
            var prefs = readPreferences(context, element);
            var style = readThemeStyle(element, prefs.theme);
            lastStyle = style;
            lastPrefs = prefs;
            var report;
            tooltip.style.background = style.tooltipBackground;
            tooltip.style.color = style.text;
            tooltip.style.border = "1px solid " + style.border;
            tooltip.style.fontFamily = style.fontFamily;
            try {
                if (!window.d3) {
                    throw new Error("The D3 library hasn't loaded. Check that the D3 Script doc is installed and is " +
                        "a dependency of this visualisation's script.");
                }
                drawn = drawChart(window.d3, chartElement, settings, lastArgs.data, prefs, style, state);
                report = drawn.report;
                showRange();
                if (report.status === "empty") {
                    showMessage("No data", style);
                } else if (report.status === "nothing-drawn") {
                    showMessage("None of the rows could be drawn: " + Object.keys(report.droppedRows).join(", "),
                        style);
                } else {
                    hideMessage();
                }
            } catch (e) {
                drawn = null;
                if (window.d3) {
                    window.d3.select(chartElement).selectAll("svg").remove();
                }
                report = newReport(String(settings.kind || ""), window.d3);
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
            lastArgs = {context: context, settings: settings, data: data};
            render();
        };

        this.resize = function () {
            render();
        };

        this.start = function () {
            // Nothing to do: each setData redraws the whole chart.
        };

        this.end = function () {
        };

        this.teardown = function () {
            if (window.d3) {
                window.d3.select(chartElement).selectAll("svg").remove();
            }
            drawn = null;
        };
    };

    // Exposed for testing.
    visualisations.D3Generic.internals = {
        drawChart: drawChart,
        compat: compat,
        lttb: lttb,
        forceLayout: forceLayout,
        selectionLabel: selectionLabel,
        reversed: reversed,
        Fields: Fields,
        readPreferences: readPreferences,
        version: ADAPTER_VERSION
    };
})();
