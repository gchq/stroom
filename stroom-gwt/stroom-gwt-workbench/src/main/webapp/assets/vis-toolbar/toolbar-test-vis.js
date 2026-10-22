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

// The workbench's test visualisation (App/Dashboard/VisToolbar): it asks Stroom for two buttons in
// its toolbar (stroom.setToolbarButtons in vis.js), 'Save as image' and the toggle 'Show labels',
// and shows what it hears when they are pressed. A box fills its frame, so that anything Stroom
// draws over the frame would cover it, and a label, which 'Show labels' shows and hides.

var workbench = workbench || {};

workbench.ToolbarTestVis = function() {

    var element = document.createElement("div");
    element.style.cssText = "position: absolute; inset: 0; box-sizing: border-box; border: 4px solid #1f77b4;"
                            + " font: 14px sans-serif; color: #595959; background-color: #fff; padding: 8px;";

    var label = document.createElement("div");
    label.className = "label";
    label.textContent = "A label";
    element.appendChild(label);

    var pressedText = document.createElement("div");
    pressedText.className = "pressed";
    pressedText.textContent = "Nothing pressed";
    element.appendChild(pressedText);

    var showLabels = true;

    var askForButtons = function() {
        if (typeof stroom === "undefined" || typeof stroom.setToolbarButtons !== "function") {
            return;
        }
        stroom.setToolbarButtons([
            {id: "save", title: "Save as image", icon: "DOWNLOAD"},
            {id: "labels", title: "Show labels", icon: "EYE", pressed: showLabels}
        ], function(id, pressed) {
            pressedText.textContent = "Pressed " + id + (pressed === null ? "" : " " + pressed);
            if (id === "labels") {
                showLabels = pressed;
                label.style.display = showLabels ? "" : "none";
                // As a visualisation keeps its toggles' states, telling Stroom
                askForButtons();
            }
        });
    };

    this.element = element;

    this.setData = function(context, settings, data) {
        // Stroom can be asked once it has been in touch (vis.js knows where it is then)
        askForButtons();
    };

    this.resize = function() {
        // Laid out by CSS
    };
};
