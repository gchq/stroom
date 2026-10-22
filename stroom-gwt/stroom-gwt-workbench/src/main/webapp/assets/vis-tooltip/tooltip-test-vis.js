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

// The workbench's test visualisation (App/Dashboard/VisTooltip): bars against each edge of its
// frame, each showing a tooltip through Stroom (stroom.showTooltip in vis.js) when the mouse is
// over it or it has the focus, on the side facing out of the frame, where a tooltip drawn in the
// frame would be cut off.
//
// The 'Hostile' bar's tooltip has markup that Stroom must remove before showing it: an image whose
// error handler, and a script, would set window.parent.visTooltipTestXss if they ran.
//
// It draws the same bars whatever data it is given, so a story needs no search results.

var workbench = workbench || {};

workbench.TooltipTestVis = function() {

    var BARS = [
        {name: "Left", value: 10, direction: "w", style: "left: 0; top: 40%;"},
        {name: "Right", value: 20, direction: "e", style: "right: 0; top: 40%;"},
        {name: "Top", value: 30, direction: "n", style: "top: 0; left: 30%;"},
        {name: "Bottom", value: 40, direction: "s", style: "bottom: 0; left: 30%;"},
        {name: "Hostile", value: 50, direction: "e", style: "left: 60%; top: 40%;", hostile: true}
    ];

    var element = document.createElement("div");
    element.style.cssText = "position: absolute; inset: 0;";

    // Made as the visualisations make theirs (stroom.tooltipHtml), with the Hostile bar's markup
    // after it
    var tipHtml = function(bar) {
        var html = stroom.tooltipHtml(null, [["Bar", bar.name], ["Value", bar.value]]);
        if (bar.hostile) {
            html += '<img src="no-such-image" onerror="window.parent.visTooltipTestXss = true">'
                    + '<script>window.parent.visTooltipTestXss = true;</script>';
        }
        return html;
    };

    var show = function(bar, barElement) {
        if (typeof stroom !== "undefined" && typeof stroom.showTooltip === "function") {
            stroom.showTooltip(tipHtml(bar), barElement.getBoundingClientRect(), bar.direction);
        }
    };

    var hide = function() {
        if (typeof stroom !== "undefined" && typeof stroom.hideTooltip === "function") {
            stroom.hideTooltip();
        }
    };

    BARS.forEach(function(bar) {
        var barElement = document.createElement("div");
        barElement.setAttribute("role", "img");
        barElement.setAttribute("aria-label", bar.name);
        barElement.tabIndex = 0;
        barElement.style.cssText = "position: absolute; width: 40px; height: 30px; "
                                   + "background-color: #1f77b4; " + bar.style;
        barElement.addEventListener("mouseover", function() {
            show(bar, barElement);
        });
        barElement.addEventListener("mouseout", hide);
        barElement.addEventListener("focus", function() {
            show(bar, barElement);
        });
        barElement.addEventListener("blur", hide);
        element.appendChild(barElement);
    });

    this.element = element;

    this.setData = function(context, settings, data) {
        // The bars are fixed
    };

    this.resize = function() {
        // The bars are placed by CSS
    };
};
