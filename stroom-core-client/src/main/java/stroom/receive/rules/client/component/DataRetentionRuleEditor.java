/*
 * Copyright 2017 Crown Copyright
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

package stroom.receive.rules.client.component;

import stroom.query.client.component.ExpressionEditor;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A data retention rule: its name, whether it keeps data for ever or for how long, and the
/// expression choosing the data it applies to.
public class DataRetentionRuleEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ExpressionEditor expressionEditor;
    @UiField
    TextBox name;
    @UiField
    TickBox forever;
    @UiField
    FormGroup retainLabel;
    @UiField
    ValueSpinner age;
    @UiField
    SelectionBox<TimeUnit> timeUnit;

    /// Makes the form, retaining for 1 minute until a rule is shown.
    public DataRetentionRuleEditor() {
        initWidget(BINDER.createAndBindUi(this));
        forever.setOnChange(value -> onForeverChange());

        age.setValue(1);

        timeUnit.addItem(TimeUnit.MINUTES);
        timeUnit.addItem(TimeUnit.HOURS);
        timeUnit.addItem(TimeUnit.DAYS);
        timeUnit.addItem(TimeUnit.WEEKS);
        timeUnit.addItem(TimeUnit.MONTHS);
        timeUnit.addItem(TimeUnit.YEARS);
    }

    /// @return The expression's editor, which its controller attaches the editor's controller to.
    public ExpressionEditor getExpressionEditor() {
        return expressionEditor;
    }

    /// Puts the keyboard focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The name typed.
    public String getName() {
        return name.getText();
    }

    /// @param name The rule's name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return Whether Keep Forever is ticked.
    public boolean isForever() {
        return forever.getValue();
    }

    /// @param forever Whether the rule keeps data for ever; Retain For is disabled if so.
    public void setForever(final boolean forever) {
        this.forever.setValue(forever);
        setRetainForEnabled(!forever);
    }

    /// @return How many of the time unit to retain data for.
    public int getAge() {
        return age.getIntValue();
    }

    /// @param age How many of the time unit to retain data for.
    public void setAge(final int age) {
        this.age.setValue(age);
    }

    /// @return The unit of how long to retain data.
    public TimeUnit getTimeUnit() {
        return timeUnit.getValue();
    }

    /// @param timeUnit The unit of how long to retain data.
    public void setTimeUnit(final TimeUnit timeUnit) {
        this.timeUnit.setValue(timeUnit);
    }

    private void onForeverChange() {
        setRetainForEnabled(!isForever());
    }

    private void setRetainForEnabled(final boolean enabled) {
        retainLabel.setStyleName("section--disabled", !enabled);
        age.setEnabled(enabled);
        timeUnit.setEnabled(enabled);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataRetentionRuleEditor> {

    }
}
