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

package stroom.widget.datepicker.client.component;

import stroom.util.shared.time.Time;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;

/// Opens the "Set Time" dialog for the [TimeBox] it is attached to, and puts the time chosen in it.
///
/// ```java
/// // A template declares the box; its screen's controller attaches one of these to it
/// make(timeBoxControllerProvider).attach(view.getStartTime());
/// ```
public class TimeBoxController extends Controller {

    private final Provider<TimePickerController> timePickerProvider;
    private TimeBox box;
    private boolean hourVisible = true;
    private boolean minuteVisible = true;
    private boolean secondVisible = true;

    /// Made by GIN; [#attach(TimeBox)] gives it its box.
    ///
    /// @param timePickerProvider Makes the time picker.
    @Inject
    public TimeBoxController(final Provider<TimePickerController> timePickerProvider) {
        this.timePickerProvider = timePickerProvider;
    }

    /// Attaches this to the box whose requests to open the dialog it answers.
    ///
    /// @param box The box, e.g. one a template declares.
    /// @return This, for its owner to keep.
    public TimeBoxController attach(final TimeBox box) {
        this.box = Objects.requireNonNull(box, "box");
        box.setOnOpenPicker(this::openPicker);
        return this;
    }

    /// @param visible Whether the dialog lets the hour be chosen.
    public void setHourVisible(final boolean visible) {
        this.hourVisible = visible;
    }

    /// @param visible Whether the dialog lets the minute be chosen.
    public void setMinuteVisible(final boolean visible) {
        this.minuteVisible = visible;
    }

    /// @param visible Whether the dialog lets the second be chosen.
    public void setSecondVisible(final boolean visible) {
        this.secondVisible = visible;
    }

    private void openPicker() {
        final Time time = box.getValue();
        final TimePickerController picker = timePickerProvider.get();
        picker.setHourVisible(hourVisible);
        picker.setMinuteVisible(minuteVisible);
        picker.setSecondVisible(secondVisible);
        picker.edit(this, time, chosen -> {
            if (!Objects.equals(time, chosen)) {
                box.setValue(chosen, true);
            }
        });
    }
}
