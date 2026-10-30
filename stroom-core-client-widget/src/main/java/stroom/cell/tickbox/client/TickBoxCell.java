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

package stroom.cell.tickbox.client;

import stroom.widget.tickbox.client.TickBox;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.cell.client.AbstractEditableCell;
import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * A {@link Cell} used to render a checkbox. The value of the checkbox may be
 * toggled using the ENTER key as well as via mouse click.
 * <p>
 * It is drawn as the tick box widget's box ({@code tick-box.css}): the block {@code tick-box} with
 * the {@code tick-box--cell} modifier, an element with {@code role="checkbox"} and
 * {@code aria-checked} (true, false or mixed, part ticked) holding the drawn box
 * ({@code tick-box__mark}). One that can't be clicked is {@code aria-readonly}, which draws the
 * read-only look.
 */
public class TickBoxCell extends AbstractEditableCell<TickBoxState, TickBoxState> {

    private static final Appearance DEFAULT_APPEARANCE = new DefaultAppearance();
    private final Appearance appearance;
    private final boolean dependsOnSelection;
    private final boolean handlesSelection;
    private final boolean clickable;

    /**
     * Construct a new {@link CheckboxCell} that optionally controls selection.
     *
     * @param dependsOnSelection true if the cell depends on the selection state
     * @param handlesSelection   true if the cell modifies the selection state
     */
    // A row's tick box's accessible name, e.g. "Create Pipeline", or null to leave it unnamed
    private IntFunction<String> nameForRow;

    private TickBoxCell(final Appearance appearance,
                        final boolean dependsOnSelection,
                        final boolean handlesSelection,
                        final Set<String> consumedEvents) {
        super(consumedEvents);
        this.appearance = appearance;
        this.dependsOnSelection = dependsOnSelection;
        this.handlesSelection = handlesSelection;
        this.clickable = consumedEvents.contains(BrowserEvents.MOUSEDOWN);
    }

    public static TickBoxCell create(final boolean dependsOnSelection, final boolean handlesSelection) {
        return create(DEFAULT_APPEARANCE, dependsOnSelection, handlesSelection, true);
    }

    public static TickBoxCell create(final Appearance appearance,
                                     final boolean dependsOnSelection,
                                     final boolean handlesSelection) {
        return create(appearance, dependsOnSelection, handlesSelection, true);
    }

    public static TickBoxCell create(final Appearance appearance,
                                     final boolean dependsOnSelection,
                                     final boolean handlesSelection,
                                     final boolean clickable) {
        final Set<String> consumedEvents = new HashSet<>();
        if (clickable) {
            consumedEvents.add(BrowserEvents.MOUSEDOWN);
            consumedEvents.add(BrowserEvents.KEYDOWN);
        }

        return new TickBoxCell(appearance, dependsOnSelection, handlesSelection, consumedEvents);
    }

    /// Name each row's tick box for its row, e.g. "Create Pipeline", rather than leaving assistive
    /// technology only its state ("Ticked", its title, which says what the mouse would see).
    ///
    /// @param nameForRow The name of the tick box of the row at an index (as the grid counts them).
    /// @return This cell.
    public TickBoxCell withNameForRow(final IntFunction<String> nameForRow) {
        this.nameForRow = nameForRow;
        return this;
    }

    // The tick box's HTML with its row's name, if it is given one
    private SafeHtml named(final SafeHtml html, final Context context) {
        if (nameForRow == null || context == null) {
            return html;
        }
        final String name = nameForRow.apply(context.getIndex());
        if (name == null || name.isEmpty()) {
            return html;
        }
        final String tickBox = html.asString();
        final String start = "<div ";
        if (!tickBox.startsWith(start)) {
            return html;
        }
        return SafeHtmlUtils.fromTrustedString(start + "aria-label=\"" + SafeHtmlUtils.htmlEscape(name) + "\" "
                                               + tickBox.substring(start.length()));
    }

    @Override
    public boolean dependsOnSelection() {
        return dependsOnSelection;
    }

