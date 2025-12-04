/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.editor.client.presenter;

import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;

public class Option {

    private ChangeHandler changeHandler;
    private final String text;
    private boolean on;
    private boolean available;
    private final boolean defaultValue;
    private final boolean defaultAvailability;

    public Option(final String text,
                  final boolean on,
                  final boolean available,
                  final ChangeHandler changeHandler) {
        this.text = text;
        this.on = on;
        this.defaultValue = on;
        this.available = available;
        this.defaultAvailability = available;
        this.changeHandler = changeHandler;
        // Ensure the change handler is in sync with our state
        if (changeHandler != null) {
            changeHandler.onChange(on);
        }
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(final boolean on, final boolean force) {
        if (force || this.on != on) {
            this.on = on;
            if (changeHandler != null) {
                changeHandler.onChange(on);
            }
        }
    }

    public void setOn(final boolean on) {
        setOn(on, false);
    }

    public void setOn() {
        setOn(true, false);
    }

    public void setOff() {
        setOn(false, false);
    }

    public void setToDefaultState() {
        setOn(defaultValue, false);
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(final boolean available) {
        this.available = available;
    }

    public void setAvailable() {
        this.available = true;
    }

    public void setUnavailable() {
        this.available = false;
    }

    public void setToDefaultAvailability() {
        setAvailable(defaultAvailability);
    }

    public void setChangeHandler(final ChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
    }

    public SafeHtml getText() {
        final String value = on
                ? "ON"
                : "OFF";
        final String classNameSuffix = value.toLowerCase();
        final String className = "editor-menu-option-" + classNameSuffix;
        return HtmlBuilder.builder()
                .append(text)
                .append(" (")
                .span(spanBuilder -> spanBuilder.append(value), Attribute.className(className))
                .append(")")
                .toSafeHtml();
    }

    public boolean isOnAndAvailable() {
        return available && on;
    }


    // --------------------------------------------------------------------------------


    public interface ChangeHandler {

        void onChange(boolean setting);
    }
}
