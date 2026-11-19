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

import stroom.dashboard.client.vis.HasReadAndWrite;
import stroom.dashboard.client.vis.VisFieldOption;
import stroom.dashboard.client.vis.VisInputChoices;
import stroom.util.client.JSONUtil;
import stroom.visualisation.shared.VisInput;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.Layer;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.BaseSelectionBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// A tab of a visualisation's settings: a form group for each control the visualisation declares
/// (a field, a selection, text, a number, a date or a tick box), made from its JSON as it is read,
/// so the controls are made in Java. Its markup is `DynamicSettingsPane.ui.xml`.
public class DynamicSettingsPane extends Component implements Layer, HasReadAndWrite<JSONObject>, Focus {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel outer;
    private final List<FieldControl> fieldControls = new ArrayList<>();
    // Each control's read-only setter
    private final List<Consumer<Boolean>> readOnlySetters = new ArrayList<>();
    private Runnable onChange;
    // The table's columns the field controls can choose
    private List<VisFieldOption> fieldOptions = Collections.emptyList();
    private final List<HasReadAndWrite<JSONObject>> controls = new ArrayList<>();
    private Consumer<DateTimeBox> onDateTimeBoxAdded = box -> {
    };

    /// Makes a pane with no controls; [#addControls] adds them.
    public DynamicSettingsPane() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param onChange Run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        readOnlySetters.forEach(setter -> setter.accept(readOnly));
    }

    /// @param onDateTimeBoxAdded Given each date-time box the pane adds, for its owner's controller
    ///                           to attach a [stroom.widget.datepicker.client.component.DateTimeBoxController]
    ///                           to, which opens its dialog. Set before [#addControls].
    public void setOnDateTimeBoxAdded(final Consumer<DateTimeBox> onDateTimeBoxAdded) {
        this.onDateTimeBoxAdded = onDateTimeBoxAdded;
    }

    @Override
    public void focus() {
        if (controls.size() > 0) {
            controls.get(0).focus();
        }
    }

    /// Adds a form group for each control.
    ///
    /// @param controls The controls of a tab of the visualisation's settings.
    /// @param inputs   The visualisation's typed inputs by id (see [VisInput]), which describe its
    ///                 field controls: a field control with an input offers only the columns it
    ///                 takes, says what it is for and whether it is required, and says what is wrong
    ///                 when the column chosen isn't one it takes. Empty if it declares none.
    public void addControls(final JSONArray controls, final Map<String, VisInput> inputs) {
        for (int i = 0; i < controls.size(); i++) {
            final JSONObject control = JSONUtil.getObject(controls.get(i));

            final String label = JSONUtil.getString(control.get("label"));
            final Widget widget = createWidget(control);
            watch(widget);

            final FormGroup formGroup = new FormGroup();
            formGroup.setLabel(label);
            formGroup.add(widget);

            if ("field".equals(JSONUtil.getString(control.get("type")))) {
                @SuppressWarnings("unchecked") final SelectionBox<String> box = (SelectionBox<String>) widget;
                final VisInput input = inputs.get(JSONUtil.getString(control.get("id")));
                final FieldControl fieldControl = new FieldControl(box, formGroup, input);
                fieldControls.add(fieldControl);
                if (input != null) {
                    formGroup.setRequired(input.isRequired());
                    formGroup.setHelpText(VisInputChoices.helpText(input));
                }
                box.addValueChangeHandler(event -> fieldControl.check(fieldOptions));
            }

            outer.add(formGroup);
        }
    }

    // Tells the owner of changes to the control, and makes it read only with the pane
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void watch(final Widget widget) {
        if (widget instanceof HasValueChangeHandlers) {
            ((HasValueChangeHandlers) widget).addValueChangeHandler(event -> {
                if (onChange != null) {
                    onChange.run();
                }
            });
        }
        final Consumer<Boolean> readOnlySetter;
        if (widget instanceof Component) {
            readOnlySetter = ((Component) widget)::setReadOnly;
        } else if (widget instanceof BaseSelectionBox) {
            readOnlySetter = ((BaseSelectionBox<?, ?>) widget)::setReadOnly;
        } else if (widget instanceof ValueSpinner) {
            readOnlySetter = ((ValueSpinner) widget)::setReadOnly;
        } else if (widget instanceof TickBox) {
            readOnlySetter = ((TickBox) widget)::setReadOnly;
        } else if (widget instanceof TextBox) {
            readOnlySetter = ((TextBox) widget)::setReadOnly;
        } else {
            readOnlySetter = readOnly -> {
            };
        }
        readOnlySetter.accept(isReadOnly());
        readOnlySetters.add(readOnlySetter);
    }

    private Widget createWidget(final JSONObject control) {
        final String id = JSONUtil.getString(control.get("id"));
        final String type = JSONUtil.getString(control.get("type"));
        final String defaultValue = JSONUtil.getString(control.get("defaultValue"));

        Widget widget = null;

        if ("field".equals(type)) {
            widget = createSelectionBox(id);

        } else if ("selection".equals(type)) {
            final SelectionBox<String> ctrl = createSelectionBox(id);
            widget = ctrl;

            final String[] values = JSONUtil.getStrings(control.get("values"));

            if (values != null) {
                for (final String val : values) {
                    ctrl.addItem(val);
                }
            }

            if (defaultValue != null) {
                ctrl.setValue(defaultValue);
            }
        } else if ("text".equals(type)) {
            final TextBox ctrl = createTextBox(id);
            widget = ctrl;

            if (defaultValue != null) {
                ctrl.setText(defaultValue);
            }
        } else if ("date".equals(type)) {
            final DateTimeBox ctrl = createDateBox(id);
            widget = ctrl;

            if (defaultValue != null) {
                ctrl.setText(defaultValue);
            }
        } else if ("number".equals(type)) {
            final ValueSpinner ctrl = createNumberBox(id);
            widget = ctrl;

            final Long min = getLong(JSONUtil.getString(control.get("min")));
            final Long max = getLong(JSONUtil.getString(control.get("max")));
            final Long minStep = getLong(JSONUtil.getString(control.get("minStep")));
            final Long maxStep = getLong(JSONUtil.getString(control.get("maxStep")));

            if (min != null) {
                ctrl.setMin(min);
            }
            if (max != null) {
                ctrl.setMax(max);
            }
            if (minStep != null) {
                ctrl.setMinStep(minStep.intValue());
            }
            if (maxStep != null) {
                ctrl.setMaxStep(maxStep.intValue());
            }

            if (defaultValue != null) {
                final Long l = getLong(defaultValue);
                if (l != null) {
                    ctrl.setValue(l);
                }
            }
        } else if ("boolean".equals(type)) {
            final TickBox ctrl = createBooleanBox(id);
            widget = ctrl;

            if (defaultValue != null) {
                final Boolean b = getBoolean(defaultValue);
                if (b != null) {
                    ctrl.setValue(b);
                }
            }
        }

        return widget;
    }

    private SelectionBox<String> createSelectionBox(final String id) {
        final SelectionBox<String> ctrl = new SelectionBox<>();
        ctrl.addStyleName("w-100");

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void focus() {
                ctrl.focus();
            }

            @Override
            public void read(final JSONObject settings) {
                final String val = JSONUtil.getString(settings.get(id));
                if (val != null) {
                    ctrl.setValue(val);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final String selected = ctrl.getValue();
                if (selected != null) {
                    settings.put(id, new JSONString(selected));
                }
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private TextBox createTextBox(final String id) {
        final TextBox ctrl = new TextBox();
        ctrl.addStyleName("w-100");

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void focus() {
                ctrl.setFocus(true);
            }

            @Override
            public void read(final JSONObject settings) {
                final String val = JSONUtil.getString(settings.get(id));
                if (val != null) {
                    ctrl.setText(val);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final String val = ctrl.getText();
                if (val != null) {
                    settings.put(id, new JSONString(val.trim()));
                }
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private DateTimeBox createDateBox(final String id) {
        // The text is the vis's to read, so any is allowed; the dialog puts an ISO time in it
        final DateTimeBox ctrl = new DateTimeBox();
        ctrl.setExpressionsAllowed(true);
        ctrl.addStyleName("w-100");
        onDateTimeBoxAdded.accept(ctrl);

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void focus() {
                ctrl.focus();
            }

            @Override
            public void read(final JSONObject settings) {
                ctrl.setText(JSONUtil.getString(settings.get(id)));
            }

            @Override
            public void write(final JSONObject settings) {
                final String val = ctrl.getText();
                if (val != null) {
                    settings.put(id, new JSONString(val.trim()));
                }
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private ValueSpinner createNumberBox(final String id) {
        final ValueSpinner ctrl = new ValueSpinner();
        ctrl.addStyleName("w-100");

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void focus() {
                ctrl.focus();
            }

            @Override
            public void read(final JSONObject settings) {
                final Long l = getLong(JSONUtil.getString(settings.get(id)));
                if (l != null) {
                    ctrl.setValue(l);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final long l = ctrl.getIntValue();
                settings.put(id, new JSONString(Long.toString(l)));
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private TickBox createBooleanBox(final String id) {
        final TickBox ctrl = new TickBox();

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void focus() {
                ctrl.setFocus(true);
            }

            @Override
            public void read(final JSONObject settings) {
                final Boolean b = getBoolean(JSONUtil.getString(settings.get(id)));
                if (b != null) {
                    ctrl.setValue(b);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final boolean b = ctrl.getValue();
                settings.put(id, new JSONString(Boolean.toString(b)));
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private Long getLong(final String val) {
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (final RuntimeException e) {
                // Ignore
            }
        }
        return null;
    }

    private Boolean getBoolean(final String val) {
        if (val != null) {
            try {
                return Boolean.parseBoolean(val);
            } catch (final RuntimeException e) {
                // Ignore
            }
        }
        return null;
    }

    /// Sets the columns the field controls can choose, each offering those its input takes.
    ///
    /// @param fieldOptions The columns of the visualisation's table.
    public void setFields(final List<VisFieldOption> fieldOptions) {
        this.fieldOptions = fieldOptions;
        for (final FieldControl fieldControl : fieldControls) {
            fieldControl.setOptions(fieldOptions);
        }
    }

    @Override
    public void read(final JSONObject settings) {
        for (final HasReadAndWrite<JSONObject> control : controls) {
            control.read(settings);
        }
        // The chosen columns may not be offered yet, or be of a type their inputs don't take
        for (final FieldControl fieldControl : fieldControls) {
            fieldControl.setOptions(fieldOptions);
        }
    }

    @Override
    public void write(final JSONObject settings) {
        for (final HasReadAndWrite<JSONObject> control : controls) {
            control.write(settings);
        }
    }

    @Override
    public void setLayerVisible(final boolean fade, final boolean visible) {
        Layer.setLayerVisible(getWidget().getElement(), fade, visible);
    }

    @Override
    public void addLayer(final LayerContainer layerContainer) {
        layerContainer.add(this);
    }

    @Override
    public boolean removeLayer() {
        removeFromParent();
        return true;
    }

    @Override
    public void onResize() {
        // Nothing in it resizes
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DynamicSettingsPane> {

    }


    // --------------------------------------------------------------------------------


    // A field control, its form group, and the input the visualisation declares for it (or null)
    private static final class FieldControl {

        private final SelectionBox<String> box;
        private final FormGroup formGroup;
        private final VisInput input;

        private FieldControl(final SelectionBox<String> box, final FormGroup formGroup, final VisInput input) {
            this.box = box;
            this.formGroup = formGroup;
            this.input = input;
        }

        // Offers the columns the input takes, keeping the one chosen
        void setOptions(final List<VisFieldOption> options) {
            final String selected = box.getValue();
            box.clear();
            box.addItems(VisInputChoices.choices(input, options, selected));
            box.setValue(selected);
            check(options);
        }

        // Says what is wrong with the column chosen, if anything
        void check(final List<VisFieldOption> options) {
            final String problem = VisInputChoices.problem(input, options, box.getValue());
            if (problem == null) {
                formGroup.clearFeedback();
            } else {
                formGroup.setFeedback(problem);
            }
        }
    }
}
