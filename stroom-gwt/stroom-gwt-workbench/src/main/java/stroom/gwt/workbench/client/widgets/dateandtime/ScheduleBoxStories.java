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

package stroom.gwt.workbench.client.widgets.dateandtime;

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.DateTimeFormatter;
import stroom.schedule.client.ScheduleBox;
import stroom.schedule.client.ScheduleBoxController;
import stroom.schedule.client.ScheduledTimeClient;
import stroom.schedule.client.component.SchedulePicker;
import stroom.schedule.client.component.SchedulePickerController;
import stroom.util.shared.scheduler.CronExpressions;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.widget.form.client.AccessibleName;

import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [ScheduleBox] (a text field for a schedule's expression, whose clock icon
/// opens the Change Schedule dialog, [SchedulePickerController], used by e.g. the execution schedule
/// dialogs).
///
/// The box's controller is made as Stroom's GIN module makes it; `POST /scheduledTime/v1`, which checks a
/// schedule and works out its next time, always accepts it, replying with the schedule (as Stroom
/// does), which the box takes as its value.
public final class ScheduleBoxStories {

    private static final String ON_NEW_DIALOG = "onNewDialog";
    private static final String ON_CHANGE = DateTimeWidgets.ON_CHANGE;
    private static final String TITLE = "Change Schedule";
    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/scheduledTime/v1", RestReply.json("{\"schedule\": {\"type\": \"CRON\", "
                                                      + "\"expression\": \"0 0 2 * * ?\"}, "
                                                      + "\"nextScheduledTimeMs\": 1700003600000}"))
            .build();

    private ScheduleBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Date & Time/ScheduleBox", ScheduleBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Type a schedule's expression, or use the clock icon to open the dialog
                .story("Basic", context -> render(context, false))
                // The field says it opens a dialog; Alt+Down opens it as Enter does, and Escape
                // closes it
                .story("KeysOpenTheDialog", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(screen.getByRole("textbox", "Schedule")).toHaveAttribute("aria-haspopup", "dialog");
                    play.tab();
                    play.keyboard("{Alt>}{ArrowDown}{/Alt}");
                    screen.findByText(TITLE);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByText(TITLE)).toBeNull());
                    play.expect(screen.getByRole("textbox", "Schedule")).toHaveFocus();
                    play.keyboard("{Enter}");
                    screen.findByText(TITLE);
                })
                // The dialog's quick settings are buttons in groups named by their titles; pressing
                // one sets the expression
                .story("QuickSettingsAreGroupedButtons", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{Enter}");
                    screen.findByText(TITLE);
                    for (final String group : new String[]{"Minute", "Hour", "Day", "Month"}) {
                        screen.getByRole("group", group);
                    }
                    play.click(screen.within(screen.getByRole("group", "Hour"))
                            .getByRole("button", CronExpressions.EVERY_HOUR.getName()));
                    play.expect(screen.getByRole("textbox", "Expression"))
                            .toHaveValue(CronExpressions.EVERY_HOUR.getExpression());
                })
                // Checking the expression (each time the box is left, as here) asks the server and
                // makes no dialog: it once made a new dialog for every check
                .story("CheckingMakesNoDialog", context -> render(context, false))
                .withPlay(play -> {
                    for (int i = 0; i < 3; i++) {
                        play.tab();
                        play.keyboard("{Shift>}{Tab}{/Shift}");
                    }
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledTimes(4));
                    play.expect(play.spy(ON_NEW_DIALOG)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // OK puts the schedule chosen in the box (the server, here, accepts any)
                .story("ChooseSchedule", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{Enter}");
                    screen.findByText(TITLE);
                    play.click(screen.within(screen.getByRole("group", "Hour"))
                            .getByRole("button", CronExpressions.EVERY_HOUR.getName()));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(TITLE)).toBeNull());
                    play.expect(screen.getByRole("textbox", "Schedule"))
                            .toHaveValue(CronExpressions.EVERY_HOUR.getExpression());
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ON_NEW_DIALOG)).toHaveBeenCalledTimes(1);
                })
                // Read only: neither the clock nor Enter opens the dialog, and no dialog is made
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(screen.getByRole("textbox", "Schedule")).toHaveAttribute("readonly");
                    play.expect(play.querySelector(".schedule-box")).toHaveClass("icon-box--read-only");
                    play.click(play.querySelector(".schedule-box .icon-box__icon"));
                    play.tab();
                    play.keyboard("{Enter}");
                    play.sleep(200);
                    play.expect(screen.queryByText(TITLE)).toBeNull();
                    play.expect(play.spy(ON_NEW_DIALOG)).not().toHaveBeenCalled();
                })
                // Cancel leaves the box as it was
                .story("CancelKeepsSchedule", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{Enter}");
                    screen.findByText(TITLE);
                    play.click(screen.within(screen.getByRole("group", "Hour"))
                            .getByRole("button", CronExpressions.EVERY_HOUR.getName()));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(TITLE)).toBeNull());
                    play.expect(screen.getByRole("textbox", "Schedule")).toHaveValue("0 0 2 * * ?");
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
        final Spy onNewDialog = context.fn(ON_NEW_DIALOG);
        final Spy onChange = context.fn(ON_CHANGE);
        final ScheduleBox box = new ScheduleBox();
        // Named, as a form group's label names it on Stroom's screens
        AccessibleName.set(box, "Schedule");
        // As Stroom's controllers attach one, e.g. ScheduledProcessEditorController
        final ScheduledTimeClient scheduledTimeClient =
                new ScheduledTimeClient(harness.getEventBus(), harness.getRestFactory());
        harness.own(new ScheduleBoxController(() -> {
            onNewDialog.call();
            return new SchedulePickerController(harness.getEventBus(),
                    new SchedulePicker(),
                    scheduledTimeClient,
                    new DateTimeFormatter(harness.getUserPreferencesManager()));
        }, scheduledTimeClient)).attach(box);
        box.setValue(Schedule.builder().type(ScheduleType.CRON).expression("0 0 2 * * ?").build());
        box.setOnChange(onChange::call);
        box.setReadOnly(readOnly);
        harness.add(box);
        return harness.asWidget();
    }
}
