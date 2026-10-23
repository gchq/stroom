/*
 * Copyright 2016 Crown Copyright
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

var stroomParent;
var stroomFrameId;
var stroomOrigin;
// Given the id of a button the visualisation asked for in its toolbar when it is pressed (see
// stroom.setToolbarButtons)
var stroomToolbarHandler = null;
// The palette Stroom says the visualisation's colours come from, e.g. 'Vivid' (null until it
// says), what to do when it changes (see stroom.onColoursChanged), and the callbacks of colours
// asked for (see stroom.colours) by request id
var stroomColourPalette = null;
var stroomColoursChangedHandler = null;
// The user's time zone and date-time pattern, as Stroom gives them (see stroom.time), or null until
// it does (an older Stroom never does)
var stroomTimeSettings = null;
var stroomTimeChangedHandler = null;
var stroomColourCallbacks = {};
var stroomColourRequestId = 0;

// DEPRECATED - HERE FOR BACKWARD COMPATIBILITY
// Send a message to Stroom to open a link
// e.g. `stroomLink('type=Dashboard&uuid=<TARGET_DASHBOARD_UUID>&title=title&params=userId%3Duser2', 'DASHBOARD')`
var stroomLink = function(href, target) {
    target = (typeof target === 'undefined') ? 'browser' : target;

    if (stroomParent && stroomFrameId && stroomOrigin) {
        var obj = {
          frameId : stroomFrameId,
          functionName : 'link',
          href : String(encodeURI(href)),
          target : String(encodeURI(target)),
        };

        var message = JSON.stringify(obj);
        stroomParent.postMessage(message, stroomOrigin);
    }
}

!function() {
    var stroom = {};

    function addUrlParam(url, param, value) {
        if (param && value) {
            if (url.length == 0) {
                url += "?";
            } else {
                url += "&";
            }
            url += param + "=" + encodeURI(value);
        }
        return url;
    }

    stroom.select = function(selection) {
        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'select',
              selection : selection,
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Send a message to Stroom to open a link
    // e.g. `stroom.link('type=Dashboard&uuid=<TARGET_DASHBOARD_UUID>&title=title&params=userId%3Duser2', 'DASHBOARD')`
    stroom.link = function(href, target) {
        target = (typeof target === 'undefined') ? 'browser' : target;

        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'link',
              href : String(encodeURI(href)),
              target : String(encodeURI(target)),
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Ask Stroom to show a hover tooltip on its own page, so that it isn't cut off at the edges
    // of this frame. `rect` is the bounds of the element the tooltip describes, relative to this
    // frame (e.g. from getBoundingClientRect()), and `direction` is the side it should go on if
    // there is room ('n', 's', 'e' or 'w'). Visualisations should test that this function
    // exists before using it, as older versions of Stroom don't have it.
    stroom.showTooltip = function(html, rect, direction) {
        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'showTooltip',
              html : String(html),
              rect : {
                left : rect.left,
                top : rect.top,
                width : rect.width,
                height : rect.height
              },
              direction : direction ? String(direction) : null
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Ask Stroom to hide the tooltip shown by stroom.showTooltip.
    stroom.hideTooltip = function() {
        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'hideTooltip'
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Ask Stroom to show buttons for this visualisation in the toolbar above it, beside its pause
    // button, replacing any asked for before (an empty list removes them). Stroom makes them real
    // toolbar buttons, so they look and work as every other toolbar's do. Each button is
    // {id, title, icon, pressed, enabled}:
    //   id       what the visualisation calls it, given to onPress
    //   title    its name, shown as its tooltip and announced by screen readers
    //   icon     the name of one of Stroom's icons, e.g. 'DOWNLOAD', 'FILTER', 'EYE' or 'SETTINGS'
    //   pressed  for a toggle, whether it is pressed (true or false); leave it out for a button
    //   enabled  false if it can't be pressed for now (e.g. a save with nothing to save): it is
    //            still shown, greyed out; leave it out for one that can be
    // `onPress(id, pressed)` is called when one is pressed, `pressed` being a toggle's new state
    // (null for a button). To change a toggle's state, ask again with the new state. Visualisations
    // should test that this function exists before using it, as older versions of Stroom don't
    // have it.
    // e.g. `stroom.setToolbarButtons([{id: 'legend', title: 'Show legend', icon: 'EYE',
    //       pressed: true}], function(id, pressed) { ... })`
    stroom.setToolbarButtons = function(buttons, onPress) {
        stroomToolbarHandler = typeof onPress === 'function' ? onPress : null;
        if (stroomParent && stroomFrameId && stroomOrigin) {
            var obj = {
              frameId : stroomFrameId,
              functionName : 'setToolbarButtons',
              buttons : (buttons || []).map(function(button) {
                return {
                  id : String(button.id),
                  title : button.title ? String(button.title) : null,
                  icon : button.icon ? String(button.icon) : null,
                  pressed : typeof button.pressed === 'boolean' ? button.pressed : null,
                  enabled : button.enabled !== false
                };
              })
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // Asks Stroom for the colours of some values of a field, e.g. the hosts of a Host field, so that
    // each value has the same colour in every visualisation of the dashboard. `callback(colours)` is
    // given each value's colour, '#rrggbb', in the order asked, from the palette the user chose and
    // for the theme. Visualisations should colour values themselves until it answers, and should
    // test that this function exists before using it, as older versions of Stroom don't have it.
    // e.g. `stroom.colours('Host', ['web-01', 'web-02'], function(colours) { ... })`
    stroom.colours = function(field, values, callback) {
        if (stroomParent && stroomFrameId && stroomOrigin) {
            stroomColourRequestId++;
            stroomColourCallbacks[stroomColourRequestId] = callback;
            var obj = {
              frameId : stroomFrameId,
              functionName : 'colours',
              requestId : stroomColourRequestId,
              field : field === null || field === undefined ? '' : String(field),
              values : (values || []).map(function(value) {
                return value === null || value === undefined ? '' : String(value);
              })
            };

            var message = JSON.stringify(obj);
            stroomParent.postMessage(message, stroomOrigin);
        }
    };

    // The name of the palette Stroom says colours come from, e.g. 'Vivid', or null if it hasn't
    // said (an older Stroom, or before the visualisation has been started)
    stroom.colourPalette = function() {
        return stroomColourPalette;
    };

    // Sets what to do when the palette or theme changes, e.g. ask for the colours again and redraw
    stroom.onColoursChanged = function(handler) {
        stroomColoursChangedHandler = typeof handler === 'function' ? handler : null;
    };

    // Times as the user has asked Stroom to show them (their time zone and date-time pattern, in
    // their preferences), with moment.js as Stroom's own DateTimeFormatter does, so that a time in a
    // visualisation reads as it does everywhere else in Stroom. Each works without them, in UTC:
    //   format(ms)          the time in full, e.g. '2026-10-12T09:00:00.000Z'
    //   formatCompact(ms)   to the minute, without the zone, e.g. '2026-10-12T09:00'
    //   offsetMinutes(ms)   the user's zone's offset from UTC at that time, in minutes (e.g. 60 for
    //                       British Summer Time), for a visualisation to put values in the user's
    //                       days and hours
    //   is12Hour()          whether the user's pattern has a 12-hour clock (h or a), for axes' labels
    //   settings()          what Stroom gave: {use, zoneId, offsetMinutes, pattern, compactPattern},
    //                       or null
    stroom.time = (function() {
        var DEFAULT_PATTERN = 'YYYY-MM-DD[T]HH:mm:ss.SSS[Z]';
        var DEFAULT_COMPACT = 'YYYY-MM-DD[T]HH:mm';

        // The moment of a time in the user's zone, or null without moment.js
        var momentOf = function(ms) {
            if (typeof window.moment !== 'function') {
                return null;
            }
            var m = window.moment.utc(ms);
            var settings = stroomTimeSettings;
            if (!settings) {
                return m;
            }
            switch (settings.use) {
                case 'Local':
                    return m.local();
                case 'Offset':
                    return m.utcOffset(settings.offsetMinutes || 0);
                case 'Id':
                    return typeof m.tz === 'function' && settings.zoneId ? m.tz(settings.zoneId) : m;
                default:
                    return m;
            }
        };

        var iso = function(ms) {
            return new Date(ms).toISOString();
        };

        // A pattern without its [literals], to look for tokens in
        var tokensOf = function(pattern) {
            return String(pattern || '').replace(/\[[^\]]*\]/g, '');
        };

        return {
            format: function(ms) {
                var m = momentOf(ms);
                return m ? m.format(stroomTimeSettings ? stroomTimeSettings.pattern : DEFAULT_PATTERN) : iso(ms);
            },
            formatCompact: function(ms) {
                var m = momentOf(ms);
                return m
                    ? m.format(stroomTimeSettings ? stroomTimeSettings.compactPattern : DEFAULT_COMPACT)
                    : iso(ms).substring(0, 16);
            },
            offsetMinutes: function(ms) {
                var m = momentOf(ms);
                return m ? m.utcOffset() : 0;
            },
            is12Hour: function() {
                return stroomTimeSettings ? /[hAa]/.test(tokensOf(stroomTimeSettings.pattern)) : false;
            },
            settings: function() {
                return stroomTimeSettings;
            }
        };
    }());

    // Sets what to do when the user's time zone or date-time pattern changes, e.g. redraw
    stroom.onTimeChanged = function(handler) {
        stroomTimeChangedHandler = typeof handler === 'function' ? handler : null;
    };

    // Stroom's own Escape and mouse listeners can't hear events in this frame, so hide the
    // tooltip here too when Escape is pressed or the mouse leaves the frame
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape') {
            stroom.hideTooltip();
        }
    }, true);
    document.documentElement.addEventListener('mouseleave', function() {
        stroom.hideTooltip();
    });

    // Whether the dashboard has the dark theme, from the theme class Stroom gives the
    // visualisation's element
    stroom.isDarkTheme = function() {
        return document.querySelector(".stroom-theme-dark") !== null;
    };

    // The HTML of a tooltip of a title and rows of [name, value], as Stroom's tooltips lay them
    // out. The text is escaped, so it can come from the data.
    stroom.tooltipHtml = function(title, rows) {
        var escape = function(text) {
            var element = document.createElement("span");
            element.textContent = text === null || text === undefined ? "" : String(text);
            return element.innerHTML;
        };
        var html = "";
        if (title !== null && title !== undefined && title !== "") {
            html += "<div class=\"vis-tooltip__title\">" + escape(title) + "</div>";
        }
        if (rows && rows.length > 0) {
            html += "<table class=\"vis-tooltip__rows\">";
            rows.forEach(function(row) {
                html += "<tr class=\"vis-tooltip__row\">"
                        + "<th class=\"vis-tooltip__key\">" + escape(row[0]) + "</th>"
                        + "<td class=\"vis-tooltip__value\">" + escape(row[1]) + "</td>"
                        + "</tr>";
            });
            html += "</table>";
        }
        return html;
    };

    // Makes the data points of a chart reachable from the keyboard, shows each one's tooltip on
    // Stroom's page when the mouse is over it or it has the focus, and names it for screen
    // readers. `root` is the element the points are in (e.g. the chart's <svg>) and `options` is:
    //   selector   which elements in `root` are the points (required)
    //   group      function(point) giving the key of its group, e.g. its series; points with the
    //              same key are a group, in the order they are in the document. One group if left out
    //   tooltip    function(point) giving {title, rows: [[name, value], ...]}, or HTML (required)
    //   label      function(point) giving its name for screen readers; made from the tooltip if
    //              left out
    //   highlight  function(point) called when a point is hovered or focused, and with null when
    //              it no longer is, so that the chart can highlight it
    //   select     function(point, event) called when a point is clicked, or Enter or Space is
    //              pressed on it (as Ctrl+Click, to add to the selection; the event then has
    //              `keyboard` true and `key` 'Enter' or ' ', so that a chart can give them different
    //              jobs, e.g. Enter opens a branch and Space selects it)
    //   role       function(point) giving its ARIA role, e.g. 'button' for a point that does
    //              something when pressed (e.g. zooms in to a series); 'img' if left out
    //   summary    a description of the whole chart, e.g. 'Bar chart, 6 statuses', the name
    //              `root` has for screen readers (may be a function giving it)
    //   groupDirection  which way the next group is on the screen: 'down' (the default, e.g. the
    //              rows of a heat map), 'up', 'right' or 'left' (e.g. the levels of a tree growing
    //              right), or a function giving it, so that the arrow keys go the way the chart is
    //              drawn. Groups that are columns ('right' or 'left') have their points top to bottom
    // The keys are (for groups going down; for groups going another way the keys turn with them):
    //   Tab          to the chart, which is one tab stop (a roving tabindex)
    //   Left/Right   the previous/next point, crossing to the previous/next group at the ends
    //   Up/Down      the point at the same place in the previous/next group (the previous/next
    //                point when there is one group)
    //   Home/End     the first/last point of the group
    //   Ctrl+Home/End  the first/last point of the whole chart
    //   Page Up/Down   ten points back/on in the group, so that a long group (e.g. a line of a
    //                hundred points) isn't a long walk
    //   Enter/Space  selects the point
    //   Escape       hides the tooltip, and selects nothing
    // A click on a point selects it (`select`, the click's event saying whether Ctrl was down); a
    // click on nothing selects nothing. Selecting nothing is told to the chart as a
    // 'vis-clear-selection' event from `root`, which reaches the chart's element. A point is
    // selected when its class has a BEM '--selected' modifier (e.g. 'vis-bar-chart__bar--selected'),
    // and its name then says so ('…, selected').
    // Points come and go as the chart draws its data, and are found again each time. Returns
    // {refresh, show, hide, destroy}: refresh after drawing (it also happens by itself shortly
    // after the points change), show(point) and hide() for a chart that finds the point nearest
    // the mouse itself (e.g. along a line, whose points are too small to point at), and destroy
    // when the chart is removed.
    stroom.points = function(root, options) {
        var POINT_CLASS = "vis-point";
        // How many points Page Up and Page Down move by
        var PAGE_SIZE = 10;
        // Told to the chart (from `root`, bubbling) when a click on nothing, or Escape, is to
        // select nothing
        var CLEAR_SELECTION_EVENT = "vis-clear-selection";
        // Where a click doesn't select nothing: a control the chart has in `root` (e.g. a slider
        // for zooming an axis)
        var CONTROL_SELECTOR = "button, a, input, select, textarea, [role='slider'], [role='button']";
        // Whether a point is selected: its class has a BEM '--selected' modifier
        var isSelected = function(point) {
            return /--selected(\s|$)/.test(point.getAttribute("class") || "");
        };
        // The arrow keys for the previous and next point of a group, and the previous and next
        // group, by the way the next group is on the screen
        var GROUP_KEYS = {
            down: {previousPoint: "ArrowLeft", nextPoint: "ArrowRight", previousGroup: "ArrowUp", nextGroup: "ArrowDown"},
            up: {previousPoint: "ArrowLeft", nextPoint: "ArrowRight", previousGroup: "ArrowDown", nextGroup: "ArrowUp"},
            right: {previousPoint: "ArrowUp", nextPoint: "ArrowDown", previousGroup: "ArrowLeft", nextGroup: "ArrowRight"},
            left: {previousPoint: "ArrowUp", nextPoint: "ArrowDown", previousGroup: "ArrowRight", nextGroup: "ArrowLeft"}
        };
        var tabStop = null;
        var active = null;
        var refreshTimer = null;

        var tooltipOf = function(point) {
            var tooltip = options.tooltip ? options.tooltip(point) : null;
            if (tooltip === null || tooltip === undefined) {
                return null;
            }
            if (typeof tooltip === "string") {
                return {html: tooltip, text: htmlText(tooltip)};
            }
            var rows = tooltip.rows || [];
            var parts = [];
            if (tooltip.title !== null && tooltip.title !== undefined && tooltip.title !== "") {
                parts.push(String(tooltip.title));
            }
            rows.forEach(function(row) {
                parts.push(row[0] + ": " + row[1]);
            });
            return {html: stroom.tooltipHtml(tooltip.title, rows), text: parts.join(", ")};
        };

        // The text of some HTML, for a screen reader. A template's content is inert, so no
        // scripts run and no images load
        var htmlText = function(html) {
            var template = document.createElement("template");
            template.innerHTML = html;
            return template.content.textContent.replace(/\s+/g, " ").trim();
        };

        var isShown = function(element) {
            return element.getClientRects().length > 0;
        };

        // The points shown, in groups in the order their first points are in the document
        var layout = function() {
            var points = Array.prototype.slice.call(root.querySelectorAll(options.selector))
                .filter(isShown);
            var groups = [];
            var byKey = new Map();
            points.forEach(function(point) {
                var key = options.group ? options.group(point) : "";
                var group = byKey.get(key);
                if (!group) {
                    group = [];
                    byKey.set(key, group);
                    groups.push(group);
                }
                group.push(point);
            });
            return groups;
        };

        // Where a point is, as {groups, groupIndex, index}, or null if it isn't one
        var locate = function(element) {
            var groups = layout();
            for (var i = 0; i < groups.length; i++) {
                var index = groups[i].indexOf(element);
                if (index >= 0) {
                    return {groups: groups, groupIndex: i, index: index};
                }
            }
            return null;
        };

        var refresh = function() {
            clearTimeout(refreshTimer);
            refreshTimer = null;
            if (!document.documentElement.contains(root)) {
                return;
            }
            var summary = typeof options.summary === "function" ? options.summary() : options.summary;
            root.setAttribute("role", "group");
            root.setAttribute("aria-roledescription", "chart");
            if (summary) {
                root.setAttribute("aria-label", String(summary));
            }

            var points = [].concat.apply([], layout());
            if (points.length === 0) {
                tabStop = null;
                return;
            }
            if (points.indexOf(document.activeElement) >= 0) {
                tabStop = document.activeElement;
            } else if (tabStop === null || points.indexOf(tabStop) < 0) {
                tabStop = points[0];
            }
            points.forEach(function(point) {
                point.classList.add(POINT_CLASS);
                var tabIndex = point === tabStop ? "0" : "-1";
                if (point.getAttribute("tabindex") !== tabIndex) {
                    point.setAttribute("tabindex", tabIndex);
                }
                point.setAttribute("role", options.role ? options.role(point) || "img" : "img");
                var name = options.label ? options.label(point) : null;
                if (!name) {
                    var tooltip = tooltipOf(point);
                    name = tooltip ? tooltip.text : null;
                }
                if (name) {
                    point.setAttribute("aria-label", isSelected(point) ? name + ", selected" : name);
                }
            });
        };

        var scheduleRefresh = function() {
            if (refreshTimer === null) {
                refreshTimer = setTimeout(refresh, 50);
            }
        };

        var setTabStop = function(point) {
            if (tabStop !== point) {
                if (tabStop !== null) {
                    tabStop.setAttribute("tabindex", "-1");
                }
                tabStop = point;
                point.setAttribute("tabindex", "0");
            }
        };

        var show = function(point) {
            if (active !== null && active !== point) {
                hide();
            }
            active = point;
            if (options.highlight) {
                options.highlight(point);
            }
            var tooltip = tooltipOf(point);
            if (tooltip !== null) {
                stroom.showTooltip(tooltip.html, point.getBoundingClientRect(), null);
            }
        };

        var hide = function() {
            if (active !== null) {
                active = null;
                stroom.hideTooltip();
                if (options.highlight) {
                    options.highlight(null);
                }
            }
        };

        // The point an event happened on, if any
        var pointOf = function(target) {
            var element = target instanceof Element ? target.closest(options.selector) : null;
            return element !== null && root.contains(element) ? element : null;
        };

        var focusPoint = function(location, groupIndex, index) {
            var group = location.groups[groupIndex];
            var point = group[Math.max(0, Math.min(index, group.length - 1))];
            if (point !== document.activeElement) {
                setTabStop(point);
                point.focus();
            }
        };

        // Moves to the previous (step -1) or next (step 1) point, going on to the end of the
        // previous group or the start of the next at the ends of the group
        var stepPoint = function(location, step) {
            var index = location.index + step;
            var groupIndex = location.groupIndex;
            if (index < 0 && groupIndex > 0) {
                groupIndex--;
                index = location.groups[groupIndex].length - 1;
            } else if (index >= location.groups[groupIndex].length
                       && groupIndex < location.groups.length - 1) {
                groupIndex++;
                index = 0;
            }
            focusPoint(location, groupIndex, index);
        };

        var onKeyDown = function(event) {
            if (event.defaultPrevented || event.altKey || event.metaKey
                || (event.ctrlKey && event.key !== "Home" && event.key !== "End")) {
                return;
            }
            var location = locate(event.target);
            if (location === null) {
                return;
            }
            var groupCount = location.groups.length;
            // The arrow keys by what they do, turned to the way the groups go on the screen
            var direction = typeof options.groupDirection === "function"
                ? options.groupDirection()
                : (options.groupDirection || "down");
            var keys = GROUP_KEYS[direction] || GROUP_KEYS.down;
            switch (event.key) {
                case keys.previousPoint:
                    stepPoint(location, -1);
                    break;
                case keys.nextPoint:
                    stepPoint(location, 1);
                    break;
                case keys.previousGroup:
                    if (groupCount > 1) {
                        focusPoint(location, Math.max(0, location.groupIndex - 1), location.index);
                    } else {
                        stepPoint(location, -1);
                    }
                    break;
                case keys.nextGroup:
                    if (groupCount > 1) {
                        focusPoint(location, Math.min(groupCount - 1, location.groupIndex + 1), location.index);
                    } else {
                        stepPoint(location, 1);
                    }
                    break;
                case "Home":
                    // With Ctrl, the first point of the whole chart
                    focusPoint(location, event.ctrlKey ? 0 : location.groupIndex, 0);
                    break;
                case "End":
                    // With Ctrl, the last point of the whole chart
                    var lastGroup = event.ctrlKey ? groupCount - 1 : location.groupIndex;
                    focusPoint(location, lastGroup, location.groups[lastGroup].length - 1);
                    break;
                case "PageUp":
                    focusPoint(location, location.groupIndex, Math.max(0, location.index - PAGE_SIZE));
                    break;
                case "PageDown":
                    focusPoint(location, location.groupIndex, location.index + PAGE_SIZE);
                    break;
                case "Enter":
                case " ":
                    if (!options.select) {
                        return;
                    }
                    options.select(event.target, {ctrlKey: true, keyboard: true, key: event.key});
                    break;
                case "Escape":
                    // Not prevented, so that vis.js hides Stroom's tooltip too
                    hide();
                    clearSelection();
                    return;
                default:
                    return;
            }
            event.preventDefault();
        };

        var onFocusIn = function(event) {
            var point = pointOf(event.target);
            if (point !== null) {
                setTabStop(point);
                show(point);
            }
        };

        var onFocusOut = function(event) {
            if (event.target === active) {
                hide();
            }
        };

        var onMouseOver = function(event) {
            var point = pointOf(event.target);
            if (point !== null) {
                show(point);
            }
        };

        var onMouseOut = function(event) {
            var point = pointOf(event.target);
            // Moving to a child of the same point isn't leaving it
            if (point !== null && point === active && pointOf(event.relatedTarget) !== point
                && point !== document.activeElement) {
                hide();
            }
        };

        // Tells the chart to select nothing (see CLEAR_SELECTION_EVENT)
        var clearSelection = function() {
            root.dispatchEvent(new CustomEvent(CLEAR_SELECTION_EVENT, {bubbles: true}));
        };

        var onClick = function(event) {
            var point = pointOf(event.target);
            if (point !== null) {
                if (options.select) {
                    options.select(point, event);
                }
            } else if (!event.defaultPrevented && !event.target.closest(CONTROL_SELECTOR)) {
                // A click on nothing (not a point, nor a control such as a slider, and not one the
                // chart has dealt with itself, e.g. one that selects the nearest point)
                clearSelection();
            }
        };

        root.addEventListener("keydown", onKeyDown);
        root.addEventListener("focusin", onFocusIn);
        root.addEventListener("focusout", onFocusOut);
        root.addEventListener("mouseover", onMouseOver);
        root.addEventListener("mouseout", onMouseOut);
        root.addEventListener("click", onClick);
        var observer = typeof MutationObserver === "undefined" ? null : new MutationObserver(scheduleRefresh);
        if (observer !== null) {
            observer.observe(root, {childList: true, subtree: true});
        }
        scheduleRefresh();

        return {
            refresh: refresh,
            // Shows a point's tooltip and highlights it, as when the mouse is over it
            show: function(point) {
                if (point !== active) {
                    show(point);
                }
            },
            // Hides the tooltip and highlight of the point shown, unless it has the focus
            hide: function() {
                if (active !== null && active !== document.activeElement) {
                    hide();
                }
            },
            destroy: function() {
                hide();
                clearTimeout(refreshTimer);
                if (observer !== null) {
                    observer.disconnect();
                }
                root.removeEventListener("keydown", onKeyDown);
                root.removeEventListener("focusin", onFocusIn);
                root.removeEventListener("focusout", onFocusOut);
                root.removeEventListener("mouseover", onMouseOver);
                root.removeEventListener("mouseout", onMouseOut);
                root.removeEventListener("click", onClick);
            }
        };
    };

    stroom.dashboard = function(uuid, params, queryOnOpen) {
       var url = "";
       url = addUrlParam(url, "uuid", uuid);
       url = addUrlParam(url, "params", params);
       url = addUrlParam(url, "queryOnOpen", queryOnOpen);
       stroom.link(url, "dashboard");
    };

    stroom.annotation = function(annotationId, streamId, eventId, title, subject, status, assignedTo, comment) {
       var url = "";
       url = addUrlParam(url, "annotationId", annotationId);
       url = addUrlParam(url, "streamId", streamId);
       url = addUrlParam(url, "eventId", eventId);
       url = addUrlParam(url, "title", title);
       url = addUrlParam(url, "subject", subject);
       url = addUrlParam(url, "status", status);
       url = addUrlParam(url, "assignedTo", assignedTo);
       url = addUrlParam(url, "comment", comment);
       stroom.link(url, "annotation");
    };

    stroom.stepping = function(id, partNo, recordNo) {
       var url = "";
       url = addUrlParam(url, "id", id);
       url = addUrlParam(url, "partNo", partNo);
       url = addUrlParam(url, "recordNo", recordNo);
       stroom.link(url, "stepping");
    };

    stroom.data = function(id, partNo, recordNo, lineFrom, colFrom, lineTo, colTo) {
       var url = "";
       url = addUrlParam(url, "id", id);
       url = addUrlParam(url, "partNo", partNo);
       url = addUrlParam(url, "recordNo", recordNo);
       url = addUrlParam(url, "lineFrom", lineFrom);
       url = addUrlParam(url, "colFrom", colFrom);
       url = addUrlParam(url, "lineTo", lineTo);
       url = addUrlParam(url, "colTo", colTo);
       stroom.link(url, "data");
    };

    this.stroom = stroom;
}();

/**
 * AN OBJECT TO SEND CALLBACK MESSAGES TO Stroom
 */