    @Override
    public boolean handlesSelection() {
        return handlesSelection;
    }

    @Override
    public boolean isEditing(final Context context, final Element parent, final TickBoxState value) {
        // A checkbox is never in "edit mode". There is no intermediate state
        // between checked and unchecked.
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final TickBoxState value,
                               final NativeEvent event, final ValueUpdater<TickBoxState> valueUpdater) {
        if (value != null) {
            super.onBrowserEvent(context, parent, value, event, valueUpdater);
            final String type = event.getType();

            final Action action = KeyBinding.test(event);
            if (clickable && isTickBox(event) &&
                ((BrowserEvents.MOUSEDOWN.equals(type) && MouseUtil.isPrimary(event)) ||
                 (BrowserEvents.KEYDOWN.equals(type) && action == Action.SELECT))) {
                event.preventDefault();

                TickBoxState state = value;

                // Toggle the value if the enter key was pressed and the cell
                // handles selection or doesn't depend on selection. If the cell
                // depends on selection but doesn't handle selection, then
                // ignore the enter key and let the SelectionEventManager
                // determine which keys will trigger a change.
                if (handlesSelection() || !dependsOnSelection()) {
                    switch (value) {
                        case TICK:
                            state = TickBoxState.UNTICK;
                            break;
                        case HALF_TICK:
                        case UNTICK:
                            state = TickBoxState.TICK;
                            break;
                    }

                    // Update the tick image immediately.
                    final SafeHtml html = named(appearance.getHTML(state, clickable), context);
                    final Element active = activeElement();
                    final boolean focused = active != parent && parent.isOrHasChild(active);
                    parent.setInnerHTML(html.asString());
                    // A tick box with the focus (a grid keyboard's, ticked with Space) keeps it
                    if (focused) {
                        focusTickBox(parent);
                    }
                }

                // Save the new value. However, if the cell depends on the
                // selection, then do not save the value because we can get into
                // an inconsistent state.
                if (value != state && !dependsOnSelection()) {
                    setViewData(context.getKey(), state);
                } else {
                    clearViewData(context.getKey());
                }

                if (valueUpdater != null) {
                    valueUpdater.update(state);
                }
            }
        }
    }

    private static native Element activeElement() /*-{
        return $doc.activeElement;
    }-*/;

    // Focuses the tick box drawn in the cell, keeping it the grid's tab stop
    private static native void focusTickBox(Element parent) /*-{
        var tickBox = parent.querySelector('[role="checkbox"]');
        if (tickBox) {
            tickBox.tabIndex = 0;
            tickBox.focus({ preventScroll: true });
        }
    }-*/;

    /// @return Whether the tick can be changed by clicking it (or pressing Enter or Space).
    public boolean isClickable() {
        return clickable;
    }

    private boolean isTickBox(final NativeEvent event) {
        boolean isTickBox = false;
        Element element = event.getEventTarget().cast();
        for (int i = 0; i < 4 && !isTickBox && element != null; i++) {
            final String className = element.getClassName();
            try {
                isTickBox = className != null && className.contains(DefaultAppearance.BLOCK);
            } catch (final RuntimeException e) {
                // Ignore.
            }
            element = element.getParentElement();
        }
        return isTickBox;
    }

    @Override
    public void render(final Context context, final TickBoxState value, final SafeHtmlBuilder sb) {
        final SafeHtmlBuilder html = new SafeHtmlBuilder();
        appearance.render(this, context, value, html);
        sb.append(named(html.toSafeHtml(), context));
    }


    // --------------------------------------------------------------------------------


    /**
     * The appearance used to render this Cell.
     */
    public interface Appearance {

        /**
         * Render the button and its contents.
         *
         * @param cell    the cell that is being rendered
         * @param context the {@link Context} of the cell
         * @param value   the value that generated the content
         * @param sb      the {@link SafeHtmlBuilder} to render into
         */
        void render(TickBoxCell cell,
                    Context context,
                    TickBoxState value,
                    SafeHtmlBuilder sb);

