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
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Label;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for [ValueSpinner].
///
/// [ValueSpinner]'s field is a text input with the WAI-ARIA role `spinbutton`, so the plays find it
/// as a spin button and compare its value as text.
/// [ValueSpinner] parses what is typed with `Long.valueOf` when the field loses the focus (or
/// on Enter), and puts the previous value back if it isn't a whole number within the bounds, saying
/// why.
public final class ValueSpinnerStories {

    // Arg names
    private static final String VALUE = "value";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String STEP = "step";
    private static final String ENABLED = "enabled";
    private static final String ON_CHANGE = InputWidgets.ON_CHANGE;

    // The arrows' titles
    private static final String INCREASE = "Increase value";
    private static final String DECREASE = "Decrease value";
    // Why the contract harness rejects a value
    private static final String OUT_OF_BOUNDS = "\"500\" isn't allowed. Enter a whole number from 0 to 100.";
    private static final String NOT_A_NUMBER = "\"abc\" isn't allowed. Enter a whole number from 0 to 100.";
    private static final String EMPTY = "A value is needed. Enter a whole number from 0 to 100.";
    // The field's name
    private static final String NAME = "Amount";

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
                // ValueSpinner's maximum step and delta setters aren't shown by these stories
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
                // Disabled spinner: its arrows are dimmed and do nothing
                .story("Disabled", ValueSpinnerStories::fromArgs)
                .withArgs(Args.of(VALUE, 42, ENABLED, false))
                .withPlay(play -> {
                    play.click(play.getByTitle(INCREASE));
                    play.expect(spinnerInput(play)).toHaveValue("42");
                })
                // Read only: the value can be selected and copied, but the arrows are dimmed and do
                // nothing
                .story("ReadOnly", context -> {
                    final ValueSpinner spinner = valueSpinner(context, 42, 0, 100);
                    spinner.setReadOnly(true);
                    return spinner;
                })
                .withPlay(play -> {
                    play.click(play.getByTitle(INCREASE));
                    play.expect(spinnerInput(play)).toHaveValue("42");
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })

                // The arrows: down then up, inside the end of the field's border