function Callback(event, frameId, callbackId) {
  stroomParent = event.source;
  stroomFrameId = frameId;
  stroomOrigin = event.origin;

  var event = event;
  var frameId = frameId;
  var callbackId = callbackId;
  var sendCallback = function(event, frameId, callbackId, functionName, param) {
    var obj = {
      frameId : frameId,
      callbackId : callbackId,
      functionName : functionName,
      param : param
    };

    var message = JSON.stringify(obj);

    event.source.postMessage(message, event.origin);
  }

  this.onSuccess = function(message) {
    sendCallback(event, frameId, callbackId, "onSuccess", message);
  };
  this.onFailure = function(ex) {
    sendCallback(event, frameId, callbackId, "onFailure", ex);
  };
}

/**
 * AN OBJECT TO PERFORM SCRIPT INJECTION
 */
function ScriptInjector() {
  var makeScriptElement = function(doc) {
    var script = doc.createElement("script");
    script.setAttribute("type", "text/javascript");
    script.setAttribute("charset", "UTF-8");
    return script;
  }

  var attachListeners = function(scriptElement, url, callback, removeTag) {
    var clearCallbacks = function() {
      scriptElement.onerror = scriptElement.onreadystatechange = scriptElement.onload = null;
      if (removeTag) {
        scriptElement.parentNode.removeChild(scriptElement);
      }
    };
    scriptElement.onload = function() {
      clearCallbacks();
      if (callback) {
        callback.onSuccess(url);
      }
    };
    // or possibly more portable script_tag.addEventListener('error',
    // function(){...}, true);
    scriptElement.onerror = function() {
      clearCallbacks();
      if (callback) {
        var ex = "onerror() called.";
        callback.onFailure(ex);
      }
    };
    scriptElement.onreadystatechange = function() {
      if (/loaded|complete/.test(scriptElement.readyState)) {
        scriptElement.onload();
      }
    };
  }

  this.inject = function(url, callback) {
    var wnd = window;
    var doc = wnd.document;

    var scriptElement = makeScriptElement(doc);
    (doc.head || doc.getElementsByTagName("head")[0]).appendChild(scriptElement);
    attachListeners(scriptElement, url, callback, false);
    scriptElement.src = url;
  }
}

