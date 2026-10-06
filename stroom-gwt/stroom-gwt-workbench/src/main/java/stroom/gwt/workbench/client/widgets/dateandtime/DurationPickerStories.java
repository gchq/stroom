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
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.customdatebox.client.DurationPicker;

import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DurationPicker] (a value spinner and a time unit selection box),
/// matching `Widgets/Date & Time/DurationPicker` in the React Storybook.
public final class DurationPickerStories {

    // Arg names. React's value prop ({time, timeUnit}) is split into its two parts
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
        // React's focusWhen prop is omitted: Stroom's views call focus() when shown
        registry.component("Widgets/Date & Time/DurationPicker", DurationPickerStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.number(TIME).description("The number of time units (React's value.time)."))
                .argType(ArgType.select(TIME_UNIT, timeUnits).description("The time unit (React's value.timeUnit).")
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
                .withArgs(Args.of(TIME, 7, TIME_UNIT, "Days", ENABLED, false));
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
