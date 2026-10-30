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

package stroom.schedule.client.component;

import stroom.job.shared.ScheduleReferenceTime;
import stroom.job.shared.ScheduleRestriction;
import stroom.job.shared.ScheduledTimes;
import stroom.schedule.client.ScheduledTimeClient;
import stroom.schedule.client.component.SchedulePickerController;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.function.Consumer;

/// Checks the schedule in the [ScheduleBox] it is attached to with the server, marking the box
/// invalid if it is rejected, and opens the "Change Schedule" dialog for it.
///
/// ```java
/// // A template declares the box; its screen's controller attaches one of these to it
/// scheduleBox = make(scheduleBoxControllerProvider).attach(view.getScheduleBox());
/// scheduleBox.setScheduleRestriction(new ScheduleRestriction(false, false, true));
/// ```
public class ScheduleBoxController extends Controller {

    private final Provider<SchedulePickerController> schedulePickerProvider;
    private final ScheduledTimeClient scheduledTimeClient;
    private ScheduleBox box;
    private ScheduleRestriction scheduleRestriction = new ScheduleRestriction(false, true, true);
    private Consumer<Consumer<ScheduleReferenceTime>> scheduleReferenceTimeSupplier = consumer ->
            consumer.accept(null);

    /// Made by GIN; [#attach(ScheduleBox)] gives it its box.
    ///
    /// @param schedulePickerProvider Makes the schedule picker.
    /// @param scheduledTimeClient    The scheduled time client.
    @Inject
    public ScheduleBoxController(final Provider<SchedulePickerController> schedulePickerProvider,
                                 final ScheduledTimeClient scheduledTimeClient) {
        this.schedulePickerProvider = schedulePickerProvider;
        this.scheduledTimeClient = scheduledTimeClient;
    }

    /// Attaches this to the box it checks and opens the dialog for.
    ///
    /// @param box The box, e.g. one a template declares.
    /// @return This, for its owner to keep.
    public ScheduleBoxController attach(final ScheduleBox box) {
        this.box = Objects.requireNonNull(box, "box");
        box.setOnOpenPicker(this::openPicker);
        box.setOnCheck(this::check);
        return this;
    }

    /// @param scheduleRestriction What the schedule may be, e.g. whether it may run more often
    ///                            than once a minute.
    public void setScheduleRestriction(final ScheduleRestriction scheduleRestriction) {
        this.scheduleRestriction = scheduleRestriction;
    }

    /// @param scheduleReferenceTimeSupplier Gives the dialog when the schedule's times are worked
    ///                                      out from, and when it last ran, e.g. from the server,
    ///                                      when it opens.
    public void setScheduleReferenceTimeSupplier(
            final Consumer<Consumer<ScheduleReferenceTime>> scheduleReferenceTimeSupplier) {
        this.scheduleReferenceTimeSupplier = scheduleReferenceTimeSupplier;
    }

    /// Checks the schedule entered with the server, marking the box invalid if it is rejected (an
    /// enabled box's schedule that isn't instant), and taking the schedule as the server read it.
    ///
    /// @param consumer Given the schedule's times, with the error if it was rejected, or null if
    ///                 it couldn't be checked.
    public void validate(final Consumer<ScheduledTimes> consumer) {
        final Schedule schedule = box.getEnteredSchedule();
        scheduledTimeClient.validate(schedule, scheduleRestriction, guard(scheduledTimes -> {
            box.setInvalid(box.isEnabled()
                           && !box.isInstant()
                           && (scheduledTimes == null || scheduledTimes.isError()));
            if (scheduledTimes != null && scheduledTimes.getSchedule() != null) {
                box.setCheckedSchedule(scheduledTimes.getSchedule());
            }
            consumer.accept(scheduledTimes);
        }), getProvidedTaskMonitor());
    }

    private void check() {
        validate(scheduledTimes -> {
        });
    }

    private void openPicker() {
        final Schedule entered = box.getEnteredSchedule();
        scheduledTimeClient.validate(entered,
                new ScheduleRestriction(true, true, true),
                guard(scheduledTimes -> {
                    final Schedule schedule = scheduledTimes != null && scheduledTimes.getSchedule() != null
                            ? scheduledTimes.getSchedule()
                            : entered;
                    box.setCheckedSchedule(schedule);
                    scheduleReferenceTimeSupplier.accept(guard(scheduleReferenceTime -> {
                        final SchedulePickerController picker = schedulePickerProvider.get();
                        picker.setScheduleRestriction(scheduleRestriction);
                        picker.edit(this, schedule, scheduleReferenceTime, chosen -> {
                            if (!Objects.equals(schedule, chosen)) {
                                box.setValue(chosen, true);
                            }
                        });
                    }));
                }),
                getProvidedTaskMonitor());
    }
}