                // A click steps once and leaves the focus in the field
                .story("ArrowsStepTheValue", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.click(play.getByTitle(INCREASE));
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL + 1));
                    play.expect(input).toHaveFocus();
                    play.click(play.getByTitle(DECREASE));
                    play.click(play.getByTitle(DECREASE));
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL - 1));
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledTimes(3);
                })
                // A press steps from what has been typed, not from the value before it
                .story("ArrowsStepFromTypedText", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.type(input, "50");
                    play.click(play.getByTitle(INCREASE));
                    play.expect(input).toHaveValue("51");
                })
                // The arrows are for the mouse: Up and Down in the field do what they do, so they
                // are no tab stop and screen readers don't hear them (the spinner is one tab stop)
                .story("ArrowsAreNotTabStops", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    play.expect(play.queryAllByRole("button")).toHaveLength(0);
                    play.expect(play.getByTitle(INCREASE)).not().toHaveAttribute("tabindex");
                    play.expect(play.getByTitle(INCREASE)).toHaveAttribute("aria-hidden", "true");
                    play.expect(play.getByTitle(DECREASE)).not().toHaveAttribute("tabindex");
                    play.expect(play.getByTitle(DECREASE)).toHaveAttribute("aria-hidden", "true");
                })
                // The WAI-ARIA spin button: screen readers hear its role, value and bounds, and the
                // value as they hear it follows every change
                .story("SpinButtonRoleAndValue", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = play.getByRole("spinbutton", NAME);
                    play.expect(input).toHaveAttribute("aria-valuenow", String.valueOf(CONTRACT_INITIAL));
                    play.expect(input).toHaveAttribute("aria-valuemin", String.valueOf(CONTRACT_MIN));
                    play.expect(input).toHaveAttribute("aria-valuemax", String.valueOf(CONTRACT_MAX));
                    // Shown as the number, so there is no separate text
                    play.expect(input).not().toHaveAttribute("aria-valuetext");
                    play.click(input);
                    play.keyboard("{ArrowUp}");
                    play.expect(input).toHaveAttribute("aria-valuenow", String.valueOf(CONTRACT_INITIAL + 1));
                    play.click(play.getByTitle(DECREASE));
                    play.expect(input).toHaveAttribute("aria-valuenow", String.valueOf(CONTRACT_INITIAL));
                    play.clear(input);
                    play.type(input, "42{Enter}");
                    play.expect(input).toHaveAttribute("aria-valuenow", "42");
                })
                // Page Up and Page Down take ten steps, stopping at the bounds; Home and End go to
                // them
                .story("PageHomeAndEndKeys", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.click(input);
                    play.keyboard("{PageUp}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL + 10));
                    play.keyboard("{PageDown}{PageDown}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL - 10));
                    // Ten steps down from 0 stops at the minimum rather than wrapping
                    play.keyboard("{PageDown}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_MIN));
                    play.keyboard("{End}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_MAX));
                    play.expect(input).toHaveAttribute("aria-valuenow", String.valueOf(CONTRACT_MAX));
                    play.keyboard("{Home}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_MIN));
                })
                // A key steps from what has been typed, as an arrow does
                .story("KeysStepFromTypedText", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.type(input, "50{ArrowUp}");
                    play.expect(input).toHaveValue("51");
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledWith(51);
                })
                // A spinner that shows its value other than as the number (as a subclass's
                // formatValue may) gives screen readers the text it shows as well
                .story("FormattedValueText", context -> {
                    final ValueSpinner spinner = new ValueSpinner() {
                        @Override
                        protected String formatValue(final long value) {
                            return value + "%";
                        }

                        @Override
                        protected long parseValue(final String value) {
                            return Long.parseLong(value.replace("%", "").trim());
                        }
                    };
                    spinner.setMin(0);
                    spinner.setMax(100);
                    spinner.setValue(25L);
                    reportChanges(context, spinner);
                    AccessibleName.set(spinner, "Percent");
                    return spinner;
                })
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.expect(input).toHaveValue("25%");
                    play.expect(input).toHaveAttribute("aria-valuenow", "25");
                    play.expect(input).toHaveAttribute("aria-valuetext", "25%");
                    play.click(play.getByTitle(INCREASE));
                    play.expect(input).toHaveAttribute("aria-valuetext", "26%");
                })
                // A rejected value is explained (WCAG 3.3.1): the value before it is put back, the
                // field is marked invalid and described by why, which is shown under it and
                // announced, until a value is accepted
                .story("RejectedValueIsExplained", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.type(input, "500");
                    play.tab();
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));
                    play.expect(input).toHaveAttribute("aria-invalid", "true");
                    play.expect(input).toHaveAccessibleDescription(OUT_OF_BOUNDS);
                    play.expect(play.querySelector(".value-spinner__feedback")).toHaveTextContent(OUT_OF_BOUNDS);
                    play.expect(play.querySelector(".value-spinner__status")).toHaveTextContent(OUT_OF_BOUNDS);
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                    // Leaving the field again, unchanged, doesn't clear it
                    play.click(input);
                    play.tab();
                    play.expect(input).toHaveAttribute("aria-invalid", "true");
                    // A value accepted clears it
                    play.click(input);
                    play.keyboard("{ArrowUp}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL + 1));
                    play.expect(input).not().toHaveAttribute("aria-invalid");
                    play.expect(input).not().toHaveAccessibleDescription();
                    play.expect(play.querySelector(".value-spinner__feedback")).toHaveTextContent("");
                    play.expect(play.querySelector(".value-spinner__status")).toHaveTextContent("");
                })
                // In a form group, the reason is the group's feedback, as the group's other
                // controls' are (FormGroup.setFeedback)
                .story("RejectedValueInAFormGroup", context -> {
                    final FormGroup formGroup = new FormGroup();
                    formGroup.setIdentity("valueSpinnerRejected");
                    formGroup.setLabel(NAME);
                    formGroup.add(contractHarness(context));
                    return formGroup;
                })
                .withPlay(play -> {
                    final Query input = play.getByRole("spinbutton", NAME);
                    play.clear(input);
                    play.type(input, "abc{Enter}");
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));
                    play.expect(input).toHaveAttribute("aria-invalid", "true");
                    play.expect(play.querySelector(".form-group__feedback")).toHaveTextContent(NOT_A_NUMBER);
                    play.expect(input).toHaveAccessibleDescription(NOT_A_NUMBER);
                    // Not also under the field
                    play.expect(play.querySelector(".value-spinner__feedback")).toHaveTextContent("");
                    play.clear(input);
                    play.type(input, "20{Enter}");
                    play.expect(input).toHaveValue("20");
                    play.expect(input).not().toHaveAttribute("aria-invalid");
                    // The feedback is gone, and its element with it
                    play.expect(play.querySelectorAll(".form-group__feedback")).toHaveLength(0);
                })
                // An empty field asks for a value
                .story("EmptyValueIsExplained", ValueSpinnerStories::contractHarness)
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.clear(input);
                    play.tab();
                    play.expect(input).toHaveValue(String.valueOf(CONTRACT_INITIAL));
                    play.expect(input).toHaveAccessibleDescription(EMPTY);
                })
                // A spinner that hasn't been given a value is empty, which isn't an error
                .story("UnsetIsNotAnError", context -> {
                    final ValueSpinner spinner = new ValueSpinner();
                    AccessibleName.set(spinner, NAME);
                    return spinner;
                })
                .withPlay(play -> {
                    final Query input = spinnerInput(play);
                    play.click(input);
                    play.tab();
                    play.expect(input).toHaveValue("");
                    play.expect(input).not().toHaveAttribute("aria-invalid");
                })
                // The spinner at its own width, and given a width as Stroom's screens give one:
                // width="200px", and the w-100 class
                .story("Widths", context -> {
                    final ValueSpinner natural = valueSpinner(context, 10, 0, 100);
                    final ValueSpinner fixed = valueSpinner(context, 2000, 0, 10000);
                    fixed.setWidth("200px");
                    final ValueSpinner full = valueSpinner(context, 123456, 0, 1000000);
                    full.addStyleName("w-100");
                    AccessibleName.set(natural, "Natural width");
                    AccessibleName.set(fixed, "200px wide");
                    AccessibleName.set(full, "Full width");
                    final Widget column = StoryPanels.column(8, natural, fixed, full);
                    column.setWidth("320px");
                    return column;
                })

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
                    // The field is a text input, so it holds "1.5" until it is rejected on blur
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
                    // Not "42" - the emitted value is a number
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
        AccessibleName.set(spinner, NAME);
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
        // Named as a label would name it on Stroom's screens
        AccessibleName.set(spinner, NAME);
        return spinner;
    }

    /// A spinner from 0 to 100, starting at 10, recording every change.
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
        return play.getByRole("spinbutton");
    }
}
