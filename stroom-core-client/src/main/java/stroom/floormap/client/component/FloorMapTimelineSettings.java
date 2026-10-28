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

package stroom.floormap.client.component;

import stroom.floormap.client.FloorMapAria;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The timeline settings popup's content, which its [FloorMapTimelineSettingsController] shows:
/// start and end date-time boxes that constrain the visible time range, a loop playback tick box,
/// and a "Show All" button that resets the range to cover all the data. Playback speed is not set
/// here: it has its own menu, opened from the speed badge on the timeline.
public class FloorMapTimelineSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DateTimeBox startDateTimeBox;
    @UiField
    DateTimeBox endDateTimeBox;
    @UiField
    TickBox loopCheckBox;
    @UiField
    Button showAllButton;

    private Runnable onShowAll = () -> {
    };
    private Runnable onStartChange = () -> {
    };
    private Runnable onEndChange = () -> {
    };

    /// Makes the popup's content.
    public FloorMapTimelineSettings() {
        initWidget(BINDER.createAndBindUi(this));
        // The visible "Start Date" and "End Date" text is a plain Label beside each box, not a
        // <label for>, and DateTimeBox is a composite whose root is a div, so there is nothing for
        // a `for` to reach even if it were one. Name them directly instead
        FloorMapAria.group(startDateTimeBox, "Timeline start date and time");
        FloorMapAria.group(endDateTimeBox, "Timeline end date and time");
        showAllButton.setOnClick(() -> onShowAll.run());
        startDateTimeBox.addValueChangeHandler(e -> onStartChange.run());
        endDateTimeBox.addValueChangeHandler(e -> onEndChange.run());
    }

    /// @return The start date and time box, for a controller to open its dialog.
    public DateTimeBox getStartBox() {
        return startDateTimeBox;
    }

    /// @return The end date and time box, for a controller to open its dialog.
    public DateTimeBox getEndBox() {
        return endDateTimeBox;
    }

    /// @return Whether loop playback is ticked.
    public boolean isLoopPlayback() {
        return Boolean.TRUE.equals(loopCheckBox.getValue());
    }

    /// @param loop Whether loop playback is ticked.
    public void setLoopPlayback(final boolean loop) {
        loopCheckBox.setValue(loop);
    }

    /// @param startTime The start of the range, in milliseconds.
    public void setStartTime(final long startTime) {
        startDateTimeBox.setValue(startTime);
    }

    /// @param endTime The end of the range, in milliseconds.
    public void setEndTime(final long endTime) {
        endDateTimeBox.setValue(endTime);
    }

    /// @return The start of the range, in milliseconds, or 0 for none.
    public long getStartTime() {
        return getTimeOrZero(startDateTimeBox);
    }

    /// @return The end of the range, in milliseconds, or 0 for none.
    public long getEndTime() {
        return getTimeOrZero(endDateTimeBox);
    }

    /// @param enabled Whether "Show All" can be pressed.
    public void setShowAllEnabled(final boolean enabled) {
        showAllButton.setEnabled(enabled);
    }

    /// @param onShowAll Run when "Show All" is pressed.
    public void setOnShowAll(final Runnable onShowAll) {
        this.onShowAll = onShowAll;
    }

    /// @param onStartChange Run when the user changes the start of the range.
    public void setOnStartChange(final Runnable onStartChange) {
        this.onStartChange = onStartChange;
    }

    /// @param onEndChange Run when the user changes the end of the range.
    public void setOnEndChange(final Runnable onEndChange) {
        this.onEndChange = onEndChange;
    }

    private static long getTimeOrZero(final DateTimeBox box) {
        final Long value = box.getValue();
        return value != null
                ? value
                : 0L;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapTimelineSettings> {

    }
}
