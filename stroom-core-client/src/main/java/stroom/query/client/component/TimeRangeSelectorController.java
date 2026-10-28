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

import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;

/// Opens the Time Range dialog below the [TimeRangeSelector] it is attached to, and puts the range
/// chosen in it.
///
/// ```java
/// // A template declares the selector; its screen's controller attaches one of these to it
/// make(timeRangeSelectorControllerProvider).attach(view.getTimeRangeSelector());
/// ```
public class TimeRangeSelectorController extends Controller {

    private final Provider<TimeRangePickerController> timeRangePickerProvider;

    /// Made by GIN; [#attach(TimeRangeSelector)] gives it its selector.
    @Inject
    public TimeRangeSelectorController(final Provider<TimeRangePickerController> timeRangePickerProvider) {
        this.timeRangePickerProvider = timeRangePickerProvider;
    }

    /// Attaches this to the selector whose requests to open the dialog it answers.
    ///
    /// @param selector The selector, e.g. one a template declares.
    /// @return This, for its owner to keep.
    public TimeRangeSelectorController attach(final TimeRangeSelector selector) {
        Objects.requireNonNull(selector, "selector");
        selector.setOnOpenPicker(() -> timeRangePickerProvider.get().edit(this,
                selector.getValue(),
                selector.getElement(),
                timeRange -> selector.setValue(timeRange, true)));
        return this;
    }
}
