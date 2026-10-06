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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for [ValueSpinner], matching `Widgets/Inputs/ValueSpinner` in the React Storybook.
///
/// [ValueSpinner]'s field is a text input (role `textbox`), where the React port's is a number
/// input (role `spinbutton`), so the plays find it as a text box and compare its value as text.
/// [ValueSpinner] parses what is typed with `Long.valueOf` when the field loses the focus (or
/// on Enter), and puts the previous value back if it isn't a whole number within the bounds.
public final class ValueSpinnerStories {

    // Arg names, the same as the React ValueSpinner's props
    private static final String VALUE = "value";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String STEP = "step";
    private static final String ENABLED = "enabled";
    private static final String ON_CHANGE = InputWidgets.ON_CHANGE;

    private static final long CONTRACT_MIN = 0;
    private static final long CONTRACT_MAX = 100;
    private static final long CONTRACT_INITIAL = 10;

    private ValueSpinnerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/ValueSpinner", ValueSpinnerStories.class)
                .layout(StoryLayout.CENTERED)
                // React's maxStep, delta and inputId props are omitted: ValueSpinner has setters for
                // the first two (not shown in React's stories) and no way to set its field's id
                .argType(ArgType.number(VALUE).description("The value (GWT setValue(...))."))
                .argType(ArgType.number(MIN).description("The minimum value (GWT setMin(...)).").defaultSummary("0"))
                .argType(ArgType.number(MAX).description("The maximum value (GWT setMax(...)).")
                        .defaultSummary("100"))
                .argType(ArgType.number(STEP).description("The step of each change (GWT setMinStep(...)).")
                        .defaultSummary("1"))
                .argType(ArgType.bool(ENABLED).description("GWT setEnabled(...).").defaultSummary("true"))
                .argType(ArgType.action(ON_CHANGE).description("Called with the new value when it changes."))
                .args(Args.of(VALUE, 0))
                // Basic spinner constrained to 0..100
                .story("Basic", context -> withValueLabel(valueSpinner(context, 10, 0, 100)))
                // Step of 5 across a -20..50 range
                .story("Step", context -> {
                    final ValueSpinner spinner = valueSpinner(context, 0, -20, 50);
                    spinner.setMinStep(5);
                    return withValueLabel(spinner);
                })
                // Disabled spinner
                .story("Disabled", ValueSpinnerStories::fromArgs)
                .withArgs(Args.of(VALUE, 42, ENABLED, false))

                // The numeric contract: the value is a number (a Long in GWT), and the field never
                // commits anything else

                // Non-numeric text never commits: the field reverts on blur and onChange never fires
                .story("NonNumericTextIsRejected", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.type(input, "abc");
                    // Blur commits
                    play.tab();
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // Decimals and exponents are rejected too - the parse is integer-only (GWT Long.valueOf)
                .story("DecimalsAndExponentsAreRejected", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.type(input, "1.5");
                    // Differs from React: the field is a text input, so of course it holds "1.5"; in
                    // React this proves that the number input accepts it before it is rejected.
                    play.expect(input).toHaveValue("1.5");
                    play.tab();
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));

                    play.clear(input);
                    play.type(input, "1e3");
                    play.tab();
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));

                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // A typed out-of-bounds value is rejected and reverted, not clamped
                .story("OutOfBoundsTypedValueIsRejected", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    // The max is 100
                    play.type(input, "500");
                    play.tab();
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // A valid in-range value commits, and what onChange receives is a number, never a
                // string
                .story("ValidValueCommitsAsANumber", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.type(input, "42");
                    play.tab();
                    play.expect(input).toHaveValue("42");
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledWith(42);
                    // Not "42" - the emitted value is a number (React checks typeof is 'number')
                    final Value<List<Object>> lastCall = play.spy(ON_CHANGE).lastCall();
                    play.expect("the emitted value", () -> lastCall.get().get(0))
                            .toSatisfy("is a Long", value -> value instanceof Long);
                })
                // The arrow path clamps at the boundary rather than rejecting (the asymmetry is GWT's)
                .story("ArrowsClampAtTheBoundary", context -> contractHarness(context, CONTRACT_MAX - 1))
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.click(input);
                    play.keyboard("{ArrowUp}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_MAX));
                    // Already at max - a further step clamps rather than overshooting or wrapping
                    play.keyboard("{ArrowUp}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_MAX));
                });
    }

    /// A spinner made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final ValueSpinner spinner = new ValueSpinner();
        final Long min = StoryArgs.getLong(args, MIN);
        if (min != null) {
            spinner.setMin(min);
        }
        final Long max = StoryArgs.getLong(args, MAX);
        if (max != null) {
            spinner.setMax(max);
        }
        final Long step = StoryArgs.getLong(args, STEP);
        if (step != null) {
            spinner.setMinStep(step.intValue());
        }
        spinner.setValue(StoryArgs.getLong(args, VALUE));
        spinner.setEnabled(StoryArgs.getBoolean(args, ENABLED, true));
        reportChanges(context, spinner);
        return spinner;
    }

    private static ValueSpinner valueSpinner(final StoryContext context,
                                             final long value,
                                             final long min,
                                             final long max) {
        final ValueSpinner spinner = new ValueSpinner();
        spinner.setMin(min);
        spinner.setMax(max);
        spinner.setValue(value);
        reportChanges(context, spinner);
        return spinner;
    }

    /// React's `ContractHarness`: a spinner from 0 to 100, starting at 10, recording every change.
    private static Widget contractHarness(final StoryContext context) {
        return contractHarness(context, CONTRACT_INITIAL);
    }

    private static Widget contractHarness(final StoryContext context, final long initial) {
        return valueSpinner(context, initial, CONTRACT_MIN, CONTRACT_MAX);
    }

    private static void reportChanges(final StoryContext context, final ValueSpinner spinner) {
        final Spy onChange = context.fn(ON_CHANGE);
        spinner.addValueChangeHandler(event -> onChange.call(event.getValue()));
    }

    /// The spinner with `<p style={{marginTop: 8, fontSize: 12}}>Value: {val}</p>` below it.
    private static Widget withValueLabel(final ValueSpinner spinner) {
        final Label value = new Label("Value: " + spinner.getValue());
        value.getElement().getStyle().setProperty("marginTop", "8px");
        value.getElement().getStyle().setProperty("fontSize", "12px");
        spinner.addValueChangeHandler(event -> value.setText("Value: " + event.getValue()));
        return StoryPanels.column(8, spinner, value);
    }

    /// The spinner's field.
    private static Query spinnerInput(final Play play) {
        // Differs from React: a text box, not a spinbutton (see the class comment)
        return play.getByRole("textbox");
    }
}
