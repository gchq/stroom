/*
 * Copyright 2024 Crown Copyright
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

package stroom.schedule.client;

import stroom.item.client.EventBinder;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.job.shared.ScheduleRestriction;
import stroom.job.shared.ScheduledTimes;
import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.form.client.FieldValidity;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.function.Consumer;

public class ScheduleBox
        extends Composite
        implements Focus, HasValueChangeHandlers<Schedule> {

    private Provider<SchedulePopup> schedulePresenterProvider;
    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private Schedule value = Schedule
            .builder()
            .type(ScheduleType.CRON)
            .build();
    private SchedulePopup popup;
    private boolean enabled = true;
    private boolean readOnly;
    private ScheduleRestriction scheduleRestriction = new ScheduleRestriction(false, true, true);

    private Consumer<Consumer<ScheduleReferenceTime>> scheduleReferenceTimeConsumer = (consumer) ->
            consumer.accept(null);

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyDownHandler(event -> {
                final int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode) {
                    showPopup();
                }
            }));
            registerHandler(textBox.addBlurHandler(event -> validate()));
            registerHandler(textBox.addFocusHandler(event ->
                    FieldValidity.setInvalid(textBox.getElement(), false)));
        }
    };

    public void validate() {
        validate(scheduledTimes -> {
        });
    }

    public void validate(final Consumer<ScheduledTimes> consumer) {
        final Schedule schedule = value.copy().expression(textBox.getValue()).build();
        final SchedulePopup popup = getSchedulePresenter();
        if (popup != null) {
            schedulePresenterProvider.get().validate(schedule, scheduleRestriction, scheduledTimes -> {
                if (isEnabled() && !isInstant() && (scheduledTimes == null || scheduledTimes.isError())) {
                    FieldValidity.setInvalid(textBox.getElement(), true);
                } else {
                    FieldValidity.setInvalid(textBox.getElement(), false);
                }
                if (scheduledTimes != null && scheduledTimes.getSchedule() != null) {
                    value = scheduledTimes.getSchedule();
                    updateReadOnly();
                }
                consumer.accept(scheduledTimes);
            });
        } else {
            FieldValidity.setInvalid(textBox.getElement(), false);
            consumer.accept(new ScheduledTimes(schedule, null,
                    "No schedule presenter has been configured"));
        }
    }

    public ScheduleBox() {
        textBox = new TextBox();
        textBox.addStyleName("ScheduleBox-textBox stroom-control allow-focus");

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("ScheduleBox");
        svgIconBox.setWidget(textBox, SvgImage.HISTORY);

        initWidget(svgIconBox);
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    public void setSchedulePresenterProvider(final Provider<SchedulePopup> schedulePresenterProvider) {
        this.schedulePresenterProvider = schedulePresenterProvider;
    }

    private void showPopup() {
        if (!enabled || readOnly) {
            return;
        }
        final SchedulePopup popup = getSchedulePresenter();
        if (popup != null) {
            value = value.copy().expression(textBox.getValue()).build();
            popup.validate(value,
                    new ScheduleRestriction(true, true, true),
                    scheduledTimes -> {
                        if (scheduledTimes != null) {
                            this.value = scheduledTimes.getSchedule();
                        }
                        scheduleReferenceTimeConsumer.accept(scheduleReferenceTime -> {
                            popup.setScheduleRestriction(scheduleRestriction);
                            popup.setSchedule(value, scheduleReferenceTime);
                            popup.show(schedule -> {
                                if (!Objects.equals(value, schedule)) {
                                    setValue(schedule, true);
                                }
                            });
                        });
                    });
        }
    }

    private SchedulePopup getSchedulePresenter() {
        if (popup == null && schedulePresenterProvider != null) {
            popup = schedulePresenterProvider.get();
        }
        return popup;
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    public void setName(final String name) {
        textBox.setName(name);
    }

    /// Disables the whole box: the text and the icon that opens the schedule dialog.
    ///
    /// @param enabled Whether the schedule can be changed.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        textBox.setEnabled(enabled);
        svgIconBox.setReadonly(!enabled || readOnly);
        updateReadOnly();
        validate();
    }

    /// Makes the schedule read only: it can be read, selected and copied, and stays in the tab
    /// order, but can't be changed, and its dialog doesn't open. It looks like the normal field
    /// with its value greyed.
    ///
    /// @param readOnly Whether the schedule is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        svgIconBox.setReadonly(!enabled || readOnly);
        updateReadOnly();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isInstant() {
        return ScheduleType.INSTANT.equals(value.getType());
    }

    // An instant schedule has no expression to type, so the text is read-only, but it stays
    // focusable and Enter (or the icon) still opens the dialog to change the type.
    private void updateReadOnly() {
        textBox.setReadOnly(readOnly || (enabled && isInstant()));
    }

    public Schedule getValue() {
        return value;
    }

    public void setValue(final Schedule value) {
        setValue(value, false);
    }

    public void setValue(final Schedule value, final boolean fireEvents) {
        if (value != null) {
            this.value = value;
            if (value.getExpression() != null) {
                textBox.setValue(value.getExpression());
            } else {
                textBox.setValue("");
            }

            updateReadOnly();

            if (fireEvents) {
                ValueChangeEvent.fire(this, value);
            }

            validate();
        }
    }

    public void setScheduleRestriction(final ScheduleRestriction scheduleRestriction) {
        this.scheduleRestriction = scheduleRestriction;
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<Schedule> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setScheduleReferenceTimeConsumer(
            final Consumer<Consumer<ScheduleReferenceTime>> scheduleReferenceTimeConsumer) {
        this.scheduleReferenceTimeConsumer = scheduleReferenceTimeConsumer;
    }
}
