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

package stroom.dashboard.client.component;

import stroom.dashboard.shared.TextInputFormat;
import stroom.dashboard.shared.TextInputValidator;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A Text Input pane's Basic settings: its id, its name, the key its value is given as a query
/// parameter, and what the value must be (its format, and the pattern it must match, which can only
/// be typed for the Regex Pattern format, and is refused if it isn't a regular expression).
public class BasicTextInputSettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    TextBox key;
    @UiField
    SelectionBox<TextInputFormat> format;
    @UiField
    TextBox pattern;

    /// Makes the tab, empty.
    public BasicTextInputSettings() {
        initWidget(BINDER.createAndBindUi(this));
        format.addItems(TextInputFormat.values());
        format.setValue(TextInputFormat.TEXT);
        format.addValueChangeHandler(event -> updatePatternEnabled());
        pattern.setInvalidReason(text -> TextInputValidator.isValidPattern(text)
                ? null
                : "Must be a regular expression, e.g. [A-Z]{3}\\d+");
        updatePatternEnabled();
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void setId(final String id) {
        this.id.setText(id);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return The parameter's key, as typed.
    public String getKey() {
        return key.getValue();
    }

    /// @param key The parameter's key.
    public void setKey(final String key) {
        this.key.setValue(key);
    }

    /// @return What the value must be.
    public TextInputFormat getFormat() {
        return format.getValue();
    }

    /// @param format What the value must be, or `null` for any text.
    public void setFormat(final TextInputFormat format) {
        this.format.setValue(format != null
                ? format
                : TextInputFormat.TEXT);
        updatePatternEnabled();
    }

    /// @return The pattern the value must match, or `null` if none is typed, so that an untouched
    /// setting isn't seen as a change.
    public String getPattern() {
        final String value = pattern.getValue();
        return value.isEmpty()
                ? null
                : value;
    }

    /// @param pattern The pattern the value must match, or `null` for none.
    public void setPattern(final String pattern) {
        this.pattern.setValue(pattern);
    }

    // The pattern can only be typed, and is only checked, for the Regex Pattern format
    private void updatePatternEnabled() {
        pattern.setEnabled(format.getValue() == TextInputFormat.PATTERN);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicTextInputSettings> {

    }
}
