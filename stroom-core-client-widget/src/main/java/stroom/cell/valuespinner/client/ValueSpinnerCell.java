/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractEditableCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ValueSpinnerCell extends AbstractEditableCell<Number, ValueSpinnerCell.ViewData>
        implements HasSpinnerConstraints {

    private static volatile Template template;
    private static final SafeHtml arrowUpHtml = new SafeHtmlBuilder()
            .appendHtmlConstant("<div class=\"valueSpinner-arrow valueSpinner-arrowUp\">")
            .append(SvgImageUtil.toSafeHtml(SvgImage.ARROW_UP))
            .appendHtmlConstant("</div>")
            .toSafeHtml();
//    private static final SafeHtml arrowUpHtml = SvgImageUtil.toSafeHtml(
//            SvgImage.ARROW_UP, "valueSpinner-arrow", "valueSpinner-arrowUp");

    //    private static final SafeHtml arrowDownHtml =
//            SafeHtmlUtils.fromTrustedString("<div class=\"valueSpinner-arrow valueSpinner-arrowDown\">" +
//                    SvgImage.ARROW_DOWN.getSvg() +
//                    "</div>");
//    private static final SafeHtml arrowDownHtml = SvgImageUtil.toSafeHtml(
//            SvgImage.ARROW_DOWN, "valueSpinner-arrow", "valueSpinner-arrowDown");
    private static final SafeHtml arrowDownHtml = new SafeHtmlBuilder()
            .appendHtmlConstant("<div class=\"valueSpinner-arrow valueSpinner-arrowDown\">")
            .append(SvgImageUtil.toSafeHtml(SvgImage.ARROW_DOWN))
            .appendHtmlConstant("</div>")
            .toSafeHtml();

    private static volatile Spinner spinner;
    private long min = 0;
    private long max = 100;
    private int step = 1;
    private int maxStep = 99;
    /**
     * The currently focused value key. Only one key can be focused at any time.
     */
    private Object focusedKey;

    public ValueSpinnerCell() {
        this(0, 100, 1, 99);
    }

    public ValueSpinnerCell(final long min, final long max) {
        this(min, max, 1, 99);
    }

    public ValueSpinnerCell(final long min, final long max, final int step, final int maxStep) {
        super(BrowserEvents.FOCUS,
                BrowserEvents.BLUR,
                BrowserEvents.KEYDOWN,
                BrowserEvents.KEYUP,
                BrowserEvents.CHANGE,
                BrowserEvents.MOUSEOVER,
                BrowserEvents.MOUSEOUT,
                BrowserEvents.MOUSEDOWN,
                BrowserEvents.MOUSEUP);
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

        } else if ("change".equals(eventType)) {
            // Ignore change events that don't target the input.
            final InputElement input = getInputElement(parent);
            if (input == null || !input.isOrHasChild(target)) {
                return;
            }

            // The input has changed so finish editing.
            finishEditing(parent, value, context.getKey(), valueUpdater);

        } else if ("keyup".equals(eventType)) {
            // Ignore key up events that don't target the input.
            final InputElement input = getInputElement(parent);
            if (input == null || !input.isOrHasChild(target)) {
                return;
            }

            // Record keys as they are typed.
            final Object key = context.getKey();
            ViewData vd = getViewData(key);
            if (vd == null) {
                vd = new ViewData(String.valueOf(value));
                setViewData(key, vd);
            }

            vd.setCurrentValue(input.getValue());

        } else {
            final Element upArrow = getByClassName(parent, "valueSpinner-arrowUp");
            final Element downArrow = getByClassName(parent, "valueSpinner-arrowDown");

            if (upArrow != null && upArrow.isOrHasChild(target)) {
                if (BrowserEvents.MOUSEOVER.equals(eventType)) {
                    upArrow.setClassName("valueSpinner-arrow valueSpinner-arrowUp valueSpinner-arrowUp--hover");

                } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
                    upArrow.setClassName("valueSpinner-arrow valueSpinner-arrowUp");
                    stopSpinning(context, parent, value, valueUpdater);

                } else if (BrowserEvents.MOUSEUP.equals(eventType)) {
                    upArrow.setClassName("valueSpinner-arrow valueSpinner-arrowUp valueSpinner-arrowUp--hover");
                    stopSpinning(context, parent, value, valueUpdater);

                } else if (BrowserEvents.MOUSEDOWN.equals(eventType)) {
                    upArrow.setClassName("valueSpinner-arrow valueSpinner-arrowUp valueSpinner-arrowUp--pressed");
                    ensureSpinner();

                    // Get the object that we are going to use to apply
                    // value constraints.
                    HasSpinnerConstraints constraints = this;
                    if (value instanceof HasSpinnerConstraints) {
                        constraints = (HasSpinnerConstraints) value;
                    }

                    final InputElement input = getInputElement(parent);
                    if (input == null) {
                        spinner.start(constraints, true, 0, input);
                    } else {
                        final String constrainedValue = constrainValue(constraints, input.getValue());
                        spinner.start(constraints, true, Long.parseLong(constrainedValue), input);
                    }
                }
            } else if (downArrow != null && downArrow.isOrHasChild(target)) {
                if (BrowserEvents.MOUSEOVER.equals(eventType)) {
                    downArrow.setClassName("valueSpinner-arrow valueSpinner-arrowDown valueSpinner-arrowDown--hover");

                } else if (BrowserEvents.MOUSEOUT.equals(eventType)) {
                    downArrow.setClassName("valueSpinner-arrow valueSpinner-arrowDown");
                    stopSpinning(context, parent, value, valueUpdater);

                } else if (BrowserEvents.MOUSEUP.equals(eventType)) {
                    downArrow.setClassName("valueSpinner-arrow valueSpinner-arrowDown valueSpinner-arrowDown--hover");
                    stopSpinning(context, parent, value, valueUpdater);

                } else if (BrowserEvents.MOUSEDOWN.equals(eventType)) {
                    downArrow.setClassName("valueSpinner-arrow valueSpinner-arrowDown valueSpinner-arrowDown--pressed");
                    ensureSpinner();

                    // Get the object that we are going to use to apply
                    // value constraints.
                    HasSpinnerConstraints constraints = this;
                    if (value instanceof HasSpinnerConstraints) {
                        constraints = (HasSpinnerConstraints) value;
                    }

                    final InputElement input = getInputElement(parent);
                    final String constrainedValue = constrainValue(constraints, input.getValue());
                    spinner.start(constraints, false, Long.parseLong(constrainedValue), input);
                }
            }
        }
    }

    private Element getByClassName(final Element parent, final String className) {
        final NodeList<Element> nodes = parent.getElementsByTagName("div");
        for (int i = 0; i < nodes.getLength(); i++) {
            final Element child = nodes.getItem(i);
            if (ElementUtil.hasClassName(child, className)) {
                return child;
            }
        }
        return null;
    }

    private void ensureSpinner() {
        if (spinner == null) {
            synchronized (ValueSpinnerCell.class) {
                if (spinner == null) {
                    spinner = new Spinner();
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
                finishEditing(parent, value, context.getKey(), valueUpdater);
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
                finishEditing(parent, value, key, valueUpdater);
            } else {
                focusedKey = key;
                input.focus();
            }
        }
    }

    @Override
    public void render(final Context context, final Number value, final SafeHtmlBuilder sb) {
        // If the value isn't editable then just output the value.
        if (value != null) {
            if (!(value instanceof Editable) || !((Editable) value).isEditable()) {
                sb.append(SafeHtmlUtils.fromString(String.valueOf(value)));

            } else {
                // Get the view data.
                final Object key = context.getKey();

                // Get the current view data if there is some.
                ViewData viewData = getViewData(key);

                // If the value held by the view data object is the same as the
                // passed value then we no longer need the view data object to
                // hold temporary state.
                if (viewData != null && viewData.getCurrentValue() != null) {
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

                if (viewData != null) {
                    sb.append(template.input(viewData.getCurrentValue(), arrowUpHtml, arrowDownHtml));
                } else {
                    sb.append(template.input(String.valueOf(value), arrowUpHtml, arrowDownHtml));
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
                               final ValueUpdater<Number> valueUpdater) {
        // Get the input element.
        final InputElement input = getInputElement(parent);
        if (input != null) {
            final String newValue = input.getValue();

            // Get the object that we are going to use to apply value
            // constraints.
            HasSpinnerConstraints constraints = this;
            if (value instanceof HasSpinnerConstraints) {
                constraints = (HasSpinnerConstraints) value;
            }

            final String constrainedValue = constrainValue(constraints, newValue);
            // Set the value back if it has been constrained by the
            // constrainValue() method.
            if (!newValue.equals(constrainedValue)) {
                input.setValue(constrainedValue);
            }

            // Get the view data.
            ViewData vd = getViewData(key);
            if (vd == null) {
                vd = new ViewData(String.valueOf(value));
                setViewData(key, vd);
            }
            vd.setCurrentValue(constrainedValue);

            // Fire the value updater if the value has changed.
            if (valueUpdater != null && !vd.getCurrentValue().equals(vd.getLastValue())) {
                vd.setLastValue(vd.getCurrentValue());
                valueUpdater.update(Long.valueOf(constrainedValue));
            }
        }

        // Blur the element.
        focusedKey = null;
        getInputElement(parent).blur();
    }

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
        Element vsDiv = null;
        if (parent.hasTagName("div") && ElementUtil.hasClassName(parent, "valueSpinner")) {
            vsDiv = parent;
        } else {
            final NodeList<Element> divs = parent.getElementsByTagName("div");
            for (int i = 0; i < divs.getLength(); i++) {
                final Element div = divs.getItem(i);
                if (ElementUtil.hasClassName(div, "valueSpinner")) {
                    vsDiv = div;
                    break;
                }
            }
        }

        if (vsDiv != null) {
            final Element inputElement = vsDiv.getFirstChildElement();
            if (inputElement != null) {
                return inputElement.cast();
            }
        }

        return null;
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


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"valueSpinner\">" +
                "<input class=\"gwt-TextBox\" type=\"text\" value=\"{0}\" tabindex=\"-1\"></input>" +
                "<div class=\"arrows\">{1}{2}</div>" +
                "</div>")
        SafeHtml input(String value, SafeHtml imgUp, SafeHtml imgDown);
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
