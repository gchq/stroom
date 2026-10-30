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

package stroom.schedule.client.component;

import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.IconBox;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;

/// A text field for a schedule's expression (a frequency such as `10m`, or a cron expression), with a
/// clock icon at its end. Enter or Alt+Down in the field, or a click on the icon, asks to open the
/// Change Schedule dialog ([#setOnOpenPicker(Runnable)]), where the schedule's type can be changed
/// too; a [ScheduleBoxController] attached to it opens it, and checks the schedule with the server
/// when the box asks ([#setOnCheck(Runnable)]), marking it invalid ([#setInvalid(boolean)]) if the
/// server rejects it. The field says it opens a dialog (`aria-haspopup="dialog"`); the icon is a
/// picture, hidden from screen readers.
///
/// The BEM block is `schedule-box` (`schedule-box.css`); its field is a text field. Its markup is
/// `ScheduleBox.ui.xml`.
public class ScheduleBox
        extends Component
        implements Focus, HasValueChangeHandlers<Schedule> {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    IconBox iconBox;
    @UiField
    TextBox textBox;

    private Schedule value = Schedule
            .builder()
            .type(ScheduleType.CRON)
            .build();
    private boolean enabled = true;
    private Runnable onOpenPicker;
    private Runnable onCheck;
    private Runnable onChange;

    /// Makes a box with an empty cron schedule.
    public ScheduleBox() {
        initWidget(BINDER.createAndBindUi(this));
        iconBox.setOnIconClick(this::openPicker);
        textBox.addKeyDownHandler(event -> {
            // As a date-time box: Enter or Alt+Down opens the dialog
            final int keyCode = event.getNativeKeyCode();
            if ((KeyCodes.KEY_ENTER == keyCode && !event.isAnyModifierKeyDown())
                || (KeyCodes.KEY_DOWN == keyCode && event.isAltKeyDown())) {
                event.preventDefault();
                openPicker();
            }
        });
        textBox.addBlurHandler(event -> check());
        textBox.addFocusHandler(event -> FieldValidity.setInvalid(textBox.getElement(), false));
    }

    /// @param onOpenPicker Run when the user asks to change the schedule in the dialog, unless the
    ///                     box is read only or disabled.
    public void setOnOpenPicker(final Runnable onOpenPicker) {
        this.onOpenPicker = onOpenPicker;
    }

    /// @param onCheck Run when the schedule should be checked: when the box is left, enabled or
    ///                given a schedule.
    public void setOnCheck(final Runnable onCheck) {
        this.onCheck = onCheck;
    }

    /// @param onChange Run when the user changes the schedule with the dialog.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param invalid Whether the schedule is shown as invalid, e.g. as the server rejected it.
    public void setInvalid(final boolean invalid) {
        FieldValidity.setInvalid(textBox.getElement(), invalid);
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    /// @param name The text box's form name.
    public void setName(final String name) {
        textBox.setName(name);
    }

    /// Disables the whole box: the text and the icon that opens the schedule dialog.
    ///
    /// @param enabled Whether the schedule can be changed.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        textBox.setEnabled(enabled);
        iconBox.setReadOnly(!enabled || isReadOnly());
        updateReadOnly();
        check();
    }

    /// Makes the schedule read only: it can be read, selected and copied, and stays in the tab
    /// order, but can't be changed, and its dialog doesn't open. It looks like the normal field
    /// with its value greyed.
    ///
    /// @param readOnly Whether the schedule is read only.
    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        iconBox.setReadOnly(!enabled || readOnly);
        updateReadOnly();
    }

    /// @return Whether the schedule can be changed.
    public boolean isEnabled() {
        return enabled;
    }

    /// @return Whether the schedule is an instant one, which has no expression.
    public boolean isInstant() {
        return ScheduleType.INSTANT.equals(value.getType());
    }

    /// @return The schedule, as last given or checked.
    public Schedule getValue() {
        return value;
    }

    /// @return The schedule with the expression typed in the field, which may not have been
    ///         checked yet.
    public Schedule getEnteredSchedule() {
        return value.copy().expression(textBox.getValue()).build();
    }

    /// @param value The schedule to show; null leaves the box as it is.
    public void setValue(final Schedule value) {
        setValue(value, false);
    }

    /// @param value      The schedule to show; null leaves the box as it is.
    /// @param fireEvents Whether to tell its listeners, as when the user changes it.
    public void setValue(final Schedule value, final boolean fireEvents) {
        if (value != null) {
            setCheckedSchedule(value);
            textBox.setValue(value.getExpression() != null
                    ? value.getExpression()
                    : "");
            if (fireEvents) {
                ValueChangeEvent.fire(this, value);
                if (onChange != null) {
                    onChange.run();
                }
            }
            check();
        }
    }

    /// Takes the schedule as the server read it, without changing the text, e.g. its type.
    ///
    /// @param schedule The schedule.
    public void setCheckedSchedule(final Schedule schedule) {
        this.value = schedule;
        updateReadOnly();
    }

    /// Kept beside [#setOnChange(Runnable)] until its callers have moved.
    ///
    /// @param handler Told when the user changes the schedule with the dialog.
    /// @return The handler's registration.
    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<Schedule> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    private void openPicker() {
        if (enabled && !isReadOnly() && onOpenPicker != null) {
            onOpenPicker.run();
        }
    }

    // Without a controller to check it, it isn't shown as invalid
    private void check() {
        if (onCheck != null) {
            onCheck.run();
        } else {
            setInvalid(false);
        }
    }

    // An instant schedule has no expression to type, so the text is read-only, but it stays
    // focusable and Enter (or the icon) still opens the dialog to change the type.
    private void updateReadOnly() {
        textBox.setReadOnly(isReadOnly() || (enabled && isInstant()));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<IconBox, ScheduleBox> {

    }
}
