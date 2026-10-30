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

package stroom.processor.client.component;

import stroom.util.shared.time.Days;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DaysOfWeek;
import stroom.widget.datepicker.client.component.TimeBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Edit Period dialog's content: when a processor profile's period applies, and the
/// threads processing may use then.
public class ProfilePeriodEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DaysOfWeek days;
    @UiField
    TimeBox startTime;
    @UiField
    TimeBox endTime;
    @UiField
    TickBox limitNodeThreads;
    @UiField
    ValueSpinner maxNodeThreads;
    @UiField
    TickBox limitClusterThreads;
    @UiField
    ValueSpinner maxClusterThreads;

    /// Makes the content.
    public ProfilePeriodEditor() {
        initWidget(BINDER.createAndBindUi(this));
        limitNodeThreads.setOnChange(value -> onLimitNodeThreads());
        limitClusterThreads.setOnChange(value -> onLimitClusterThreads());
    }


    /// Puts the focus in the start time.
    public void focus() {
        startTime.focus();
    }

    /// @return The days ticked.
    public Days getDays() {
        return days.getValue();
    }

    /// @param days The days the period applies on.
    public void setDays(final Days days) {
        this.days.setValue(days);
    }

    /// @return The start time, for its controller to attach to.
    public TimeBox getStartTime() {
        return startTime;
    }

    /// @return The end time, for its controller to attach to.
    public TimeBox getEndTime() {
        return endTime;
    }

    /// @return Whether each node's threads are limited.
    public boolean isLimitNodeThreads() {
        return limitNodeThreads.getValue();
    }

    /// @param limitNodeThreads Whether each node's threads are limited.
    public void setLimitNodeThreads(final boolean limitNodeThreads) {
        this.limitNodeThreads.setValue(limitNodeThreads);
        maxNodeThreads.setEnabled(limitNodeThreads);
    }

    /// @return The most threads each node may use.
    public int getMaxNodeThreads() {
        return maxNodeThreads.getIntValue();
    }

    /// @param maxNodeThreads The most threads each node may use.
    public void setMaxNodeThreads(final int maxNodeThreads) {
        this.maxNodeThreads.setValue(maxNodeThreads);
    }

    /// @return Whether the cluster's threads are limited.
    public boolean isLimitClusterThreads() {
        return limitClusterThreads.getValue();
    }

    /// @param limitTotalClusterThreads Whether the cluster's threads are limited.
    public void setLimitClusterThreads(final boolean limitTotalClusterThreads) {
        this.limitClusterThreads.setValue(limitTotalClusterThreads);
        maxClusterThreads.setEnabled(limitTotalClusterThreads);
    }

    /// @return The most threads the cluster may use.
    public int getMaxClusterThreads() {
        return maxClusterThreads.getIntValue();
    }

    /// @param totalClusterThreads The most threads the cluster may use.
    public void setMaxClusterThreads(final int totalClusterThreads) {
        this.maxClusterThreads.setValue(totalClusterThreads);
    }

    private void onLimitNodeThreads() {
        maxNodeThreads.setEnabled(limitNodeThreads.getValue());
    }

    private void onLimitClusterThreads() {
        maxClusterThreads.setEnabled(limitClusterThreads.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProfilePeriodEditor> {

    }
}
