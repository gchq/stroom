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
        "gauge"];

    // A gauge's bands: below the first band's start, then the first band, then above the second.
    var GAUGE_BAND_COLOURS = ["#3ba272", "#fac858", "#ee6666"];

    // Stroom puts its own refresh button over the top-right corner of a dashboard's visualisation, so nothing
    // that can be clicked or read is put within this many pixels of the right edge at the top.
    var STROOM_BUTTON_SPACE = 36;

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
            fontFamily: cssVar("--regular-font-family", "Roboto, sans-serif")
        };
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

    // Links between nodes from the rows: their values added up (1 a row if there is no value column), links from a
    // node to itself left out, and only the maxNodes nodes with the most flowing through them kept.
    function linkTotals(rows, fields, settings, prefs, report, valueId) {
        var hasValue = fields.has(valueId);
        var links = {};
        var linkKeys = [];
        var nodeTotals = {};
        var missing = 0;
        var selfLinks = 0;
        var badValue = 0;
        rows.forEach(function (row) {
            var sourceValue = fields.value(row, "source");
            var targetValue = fields.value(row, "target");
            if (isBlank(sourceValue) || isBlank(targetValue)) {
                missing++;
                return;
            }
            var source = fields.label(row, "source", prefs);
            var target = fields.label(row, "target", prefs);
            if (source === target) {
                selfLinks++;
                return;
            }
            var value = hasValue ? toNumber(fields.value(row, valueId)) : 1;
            if (isNaN(value) || value < 0) {
                badValue++;
                return;
            }
            var key = source + "\u0000" + target;
            if (!links[key]) {
                links[key] = {source: source, target: target, value: 0};
                linkKeys.push(key);
            }
            links[key].value += value;
            nodeTotals[source] = (nodeTotals[source] || 0) + value;
            nodeTotals[target] = (nodeTotals[target] || 0) + value;
        });
        addDropped(report, "source or target empty", missing);
        addDropped(report, "source and target the same", selfLinks);
        addDropped(report, (fields.column(valueId) || valueId) + " not a number or negative", badValue);

        var nodeNames = Object.keys(nodeTotals).sort(function (a, b) {
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
            links: linkKeys.map(function (key) {
                return links[key];
            }).filter(function (link) {
                return kept[link.source] && kept[link.target];
            })
        };
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

    var FONT_SIZE = 12;
    var SMALL_FONT_SIZE = 11;

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

    // A value axis along the bottom (horizontal) or left (vertical) of the plot area, with grid lines across it.
    // Along the bottom, as many ticks as their labels have room for, and no label that would overlap the last.
    function drawValueAxis(g, scale, horizontal, plot, format, style, name) {
        var count = horizontal ? Math.max(2, Math.floor(plot.width / 90)) : Math.max(2, Math.floor(plot.height / 45));
        var ticks = scale.ticks(count);
        if (horizontal) {
            var widest = ticks.reduce(function (most, tick) {
                return Math.max(most, textWidth(format(tick), SMALL_FONT_SIZE));
            }, 0);
            var fits = Math.max(2, Math.floor(plot.width / (widest + 16)));
            if (fits < ticks.length) {
                ticks = scale.ticks(fits);
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
                axis.append("line").attr("x1", at).attr("x2", at).attr("y1", 0).attr("y2", plot.height)
                    .attr("stroke", style.border).attr("stroke-dasharray", "2,3");
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
                axis.append("line").attr("x1", 0).attr("x2", plot.width).attr("y1", at).attr("y2", at)
                    .attr("stroke", style.border).attr("stroke-dasharray", "2,3");
                svgText(axis, format(tick), -6, at, style,
                    {anchor: "end", baseline: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            }
        });
        axis.append("line")
            .attr("x1", 0).attr("y1", horizontal ? plot.height : 0)
            .attr("x2", horizontal ? plot.width : 0).attr("y2", plot.height)
            .attr("stroke", style.subtleText);
        if (name) {
            if (horizontal) {
                svgText(axis, name, plot.width / 2, plot.height + 32, style,
                    {anchor: "middle", colour: style.subtleText});
            } else {
                svgText(axis, name, -plot.left + 12, plot.height / 2, style,
                    {anchor: "middle", colour: style.subtleText, rotate: -90});
            }
        }
    }

    // A category axis: a label under (or beside) each band, thinned out when they would overlap.
    function drawCategoryAxis(g, categories, band, horizontal, plot, style, name) {
        var axis = g.append("g").attr("class", "axis");
        var step = band.step;
        var widest = categories.reduce(function (most, category) {
            return Math.max(most, textWidth(category, SMALL_FONT_SIZE));
        }, 0);
        var every = horizontal ? Math.max(1, Math.ceil((Math.min(widest, 120) + 8) / Math.max(1, step))) :
            Math.max(1, Math.ceil(16 / Math.max(1, step)));
        categories.forEach(function (category, i) {
            if (i % every !== 0) {
                return;
            }
            var at = band.position(i) + band.width / 2;
            if (horizontal) {
                svgText(axis, shorten(category, 20), at, plot.height + 16, style,
                    {anchor: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            } else {
                svgText(axis, shorten(category, 20), -6, at, style,
                    {anchor: "end", baseline: "middle", colour: style.subtleText, size: SMALL_FONT_SIZE});
            }
        });
        axis.append("line")
            .attr("x1", 0).attr("y1", horizontal ? plot.height : 0)
            .attr("x2", horizontal ? plot.width : 0).attr("y2", plot.height)
            .attr("stroke", style.subtleText);
        if (name) {
            if (horizontal) {
                svgText(axis, name, plot.width / 2, plot.height + 32, style,
                    {anchor: "middle", colour: style.subtleText});
            } else {
                svgText(axis, name, -plot.left + 12, plot.height / 2, style,
                    {anchor: "middle", colour: style.subtleText, rotate: -90});
            }
        }
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

    // The ticks of a value axis's labels, as the Units setting shows them, and how wide the widest is.
    function labelWidth(scale, format) {
        return scale.ticks(5).reduce(function (most, tick) {
            return Math.max(most, textWidth(format(tick), SMALL_FONT_SIZE));
        }, 0);
    }

    // A legend of series (or categories) and their colours: along the top, bottom or right. Clicking an entry
    // hides or shows it.
    function drawLegend(svg, names, colours, position, size, style, hidden, onToggle) {
        if (position === "none" || names.length < 2) {
            return 0;
        }
        var legend = svg.append("g").attr("class", "legend");
        var x = 8;
        var y = position === "bottom" ? size.height - 14 : 12;
        var rightX = size.width - 8 - STROOM_BUTTON_SPACE;
        names.forEach(function (name, i) {
            var label = shorten(name, 24);
            var width = 14 + textWidth(label, SMALL_FONT_SIZE) + 12;
            if (position === "right") {
                x = rightX - 130;
                y = 30 + i * 18;
            } else if (x + width > size.width - 8 - STROOM_BUTTON_SPACE && x > 8) {
                x = 8;
                y += position === "bottom" ? -18 : 18;
            }
            var item = legend.append("g").attr("class", "legend-item").attr("transform", "translate(" + x + "," + y +
                ")").style("cursor", "pointer");
            var off = hidden && hidden[name];
            item.append("rect").attr("x", 0).attr("y", -9).attr("width", 10).attr("height", 10).attr("rx", 2)
                .attr("fill", off ? style.border : colours[i % colours.length]);
            svgText(item, label, 14, 0, style, {colour: off ? style.subtleText : style.text, size: SMALL_FONT_SIZE});
            item.node().addEventListener("click", function (event) {
                event.stopPropagation();
                onToggle(name);
            });
            if (position !== "right") {
                x += width;
            }
        });
        if (position === "right") {
            return 140;
        }
        return position === "bottom" ? size.height - y + 8 : y + 10;
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

    // A line across the plot at a value (a threshold or an average), with its label.
    function drawValueLine(g, scale, horizontalValues, plot, value, label, colour, style, dashed) {
        var at = scale(value);
        if (!isFinite(at) || at < -1 || at > (horizontalValues ? plot.width : plot.height) + 1) {
            return;
        }
        var line = g.append("line").attr("stroke", colour).attr("stroke-width", 1.5);
        if (dashed) {
            line.attr("stroke-dasharray", "6,4");
        }
        if (horizontalValues) {
            line.attr("x1", at).attr("x2", at).attr("y1", 0).attr("y2", plot.height);
            svgText(g, label, at + 4, 12, style, {colour: colour, size: SMALL_FONT_SIZE});
        } else {
            line.attr("x1", 0).attr("x2", plot.width).attr("y1", at).attr("y2", at);
            svgText(g, label, plot.width - 4, at - 4, style, {colour: colour, anchor: "end", size: SMALL_FONT_SIZE});
        }
    }

    // ------------------------------------------------------------------------------------------
    // Charts on axes: bar, line, scatter, histogram
    // ------------------------------------------------------------------------------------------

    // The plot area: the space left for the chart once the legend and the axes' labels have theirs.
    function plotArea(ctx, legend, left, bottomExtra) {
        var top = Math.max(16, 10 + (legend.position === "top" ? legend.space : 0));
        var bottom = 26 + (bottomExtra || 0) + (legend.position === "bottom" ? legend.space : 0);
        var right = 16 + (legend.position === "right" ? legend.space : 0);
        return {
            left: left,
            top: top,
            width: Math.max(10, ctx.size.width - left - right),
            height: Math.max(10, ctx.size.height - top - bottom)
        };
    }

    // The legend's position and the space it takes, once drawn.
    function legendOf(ctx, names, colours) {
        var position = textSetting(ctx.settings, "legend") || "top";
        var space = drawLegend(ctx.svg, names, colours, position, ctx.size, ctx.style, ctx.hidden, ctx.toggleSeries);
        return {position: space > 0 ? position : "none", space: space};
    }

    // A value scale for values (linear, or log when the Log Scale setting is on and the values allow), its domain
    // padded and including zero where the chart's bars or areas start from it.
    function valueScaleOf(ctx, values, range, fromZero) {
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
        if (log) {
            scale = ctx.C.log().domain([min > 0 ? min : 1, max > min ? max : min * 10 || 10]).range(range).nice();
        } else {
            if (fromZero) {
                min = Math.min(0, min);
                max = Math.max(0, max);
            }
            if (!(max > min)) {
                max = min + 1;
            }
            scale = ctx.C.linear().domain([min, max]).range(range).nice();
        }
        scale.isLog = log;
        return scale;
    }

    // The threshold and average lines a chart's settings ask for, across the plot.
    function drawValueLines(ctx, g, scale, valuesHorizontal, plot, averages) {
        var threshold = decimalSetting(ctx.settings, "threshold", NaN);
        if (isFinite(threshold)) {
            drawValueLine(g, scale, valuesHorizontal, plot, threshold,
                textSetting(ctx.settings, "thresholdLabel") || ctx.format(threshold), "#ee6666", ctx.style, false);
        }
        (averages || []).forEach(function (average) {
            if (isFinite(average.value)) {
                drawValueLine(g, scale, valuesHorizontal, plot, average.value, "avg " + ctx.format(average.value),
                    average.colour, ctx.style, true);
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
        var marks = g.append("g").attr("clip-path", "url(#" + clipId + ")");
        return {axes: g, marks: marks};
    }

    function drawBar(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["category", "value"]);
        var matrix = categoryMatrix(ctx.rows, fields, settings, ctx.prefs, ctx.report, "category", "value");
        var horizontal = boolSetting(settings, "horizontal", false);
        var stack = boolSetting(settings, "stack", false);
        var showLabels = boolSetting(settings, "showLabels", false);
        var colours = paletteOf(ctx.options, matrix.series.length, ctx.style);
        var shown = matrix.series.filter(function (series) {
            return !ctx.hidden[series.name];
        });
        var legend = legendOf(ctx, matrix.series.map(function (series) {
            return series.name;
        }), colours);

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
        var probe = valueScaleOf(ctx, values, [0, 1], true);
        var left = horizontal ? Math.min(140, matrix.categories.reduce(function (most, category) {
            return Math.max(most, textWidth(shorten(category, 20), SMALL_FONT_SIZE));
        }, 0) + 14) : labelWidth(probe, ctx.format) + 14;
        var plot = plotArea(ctx, legend, Math.max(36, left + (horizontal || textSetting(settings, "yAxisName") ?
            16 : 0)), !horizontal || textSetting(settings, "xAxisName") ? 16 : 0);
        var scale = valueScaleOf(ctx, values, horizontal ? [0, plot.width] : [plot.height, 0], true);
        var groups = plotGroup(ctx, plot);
        var band = bands(matrix.categories.length, horizontal ? plot.height : plot.width, 0.25, ctx.zoom);
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
                var width = stack ? band.width : band.width / shown.length;
                var at = band.position(i) + (stack ? 0 : s * width);
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
                if (showLabels) {
                    if (horizontal) {
                        svgText(groups.marks, ctx.format(value), Math.max(a, b) + 4, at + width / 2, ctx.style,
                            {baseline: "middle", size: SMALL_FONT_SIZE});
                    } else {
                        svgText(groups.marks, ctx.format(value), at + width / 2, Math.min(a, b) - 4, ctx.style,
                            {anchor: "middle", size: SMALL_FONT_SIZE});
                    }
                }
            });
        });
        drawCategoryAxis(groups.axes, matrix.categories, band, !horizontal, plot, ctx.style,
            horizontal ? textSetting(settings, "yAxisName") || fields.column("category") :
                textSetting(settings, "xAxisName") || fields.column("category"));
        drawValueAxis(groups.axes, scale, horizontal, plot, ctx.format, ctx.style,
            horizontal ? textSetting(settings, "xAxisName") : textSetting(settings, "yAxisName"));
        drawValueLines(ctx, groups.axes, scale, horizontal, plot, boolSetting(settings, "averageLine", false) ?
            shown.map(function (series) {
                return {value: mean(series.data), colour: colours[matrix.series.indexOf(series) % colours.length]};
            }) : []);
        ctx.report.series = shown.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
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
        ctx.rows.forEach(function (row) {
            var x = toNumber(fields.value(row, xId));
            var y = toNumber(fields.value(row, yId));
            if (isNaN(x) || isNaN(y)) {
                bad++;
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
        return {names: seriesNames, bySeries: bySeries, bad: bad};
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
        addDropped(ctx.report, "x not a number or date", data.bad);
        var colours = paletteOf(ctx.options, data.names.length, ctx.style);
        var shown = data.names.filter(function (name) {
            return !ctx.hidden[name];
        });
        var legend = legendOf(ctx, data.names, colours);
        var stack = boolSetting(settings, "stack", false);
        var area = boolSetting(settings, "area", false);
        var smooth = boolSetting(settings, "smooth", false);

        // Stacked: each series' y on top of those before it at the same x.
        var tops = {};
        var lines = shown.map(function (name) {
            return {
                name: name,
                points: data.bySeries[name].map(function (point) {
                    var base = stack ? tops[point[0]] || 0 : 0;
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
        var probe = valueScaleOf(ctx, ys, [0, 1], area || stack);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, ctx.format) + 14 +
            (textSetting(settings, "yAxisName") ? 16 : 0)), 16);
        var extent = xs.length ? [Math.min.apply(null, xs), Math.max.apply(null, xs)] : [0, 1];
        var x = xScaleOf(ctx, extent, time, plot);
        var y = valueScaleOf(ctx, ys, [plot.height, 0], area || stack);
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
            // Points to click, when there are few enough to tell apart.
            if (sampled.length <= 400) {
                sampled.forEach(function (point) {
                    var pairs = [["x", point[3]]];
                    if (ctx.fields.has("series")) {
                        pairs.push(["series", fields.rawOf("series", line.name)]);
                    }
                    var selection = fields.selection(pairs);
                    var selected = ctx.isSelected(selection);
                    var dot = groups.marks.append("circle").attr("cx", xOf(point[0])).attr("cy", y(point[1]))
                        .attr("r", selected ? 5 : 3).attr("fill", colour);
                    outline(dot, ctx, selected);
                    ctx.mark(dot, selection, (time ? timeFormat(ctx)(point[0]) : ctx.format(point[0])) +
                        (ctx.fields.has("series") ? " · " + line.name : "") + ": " + ctx.format(point[4]));
                    if (showLabels) {
                        svgText(groups.marks, ctx.format(point[4]), xOf(point[0]), y(point[1]) - 6, ctx.style,
                            {anchor: "middle", size: SMALL_FONT_SIZE});
                    }
                });
            }
        });
        drawValueAxis(groups.axes, x, true, plot, time ? timeFormat(ctx) : ctx.format, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("x"));
        drawValueAxis(groups.axes, y, false, plot, ctx.format, ctx.style, textSetting(settings, "yAxisName"));
        drawValueLines(ctx, groups.axes, y, false, plot, boolSetting(settings, "averageLine", false) ?
            lines.map(function (line) {
                return {value: mean(line.points.map(function (point) {
                    return point[4];
                })), colour: colours[data.names.indexOf(line.name) % colours.length]};
            }) : []);
        ctx.report.series = lines.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        if (time) {
            ctx.timeAxis = {scale: x, plot: plot, group: groups.axes};
        }
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
        }), colours);
        var stack = boolSetting(settings, "stack", false);
        var area = boolSetting(settings, "area", false);
        var smooth = boolSetting(settings, "smooth", false);
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
        var probe = valueScaleOf(ctx, ys, [0, 1], area || stack);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, ctx.format) + 14 +
            (textSetting(settings, "yAxisName") ? 16 : 0)), 16);
        var y = valueScaleOf(ctx, ys, [plot.height, 0], area || stack);
        var groups = plotGroup(ctx, plot);
        var band = bands(matrix.categories.length, plot.width, 0, ctx.zoom);
        var xOf = function (i) {
            return band.position(i) + band.width / 2;
        };
        var points = 0;
        lines.forEach(function (line) {
            var colour = colours[matrix.series.indexOf(line.series) % colours.length];
            var drawn = line.points.filter(function (point) {
                return point && (!y.isLog || point[1] > 0);
            });
            points += drawn.length;
            if (area || stack) {
                groups.marks.append("path").attr("d", ctx.C.area(smooth)
                    .x(function (point) {
                        return xOf(point[0]);
                    })
                    .y0(function (point) {
                        return y(y.isLog ? y.domain()[0] : point[2]);
                    })
                    .y1(function (point) {
                        return y(point[1]);
                    })(drawn)).attr("fill", colour).attr("fill-opacity", 0.25);
            }
            groups.marks.append("path").attr("d", ctx.C.line(smooth)
                .x(function (point) {
                    return xOf(point[0]);
                })
                .y(function (point) {
                    return y(point[1]);
                })(drawn)).attr("fill", "none").attr("stroke", colour).attr("stroke-width", 2);
            drawn.forEach(function (point) {
                var category = matrix.categories[point[0]];
                var pairs = [["x", fields.rawOf("x", category)]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", line.series.name)]);
                }
                var selection = fields.selection(pairs);
                var selected = ctx.isSelected(selection);
                var dot = groups.marks.append("circle").attr("cx", xOf(point[0])).attr("cy", y(point[1]))
                    .attr("r", selected ? 5 : 3).attr("fill", colour);
                outline(dot, ctx, selected);
                ctx.mark(dot, selection, category + (fields.has("series") ? " · " + line.series.name : "") +
                    ": " + ctx.format(point[3]));
            });
        });
        drawCategoryAxis(groups.axes, matrix.categories, band, true, plot, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("x"));
        drawValueAxis(groups.axes, y, false, plot, ctx.format, ctx.style, textSetting(settings, "yAxisName"));
        drawValueLines(ctx, groups.axes, y, false, plot, boolSetting(settings, "averageLine", false) ?
            lines.map(function (line) {
                return {value: mean(line.series.data), colour: colours[matrix.series.indexOf(line.series) %
                    colours.length]};
            }) : []);
        ctx.report.series = lines.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
    }

    // A fraction from 0 to 1 that is the same for the same index, to spread overlapping points (Jitter).
    function jitterOf(index) {
        var value = Math.sin(index * 12.9898 + 78.233) * 43758.5453;
        return value - Math.floor(value) - 0.5;
    }

    function drawScatter(ctx) {
        var fields = ctx.fields;
        var settings = ctx.settings;
        requireFields(fields, ["x", "y"]);
        var time = axisFor(fields.type("x")) === "time";
        var hasSize = fields.has("size");
        var data = seriesPoints(ctx, "x", "y", hasSize ? function (row) {
            var size = toNumber(fields.value(row, "size"));
            return isNaN(size) || size < 0 ? 0 : size;
        } : null);
        addDropped(ctx.report, "x or y not a number or date", data.bad);
        var colours = paletteOf(ctx.options, data.names.length, ctx.style);
        var legend = legendOf(ctx, data.names, colours);
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
        var probe = valueScaleOf(ctx, ys, [0, 1], false);
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, ctx.format) + 30), 16);
        var extent = xs.length ? [Math.min.apply(null, xs), Math.max.apply(null, xs)] : [0, 1];
        var x = xScaleOf(ctx, extent, time, plot);
        var y = valueScaleOf(ctx, ys, [plot.height, 0], false);
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
                var cx = x(time ? new Date(point[0]) : point[0]) + (jitter ? jitterOf(index) * plot.width * 0.02 : 0);
                var cy = y(point[1]) + (jitter ? jitterOf(index + 7919) * plot.height * 0.02 : 0);
                var radius = hasSize ? scaleSqrt(point[4], minSize, maxSize, 3, 18) : 4;
                var pairs = [["x", point[2]], ["y", point[3]]];
                if (fields.has("series")) {
                    pairs.push(["series", fields.rawOf("series", name)]);
                }
                var selection = fields.selection(pairs);
                var dot = groups.marks.append("circle").attr("cx", cx).attr("cy", cy).attr("r", radius)
                    .attr("fill", colour).attr("fill-opacity", 0.75);
                outline(dot, ctx, ctx.isSelected(selection));
                ctx.mark(dot, selection, (fields.has("series") ? name + ": " : "") +
                    (time ? timeFormat(ctx)(point[0]) : ctx.format(point[0])) + ", " + ctx.format(point[1]));
            });
        });
        drawValueAxis(groups.axes, x, true, plot, time ? timeFormat(ctx) : ctx.format, ctx.style,
            textSetting(settings, "xAxisName") || fields.column("x"));
        drawValueAxis(groups.axes, y, false, plot, ctx.format, ctx.style,
            textSetting(settings, "yAxisName") || fields.column("y"));
        drawValueLines(ctx, groups.axes, y, false, plot, []);
        ctx.report.series = shown.length;
        ctx.report.points = points;
        ctx.zoomKind = "axis";
        ctx.plot = plot;
        if (time) {
            ctx.timeAxis = {scale: x, plot: plot, group: groups.axes};
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
        if (!(max > min)) {
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
        var legend = legendOf(ctx, names, colours);
        var shown = names.filter(function (name) {
            return !ctx.hidden[name];
        });
        var stack = boolSetting(settings, "stack", false);
        var heights = labels.map(function (label, b) {
            return shown.reduce(function (sum, name) {
                return stack ? sum + counts[name][b] : Math.max(sum, counts[name][b]);
            }, 0);
        });
        var probe = ctx.C.linear().domain([0, Math.max(1, Math.max.apply(null, heights.concat([1])))]).nice();
        var plot = plotArea(ctx, legend, Math.max(36, labelWidth(probe, formatNumber) + 30), 16);
        var y = ctx.C.linear().domain(probe.domain()).range([plot.height, 0]).nice();
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
                    svgText(groups.marks, String(count), at + barWidth / 2, y(from + count) - 4, ctx.style,
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
        var legend = legendOf(ctx, slices.map(function (slice) {
            return slice.name;
        }), colours);
        var plot = plotArea(ctx, legend, 8, -18);
        var shown = slices.filter(function (slice) {
            return !ctx.hidden[slice.name];
        });
        var total = shown.reduce(function (sum, slice) {
            return sum + slice.value;
        }, 0);
        var radius = Math.max(10, Math.min(plot.width, plot.height) / 2 * 0.72);
        var doughnut = boolSetting(settings, "doughnut", false);
        var showLabels = boolSetting(settings, "showLabels", true);
        var g = ctx.svg.append("g").attr("transform", "translate(" + (plot.left + plot.width / 2) + "," +
            (plot.top + plot.height / 2) + ")");
        ctx.geo = g;
        ctx.geoOrigin = [plot.left + plot.width / 2, plot.top + plot.height / 2];
        var arc = ctx.C.arc().innerRadius(doughnut ? radius * 0.55 : 0).outerRadius(radius);
        var angle = 0;
        shown.forEach(function (slice) {
            var sweep = total > 0 ? slice.value / total * Math.PI * 2 : 0;
            var part = {startAngle: angle, endAngle: angle + sweep};
            angle += sweep;
            var selection = slice.combined ? null : fields.selection([["name", fields.rawOf("name", slice.name)]]);
            var path = g.append("path").attr("d", arc(part))
                .attr("fill", colours[slices.indexOf(slice) % colours.length])
                .attr("stroke", ctx.style.dark ? "#1b1e24" : "#fff").attr("stroke-width", 1);
            outline(path, ctx, selection && ctx.isSelected(selection));
            ctx.mark(path, selection, slice.name + ": " + ctx.format(slice.value) + " (" +
                (total > 0 ? Math.round(slice.value / total * 1000) / 10 : 0) + "%)");
            if (showLabels && sweep > 0.12) {
                var middle = (part.startAngle + part.endAngle) / 2;
                var at = radius + 14;
                svgText(g, shorten(slice.name, 18), Math.sin(middle) * at, -Math.cos(middle) * at, ctx.style,
                    {anchor: Math.sin(middle) >= 0 ? "start" : "end", baseline: "middle", size: SMALL_FONT_SIZE});
            }
        });
        ctx.report.series = 1;
        ctx.report.points = shown.length;
        ctx.zoomKind = "geo";
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
        var columns = Math.ceil(Math.sqrt(names.length || 1));
        var gridRows = Math.ceil((names.length || 1) / columns);
        var cellWidth = ctx.size.width / columns;
        var cellHeight = (ctx.size.height - 10) / gridRows;
        var radius = Math.max(16, Math.min(cellWidth, cellHeight * 1.3) * 0.38);
        var start = -Math.PI * 0.75;
        var sweep = Math.PI * 1.5;
        var angleOf = function (value) {
            return start + sweep * Math.max(0, Math.min(1, (value - min) / (max - min)));
        };
        var arc = ctx.C.arc().innerRadius(radius * 0.86).outerRadius(radius);
        var g = ctx.svg.append("g");
        ctx.geo = g;
        ctx.geoOrigin = [0, 0];
        names.forEach(function (name, i) {
            var cx = (i % columns + 0.5) * cellWidth;
            var cy = 10 + (Math.floor(i / columns) + 0.55) * cellHeight;
            var dial = g.append("g").attr("transform", "translate(" + cx + "," + cy + ")");
            var from = 0;
            bandStops.forEach(function (stop) {
                dial.append("path").attr("d", arc({startAngle: start + sweep * from, endAngle: start + sweep * stop[0]}))
                    .attr("fill", stop[1]);
                from = stop[0];
            });
            [min, max].forEach(function (value, end) {
                var at = start + sweep * end;
                svgText(dial, ctx.format(value), Math.sin(at) * (radius + 4), -Math.cos(at) * (radius + 4) + 12,
                    ctx.style, {anchor: end ? "start" : "end", colour: ctx.style.subtleText, size: SMALL_FONT_SIZE});
            });
            var angle = angleOf(sums[name]);
            var selection = named ? fields.selection([["name", fields.rawOf("name", name)]]) : null;
            var selected = selection && ctx.isSelected(selection);
            var needle = dial.append("line").attr("x1", 0).attr("y1", 0)
                .attr("x2", Math.sin(angle) * radius * 0.8).attr("y2", -Math.cos(angle) * radius * 0.8)
                .attr("stroke", ctx.style.text).attr("stroke-width", selected ? 5 : 3)
                .attr("stroke-linecap", "round");
            var hub = dial.append("circle").attr("r", radius * 0.08).attr("fill", ctx.style.text);
            outline(hub, ctx, selected);
            svgText(dial, ctx.format(sums[name]), 0, radius * 0.45, ctx.style,
                {anchor: "middle", size: 16, weight: "bold"});
            svgText(dial, shorten(name, 24), 0, radius * 0.45 + 20, ctx.style,
                {anchor: "middle", colour: ctx.style.subtleText});
            // The whole dial can be clicked.
            var target = dial.append("circle").attr("r", radius).attr("fill", "transparent");
            ctx.mark(target, selection, name + ": " + ctx.format(sums[name]));
            ctx.mark(needle, selection, name + ": " + ctx.format(sums[name]));
        });
        ctx.report.series = names.length;
        ctx.report.points = names.length;
        ctx.zoomKind = null;
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
        var left = Math.min(140, ys.reduce(function (most, y) {
            return Math.max(most, textWidth(shorten(y, 20), SMALL_FONT_SIZE));
        }, 0) + 14);
        var plot = plotArea(ctx, {position: "none", space: 0}, Math.max(36, left), 30);
        var groups = plotGroup(ctx, plot);
        var xBand = bands(xs.length, plot.width, 0.06, null);
        var yBand = bands(ys.length, plot.height, 0.06, null);
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
                    .attr("fill", colourAt(colours, max > min ? (value - min) / (max - min) : 0.5));
                outline(rect, ctx, ctx.isSelected(selection));
                ctx.mark(rect, selection, x + " · " + y + ": " + ctx.format(value));
                if (showLabels && xBand.width > 24 && yBand.width > 12) {
                    svgText(groups.marks, ctx.format(value), xBand.position(i) + xBand.width / 2,
                        yBand.position(j) + yBand.width / 2, ctx.style,
                        {anchor: "middle", baseline: "middle", size: SMALL_FONT_SIZE});
                }
            });
        });
        drawCategoryAxis(groups.axes, xs, xBand, true, plot, ctx.style, null);
        drawCategoryAxis(groups.axes, ys, yBand, false, plot, ctx.style, null);
        drawScaleBar(ctx.svg, colours, min, max, ctx.format, ctx.size, ctx.style);
        ctx.geo = groups.marks;
        ctx.geoOrigin = [0, 0];
        ctx.report.series = 1;
        ctx.report.points = cells;
        ctx.zoomKind = "geo";
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
        // A single year's data shows only the weeks it covers; several years show each whole year.
        var whole = years.length > 1;
        var dayMs = 86400000;
        var utc = function (day) {
            return Date.UTC(parseInt(day.substring(0, 4), 10), parseInt(day.substring(5, 7), 10) - 1,
                parseInt(day.substring(8, 10), 10));
        };
        var blocks = years.map(function (year) {
            var first = whole || !days.length ? utc(year + "-01-01") : utc(days[0]);
            var last = whole || !days.length ? utc(year + "-12-31") : utc(days[days.length - 1]);
            // Weeks start on Monday.
            var firstMonday = first - ((new Date(first).getUTCDay() + 6) % 7) * dayMs;
            return {year: year, first: first, last: last, firstMonday: firstMonday,
                weeks: Math.floor((last - firstMonday) / dayMs / 7) + 1};
        });
        var maxWeeks = blocks.reduce(function (most, block) {
            return Math.max(most, block.weeks);
        }, 1);
        var left = 40;
        var available = ctx.size.width - left - 16;
        var cell = Math.max(4, Math.min(22, available / maxWeeks, (ctx.size.height - 50) /
            Math.max(1, blocks.length) / 9));
        var g = ctx.svg.append("g").attr("transform", "translate(" + left + ",22)");
        var selectedDays = 0;
        blocks.forEach(function (block, b) {
            var top = b * cell * 9;
            var calendar = g.append("g").attr("transform", "translate(0," + top + ")");
            svgText(calendar, block.year, -6, cell * 3.5, ctx.style, {anchor: "end", colour: ctx.style.subtleText,
                size: SMALL_FONT_SIZE, rotate: -90});
            ["Mon", "Wed", "Fri"].forEach(function (label, i) {
                if (cell >= 9) {
                    svgText(calendar, label, -16, (i * 2 + 0.5) * cell + 4, ctx.style,
                        {anchor: "end", colour: ctx.style.subtleText, size: 9});
                }
            });
            var lastMonth = -1;
            for (var t = block.first; t <= block.last; t += dayMs) {
                var date = new Date(t);
                var week = Math.floor((t - block.firstMonday) / dayMs / 7);
                var weekday = (date.getUTCDay() + 6) % 7;
                var key = date.toISOString().substring(0, 10);
                if (date.getUTCMonth() !== lastMonth) {
                    lastMonth = date.getUTCMonth();
                    svgText(calendar, MONTHS[lastMonth].substring(0, 3), week * cell, -4, ctx.style,
                        {colour: ctx.style.subtleText, size: 9});
                }
                var value = sums[key];
                var rect = calendar.append("rect").attr("x", week * cell).attr("y", weekday * cell)
                    .attr("width", cell - 1).attr("height", cell - 1).attr("rx", 1)
                    .attr("fill", value === undefined ? ctx.style.border :
                        colourAt(colours, max > min ? (value - min) / (max - min) : 0.5));
                if (value !== undefined) {
                    selectedDays++;
                    var selection = fields.selection([["date", raws[key]]]);
                    outline(rect, ctx, ctx.isSelected(selection));
                    ctx.mark(rect, selection, key + ": " + ctx.format(value));
                }
            }
        });
        drawScaleBar(ctx.svg, colours, min, max, ctx.format, ctx.size, ctx.style);
        ctx.geo = g;
        ctx.geoOrigin = [left, 22];
        ctx.report.series = 1;
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

        ctx.rows.forEach(function (row) {
            if (isBlank(fields.value(row, "source")) || isBlank(fields.value(row, "target"))) {
                missing++;
                return;
            }
            var source = fields.label(row, "source", ctx.prefs);
            var target = fields.label(row, "target", ctx.prefs);
            if (source === target) {
                selfLinks++;
                return;
            }
            var weight = hasWeight ? toNumber(fields.value(row, "weight")) : 1;
            if (isNaN(weight) || weight < 0) {
                badWeight++;
                return;
            }
            var from = node(source, groupOf(row, "sourceGroup"));
            var to = node(target, groupOf(row, "targetGroup"));
            from.neighbours[target] = true;
            to.neighbours[source] = true;
            from.weight += weight;
            to.weight += weight;
            var key = directed || source < target ? source + "\u0000" + target : target + "\u0000" + source;
            if (links[key]) {
                links[key].value += weight;
            } else {
                links[key] = {source: source, target: target, value: weight};
                linkKeys.push(key);
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

    // A network's node as a click selects it: by its value from either end's column.
    function nodeSelection(fields, name) {
        var raw = fields.rawOf("source", name);
        return fields.selection([["node", raw === name ? fields.rawOf("target", name) : raw]]);
    }

    function linkSelection(fields, link) {
        return fields.selection([["source", fields.rawOf("source", link.source)],
            ["target", fields.rawOf("target", link.target)]]);
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
        var legend = legendOf(ctx, byGroup && hasGroups ? groups : [], colours);
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
        var radiusOf = function (node) {
            return scaleSqrt(node.degree, minDegree, maxDegree, 5, 18);
        };
        var g = ctx.svg.append("g").attr("transform", "translate(" + plot.left + "," + plot.top + ")");
        var content = g.append("g");
        ctx.geo = content;
        ctx.geoOrigin = [plot.left, plot.top];
        var markerId = "stroom-d3-arrow-" + ctx.id;
        if (network.directed) {
            ctx.svg.append("defs").append("marker").attr("id", markerId).attr("viewBox", "0 0 10 10")
                .attr("refX", 10).attr("refY", 5).attr("markerWidth", 6).attr("markerHeight", 6)
                .attr("orient", "auto").append("path").attr("d", "M0,0L10,5L0,10Z")
                .attr("fill", ctx.style.subtleText);
        }
        var nodeByName = {};
        shownNodes.forEach(function (node) {
            nodeByName[node.name] = node;
        });
        shownLinks.forEach(function (link) {
            var from = at[link.source];
            var to = at[link.target];
            var width = scaleSqrt(link.value, minValue, maxValue, 1, 6);
            var end = to;
            if (network.directed) {
                // Stop at the target node's edge, so the arrow shows.
                var dx = to[0] - from[0];
                var dy = to[1] - from[1];
                var distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                var back = radiusOf(nodeByName[link.target]) + 2;
                end = [to[0] - dx / distance * back, to[1] - dy / distance * back];
            }
            var selection = linkSelection(fields, link);
            if (ctx.isSelected(selection)) {
                // A solid line either side of a selected link.
                content.append("line").attr("x1", from[0]).attr("y1", from[1]).attr("x2", end[0]).attr("y2", end[1])
                    .attr("stroke", selectionColour(ctx.style)).attr("stroke-width", width + 2 * LINK_OUTLINE)
                    .attr("stroke-linecap", "round");
            }
            var line = content.append("line").attr("x1", from[0]).attr("y1", from[1]).attr("x2", end[0])
                .attr("y2", end[1]).attr("stroke", ctx.style.subtleText).attr("stroke-opacity", 0.6)
                .attr("stroke-width", width);
            if (network.directed) {
                line.attr("marker-end", "url(#" + markerId + ")");
            }
            ctx.mark(line, selection, link.source + (network.directed ? " → " : " – ") + link.target +
                ": " + ctx.format(link.value));
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
        shownNodes.forEach(function (node, i) {
            var position = at[node.name];
            var colour = byGroup ? colours[groups.indexOf(node.group === null ? "(none)" : node.group) %
                colours.length] : colours[i % colours.length];
            var selection = nodeSelection(fields, node.name);
            var circle = content.append("circle").attr("cx", position[0]).attr("cy", position[1])
                .attr("r", radiusOf(node)).attr("fill", colour)
                .attr("stroke", ctx.style.dark ? "#1b1e24" : "#fff").attr("stroke-width", 1);
            outline(circle, ctx, ctx.isSelected(selection));
            ctx.mark(circle, selection, node.name + (node.group !== null ? " (" + node.group + ")" : "") + ": " +
                node.degree + " connections");
            if (labelled[node.name]) {
                svgText(content, shorten(node.name, 24), position[0] + radiusOf(node) + 3, position[1], ctx.style,
                    {baseline: "middle", size: SMALL_FONT_SIZE});
            }
        });
        ctx.report.series = 1;
        ctx.report.nodes = shownNodes.length;
        ctx.report.links = shownLinks.length;
        ctx.report.points = shownNodes.length;
        ctx.zoomKind = "geo";
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

    function drawSankey(ctx) {
        var fields = ctx.fields;
        requireFields(fields, ["source", "target"]);
        var network = linkTotals(ctx.rows, fields, ctx.settings, ctx.prefs, ctx.report, "value");
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
        var vertical = String(textSetting(ctx.settings, "orient") || "horizontal").toLowerCase() === "vertical";
        var length = vertical ? ctx.size.height - 40 : ctx.size.width - 32;
        var breadth = vertical ? ctx.size.width - 40 : ctx.size.height - 30;
        var nodeWidth = 12;
        var gap = 8;
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
        var g = ctx.svg.append("g").attr("transform", vertical ? "translate(20,20)" : "translate(16,12)");
        ctx.geo = g;
        ctx.geoOrigin = vertical ? [20, 20] : [16, 12];
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
            var path = g.append("path").attr("d", d).attr("fill", "none")
                .attr("stroke", colours[names.indexOf(link.source) % colours.length]).attr("stroke-opacity", 0.4)
                .attr("stroke-width", width);
            ctx.mark(path, selection, link.source + " → " + link.target + ": " + ctx.format(link.value));
        });
        var showLabels = boolSetting(ctx.settings, "showLabels", true);
        names.forEach(function (name, i) {
            var node = place[name];
            var corner = point(node.along, node.across);
            var rect = g.append("rect").attr("x", corner[0]).attr("y", corner[1])
                .attr("width", vertical ? node.size : nodeWidth).attr("height", vertical ? nodeWidth : node.size)
                .attr("fill", colours[i % colours.length]);
            var selection = nodeSelection(fields, name);
            outline(rect, ctx, ctx.isSelected(selection));
            ctx.mark(rect, selection, name + ": " + ctx.format(valueOf(name)));
            if (showLabels) {
                var last = depth[name] === columns.length - 1 && columns.length > 1;
                var label = point(last ? node.along - 4 : node.along + nodeWidth + 4, node.across + node.size / 2);
                svgText(g, shorten(name, 24), label[0], label[1], ctx.style, vertical ?
                    {anchor: "middle", size: SMALL_FONT_SIZE} :
                    {anchor: last ? "end" : "start", baseline: "middle", size: SMALL_FONT_SIZE});
            }
        });
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
        var radius = Math.max(20, Math.min(ctx.size.width, ctx.size.height) / 2 - 60);
        var padding = Math.min(0.04, Math.PI / Math.max(1, names.length));
        var available = Math.PI * 2 - padding * names.length;
        var arcs = {};
        var angle = 0;
        names.forEach(function (name) {
            var sweep = total > 0 ? totals[name] / total * available : 0;
            arcs[name] = {start: angle, end: angle + sweep, used: angle};
            angle += sweep + padding;
        });
        var colours = paletteOf(ctx.options, names.length, ctx.style);
        var g = ctx.svg.append("g").attr("transform", "translate(" + ctx.size.width / 2 + "," +
            ctx.size.height / 2 + ")");
        ctx.geo = g;
        ctx.geoOrigin = [ctx.size.width / 2, ctx.size.height / 2];
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
                .attr("fill", colours[names.indexOf(link.source) % colours.length]).attr("fill-opacity", 0.55)
                .attr("stroke", "none");
            outline(ribbon, ctx, ctx.isSelected(selection));
            ctx.mark(ribbon, selection, link.source + " – " + link.target + ": " + ctx.format(link.value));
        });
        var arc = ctx.C.arc().innerRadius(radius + 2).outerRadius(radius + 12);
        var showLabels = boolSetting(ctx.settings, "showLabels", true);
        names.forEach(function (name, i) {
            var part = arcs[name];
            var selection = nodeSelection(fields, name);
            var path = g.append("path").attr("d", arc({startAngle: part.start, endAngle: part.end}))
                .attr("fill", colours[i % colours.length]);
            outline(path, ctx, ctx.isSelected(selection));
            ctx.mark(path, selection, name + ": " + ctx.format(totals[name]));
            if (showLabels && part.end - part.start > 0.05) {
                var middle = (part.start + part.end) / 2;
                svgText(g, shorten(name, 18), Math.sin(middle) * (radius + 18), -Math.cos(middle) * (radius + 18),
                    ctx.style, {anchor: Math.sin(middle) >= 0 ? "start" : "end", baseline: "middle",
                        size: SMALL_FONT_SIZE});
            }
        });
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
        gauge: drawGauge
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
        // Stroom sends at most Max Rows rows: at the limit, the rest of the table was left out, not summarised.
        var maxRows = Number(settings.maxValues);
        if (isFinite(maxRows) && maxRows > 0 && rows.length >= maxRows) {
            report.truncated = true;
            report.warnings.push("Showing the first " + maxRows + " rows: the table has more, which were left " +
                "out. Group the table (e.g. by a rounded time, such as floorDay()) or raise Max Rows.");
        }
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
        report.width = size.width;
        report.height = size.height;
        if (rows.length === 0) {
            report.status = "empty";
        } else if (report.points === 0) {
            report.status = "nothing-drawn";
        }
        return {report: report, marks: marks, svg: svg, zoomKind: ctx.zoomKind || null, plot: ctx.plot || null,
            timeAxis: ctx.timeAxis || null, size: size};
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

        // Buttons over the chart's top right, left of where Stroom puts its own: reset zoom, and the time range tool.
        var toolbar = window.document.createElement("div");
        toolbar.style.position = "absolute";
        toolbar.style.top = "4px";
        toolbar.style.right = (STROOM_BUTTON_SPACE + 4) + "px";
        toolbar.style.zIndex = "2";
        element.appendChild(toolbar);

        function toolButton(text, title) {
            var button = window.document.createElement("button");
            button.type = "button";
            button.textContent = text;
            button.title = title;
            button.setAttribute("aria-label", title);
            button.style.marginLeft = "4px";
            button.style.border = "none";
            button.style.background = "none";
            button.style.cursor = "pointer";
            button.style.font = "inherit";
            button.style.fontSize = "14px";
            button.style.padding = "0 4px";
            button.style.display = "none";
            toolbar.appendChild(button);
            return button;
        }

        var rangeButton = toolButton("⇔", "Select a time range");
        var resetButton = toolButton("⟲", "Reset zoom");

        // Says the chart shows only the first rows, when Stroom sent as many as the row limit allows.
        var truncationNote = window.document.createElement("div");
        truncationNote.style.position = "absolute";
        truncationNote.style.right = "8px";
        truncationNote.style.bottom = "8px";
        truncationNote.style.fontSize = "11px";
        truncationNote.style.zIndex = "2";
        truncationNote.style.display = "none";
        element.appendChild(truncationNote);

        // What is selected, with a button to clear it, shown whenever something is: a filter that uses the selection
        // may leave the chart with nothing to click to clear it.
        var selectionBar = window.document.createElement("div");
        selectionBar.style.position = "absolute";
        selectionBar.style.left = "8px";
        selectionBar.style.bottom = "8px";
        selectionBar.style.maxWidth = "calc(100% - 16px)";
        selectionBar.style.boxSizing = "border-box";
        selectionBar.style.padding = "2px 4px 2px 8px";
        selectionBar.style.borderRadius = "4px";
        selectionBar.style.fontSize = "12px";
        selectionBar.style.alignItems = "center";
        selectionBar.style.zIndex = "2";
        selectionBar.style.display = "none";
        var selectionText = window.document.createElement("span");
        selectionText.style.overflow = "hidden";
        selectionText.style.textOverflow = "ellipsis";
        selectionText.style.whiteSpace = "nowrap";
        selectionBar.appendChild(selectionText);
        var clearButton = window.document.createElement("button");
        clearButton.type = "button";
        clearButton.textContent = "✕";
        clearButton.title = "Clear the selection";
        clearButton.setAttribute("aria-label", "Clear the selection");
        clearButton.style.marginLeft = "6px";
        clearButton.style.border = "none";
        clearButton.style.background = "none";
        clearButton.style.cursor = "pointer";
        clearButton.style.font = "inherit";
        clearButton.style.color = "inherit";
        clearButton.style.padding = "0 4px";
        selectionBar.appendChild(clearButton);
        element.appendChild(selectionBar);

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

        // Tells Stroom whether something is selected on the chart (never what), with the chart's report.
        function reportSelection() {
            if (lastReport) {
                lastReport.selected = !!selected;
                sendReport(lastReport);
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

        function showSelectionBar() {
            if (!selected) {
                selectionBar.style.display = "none";
                selectionText.textContent = "";
                return;
            }
            var label = selectedLabel();
            selectionText.textContent = "Selected: " + label;
            selectionBar.title = "Selected: " + (selected.items ? selected.items.map(function (item) {
                return item.label;
            }).join("; ") : label);
            if (lastStyle) {
                selectionBar.style.color = lastStyle.text;
                selectionBar.style.background = lastStyle.tooltipBackground;
                selectionBar.style.border = "1px solid " + lastStyle.border;
                selectionBar.style.fontFamily = lastStyle.fontFamily;
            }
            selectionBar.style.display = "flex";
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

        clearButton.addEventListener("click", function () {
            clearSelection();
        });

        resetButton.addEventListener("click", function () {
            state.zoom = null;
            state.geoZoom = null;
            render();
        });

        rangeButton.addEventListener("click", function () {
            rangeMode = !rangeMode;
            rangeButton.style.fontWeight = rangeMode ? "bold" : "normal";
            rangeButton.title = rangeMode ? "Stop selecting a time range" : "Select a time range";
        });

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
            if (rangeMode && drawn.timeAxis) {
                drag.range = true;
            }
        }

        function onMouseMove(event) {
            var mark = markOf(event.target);
            if (mark && mark.tooltip && !drag) {
                var at = pointerIn(event);
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
            var now = pointerIn(event);
            var dx = now[0] - drag.last[0];
            var dy = now[1] - drag.last[1];
            if (Math.abs(now[0] - drag.start[0]) + Math.abs(now[1] - drag.start[1]) > 3) {
                drag.moved = true;
            }
            drag.last = now;
            if (drag.range) {
                showRangeDrag(drag.start[0], now[0]);
                return;
            }
            if (drawn.zoomKind === "geo") {
                var zoom = state.geoZoom || {k: 1, x: 0, y: 0};
                state.geoZoom = {k: zoom.k, x: zoom.x + dx, y: zoom.y + dy};
                render();
            } else if (drawn.zoomKind === "axis" && state.zoom && drawn.plot) {
                var span = state.zoom.end - state.zoom.start;
                var shift = -dx / drawn.plot.width * span;
                shift = Math.max(-state.zoom.start, Math.min(1 - state.zoom.end, shift));
                state.zoom = {start: state.zoom.start + shift, end: state.zoom.end + shift};
                render();
            }
        }

        function onMouseUp(event) {
            if (!drag) {
                return;
            }
            var ended = drag;
            drag = null;
            if (ended.range && ended.moved && drawn && drawn.timeAxis) {
                selectRange(ended.start[0], pointerIn(event)[0]);
            }
        }

        function onClick(event) {
            if (drag && drag.moved) {
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
            var factor = event.deltaY < 0 ? 1.2 : 1 / 1.2;
            var at = pointerIn(event);
            if (drawn.zoomKind === "geo") {
                var zoom = state.geoZoom || {k: 1, x: 0, y: 0};
                var k = Math.max(0.2, Math.min(20, zoom.k * factor));
                // Keep the point under the pointer where it is.
                state.geoZoom = {k: k, x: at[0] - (at[0] - zoom.x) * k / zoom.k, y: at[1] - (at[1] - zoom.y) * k / zoom.k};
            } else if (drawn.plot) {
                var window = state.zoom || {start: 0, end: 1};
                var span = window.end - window.start;
                var fraction = Math.max(0, Math.min(1, (at[0] - drawn.plot.left) / drawn.plot.width));
                var centre = window.start + span * fraction;
                var next = Math.max(0.01, Math.min(1, span / factor));
                var start = Math.max(0, Math.min(1 - next, centre - next * fraction));
                state.zoom = next >= 1 ? null : {start: start, end: start + next};
            }
            render();
        }

        // The time range being dragged out, shown over the plot until the mouse is let go.
        function showRangeDrag(fromX, toX) {
            var plot = drawn.timeAxis.plot;
            var group = drawn.timeAxis.group;
            group.selectAll(".range-drag").remove();
            var a = Math.max(0, Math.min(plot.width, Math.min(fromX, toX) - plot.left));
            var b = Math.max(0, Math.min(plot.width, Math.max(fromX, toX) - plot.left));
            group.append("rect").attr("class", "range-drag").attr("x", a).attr("y", 0)
                .attr("width", Math.max(1, b - a)).attr("height", plot.height)
                .attr("fill", "rgba(120, 140, 180, 0.2)").attr("stroke", lastStyle ? lastStyle.subtleText : "#999");
        }

        function selectRange(fromX, toX) {
            var axis = drawn.timeAxis;
            var a = axis.scale.invert(Math.max(0, Math.min(axis.plot.width, Math.min(fromX, toX) - axis.plot.left)));
            var b = axis.scale.invert(Math.max(0, Math.min(axis.plot.width, Math.max(fromX, toX) - axis.plot.left)));
            var start = a instanceof Date ? a.getTime() : Number(a);
            var end = b instanceof Date ? b.getTime() : Number(b);
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

        // The selected time range, drawn over the plot.
        function showRange() {
            if (!selected || !selected.range || !drawn || !drawn.timeAxis) {
                return;
            }
            var axis = drawn.timeAxis;
            var a = axis.scale(new Date(selected.range[0]));
            var b = axis.scale(new Date(selected.range[1]));
            var from = Math.max(0, Math.min(axis.plot.width, Math.min(a, b)));
            var to = Math.max(0, Math.min(axis.plot.width, Math.max(a, b)));
            axis.group.append("rect").attr("x", from).attr("y", 0).attr("width", Math.max(1, to - from))
                .attr("height", axis.plot.height).attr("fill", "rgba(120, 140, 180, 0.15)")
                .attr("stroke", lastStyle ? lastStyle.subtleText : "#999").attr("pointer-events", "none");
        }

        element.addEventListener("mousedown", onMouseDown);
        element.addEventListener("mousemove", onMouseMove);
        element.addEventListener("mouseup", onMouseUp);
        element.addEventListener("mouseleave", function () {
            tooltip.style.display = "none";
            drag = null;
        });
        element.addEventListener("click", onClick);
        element.addEventListener("wheel", onWheel, {passive: false});

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
            toolbar.style.color = style.subtleText;
            rangeButton.style.color = style.subtleText;
            resetButton.style.color = style.subtleText;
            try {
                if (!window.d3) {
                    throw new Error("The D3 library hasn't loaded. Check that the D3 Script doc is installed and is " +
                        "a dependency of this visualisation's script.");
                }
                drawn = drawChart(window.d3, chartElement, settings, lastArgs.data, prefs, style, state);
                report = drawn.report;
                showRange();
                resetButton.style.display = drawn.zoomKind ? "inline" : "none";
                rangeButton.style.display = drawn.timeAxis ? "inline" : "none";
                if (report.status === "empty") {
                    showMessage("No data", style);
                } else if (report.status === "nothing-drawn") {
                    showMessage("None of the rows could be drawn: " + Object.keys(report.droppedRows).join(", "),
                        style);
                } else {
                    hideMessage();
                }
                showSelectionBar();
                if (report.truncated) {
                    truncationNote.textContent = "First " + report.rows + " rows";
                    truncationNote.title = "The table has more rows than this chart's Max Rows, which were left " +
                        "out. Group the table (e.g. by a rounded time) or raise Max Rows.";
                    truncationNote.style.color = style.subtleText;
                    truncationNote.style.display = "block";
                } else {
                    truncationNote.style.display = "none";
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
