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

import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A List Input pane's Basic settings: its id, name and key, the values typed (one a line), whether
/// to offer a dictionary's words instead (a picker its controller attaches), and whether a value
/// can be typed.
public class BasicListInputSettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    TextBox key;
    @UiField
    TextArea values;
    @UiField
    TickBox useDictionary;
    @UiField
    DropDownBox dictionary;
    @UiField
    TickBox allowTextEntry;

    private Consumer<Boolean> onUseDictionaryChange = useDictionary -> {
    };

    /// Makes the tab, empty.
    public BasicListInputSettings() {
        initWidget(BINDER.createAndBindUi(this));
        useDictionary.addValueChangeHandler(event -> onUseDictionaryChange.accept(isUseDictionary()));
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

    /// @return The values typed, one a line.
    public List<String> getValues() {
        return Arrays.asList(values.getValue().split("\n"));
    }

    /// @param values The values, one a line, or `null` to leave them.
    public void setValues(final List<String> values) {
        if (values != null) {
            this.values.setValue(values.stream().collect(Collectors.joining("\n")));
        }
    }

    /// @return Whether a dictionary's words are offered instead of the values typed.
    public boolean isUseDictionary() {
        return useDictionary.getValue();
    }

    /// @param useDictionary Whether a dictionary's words are offered instead of the values typed.
    public void setUseDictionary(final boolean useDictionary) {
        this.useDictionary.setValue(useDictionary);
    }

    /// @param onUseDictionaryChange Given whether a dictionary's words are offered, when the user
    ///                              ticks or unticks Use Dictionary.
    public void setOnUseDictionaryChange(final Consumer<Boolean> onUseDictionaryChange) {
        this.onUseDictionaryChange = onUseDictionaryChange;
    }

    /// @return The dictionary's picker, for a controller to attach.
    public DropDownBox getDictionaryBox() {
        return dictionary;
    }

    /// @return Whether a value not in the list can be typed.
    public boolean isAllowTextEntry() {
        return allowTextEntry.getValue();
    }

    /// @param allowTextEntry Whether a value not in the list can be typed.
    public void setAllowTextEntry(final boolean allowTextEntry) {
        this.allowTextEntry.setValue(allowTextEntry);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicListInputSettings> {

    }
}
