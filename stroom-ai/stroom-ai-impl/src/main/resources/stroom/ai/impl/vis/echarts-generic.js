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
    var KINDS = ["bar", "line", "pie", "scatter", "graph"];
    // Stroom puts its own refresh button over the top-right corner of a dashboard's visualisation, so nothing
    // that can be clicked or read is put within this many pixels of the right edge at the top.
    var STROOM_BUTTON_SPACE = 36;
    // The size of the reset zoom button.
    var TOOLBOX_ITEM_SIZE = 14;

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
            fontFamily: cssVar("--regular-font-family", "Roboto, sans-serif")
        };
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
                    warnings.push("Ignored option '" + path + key + "'");
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

    function Fields(settings, data) {
        var indexes = fieldIndexes(settings);
        var types = data && data.types ? data.types : [];

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

        // What a click selects, for Stroom: each column id given (e.g. category) with its value, and the same
        // value under the name of the table column it is mapped to, so a filter can name either. Dates are given
        // as ISO 8601 text, which Stroom's filters read as dates.
        this.selection = function (pairs) {
            var selection = {};
            var fields = this;
            pairs.forEach(function (pair) {
                var value = selectionValue(pair[1], pair[0] === "node" ? fields.type("source") : fields.type(pair[0]));
                selection[pair[0]] = value;
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

    function legendOption(settings, show, style) {
        var position = textSetting(settings, "legend") || "top";
        if (!show || position === "none") {
            return {show: false};
        }
        var legend = {show: true, type: "scroll", textStyle: {color: style.text}};
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
        return {
            top: top,
            left: 16,
            right: legendShown && position === "right" ? 140 : STROOM_BUTTON_SPACE,
            bottom: legendShown && position === "bottom" ? 36 : 16,
            containLabel: true
        };
    }

    function axisStyle(style) {
        return {
            axisLine: {lineStyle: {color: style.border}},
            axisTick: {lineStyle: {color: style.border}},
            axisLabel: {color: style.subtleText, hideOverlap: true},
            splitLine: {lineStyle: {color: style.border, opacity: 0.6}},
            nameTextStyle: {color: style.subtleText}
        };
    }

    function valueAxis(name, style, axisType, prefs) {
        var axis = merge({type: axisType || "value", name: name || "", nameGap: 28}, axisStyle(style));
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
        return merge({type: "category", name: name || "", nameGap: 28, data: categories}, axisStyle(style));
    }

    // What a click on a category chart's bar or point selects: its category, and its series if one is mapped.
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
        var valAxis = valueAxis(textSetting(settings, "yAxisName") || fields.column("value"), style, "value", prefs);

        return {
            legend: legendOption(settings, legendShown, style),
            grid: gridOption(settings, legendShown),
            tooltip: {trigger: "axis", axisPointer: {type: "shadow"}},
            xAxis: horizontal ? valAxis : catAxis,
            yAxis: horizontal ? catAxis : valAxis,
            selectAt: matrixSelectAt(fields, matrix, "category"),
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
                label: {show: showLabels, color: style.text},
                itemStyle: {borderColor: style.dark ? "#1b1e24" : "#fff", borderWidth: 1}
            }]
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
            return type === "time" ? formatTime(value, prefs.timePattern, prefs.timeZone) : formatNumber(value);
        }

        return {
            legend: legendOption(settings, legendShown, style),
            grid: gridOption(settings, legendShown),
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    var lines = [escapeHtml(params.seriesName)];
                    lines.push(escapeHtml((fields.column("x") || "x") + ": " + axisText(params.value[0], xType)));
                    lines.push(escapeHtml((fields.column("y") || "y") + ": " + axisText(params.value[1], yType)));
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

        rows.forEach(function (row) {
            var sourceValue = fields.value(row, "source");
            var targetValue = fields.value(row, "target");
            if (sourceValue === null || sourceValue === undefined || sourceValue === "" ||
                targetValue === null || targetValue === undefined || targetValue === "") {
                missing++;
                return;
            }
            var source = fields.label(row, "source", prefs);
            var target = fields.label(row, "target", prefs);
            if (source === target) {
                selfLinks++;
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
            var sourceNode = node(source, groupOf(row, "sourceGroup"));
            var targetNode = node(target, groupOf(row, "targetGroup"));
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
                links[key] = {source: source, target: target, value: weight};
                linkKeys.push(key);
            }
        });
        addDropped(report, "source or target empty", missing);
        addDropped(report, "source and target the same", selfLinks);
        addDropped(report, "weight not a number or negative", badWeight);

        nodeNames.forEach(function (name) {
            nodes[name].degree = Object.keys(nodes[name].neighbours).length;
        });

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
        var colourEachNode = String(textSetting(settings, "nodeColours") || "group").toLowerCase() === "node";
        var groups = [];
        var groupIndex = {};
        nodeNames.forEach(function (name) {
            var group = colourEachNode ? name : nodes[name].group;
            if (group !== null && groupIndex[group] === undefined) {
                groupIndex[group] = groups.length;
                groups.push(group);
            }
        });

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
            // A node selects itself (node); a link its two ends (source and target).
            selectAt: function (seriesIndex, dataIndex, dataType) {
                if (dataType === "edge") {
                    var link = links[linkKeys[dataIndex]];
                    return link ? fields.selection([["source", fields.rawOf("source", link.source)],
                        ["target", fields.rawOf("target", link.target)]]) : null;
                }
                var name = nodeNames[dataIndex];
                if (name === undefined) {
                    return null;
                }
                var raw = fields.rawOf("source", name);
                return fields.selection([["node", raw === name ? fields.rawOf("target", name) : raw]]);
            },
            color: colourEachNode ? distinctColours(groups.length, style) : undefined,
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    if (params.dataType === "edge") {
                        var text = params.data.source + (directed ? " → " : " — ") + params.data.target;
                        if (hasWeight) {
                            text += ": " + formatNumber(params.data.value);
                        }
                        return escapeHtml(text);
                    }
                    var lines = [escapeHtml(params.data.name),
                        escapeHtml("Connections: " + formatNumber(params.data.degree))];
                    if (hasWeight) {
                        lines.push(escapeHtml((fields.column("weight") || "Weight") + ": " +
                            formatNumber(params.data.weight)));
                    }
                    if (params.data.groupName) {
                        lines.push(escapeHtml(params.data.groupName));
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
                    return {name: group};
                }),
                force: {
                    repulsion: nodeCount > 500 ? 40 : (nodeCount > 100 ? 80 : 160),
                    edgeLength: nodeCount > 100 ? [20, 80] : [40, 140],
                    gravity: 0.1,
                    layoutAnimation: nodeCount <= 500
                },
                edgeSymbol: directed ? ["none", "arrow"] : ["none", "none"],
                edgeSymbolSize: 6,
                emphasis: {focus: "adjacency", lineStyle: {width: 4}},
                lineStyle: {color: "source", opacity: 0.5, curveness: directed ? 0.1 : 0},
                label: {color: style.text, position: "right"},
                labelLayout: {hideOverlap: true},
                data: nodeNames.map(function (name) {
                    var n = nodes[name];
                    return {
                        name: name,
                        degree: n.degree,
                        weight: n.weight,
                        groupName: n.group,
                        value: n.degree,
                        category: colourEachNode ? groupIndex[name]
                            : (n.group !== null ? groupIndex[n.group] : undefined),
                        symbolSize: scaleSqrt(n.degree, minDegree, maxDegree, 8, 48),
                        label: {show: labelled[name] === true}
                    };
                }),
                links: linkKeys.map(function (key) {
                    var link = links[key];
                    return {
                        source: link.source,
                        target: link.target,
                        value: link.value,
                        lineStyle: {width: hasWeight ? scaleSqrt(link.value, minWeight, maxWeight, 1, 6) : 1}
                    };
                })
            }]
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

    var MODELS = {
        bar: barModel,
        line: lineModel,
        pie: pieModel,
        scatter: scatterModel,
        graph: graphModel
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
    // scatter plot, and a network graph (which roams). A reset zoom button at the top right of the chart's plot
    // area puts it back. A pie chart doesn't zoom.
    function addZoom(kind, option, style) {
        var top;
        var right;
        if (kind === "graph") {
            top = 4;
            right = STROOM_BUTTON_SPACE;
        } else if (option.grid && option.xAxis && option.yAxis) {
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
            // Just inside the plot area's top-right corner.
            top = option.grid.top + 4;
            right = option.grid.right + 4;
        } else {
            return;
        }
        option.toolbox = {
            show: true,
            top: top,
            right: right,
            itemSize: TOOLBOX_ITEM_SIZE,
            feature: {
                restore: {title: "Reset zoom"}
            },
            iconStyle: {borderColor: style.subtleText},
            emphasis: {iconStyle: {borderColor: style.text, textFill: style.text}}
        };
    }

    function buildOption(context, settings, data, prefs, style) {
        settings = settings || {};
        var kind = String(settings.kind || "").toLowerCase();
        if (KINDS.indexOf(kind) < 0) {
            throw new Error("Unknown chart kind '" + settings.kind + "'. Expected one of " + KINDS.join(", "));
        }
        var report = newReport(kind);
        var rows = dataRows(data);
        report.rows = rows.length;
        var fields = new Fields(settings, data);

        var option = MODELS[kind](rows, fields, settings, prefs, style, report);
        // What a click selects, kept off the option ECharts is given.
        var selectAt = option.selectAt || function () {
            return null;
        };
        delete option.selectAt;
        // A clicked item is shown selected: one at a time.
        (option.series || []).forEach(function (series) {
            series.selectedMode = "single";
            series.select = merge(series.select || {}, {
                itemStyle: {borderColor: style.text, borderWidth: 2},
                lineStyle: {width: 4}
            });
        });

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

        addZoom(kind, option, style);

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

        if (rows.length === 0) {
            report.status = "empty";
        } else if (report.points === 0) {
            report.status = "nothing-drawn";
        }
        return {option: option, report: report, selectAt: selectAt};
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

        // The chart last drawn (its option, and what a click on it selects), and the item selected on it.
        var built = null;
        var selected = null;

        // Tells Stroom what is selected, for the dashboard's filters that use this chart's selection.
        function sendSelection(selection) {
            if (window.stroom && typeof window.stroom.select === "function") {
                window.stroom.select(selection);
            }
        }

        function selectionKey(selection) {
            return JSON.stringify(Object.keys(selection).sort().map(function (key) {
                return [key, selection[key]];
            }));
        }

        function showSelected(type) {
            if (chart && selected) {
                var action = {type: type, seriesIndex: selected.seriesIndex, dataIndex: selected.dataIndex};
                if (selected.dataType) {
                    action.dataType = selected.dataType;
                }
                chart.dispatchAction(action);
            }
        }

        function clearSelection() {
            showSelected("unselect");
            selected = null;
            sendSelection([]);
        }

        // A click on an item selects it, or clears the selection if it was selected already.
        function onItemClick(params) {
            if (!built || params.componentType !== "series") {
                return;
            }
            var dataType = params.dataType === "edge" ? "edge" : undefined;
            var selection = built.selectAt(params.seriesIndex, params.dataIndex, dataType);
            if (!selection) {
                return;
            }
            var key = selectionKey(selection);
            if (selected && selected.key === key) {
                clearSelection();
                return;
            }
            showSelected("unselect");
            selected = {key: key, seriesIndex: params.seriesIndex, dataIndex: params.dataIndex, dataType: dataType};
            showSelected("select");
            sendSelection(selection);
        }

        // After a redraw, shows the selected item selected again, wherever it now is.
        function reselect() {
            if (!selected || !built) {
                return;
            }
            var series = built.option.series || [];
            for (var s = 0; s < series.length; s++) {
                var lists = [[series[s].data || [], undefined]];
                if (series[s].links) {
                    lists.push([series[s].links, "edge"]);
                }
                for (var l = 0; l < lists.length; l++) {
                    for (var i = 0; i < lists[l][0].length; i++) {
                        var selection = built.selectAt(s, i, lists[l][1]);
                        if (selection && selectionKey(selection) === selected.key) {
                            selected.seriesIndex = s;
                            selected.dataIndex = i;
                            selected.dataType = lists[l][1];
                            showSelected("select");
                            return;
                        }
                    }
                }
            }
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
            chart.getZr().on("click", function (event) {
                // A click on no item clears the selection.
                if (!event.target && selected) {
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

            try {
                if (!window.echarts) {
                    throw new Error("The ECharts library hasn't loaded. Check that the ECharts Script doc is " +
                        "installed and is a dependency of this visualisation's script.");
                }
                built = buildOption(context, settings, lastArgs.data, prefs, style);
                report = built.report;
                var instance = ensureChart(prefs.theme);
                instance.setOption(built.option, {notMerge: true, lazyUpdate: false});
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
            sendReport(report);
        }

        this.setData = function (context, settings, data) {
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
        buildOption: buildOption,
        formatTime: formatTime,
        sanitiseOptions: sanitiseOptions,
        readPreferences: readPreferences,
        version: ADAPTER_VERSION
    };
})();
