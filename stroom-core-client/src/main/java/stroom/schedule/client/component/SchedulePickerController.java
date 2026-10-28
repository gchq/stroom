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

import stroom.alert.client.event.AlertEvent;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.ScheduleReferenceTime;
import stroom.job.shared.ScheduleRestriction;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.ScheduledTimeClient;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.FrequencyExpressions;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the "Change Schedule" dialog ([SchedulePicker]) for one use: it shows when the schedule
/// last ran and will next run (worked out by the server), and OK checks the schedule with the
/// server before giving it back. Each type remembers its expression while the dialog is open, so
/// changing the type and back keeps it.
public class SchedulePickerController extends Controller {

    private final SchedulePicker view;
    private final ScheduledTimeClient scheduledTimeClient;
    private final DateTimeFormatter dateTimeFormatter;
    private Schedule instantSchedule = new Schedule(ScheduleType.INSTANT, "");
    private Schedule frequencySchedule =
            new Schedule(ScheduleType.FREQUENCY, FrequencyExpressions.EVERY_10_MINUTES.getExpression());
    private Schedule cronSchedule =
            new Schedule(ScheduleType.CRON, CronExpressions.EVERY_10TH_MINUTE.getExpression());
    private Schedule currentSchedule = frequencySchedule;
    private ScheduleRestriction scheduleRestriction = new ScheduleRestriction(false, true, true);
    private ScheduleReferenceTime scheduleReferenceTime;

    /// Made by GIN for one use, with the dialog's content and what works its times out.
    @Inject
    public SchedulePickerController(final EventBus eventBus,
                                    final SchedulePicker view,
                                    final ScheduledTimeClient scheduledTimeClient,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.scheduledTimeClient = scheduledTimeClient;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
        view.setOnTypeChange(this::onScheduleTypeChange);
        view.setOnCalculate(this::calcTimes);
    }

    /// @param scheduleRestriction What OK accepts, e.g. whether a schedule may run more often than
    ///                            once a minute.
    public void setScheduleRestriction(final ScheduleRestriction scheduleRestriction) {
        this.scheduleRestriction = scheduleRestriction;
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK checks the schedule with the
    /// server, and gives it to `onOk` if it is valid; otherwise it warns and stays open.
    ///
    /// @param opener                What shows it.
    /// @param schedule              The schedule to start from.
    /// @param scheduleReferenceTime When the schedule's times are worked out from, and when it last
    ///                              ran, or null.
    /// @param onOk                  Given the schedule chosen.
    public void edit(final HasControllerNode opener,
                     final Schedule schedule,
                     final ScheduleReferenceTime scheduleReferenceTime,
                     final Consumer<Schedule> onOk) {
        this.scheduleReferenceTime = scheduleReferenceTime;
        // The schedule is its type's, which the dialog shows (it once asked for its times twice)
        remember(schedule);
        currentSchedule = schedule;
        read();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Change Schedule")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        // Getting the scheduled times checks the schedule
                        scheduledTimeClient.validate(createSchedule(), scheduleRestriction, guard(scheduledTimes -> {
                            if (scheduledTimes == null) {
                                e.reset();
                            } else if (scheduledTimes.isError()) {
                                AlertEvent.fireWarn(this, scheduledTimes.getError(), e::reset);
                            } else {
                                onOk.accept(createSchedule());
                                e.hide();
                            }
                        }), getProvidedTaskMonitor());
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void read() {
        view.setScheduleType(currentSchedule.getType());
        view.setExpression(StringUtil.toString(currentSchedule.getExpression()));
        calcTimes();
    }

    private void onScheduleTypeChange(final ScheduleType scheduleType) {
        remember(new Schedule(currentSchedule.getType(), view.getExpression().trim()));
        switch (scheduleType) {
            case INSTANT: {
                currentSchedule = instantSchedule;
                break;
            }
            case FREQUENCY: {
                currentSchedule = frequencySchedule;
                break;
            }
            case CRON: {
                currentSchedule = cronSchedule;
                break;
            }
        }
        read();
    }

    // Keeps a schedule as its type's, so that changing the type and back shows it again
    private void remember(final Schedule schedule) {
        switch (schedule.getType()) {
            case INSTANT: {
                instantSchedule = schedule;
                break;
            }
            case FREQUENCY: {
                frequencySchedule = schedule;
                break;
            }
            case CRON: {
                cronSchedule = schedule;
                break;
            }
        }
    }

    private Schedule createSchedule() {
        if (ScheduleType.INSTANT.equals(view.getScheduleType())) {
            return new Schedule(view.getScheduleType(), "Instant");
        }
        return new Schedule(view.getScheduleType(), view.getExpression().trim());
    }

    private void calcTimes() {
        final ScheduleType scheduleType = view.getScheduleType();
        final String currentString = view.getExpression();
        final Long referenceTime = NullSafe
                .get(scheduleReferenceTime, ScheduleReferenceTime::getScheduleReferenceTime);
        final Long lastExecutedTime = NullSafe
                .get(scheduleReferenceTime, ScheduleReferenceTime::getLastExecutedTime);
        if (scheduleType != null
            && (scheduleType == ScheduleType.INSTANT || NullSafe.isNonBlankString(currentString))) {
            final GetScheduledTimesRequest request = new GetScheduledTimesRequest(
                    createSchedule(),
                    referenceTime,
                    null);
            scheduledTimeClient.getScheduledTimes(request, guard(result -> {
                if (result != null) {
                    view.setLastExecuted(lastExecutedTime == null
                            ? "Never"
                            : dateTimeFormatter.format(lastExecutedTime));
                    if (result.isError()) {
                        view.setNextScheduledTime(result.getError());
                    } else if (result.getNextScheduledTimeMs() != null) {
                        view.setNextScheduledTime(dateTimeFormatter.format(result.getNextScheduledTimeMs()));
                    } else {
                        view.setNextScheduledTime("Never");
                    }
                }
            }), getProvidedTaskMonitor());
        }
    }
}
