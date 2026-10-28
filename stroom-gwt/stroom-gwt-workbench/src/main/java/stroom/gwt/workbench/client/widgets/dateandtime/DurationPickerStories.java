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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Label;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DurationPicker] (a value spinner and a time unit selection box).
public final class DurationPickerStories {

    // Arg names: the duration's two parts
    private static final String TIME = "time";
    private static final String TIME_UNIT = "timeUnit";
    private static final String SMALL_TIME_MODE = "smallTimeMode";
    private static final String ENABLED = "enabled";
    private static final String ON_CHANGE = DateTimeWidgets.ON_CHANGE;

    private DurationPickerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        final String[] timeUnits = new String[TimeUnit.values().length];
        for (int i = 0; i < timeUnits.length; i++) {
            timeUnits[i] = TimeUnit.values()[i].getDisplayValue();
        }
        registry.component("Widgets/Date & Time/DurationPicker", DurationPickerStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.number(TIME).description("The number of time units."))
                .argType(ArgType.select(TIME_UNIT, timeUnits).description("The time unit.")
                        .typeName("TimeUnit"))
                .argType(ArgType.bool(SMALL_TIME_MODE)
                        .description("Nanoseconds to hours with a minimum of 0 (GWT smallTimeMode()), rather "
                                     + "than seconds to years with a minimum of 1.")
                        .defaultSummary("false"))
                .argType(ArgType.bool(ENABLED).description("GWT setEnabled(...).").defaultSummary("true"))
                .argType(ArgType.action(ON_CHANGE).description("Called with the new duration when it changes."))
                .args(Args.of(TIME, 30, TIME_UNIT, "Minutes"))
                // Standard units (seconds to years)
                .story("Standard", context -> withDurationLabel(durationPicker(context, 30, TimeUnit.MINUTES,
                        false)))
                // Small time mode (nanoseconds to hours), with a minimum of 0
                .story("SmallTimeMode", context -> withDurationLabel(durationPicker(context, 500,
                        TimeUnit.MILLISECONDS, true)))
                // Disabled
                .story("Disabled", DurationPickerStories::fromArgs)
                .withArgs(Args.of(TIME, 7, TIME_UNIT, "Days", ENABLED, false))
                // In a form group, as on every Stroom screen: a group named by the label, its parts
                // "Amount" and "Unit"
                .story("InAFormGroup", context -> {
                    final FormGroup formGroup = new FormGroup();
                    formGroup.setLabel("Retain For");
                    formGroup.add(durationPicker(context, 30, TimeUnit.DAYS, false));
                    return formGroup;
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play group = screen.within(screen.getByRole("group", "Retain For"));
                    play.expect(group.getByRole("spinbutton", "Amount")).toHaveValue("30");
                    play.expect(group.getByRole("combobox", "Unit")).toBeInTheDocument();
                })
                // The arrow keys step the amount, each step firing the new duration
                .story("KeysChangeTheAmount", context -> withDurationLabel(durationPicker(context, 30,
                        TimeUnit.MINUTES, false)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.tab();
                    play.keyboard("{ArrowUp}{ArrowUp}");
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenLastCalledWith("32 Minutes");
                    play.expect(screen.getByText("Duration: 32 Minutes")).toBeInTheDocument();
                })
                // The largest amount and the longest unit both fit, beside their arrows (the picker was
                // 200px, so the amount was squeezed until its arrows hid it, and the unit's arrow was
                // cut off)
                .story("LargestValuesFit", context -> durationPicker(context, 1000000, TimeUnit.MILLISECONDS,
                        true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect("the amount's text fits",
                            () -> fits(screen.getByRole("spinbutton", "Amount").element().get()))
                            .toBe(true);
                    play.expect("the unit's text fits",
                            () -> fits(play.querySelector(".selection-box__value").element().get()))
                            .toBe(true);
                    play.expect("the picker is as wide as its parts",
                            () -> fits(play.querySelector(".duration-picker").element().get()))
                            .toBe(true);
                })
                // setValue(value, true) fires the duration, and setValue(null, false) gives the
                // default (both were ignored: it never fired, and it threw on null)
                .story("SetValueFiresWhenAsked", context -> {
                    final DurationPicker picker = durationPicker(context, 30, TimeUnit.MINUTES, false);
                    picker.setValue(null, false);
                    picker.setValue(SimpleDuration.builder().time(7).timeUnit(TimeUnit.DAYS).build(), true);
                    return picker;
                })
                .withPlay(play -> {
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledWith("7 Days");
                    play.expect(play.screen().getByRole("spinbutton", "Amount")).toHaveValue("7");
                });
    }

    /// @return Whether an element's content fits in it, so none is cut off or scrolled away.
    private static boolean fits(final Element element) {
        return element.getScrollWidth() <= element.getClientWidth();
    }

    /// A picker made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final Long time = StoryArgs.getLong(args, TIME);
        final TimeUnit timeUnit = toTimeUnit(args.getString(TIME_UNIT));
        final DurationPicker picker = durationPicker(context,
                time == null
                        ? 1
                        : time,
                timeUnit == null
                        ? TimeUnit.DAYS
                        : timeUnit,
                args.getBoolean(SMALL_TIME_MODE));
        picker.setEnabled(StoryArgs.getBoolean(args, ENABLED, true));
        return picker;
    }

    private static DurationPicker durationPicker(final StoryContext context,
                                                 final long time,
                                                 final TimeUnit timeUnit,
                                                 final boolean smallTimeMode) {
        final DurationPicker picker = new DurationPicker();
        if (smallTimeMode) {
            picker.smallTimeMode();
        }
        picker.setValue(SimpleDuration.builder().time(time).timeUnit(timeUnit).build());
        final Spy onChange = context.fn(ON_CHANGE);
        picker.addValueChangeHandler(event -> onChange.call(durationText(event.getValue())));
        return picker;
    }

    private static TimeUnit toTimeUnit(final String displayValue) {
        for (final TimeUnit timeUnit : TimeUnit.values()) {
            if (timeUnit.getDisplayValue().equals(displayValue)) {
                return timeUnit;
            }
        }
        return null;
    }

    /// The picker with `<p>Duration: {time} {timeUnit}</p>` below it, in a column with a gap of 8.
    private static Widget withDurationLabel(final DurationPicker picker) {
        final Label label = DateTimeWidgets.valueLabel("Duration: " + durationText(picker.getValue()));
        picker.addValueChangeHandler(event -> label.setText("Duration: " + durationText(event.getValue())));
        return StoryPanels.column(8, picker, label);
    }

    private static String durationText(final SimpleDuration duration) {
        return duration.getTime() + " " + duration.getTimeUnit().getDisplayValue();
    }
}
