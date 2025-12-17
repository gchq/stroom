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

package stroom.cell.tickbox.client;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

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

/**
 * A {@link Cell} used to render a checkbox. The value of the checkbox may be
 * toggled using the ENTER key as well as via mouse click.
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
                    final SafeHtml html = appearance.getHTML(state);
                    parent.setInnerHTML(html.asString());
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

    private boolean isTickBox(final NativeEvent event) {
        boolean isTickBox = false;
        Element element = event.getEventTarget().cast();
        for (int i = 0; i < 4 && !isTickBox && element != null; i++) {
            final String className = element.getClassName();
            try {
                isTickBox = className != null && className.contains("tickBox");
            } catch (final RuntimeException e) {
                // Ignore.
            }
            element = element.getParentElement();
        }
        return isTickBox;
    }

    @Override
    public void render(final Context context, final TickBoxState value, final SafeHtmlBuilder sb) {
        appearance.render(this, context, value, sb);
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

        SafeHtml getHTML(TickBoxState value);
    }


    // --------------------------------------------------------------------------------


    public static class DefaultAppearance implements Appearance {

        private static final String TICKBOX_CLASSNAME = "tickBox";
        private static final String TICK = "tickBox-tick";
        private static final String HALF_TICK = "tickBox-halfTick";
        private static final String UNTICK = "tickBox-untick";
        private static final String HALF_TICK_INNER = "tickBox-halfTick-inner";

        private static Template template;

        private final String additionalClassNames;

        public DefaultAppearance(final String additionalClassNames) {
            this.additionalClassNames = " " + additionalClassNames + " ";
        }

        public DefaultAppearance() {
            this.additionalClassNames = " ";
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
                sb.append(getHTML(value));
            }
        }

        @Override
        public SafeHtml getHTML(final TickBoxState value) {
            if (template == null) {
                template = GWT.create(Template.class);
            }

            final SafeHtml safeHtml;
            switch (value) {
                case TICK:
                    safeHtml = SvgImageUtil.toSafeHtml(
                            "Ticked",
                            SvgImage.TICK,
                            TICKBOX_CLASSNAME,
                            additionalClassNames,
                            TICK);
                    break;
                case HALF_TICK:
                    safeHtml = template.halfTick(
                            "Half-Ticked",
                            TICKBOX_CLASSNAME + additionalClassNames + HALF_TICK,
                            HALF_TICK_INNER);
                    break;
                case UNTICK:
                    safeHtml = template.untick(
                            "Not Ticked",
                            TICKBOX_CLASSNAME + additionalClassNames + UNTICK);
                    break;
                default:
                    safeHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
                    break;
            }

            return safeHtml;
        }
    }


    // --------------------------------------------------------------------------------


    public interface Template extends SafeHtmlTemplates {

        @Template("<div title=\"{0}\" class=\"{1}\"><div class=\"{2}\"></div></div>")
        SafeHtml halfTick(String title, String outerClassName, String innerClassName);

        @Template("<div title=\"{0}\" class=\"{1}\"></div>")
        SafeHtml untick(String title, String outerClassName);
    }


    // --------------------------------------------------------------------------------


    public static class NoBorderAppearance extends DefaultAppearance {

        public NoBorderAppearance() {
            super("tickBox-noBorder");
        }
    }
}
