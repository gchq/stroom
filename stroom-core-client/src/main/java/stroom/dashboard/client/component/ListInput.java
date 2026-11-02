/*
 * Copyright 2022 Crown Copyright
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

import stroom.dashboard.client.model.WordItem;
import stroom.docref.DocRef;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A dashboard's List Input pane: a list of values, typed in its settings or from a dictionary
/// (each shown with the dictionary it came from), and, if allowed, any value typed. When several
/// values can be chosen ([#setMultiSelect(boolean)]) it is a picker showing those ticked, which asks
/// its controller to open a chooser of them ([#setOnOpenValues(Runnable)]).
public class ListInput extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final SafeHtml DICT_ICON_HTML = SvgImageUtil.toSafeHtml(
            "Source Dictionary",
            SvgImage.DOCUMENT_DICTIONARY,
            "explorer-cell__icon");

    @UiField
    SelectionBox<WordItem> valueSelectionBox;
    @UiField
    DropDownBox valuesBox;

    private boolean allowTextEntry;
    private String accessibleName;
    private String valuesText = "";
    private Consumer<WordItem> onValueChange = value -> {
    };

    /// Makes the pane, with no values.
    public ListInput() {
        initWidget(BINDER.createAndBindUi(this));
        valueSelectionBox.setRenderFunction(ListInput::render);
        valueSelectionBox.addValueChangeHandler(event -> {
            if (allowTextEntry) {
                onValueChange.accept(WordItem.simpleWord(valueSelectionBox.getText()));
            } else {
                onValueChange.accept(valueSelectionBox.getValue());
            }
        });
    }

    /// Shows a value, with the dictionary it came from, if any, as the list and the chooser of
    /// several values do.
    ///
    /// @param wordItem The value.
    /// @return The value, as HTML.
    static SafeHtml render(final WordItem wordItem) {
        final String word = wordItem.getWord();

        return wordItem.getSourceDocRef()
                .map(docRef ->
                        NullSafe.getOrElseGet(docRef,
                                DocRef::getName,
                                docRef::getUuid))
                .map(sourceVal -> HtmlBuilder.builder()
                        .div(containerBuilder -> containerBuilder
                                        .div(word, cssClass("__word"))
                                        .div(sourceBuilder -> {
                                            sourceBuilder
                                                    .div(sourceVal, cssClass("__source-text"))
                                                    .append(DICT_ICON_HTML);
                                        }, cssClass("__source")),
                                cssClass(""))
                        .toSafeHtml())
                .orElseGet(() ->
                        SafeHtmlUtils.fromString(word));
    }

    private static Attribute cssClass(final String suffix) {
        return Attribute.className("list-input-item" + suffix);
    }

    /// Sets the values offered, after a blank one, keeping the value chosen.
    ///
    /// @param values The values, or `null` for none.
    public void setValues(final List<WordItem> values) {
        final WordItem selected = valueSelectionBox.getValue();
        valueSelectionBox.clear();
        if (values != null) {
            valueSelectionBox.addItem(WordItem.EMPTY);
            valueSelectionBox.addItems(values);
        }
        valueSelectionBox.setValue(selected);
    }

    /// @param selected The value to show as chosen.
    public void setSelectedValue(final WordItem selected) {
        this.valueSelectionBox.setValue(selected);
    }

    /// @return The value chosen or typed.
    public String getSelectedValue() {
        return valueSelectionBox.getText();
    }

    /// @param allowTextEntry Whether a value not in the list can be typed.
    public void setAllowTextEntry(final boolean allowTextEntry) {
        this.allowTextEntry = allowTextEntry;
        this.valueSelectionBox.setAllowTextEntry(allowTextEntry);
    }

    /// Shows one list to choose a value from, or a picker of several values.
    ///
    /// @param multiSelect Whether several values can be chosen.
    public void setMultiSelect(final boolean multiSelect) {
        valueSelectionBox.setVisible(!multiSelect);
        valuesBox.setVisible(multiSelect);
    }

    /// Shows the values ticked when several can be chosen, joined with commas.
    ///
    /// @param words The values, in the list's order.
    public void setSelectedValues(final List<String> words) {
        valuesText = String.join(", ", NullSafe.list(words));
        valuesBox.setText(valuesText);
        updateValuesName();
    }

    /// @return The picker of several values, for the chooser to open below.
    public Element getValuesElement() {
        return valuesBox.getButtonElement();
    }

    /// Names the list, and the picker of several values, for screen readers.
    ///
    /// @param name The list's name: the pane's name.
    public void setAccessibleName(final String name) {
        accessibleName = name;
        AccessibleName.set(valueSelectionBox, name);
        updateValuesName();
    }

    /// @param onValueChange Given the value the user chooses or types.
    public void setOnValueChange(final Consumer<WordItem> onValueChange) {
        this.onValueChange = onValueChange;
    }

    /// @param onOpenValues Run when the user asks to choose several values (a click, Enter or
    ///                     Space on the picker).
    public void setOnOpenValues(final Runnable onOpenValues) {
        valuesBox.setOnOpen(onOpenValues);
    }

    // The picker's name replaces its text, so it says the values too, e.g. "Colour, red, blue"
    private void updateValuesName() {
        if (NullSafe.isBlankString(accessibleName)) {
            valuesBox.setAriaLabel(null);
        } else if (valuesText.isEmpty()) {
            valuesBox.setAriaLabel(accessibleName);
        } else {
            valuesBox.setAriaLabel(accessibleName + ", " + valuesText);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ListInput> {

    }
}