/**
 * AN OBJECT TO LOAD AND INTERACT WITH VISUALISATIONS
 */
function VisualisationManager() {
  var vis = null;
  var currentContext = null;
  var currentSettings = null;
  var currentData = null;
  var running = false;

  var update = function(callback) {
    if (vis && currentData) {
      vis.setData(currentContext, currentSettings, currentData);
    }
  };

  var injectNextScript = function(scripts, callback) {
    if (scripts.length > 0) {
      var script = scripts.splice(0, 1)[0];
      var cb = {
        onSuccess : function(message) {
          injectNextScript(scripts, callback);
        },
        onFailure : function(ex) {
          callback.onFailure("Failed to inject script '" + script.name + "' - "
              + ex.message);
        }
      };

      new ScriptInjector().inject(script.url, cb);

    } else {
      callback.onSuccess(null);
    }
  };

  var doStart = function(callback) {
    running = true;
    if (vis && vis.start) {
      vis.start();
    }
  };

  var doEnd = function(callback) {
    if (vis && vis.end) {
      vis.end();
    }
    running = false;
  };

  this.injectScripts = function(scripts, callback) {
    injectNextScript(scripts, callback);
  };

  this.start = function(callback) {
    doStart();
  };

  this.end = function(callback) {
    doEnd();
  };

  this.setVisType = function(type, className, callback) {
    // Stroom clears the previous visualisation's toolbar buttons
    stroomToolbarHandler = null;
    stroomColoursChangedHandler = null;
    stroomColourCallbacks = {};
    try {
      vis = eval("new " + type + "()");
      callback.onSuccess(null);
    } catch (ex) {
      callback.onFailure(ex.message);
    }

    if (vis && vis.element) {
      document.body.innerHTML = "";
      document.body.appendChild(vis.element);
      vis.element.className = className;

      if (running) {
        doStart();
        update();
      }
    }
  };

  // A button the visualisation asked for in its toolbar was pressed (see stroom.setToolbarButtons)
  this.toolbarButtonPressed = function(id, pressed, callback) {
    if (stroomToolbarHandler) {
      stroomToolbarHandler(id, pressed);
    }
  };

  // Stroom's answer to stroom.colours: the values' colours, in the order asked
  this.colours = function(requestId, colours, callback) {
    var answer = stroomColourCallbacks[requestId];
    delete stroomColourCallbacks[requestId];
    if (typeof answer === 'function') {
      answer(colours);
    }
  };

  // The palette colours come from, now or since it changed, e.g. 'Vivid'
  this.coloursChanged = function(palette, callback) {
    stroomColourPalette = palette;
    if (stroomColoursChangedHandler) {
      stroomColoursChangedHandler();
    }
  };

  // The user's time zone and date-time pattern, now or since they changed (see stroom.time)
  this.timeChanged = function(settings, callback) {
    stroomTimeSettings = settings;
    if (stroomTimeChangedHandler) {
      stroomTimeChangedHandler();
    }
  };

  this.setClassName = function(className, callback) {
    if (vis && vis.element) {
      vis.element.className = className;
    }
  };

  this.setData = function(context, settings, data, callback) {
    currentContext = context;
    currentSettings = settings;
    currentData = data;

    update();
  };

  this.resize = function(callback) {
    if (vis) {
      vis.resize();
    }
  };
}

