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

import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;

/// Opens the "Set Date And Time" dialog for the [DateTimeBox] it is attached to, and puts the time
/// chosen in it.
///
/// ```java
/// // A template declares the box; its screen's controller attaches one of these to it
/// make(dateTimeBoxControllerProvider).attach(view.getExpiryTime());
/// ```
public class DateTimeBoxController extends Controller {

    private final Provider<DateTimePickerController> dateTimePickerProvider;
    private DateTimeBox box;

    /// Made by GIN; [#attach(DateTimeBox)] gives it its box.
    ///
    /// @param dateTimePickerProvider Makes the date time picker.
    @Inject
    public DateTimeBoxController(final Provider<DateTimePickerController> dateTimePickerProvider) {
        this.dateTimePickerProvider = dateTimePickerProvider;
    }

    /// Attaches this to the box whose requests to open the dialog it answers.
    ///
    /// @param box The box, e.g. one a template declares.
    /// @return This, for its owner to keep.
    public DateTimeBoxController attach(final DateTimeBox box) {
        this.box = Objects.requireNonNull(box, "box");
        box.setOnOpenPicker(this::openPicker);
        return this;
    }

    private void openPicker() {
        final Long shown = box.getValue();
        dateTimePickerProvider.get().edit(this, box.getPickerStartTime(), chosen -> {
            if (!Objects.equals(shown, chosen)) {
                box.setValue(chosen, true);
            }
        });
    }
}
