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

package stroom.query.client.component;

import stroom.query.api.datasource.QueryOptionChoice;
import stroom.query.api.datasource.QueryOptionDefinition;
import stroom.query.api.datasource.QueryOptionType;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The query options dialog's content: a form group for each field-level query option the term's
/// field offers (a tick box, a choice or a number), made from the options' definitions as they are
/// set, so the controls are made in Java. Its controller is [QueryOptionsController].
public class QueryOptions extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel options;

    private final Map<String, OptionControl> controls = new LinkedHashMap<>();
    // The form group of each option, so that its label can be greyed out when it is disabled
    private final Map<String, FormGroup> formGroups = new HashMap<>();
    private final Map<String, QueryOptionDefinition> definitions = new HashMap<>();
    private final Set<String> disabledOptionIds = new HashSet<>();
    private Runnable onChange = () -> {
    };

    /// Makes a form with no options; [#setDefinitions(List)] adds them.
    public QueryOptions() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param onChange Run when the user changes an option's value.
    public void setOnChange(final Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /// Adds a form group for each option, in place of those shown.
    ///
    /// @param definitions The options, in the order they are shown.
    public void setDefinitions(final List<QueryOptionDefinition> definitions) {
        options.clear();
        controls.clear();
        formGroups.clear();
        disabledOptionIds.clear();
        this.definitions.clear();

        for (final QueryOptionDefinition definition : definitions) {
            final OptionControl control = createControl(definition);
            final FormGroup formGroup = new FormGroup();
            // A tick box names itself, and its group then shows the help beside it
            if (!QueryOptionType.BOOLEAN.equals(definition.getType())) {
                formGroup.setLabel(definition.getDisplayName());
            }
            if (definition.getDescription() != null) {
                formGroup.setHelpText(definition.getDescription());
            }
            formGroup.add(control.asWidget());
            options.add(formGroup);

            controls.put(definition.getId(), control);
            formGroups.put(definition.getId(), formGroup);
            this.definitions.put(definition.getId(), definition);
        }
    }

    /// Shows each option's value.
    ///
    /// @param values The value of each option by its id; an option without one shows its default.
    public void setValues(final Map<String, String> values) {
        controls.forEach((id, control) -> {
            final String value = values.get(id);
            control.setValue(value != null
                    ? value
                    : definitions.get(id).getDefaultValue());
        });
    }

    /// @return The value of each option, by its id.
    public Map<String, String> getValues() {
        final Map<String, String> values = new LinkedHashMap<>();
        controls.forEach((id, control) -> values.put(id, control.getValue()));
        return values;
    }

    /// Disables the options that don't apply given the other options' values, setting them back to
    /// their defaults, and enables the rest. A disabled option is still shown, greyed out.
    ///
    /// @param disabledOptionIds The ids of the options to disable.
    public void setDisabledOptions(final Set<String> disabledOptionIds) {
        this.disabledOptionIds.clear();
        this.disabledOptionIds.addAll(disabledOptionIds);
        controls.forEach((id, control) -> {
            final boolean disabled = disabledOptionIds.contains(id);
            if (disabled) {
                control.setValue(definitions.get(id).getDefaultValue());
            }
            control.setEnabled(!disabled);
            NullSafe.consume(formGroups.get(id), formGroup -> formGroup.setDisabled(disabled));
        });
    }

    /// Puts the focus on the first option that is enabled.
    public void focus() {
        controls.entrySet().stream()
                .filter(entry -> !disabledOptionIds.contains(entry.getKey()))
                .findFirst()
                .ifPresent(entry -> entry.getValue().focus());
    }

    private OptionControl createControl(final QueryOptionDefinition definition) {
        return switch (definition.getType()) {
            case BOOLEAN -> createTickBox(definition);
            case CHOICE -> createSelectionBox(definition);
            case INTEGER -> createSpinner(definition);
        };
    }

    private OptionControl createTickBox(final QueryOptionDefinition definition) {
        final TickBox tickBox = new TickBox();
        tickBox.setLabel(definition.getDisplayName());
        tickBox.setOnChange(ticked -> onChange.run());
        return new OptionControl() {
            @Override
            public String getValue() {
                return String.valueOf(Boolean.TRUE.equals(tickBox.getValue()));
            }

            @Override
            public void setValue(final String value) {
                tickBox.setValue(Boolean.valueOf(value));
            }

            @Override
            public void setEnabled(final boolean enabled) {
                tickBox.setEnabled(enabled);
            }

            @Override
            public void focus() {
                tickBox.focus();
            }

            @Override
            public Widget asWidget() {
                return tickBox;
            }
        };
    }

    private OptionControl createSelectionBox(final QueryOptionDefinition definition) {
        final SelectionBox<QueryOptionChoice> selectionBox = new SelectionBox<>();
        selectionBox.addStyleName("w-100");
        selectionBox.addItems(definition.getChoices());
        selectionBox.addValueChangeHandler(e -> onChange.run());
        return new OptionControl() {
            @Override
            public String getValue() {
                final QueryOptionChoice choice = selectionBox.getValue();
                return choice != null
                        ? choice.getValue()
                        : definition.getDefaultValue();
            }

            @Override
            public void setValue(final String value) {
                for (final QueryOptionChoice choice : definition.getChoices()) {
                    if (choice.getValue().equals(value)) {
                        selectionBox.setValue(choice);
                    }
                }
            }

            @Override
            public void setEnabled(final boolean enabled) {
                selectionBox.setEnabled(enabled);
            }

            @Override
            public void focus() {
                selectionBox.focus();
            }

            @Override
            public Widget asWidget() {
                return selectionBox;
            }
        };
    }

    private OptionControl createSpinner(final QueryOptionDefinition definition) {
        final ValueSpinner spinner = new ValueSpinner();
        if (definition.getMinValue() != null) {
            spinner.setMin(definition.getMinValue());
        }
        if (definition.getMaxValue() != null) {
            spinner.setMax(definition.getMaxValue());
        }
        spinner.setOnChange(value -> onChange.run());
        return new OptionControl() {
            @Override
            public String getValue() {
                return String.valueOf(spinner.getIntValue());
            }

            @Override
            public void setValue(final String value) {
                spinner.setValue(Long.valueOf(value));
            }

            @Override
            public void setEnabled(final boolean enabled) {
                spinner.setEnabled(enabled);
            }

            @Override
            public void focus() {
                spinner.focus();
            }

            @Override
            public Widget asWidget() {
                return spinner;
            }
        };
    }


    // --------------------------------------------------------------------------------


    /// Shows and edits the value of one option.
    private interface OptionControl {

        String getValue();

        void setValue(String value);

        void setEnabled(boolean enabled);

        void focus();

        Widget asWidget();
    }


    interface Binder extends UiBinder<Widget, QueryOptions> {

    }
}
