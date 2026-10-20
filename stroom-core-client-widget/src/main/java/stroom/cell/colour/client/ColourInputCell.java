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

package stroom.cell.colour.client;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

/**
 * Uses the HTML Color Picker as a control in a Cell, to allow
 * users to choose a colour.
 * Note that the appearance does change a bit between browsers.
 */
public class ColourInputCell extends AbstractCell<String> {

    // Define a safe HTML template to prevent XSS and cleanly inject the value.
    // The "colourInputCell" class is the styling hook (see ColourInputCell.css).
    interface Template extends SafeHtmlTemplates {
        @Template("<input type=\"color\" class=\"colourInputCell\" value=\"{0}\"/>")
        SafeHtml input(String value);
    }

    private static Template template;

    public ColourInputCell() {
        // Explicitly consume the "change" browser event
        super("change");
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void render(final Context context, String val, final SafeHtmlBuilder sb) {
        // Fallback to black if value is null (HTML5 color inputs require 7-char hex)
        if (val == null || !val.matches("^#[0-9a-fA-F]{6}$")) {
            val = "#000000";
        }
        sb.append(template.input(val));
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final String val,
                               final NativeEvent event,
                               final ValueUpdater<String> valueUpdater) {
        super.onBrowserEvent(context, parent, val, event, valueUpdater);

        // Check if the change event was fired
        if ("change".equals(event.getType())) {
            // Get the actual input element inside the parent container
            final InputElement input = parent.getFirstChildElement().cast();
            final String newValue = input.getValue();

            // Push the updated hex color code back to the column updater
            if (valueUpdater != null) {
                valueUpdater.update(newValue);
            }
        }
    }
}
