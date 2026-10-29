/*
 * Copyright 2016 Crown Copyright
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

package stroom.cell.valuespinner.client;

import stroom.cell.valuespinner.shared.Editable;
import stroom.cell.valuespinner.shared.HasSpinnerConstraints;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.ElementUtil;

import com.google.gwt.cell.client.AbstractEditableCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/// The data grid's spinner: a number that a row's value lets be changed, shown as the ValueSpinner
/// widget is (its markup and value-spinner.css, with the `value-spinner--cell` modifier for the grid).
/// Its field is a WAI-ARIA spin button whose keys work while it has the focus, and its arrows step
/// it while pressed with the mouse (a finger's tap steps once). A typed value is committed on Enter or
/// when the field loses the focus, and an arrow's or a key's at once. A typed value that isn't a whole
/// number within the bounds is rejected, as the ValueSpinner widget rejects one: the value before it is
/// put back, the field is marked invalid and described by why (its title showing it to the mouse), and
/// why is announced, until a value is accepted.
public class ValueSpinnerCell extends AbstractEditableCell<Number, ValueSpinnerCell.ViewData>
        implements HasSpinnerConstraints {

    private static volatile Template template;
    // The block's classes, shared with the ValueSpinner widget (value-spinner.css)
    private static final String BLOCK = "value-spinner";
    private static final String STEP_CLASS = BLOCK + "__step";
    private static final String STATUS_CLASS = BLOCK + "__status";
    private static final String ARIA_INVALID = "aria-invalid";
    private static final String ARIA_DESCRIBED_BY = "aria-describedby";
    private static final String STEP_ATTRIBUTE = "data-step";
    private static final String UP = "up";
    // Text typed, pasted or deleted in the field (the grid hands its cells this event too)
    private static final String INPUT_EVENT = "input";
    // The steps Page Up and Page Down take
    private static final int PAGE_STEPS = 10;
    // The arrows (down, then up), inside the end of the field's border: for the pointer only, as
    // the keys in the field do what they do, so hidden from screen readers and no tab stop
    private static final SafeHtml STEPS_HTML = new SafeHtmlBuilder()
            .appendHtmlConstant("<div class=\"" + BLOCK + "__steps\">")
            .append(stepHtml(SvgImage.ARROW_DOWN, "Decrease value", "down"))
            .append(stepHtml(SvgImage.ARROW_UP, "Increase value", UP))
            .appendHtmlConstant("</div>")
            .toSafeHtml();

    private static volatile SpinStepper spinner;
    private final String name;
    private long min = 0;
    private long max = 100;
    private int step = 1;
    private int maxStep = 99;
    /**
     * The currently focused value key. Only one key can be focused at any time.
     */
    private Object focusedKey;

    /// @param name What the value is, naming each row's field for screen readers, as the column's
    ///             heading does, e.g. `Priority`.
    /// @param min  The smallest value, unless a value has its own bounds.
    /// @param max  The largest value, unless a value has its own bounds.
    public ValueSpinnerCell(final String name, final long min, final long max) {
        this(name, min, max, 1, 99);
    }

    /// @param name    What the value is, naming each row's field for screen readers, as the column's
    ///                heading does, e.g. `Priority`.
    /// @param min     The smallest value, unless a value has its own bounds.
    /// @param max     The largest value, unless a value has its own bounds.
    /// @param step    The step of each change.
    /// @param maxStep The largest step an arrow takes as it speeds up while held.
    public ValueSpinnerCell(final String name, final long min, final long max, final int step, final int maxStep) {
        super(BrowserEvents.FOCUS,
                BrowserEvents.BLUR,
                BrowserEvents.KEYDOWN,
                BrowserEvents.KEYUP,
                INPUT_EVENT,
                BrowserEvents.CHANGE,
                BrowserEvents.MOUSEOUT,
                BrowserEvents.MOUSEDOWN,
                BrowserEvents.MOUSEUP);
        this.name = name;
        this.min = min;
        this.max = max;
        this.step = step;
        this.maxStep = maxStep;

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final Number value, final NativeEvent event,
                               final ValueUpdater<Number> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);

        // A read-only value can't be changed
        if (isReadOnly(value)) {
            return;
        }

        // Get the target element.
        final Element target = event.getEventTarget().cast();

        final String eventType = event.getType();
        if ("focus".equals(eventType)) {
            // Ignore change events that don't target the input.
            final InputElement input = getInputElement(parent);
            if (input == null || !input.isOrHasChild(target)) {
                return;
            }

            focusedKey = context.getKey();

        } else if ("blur".equals(eventType)) {
            // Ignore change events that don't target the input.
            final InputElement input = getInputElement(parent);
            if (input == null || !input.isOrHasChild(target)) {
                return;
            }

            focusedKey = null;
            // The focus leaving commits what has been typed. The browser's change does too, but
            // not if the grid drew the row again since the typing (e.g. a list that polls), as the
            // field drawn again holds the typed text from the start
            finishEditing(parent, value, context.getKey(), valueUpdater, false);

        } else if ("change".equals(eventType)) {
            // Ignore change events that don't target the input.
            final InputElement input = getInputElement(parent);
            if (input == null || !input.isOrHasChild(target)) {
                return;
            }

            // The input has changed so finish editing.
            finishEditing(parent, value, context.getKey(), valueUpdater, true);

        } else if ("keyup".equals(eventType) || INPUT_EVENT.equals(eventType)) {
            // Ignore key up events that don't target the input.
            final InputElement input = getInputElement(parent);
            if (input == null || !input.isOrHasChild(target)) {
                return;
            }

            // Record the text as it is typed (or pasted, or cleared), so that the row drawn again
            // keeps it
            final Object key = context.getKey();
            ViewData vd = getViewData(key);
            if (vd == null) {
                vd = new ViewData(String.valueOf(value));
                setViewData(key, vd);
            }

            vd.setCurrentValue(input.getValue());

        } else if (BrowserEvents.KEYDOWN.equals(eventType)) {
            onKeyDown(context, parent, value, event, valueUpdater);

        } else {
            final Element step = findStep(parent, target);
            if (step != null) {
                onStepEvent(context, parent, value, event, valueUpdater, step);
            }
        }
    }

    // A press on an arrow with the main mouse button steps at once and keeps stepping while held;
    // letting go, or the mouse leaving the arrow, stops it and commits the value. A tap with a finger
    // steps once, by the mouse events the browser makes for it: the grid hands a cell its events
    // after the browser has handled them (AbstractHasData), so a cell can't take a touch for itself
    // (preventing the mouse events that follow it), and touch events don't reach it
    private void onStepEvent(final Context context,
                             final Element parent,
                             final Number value,
                             final NativeEvent event,
                             final ValueUpdater<Number> valueUpdater,
                             final Element step) {
        final String eventType = event.getType();
        if (BrowserEvents.MOUSEDOWN.equals(eventType) && event.getButton() == NativeEvent.BUTTON_LEFT) {
            ensureSpinner();
            final HasSpinnerConstraints constraints = constraintsOf(value);
            final InputElement input = getInputElement(parent);
            final long start = Long.parseLong(constrainValue(constraints, input.getValue()));
            clearRejection(parent, input, context.getKey());
            spinner.start(constraints, UP.equals(step.getAttribute(STEP_ATTRIBUTE)), start, input);

        } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
            // Moving onto the arrow's icon isn't leaving it
            final Element to = event.getRelatedEventTarget() == null
                    ? null
                    : event.getRelatedEventTarget().cast();
            if (to == null || !step.isOrHasChild(to)) {
                stopSpinning(context, parent, value, valueUpdater);
            }

        } else if (BrowserEvents.MOUSEUP.equals(eventType)) {
            stopSpinning(context, parent, value, valueUpdater);
        }
    }

    // The keys of the WAI-ARIA spin button, while the field has the focus: Up and Down step it, Page
    // Up and Page Down take ten steps, and Home and End go to its bounds. Each commits the value,
    // leaving the focus in the field
    private void onKeyDown(final Context context,
                           final Element parent,
                           final Number value,
                           final NativeEvent event,
                           final ValueUpdater<Number> valueUpdater) {
        final InputElement input = getInputElement(parent);
        final Element target = event.getEventTarget().cast();
        if (input == null || !input.isOrHasChild(target)) {
            return;
        }
        final HasSpinnerConstraints constraints = constraintsOf(value);
        final long current = Long.parseLong(constrainValue(constraints, input.getValue()));
        final long stepped;
        switch (event.getKeyCode()) {
            case KeyCodes.KEY_UP:
                stepped = current + constraints.getStep();
                break;
            case KeyCodes.KEY_DOWN:
                stepped = current - constraints.getStep();
                break;
            case KeyCodes.KEY_PAGEUP:
                stepped = current + (long) PAGE_STEPS * constraints.getStep();
                break;
            case KeyCodes.KEY_PAGEDOWN:
                stepped = current - (long) PAGE_STEPS * constraints.getStep();
                break;
            case KeyCodes.KEY_HOME:
                stepped = constraints.getMin();
                break;
            case KeyCodes.KEY_END:
                stepped = constraints.getMax();
                break;
            default:
                return;
        }
        input.setValue(String.valueOf(Math.max(constraints.getMin(), Math.min(constraints.getMax(), stepped))));
        clearRejection(parent, input, context.getKey());
        finishEditing(parent, value, context.getKey(), valueUpdater, false);
    }

    // The arrow the event is on, or null if it isn't on one
    private static Element findStep(final Element parent, final Element target) {
        Element element = target;
        while (element != null && element != parent) {
            if (ElementUtil.hasClassName(element, STEP_CLASS)) {
                return element;
            }
            element = element.getParentElement();
        }
        return null;
    }

    // What constrains the value: the value's own bounds, if it has them, else the cell's
    private HasSpinnerConstraints constraintsOf(final Number value) {
        return value instanceof HasSpinnerConstraints
                ? (HasSpinnerConstraints) value
                : this;
    }

    private void ensureSpinner() {
        if (spinner == null) {
            synchronized (ValueSpinnerCell.class) {
                if (spinner == null) {
                    spinner = new SpinStepper();
                }
            }
        }
    }

    private void stopSpinning(final Context context, final Element parent, final Number value,
                              final ValueUpdater<Number> valueUpdater) {
        if (spinner != null && spinner.isSpinning()) {
            spinner.stop();

            if (spinner.hasChanged()) {
                // The input has changed so finish editing.
                finishEditing(parent, value, context.getKey(), valueUpdater, true);
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context, final Element parent, final Number value,
                                  final NativeEvent event, final ValueUpdater<Number> valueUpdater) {
        final Element input = getInputElement(parent);
        if (input != null) {
            final Element target = event.getEventTarget().cast();
            final Object key = context.getKey();
            if (getInputElement(parent).isOrHasChild(target)) {
                finishEditing(parent, value, key, valueUpdater, true);
            } else {
                focusedKey = key;
                input.focus();
            }
        }
    }

    @Override
    public void render(final Context context, final Number value, final SafeHtmlBuilder sb) {
        // A plain number is shown as text; an editable value that can't be edited now is a
        // read-only spinner (the normal field, its value and arrows greyed)
        if (value != null) {
            if (isReadOnly(value)) {
                sb.append(template.readOnlyInput(String.valueOf(value), name, bound(value, true),
                        bound(value, false), STEPS_HTML));

            } else if (!(value instanceof Editable)) {
                sb.append(SafeHtmlUtils.fromString(String.valueOf(value)));

            } else {
                // Get the view data.
                final Object key = context.getKey();

                // Get the current view data if there is some.
                ViewData viewData = getViewData(key);

                // If the value held by the view data object is the same as the
                // passed value then we no longer need the view data object to
                // hold temporary state (unless it holds why a typed value was rejected).
                if (viewData != null && viewData.getCurrentValue() != null && viewData.getRejection() == null) {
                    try {
                        final Number currentValue = Long.valueOf(viewData.getCurrentValue());
                        if (currentValue != null && currentValue.equals(value)) {
                            clearViewData(key);
                            viewData = null;
                        }
                    } catch (final NumberFormatException e) {
                        // Ignore.
                    }
                }

                final String text = viewData != null
                        ? viewData.getCurrentValue()
                        : String.valueOf(value);
                if (viewData != null && viewData.getRejection() != null) {
                    // A row drawn again still says why a typed value was rejected
                    sb.append(template.rejectedInput(text, String.valueOf(value), name, bound(value, true),
                            bound(value, false), STEPS_HTML, Document.get().createUniqueId(),
                            viewData.getRejection()));
                } else {
                    sb.append(template.input(text, String.valueOf(value), name, bound(value, true),
                            bound(value, false), STEPS_HTML));
                }
            }
        } else {
            sb.append(SafeHtmlUtils.fromSafeConstant("<br/>"));
        }
    }

    @Override
    public boolean isEditing(final Context context, final Element parent, final Number value) {
        return focusedKey != null && focusedKey.equals(context.getKey());
    }

    @Override
    public boolean resetFocus(final Context context, final Element parent, final Number value) {
        if (isEditing(context, parent, value)) {
            getInputElement(parent).focus();
            return true;
        }
        return false;
    }

    private void finishEditing(final Element parent, final Number value, final Object key,
                               final ValueUpdater<Number> valueUpdater, final boolean blur) {
        // Get the input element.
        final InputElement input = getInputElement(parent);
        if (input != null) {
            // Get the view data.
            ViewData vd = getViewData(key);
            if (vd == null) {
                vd = new ViewData(String.valueOf(value));
                setViewData(key, vd);
            }

            final HasSpinnerConstraints constraints = constraintsOf(value);
            final String newValue = input.getValue();
            final Long parsed = parseWithinBounds(constraints, newValue);
            if (parsed == null) {
                // Not a whole number within the bounds: put back the value before it, and say why
                input.setValue(vd.getLastValue());
                vd.setCurrentValue(vd.getLastValue());
                vd.setRejection(rejectionMessage(constraints, newValue));
                reject(parent, input, vd.getRejection());

            } else {
                final String acceptedValue = String.valueOf(parsed);
                // Show the value as a number (e.g. "010" as "10")
                if (!newValue.equals(acceptedValue)) {
                    input.setValue(acceptedValue);
                }
                input.setAttribute("aria-valuenow", acceptedValue);
                vd.setCurrentValue(acceptedValue);

                // Fire the value updater if the value has changed, which also clears any rejection
                // (the value a rejection put back, committed again by the focus leaving, keeps it)
                if (!vd.getCurrentValue().equals(vd.getLastValue())) {
                    clearRejection(parent, input, key);
                    vd.setLastValue(vd.getCurrentValue());
                    if (valueUpdater != null) {
                        valueUpdater.update(parsed);
                    }
                }
            }
        }

        if (blur) {
            focusedKey = null;
            getInputElement(parent).blur();
        }
    }

    /// The typed value as a number, if it is a whole number within the bounds.
    ///
    /// @param constraints The bounds the value must be within.
    /// @param text        What was typed.
    /// @return The number, or null if the text isn't a whole number within the bounds.
    static Long parseWithinBounds(final HasSpinnerConstraints constraints, final String text) {
        try {
            final long parsed = Long.parseLong(text.trim());
            return parsed < constraints.getMin() || parsed > constraints.getMax()
                    ? null
                    : parsed;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /// Why a typed value was rejected, as the ValueSpinner widget says it.
    ///
    /// @param constraints  The bounds the value must be within.
    /// @param rejectedText What was typed.
    /// @return The message, e.g. `"500" isn't allowed. Enter a whole number from 1 to 100.`
    static String rejectionMessage(final HasSpinnerConstraints constraints, final String rejectedText) {
        final String expected = "Enter a whole number from " + constraints.getMin() + " to "
                                + constraints.getMax() + ".";
        if (rejectedText.trim().isEmpty()) {
            return "A value is needed. " + expected;
        }
        return "\"" + rejectedText.trim() + "\" isn't allowed. " + expected;
    }

    // Marks the field invalid and described by why, which its title shows to the mouse, and
    // announces it (the cell's status, a live region)
    private static void reject(final Element parent, final InputElement input, final String message) {
        final Element status = getStatusElement(parent);
        if (status != null) {
            if (status.getId().isEmpty()) {
                status.setId(Document.get().createUniqueId());
            }
            status.setInnerText(message);
            input.setAttribute(ARIA_DESCRIBED_BY, status.getId());
        }
        input.setAttribute(ARIA_INVALID, "true");
        input.setTitle(message);
    }

    // Clears what reject said, if anything
    private void clearRejection(final Element parent, final InputElement input, final Object key) {
        final ViewData vd = getViewData(key);
        if (vd != null) {
            vd.setRejection(null);
        }
        if (input.hasAttribute(ARIA_INVALID)) {
            input.removeAttribute(ARIA_INVALID);
            input.removeAttribute(ARIA_DESCRIBED_BY);
            input.removeAttribute("title");
            final Element status = getStatusElement(parent);
            if (status != null) {
                status.setInnerText("");
            }
        }
    }

    // The cell's status, which announces why a typed value was rejected
    private static Element getStatusElement(final Element parent) {
        final NodeList<Element> spans = parent.getElementsByTagName("span");
        for (int i = 0; i < spans.getLength(); i++) {
            if (ElementUtil.hasClassName(spans.getItem(i), STATUS_CLASS)) {
                return spans.getItem(i);
            }
        }
        return null;
    }

    // What a step starts from: what has been typed, made into a number within the bounds
    // (non-digits dropped, then kept within the bounds)
    private String constrainValue(final HasSpinnerConstraints constraints, final String value) {
        long val = constraints.getMin();

        final String trimmed = value.replaceAll("[\\D]", "");
        if (trimmed.length() > 0) {
            val = Long.valueOf(trimmed);
            if (val > constraints.getMax()) {
                val = constraints.getMax();
            } else if (val < constraints.getMin()) {
                val = constraints.getMin();
            }
        }

        return String.valueOf(val);
    }

    private InputElement getInputElement(final Element parent) {
        final NodeList<Element> inputs = parent.getElementsByTagName("input");
        return inputs.getLength() == 0
                ? null
                : inputs.getItem(0).cast();
    }

    // The value's lower or upper bound, as text
    private String bound(final Number value, final boolean lower) {
        final HasSpinnerConstraints constraints = constraintsOf(value);
        return String.valueOf(lower
                ? constraints.getMin()
                : constraints.getMax());
    }

    // An arrow: its icon marked as decoration, the arrow hidden from screen readers, its title
    // naming it for the mouse
    private static SafeHtml stepHtml(final SvgImage icon, final String name, final String direction) {
        final String svg = icon.getSvg().replaceFirst("<svg ",
                "<svg class=\"" + BLOCK + "__icon\" aria-hidden=\"true\" ");
        return new SafeHtmlBuilder()
                .appendHtmlConstant("<div class=\"" + STEP_CLASS + "\" title=\"" + name + "\" aria-hidden=\"true\" "
                                    + STEP_ATTRIBUTE + "=\"" + direction + "\">")
                .append(SafeHtmlUtils.fromTrustedString(svg))
                .appendHtmlConstant("</div>")
                .toSafeHtml();
    }

    @Override
    public long getMin() {
        return min;
    }

    @Override
    public long getMax() {
        return max;
    }

    @Override
    public int getStep() {
        return step;
    }

    @Override
    public int getMaxStep() {
        return maxStep;
    }


    private static boolean isReadOnly(final Number value) {
        return value instanceof Editable && !((Editable) value).isEditable();
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        // The ValueSpinner widget's markup (value-spinner.css), with the grid's modifier: a spin
        // button (WAI-ARIA) that the grid's keyboard reaches as a cell (tabindex -1), and the status
        // that announces why a typed value was rejected
        @Template("<div class=\"value-spinner value-spinner--cell\"><div class=\"value-spinner__field\">"
                  + "<input class=\"text-field value-spinner__input\" type=\"text\" role=\"spinbutton\""
                  + " value=\"{0}\" aria-valuenow=\"{1}\" aria-label=\"{2}\" aria-valuemin=\"{3}\""
                  + " aria-valuemax=\"{4}\" tabindex=\"-1\"></input>{5}</div>"
                  + "<span class=\"value-spinner__status sr-only\" aria-live=\"polite\"></span></div>")
        SafeHtml input(String text, String value, String name, String min, String max, SafeHtml steps);

        // As input, for a field whose typed value was rejected: marked invalid and described by
        // why, which its title shows to the mouse (the status already holds it, so it isn't
        // announced again)
        @Template("<div class=\"value-spinner value-spinner--cell\"><div class=\"value-spinner__field\">"
                  + "<input class=\"text-field value-spinner__input\" type=\"text\" role=\"spinbutton\""
                  + " value=\"{0}\" aria-valuenow=\"{1}\" aria-label=\"{2}\" aria-valuemin=\"{3}\""
                  + " aria-valuemax=\"{4}\" tabindex=\"-1\" aria-invalid=\"true\" aria-describedby=\"{6}\""
                  + " title=\"{7}\"></input>{5}</div>"
                  + "<span id=\"{6}\" class=\"value-spinner__status sr-only\" aria-live=\"polite\">{7}</span></div>")
        SafeHtml rejectedInput(String text, String value, String name, String min, String max, SafeHtml steps,
                               String statusId, String rejection);

        @Template("<div class=\"value-spinner value-spinner--cell\"><div class=\"value-spinner__field\">"
                  + "<input class=\"text-field value-spinner__input\" type=\"text\" role=\"spinbutton\""
                  + " value=\"{0}\" aria-valuenow=\"{0}\" aria-label=\"{1}\" aria-valuemin=\"{2}\""
                  + " aria-valuemax=\"{3}\" tabindex=\"-1\" readonly></input>{4}</div></div>")
        SafeHtml readOnlyInput(String value, String name, String min, String max, SafeHtml steps);
    }


    // --------------------------------------------------------------------------------


    /**
     * The {@code ViewData} for this cell.
     */
    public static class ViewData {

        /**
         * The last value that was updated.
         */
        private String lastValue;

        /**
         * The current value.
         */
        private String curValue;

        // Why the last value typed was rejected, or null if it wasn't
        private String rejection;

        /**
         * Construct a ViewData instance containing a given value.
         *
         * @param value a String value
         */
        public ViewData(final String value) {
            this.lastValue = value;
            this.curValue = value;
        }

        /**
         * Return true if the last and current values of this ViewData object
         * are equal to those of the other object.
         */
        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof ViewData)) {
                return false;
            }
            final ViewData vd = (ViewData) other;
            return equalsOrNull(lastValue, vd.lastValue) && equalsOrNull(curValue, vd.curValue);
        }

        /**
         * Return the current value of the input element.
         *
         * @return the current value String
         * @see #setCurrentValue(String)
         */
        public String getCurrentValue() {
            return curValue;
        }

        /**
         * Set the current value.
         *
         * @param curValue the current value
         * @see #getCurrentValue()
         */
        protected void setCurrentValue(final String curValue) {
            this.curValue = curValue;
        }

        /**
         * Return the last value sent to the {@link ValueUpdater}.
         *
         * @return the last value String
         * @see #setLastValue(String)
         */
        public String getLastValue() {
            return lastValue;
        }

        /**
         * Set the last value.
         *
         * @param lastValue the last value
         * @see #getLastValue()
         */
        protected void setLastValue(final String lastValue) {
            this.lastValue = lastValue;
        }

        /// @return Why the last value typed was rejected, or null if it wasn't.
        public String getRejection() {
            return rejection;
        }

        /// @param rejection Why the last value typed was rejected, or null if it wasn't.
        protected void setRejection(final String rejection) {
            this.rejection = rejection;
        }

        /**
         * Return a hash code based on the last and current values.
         */
        @Override
        public int hashCode() {
            return (lastValue + "_*!@HASH_SEPARATOR@!*_" + curValue).hashCode();
        }

        private boolean equalsOrNull(final Object a, final Object b) {
            return (a != null)
                    ? a.equals(b)
                    : ((b == null));
        }
    }
}