        /**
         * The tick box for a state.
         *
         * @param value     the state
         * @param clickable whether the tick can be changed by clicking it
         */
        SafeHtml getHTML(TickBoxState value, boolean clickable);
    }


    // --------------------------------------------------------------------------------


    public static class DefaultAppearance implements Appearance {

        private static final String BLOCK = "tick-box";
        private static final String CELL_CLASS_NAMES = BLOCK + " " + BLOCK + "--cell";
        private static final SafeHtml TICK_SVG = SafeHtmlUtils.fromSafeConstant(TickBox.TICK_SVG);

        private static Template template;

        private final String additionalClassNames;

        public DefaultAppearance(final String additionalClassNames) {
            this.additionalClassNames = " " + additionalClassNames;
        }

        public DefaultAppearance() {
            this.additionalClassNames = "";
        }

        @Override
        public void render(final TickBoxCell cell,
                           final Context context,
                           final TickBoxState value,
                           final SafeHtmlBuilder sb) {
            // Get the view data.
            final Object key = context.getKey();
            final TickBoxState viewData = cell.getViewData(key);
            if (viewData != null && viewData.equals(value)) {
                cell.clearViewData(key);
            }

            if (value != null) {
                sb.append(getHTML(value, cell.isClickable()));
            }
        }

        @Override
        public SafeHtml getHTML(final TickBoxState value, final boolean clickable) {
            if (template == null) {
                template = GWT.create(Template.class);
            }

            // The tick box is a check box to assistive technology: half-ticked is 'mixed', and one
            // that can't be clicked is read only (it shows a value, as a read-only tick box does)
            final String ariaReadOnly = String.valueOf(!clickable);
            final SafeHtml safeHtml;
            final String className = CELL_CLASS_NAMES + additionalClassNames;
            switch (value) {
                case TICK:
                    safeHtml = template.tickBox("Ticked", className, "true", ariaReadOnly, TICK_SVG);
                    break;
                case HALF_TICK:
                    safeHtml = template.tickBox("Half-Ticked", className, "mixed", ariaReadOnly,
                            SafeHtmlUtils.EMPTY_SAFE_HTML);
                    break;
                case UNTICK:
                    safeHtml = template.tickBox("Not Ticked", className, "false", ariaReadOnly,
                            SafeHtmlUtils.EMPTY_SAFE_HTML);
                    break;
                default:
                    safeHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
                    break;
            }

            return safeHtml;
        }

        /// The tick box drawn for a state, as a picture only (hidden from assistive technology),
        /// for a row that says the state itself, e.g. a list's option (`aria-checked`), which may
        /// not hold a check box of its own.
        ///
        /// @param value The state.
        /// @return The tick box's HTML.
        public SafeHtml getPicture(final TickBoxState value) {
            if (value == null) {
                return SafeHtmlUtils.EMPTY_SAFE_HTML;
            }
            // Drawn as a tick box that can be clicked, as its row can
            final String html = getHTML(value, true).asString();
            return SafeHtmlUtils.fromTrustedString(html.replaceFirst("<div ", "<div aria-hidden=\"true\" "));
        }
    }


    // --------------------------------------------------------------------------------


    public interface Template extends SafeHtmlTemplates {

        /// The tick box: a check box to assistive technology, holding the drawn box, with the tick
        /// in it when ticked.
        @Template("<div title=\"{0}\" class=\"{1}\" role=\"checkbox\" aria-checked=\"{2}\" "
                  + "aria-readonly=\"{3}\"><span class=\"tick-box__mark\">{4}</span></div>")
        SafeHtml tickBox(String title, String className, String ariaChecked, String ariaReadOnly, SafeHtml tick);
    }


    // --------------------------------------------------------------------------------


    /// The appearance of a tick box that can't be changed. Its read-only look comes from its
    /// `aria-readonly` (a cell that isn't clickable), so it is the default appearance.
    public static class NoBorderAppearance extends DefaultAppearance {

        public NoBorderAppearance() {
            super();
        }
    }
}
