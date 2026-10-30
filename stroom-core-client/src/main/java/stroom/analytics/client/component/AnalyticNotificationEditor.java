/*
 * Copyright 2023 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.NotificationDestinationType;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.component.client.Component;
import stroom.widget.duration.client.DurationPicker;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// An analytic's notification: its destination type and destination, whether it is sent, and the
/// limit on how many are sent.
public class AnalyticNotificationEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox enabled;
    @UiField
    TickBox limitNotifications;
    @UiField
    ValueSpinner maxNotifications;
    @UiField
    DurationPicker resumeAfter;
    @UiField
    SelectionBox<NotificationDestinationType> destinationType;
    @UiField
    SimplePanel destination;

    private Runnable onChange = () -> {
    };

    /// Makes the form, enabled, sending at most one notification if limited, offering both
    /// destination types.
    public AnalyticNotificationEditor() {
        initWidget(BINDER.createAndBindUi(this));
        enabled.setOnChange(value -> onEnabled());
        limitNotifications.setOnChange(value -> onLimitNotifications());
        maxNotifications.setOnChange(value -> onMaxNotifications());
        resumeAfter.addValueChangeHandler(this::onResumeAfter);
        destinationType.addValueChangeHandler(this::onDestinationType);
        maxNotifications.setValue(1);
        destinationType.addItem(NotificationDestinationType.STREAM);
        destinationType.addItem(NotificationDestinationType.EMAIL);
    }

    /// @return Whether the notification is sent.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// @param enabled Whether the notification is sent.
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    /// @return Whether sending stops after the maximum.
    public boolean isLimitNotifications() {
        return limitNotifications.getValue();
    }

    /// @param limitNotifications Whether sending stops after the maximum.
    public void setLimitNotifications(final boolean limitNotifications) {
        this.limitNotifications.setValue(limitNotifications);
    }

    /// @return The most notifications sent before sending stops.
    public int getMaxNotifications() {
        return maxNotifications.getIntValue();
    }

    /// @param maxNotifications The most notifications sent before sending stops.
    public void setMaxNotifications(final int maxNotifications) {
        this.maxNotifications.setValue(maxNotifications);
    }

    /// @return How long after the last notification sending starts again.
    public SimpleDuration getResumeAfter() {
        return resumeAfter.getValue();
    }

    /// @param resumeAfter How long after the last notification sending starts again.
    public void setResumeAfter(final SimpleDuration resumeAfter) {
        this.resumeAfter.setValue(resumeAfter);
    }

    /// @return Where notifications are sent.
    public NotificationDestinationType getDestinationType() {
        return destinationType.getValue();
    }

    /// @param destinationType Where notifications are sent.
    public void setDestinationType(final NotificationDestinationType destinationType) {
        this.destinationType.setValue(destinationType);
    }

    /// @param destination The destination's settings, for the destination type chosen.
    public void setDestination(final IsWidget destination) {
        this.destination.setWidget(destination);
    }

    /// @param onChange Called when any setting changes.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void onEnabled() {
        onChange.run();
    }

    private void onLimitNotifications() {
        onChange.run();
    }

    private void onMaxNotifications() {
        onChange.run();
    }

    private void onResumeAfter(final ValueChangeEvent<SimpleDuration> event) {
        onChange.run();
    }

    private void onDestinationType(final ValueChangeEvent<NotificationDestinationType> event) {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticNotificationEditor> {

    }
}