/**
 * GLOBAL VISUALISATION MANAGER
 */
var visualisationManager = new VisualisationManager();

/**
 * LISTEN TO WINDOW MESSAGES
 */
var messageListener = function(event) {
  var sendMessage = function(event, frameId, data) {
    var obj = {
      frameId : frameId,
      data : data
    }

    var message = JSON.stringify(obj);

    event.source.postMessage(message, event.origin);
  };

  var origin = event.origin;
  var hostname = window.location.hostname;

  // Stop this script being called from other domains.
  var eventLocation = document.createElement("a");
  eventLocation.href = origin;
  var eventHostname = eventLocation.hostname;
  if (eventHostname != hostname) {
    console.error("Ignoring event as host names do not match: hostname='" + hostname + "' eventHostname='" + eventHostname + "'");
    return;
  }

  var json = JSON.parse(event.data);

  if (json.data) {
    var frameId;
    var callbackId;

    if (json.frameId) {
      frameId = json.frameId;
    }
    if (json.callbackId) {
      callbackId = json.callbackId;
    }

    var callback = new Callback(event, frameId, callbackId);
    var params = json.data.params;
    if (!params) {
        params = [];
    }
    params.push(callback);

    eval(json.data.functionName + ".apply(this, params);");
  }
}

if (window.addEventListener) {
  addEventListener("message", messageListener, false);
} else {
  attachEvent("onmessage", messageListener);
}
