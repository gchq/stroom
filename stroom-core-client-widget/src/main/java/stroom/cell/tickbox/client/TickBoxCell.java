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

import com.google.gwt.cell.client.AbstractEditableCell;
import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.CssResource.ImportedWithPrefix;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import stroom.cell.tickbox.shared.TickBoxState;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link Cell} used to render a checkbox. The value of the checkbox may be
 * toggled using the ENTER key as well as via mouse click.
 */
public class TickBoxCell extends AbstractEditableCell<TickBoxState, TickBoxState> {
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
        void render(TickBoxCell cell, Context context, TickBoxState value, SafeHtmlBuilder sb);

        SafeHtml getTick();

        SafeHtml getHalfTick();

        SafeHtml getUntick();

        SafeHtml getHTML(SafeHtml image);
    }

    public static class DefaultAppearance implements Appearance {
        public interface Template extends SafeHtmlTemplates {
            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml outerDiv(String outerClass, SafeHtml icon);
        }

        @ImportedWithPrefix("stroom-tickbox")
        public interface Style extends CssResource {
            String DEFAULT_CSS = "TickBox.css";

            String outer();
        }

        public interface Resources extends ClientBundle {
            ImageResource tick();

            ImageResource halfTick();

            ImageResource untick();

            @Source(Style.DEFAULT_CSS)
            Style style();
        }

        private final Resources resources;
        private final Template template;

        private final SafeHtml imgTick;
        private final SafeHtml imgHalfTick;
        private final SafeHtml imgUntick;

        public DefaultAppearance() {
            resources = GWT.create(Resources.class);
            template = GWT.create(Template.class);
            imgTick = SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(resources.tick()).getHTML());
            imgHalfTick = SafeHtmlUtils
                    .fromTrustedString(AbstractImagePrototype.create(resources.halfTick()).getHTML());
            imgUntick = SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(resources.untick()).getHTML());

            // Make sure the CSS is injected.
            resources.style().ensureInjected();
        }

        @Override
        public void render(final TickBoxCell cell, final Context context, final TickBoxState value,
                           final SafeHtmlBuilder sb) {
            // Get the view data.
            final Object key = context.getKey();
            TickBoxState viewData = cell.getViewData(key);
            if (viewData != null && viewData.equals(value)) {
                cell.clearViewData(key);
                viewData = null;
            }

            if (value != null) {
                SafeHtml image = null;

                switch (value) {
                    case TICK:
                        image = imgTick;
                        break;
                    case HALF_TICK:
                        image = imgHalfTick;
                        break;
                    case UNTICK:
                        image = imgUntick;
                        break;
                }

                sb.append(template.outerDiv(resources.style().outer(), image));
            }
        }

        @Override
        public SafeHtml getTick() {
            return imgTick;
        }

        @Override
        public SafeHtml getHalfTick() {
            return imgHalfTick;
        }

        @Override
        public SafeHtml getUntick() {
            return imgUntick;
        }

        @Override
        public SafeHtml getHTML(final SafeHtml image) {
            return template.outerDiv(resources.style().outer(), image);
        }
    }

    public static class NoBorderAppearance implements Appearance {
        public interface Template extends SafeHtmlTemplates {
            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml outerDiv(String outerClass, SafeHtml icon);
        }

        @ImportedWithPrefix("stroom-tickbox")
        public interface Style extends CssResource {
            String DEFAULT_CSS = "TickBoxWithMargin.css";

            String outer();
        }

        public interface Resources extends ClientBundle {
            ImageResource tickNB();

            ImageResource halfTickNB();

            ImageResource untickNB();

            @Source(Style.DEFAULT_CSS)
            Style style();
        }

        private final Resources resources;
        private final Template template;

        private final SafeHtml imgTick;
        private final SafeHtml imgHalfTick;
        private final SafeHtml imgUntick;

        public NoBorderAppearance() {
            resources = GWT.create(Resources.class);
            template = GWT.create(Template.class);
            imgTick = SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(resources.tickNB()).getHTML());
            imgHalfTick = SafeHtmlUtils
                    .fromTrustedString(AbstractImagePrototype.create(resources.halfTickNB()).getHTML());
            imgUntick = SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(resources.untickNB()).getHTML());

            // Make sure the CSS is injected.
            resources.style().ensureInjected();
        }

        @Override
        public void render(final TickBoxCell cell, final Context context, final TickBoxState value,
                           final SafeHtmlBuilder sb) {
            // Get the view data.
            final Object key = context.getKey();
            TickBoxState viewData = cell.getViewData(key);
            if (viewData != null && viewData.equals(value)) {
                cell.clearViewData(key);
                viewData = null;
            }

            if (value != null) {
                SafeHtml image = null;

                switch (value) {
                    case TICK:
                        image = imgTick;
                        break;
                    case HALF_TICK:
                        image = imgHalfTick;
                        break;
                    case UNTICK:
                        image = imgUntick;
                        break;
                }

                sb.append(template.outerDiv(resources.style().outer(), image));
            }
        }

        @Override
        public SafeHtml getTick() {
            return imgTick;
        }

        @Override
        public SafeHtml getHalfTick() {
            return imgHalfTick;
        }

        @Override
        public SafeHtml getUntick() {
            return imgUntick;
        }

        @Override
        public SafeHtml getHTML(final SafeHtml image) {
            return template.outerDiv(resources.style().outer(), image);
        }
    }

    public static class MarginAppearance implements Appearance {
        public interface Template extends SafeHtmlTemplates {
            @Template("<div class=\"{0}\">{1}</div>")
            SafeHtml outerDiv(String outerClass, SafeHtml icon);
        }

        @ImportedWithPrefix("stroom-tickbox")
        public interface Style extends CssResource {
            String DEFAULT_CSS = "TickBoxWithMargin.css";

            String outer();
        }

        public interface Resources extends ClientBundle {
            ImageResource tick();

            ImageResource halfTick();

            ImageResource untick();

            @Source(Style.DEFAULT_CSS)
            Style style();
        }

        private final Resources resources;
        private final Template template;

        private final SafeHtml imgTick;
        private final SafeHtml imgHalfTick;
        private final SafeHtml imgUntick;

        public MarginAppearance() {
            resources = GWT.create(Resources.class);
            template = GWT.create(Template.class);
            imgTick = SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(resources.tick()).getHTML());
            imgHalfTick = SafeHtmlUtils
                    .fromTrustedString(AbstractImagePrototype.create(resources.halfTick()).getHTML());
            imgUntick = SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(resources.untick()).getHTML());

            // Make sure the CSS is injected.
            resources.style().ensureInjected();
        }

        @Override
        public void render(final TickBoxCell cell, final Context context, final TickBoxState value,
                           final SafeHtmlBuilder sb) {
            // Get the view data.
            final Object key = context.getKey();
            TickBoxState viewData = cell.getViewData(key);
            if (viewData != null && viewData.equals(value)) {
                cell.clearViewData(key);
                viewData = null;
            }

            if (value != null) {
                SafeHtml image = null;

                switch (value) {
                    case TICK:
                        image = imgTick;
                        break;
                    case HALF_TICK:
                        image = imgHalfTick;
                        break;
                    case UNTICK:
                        image = imgUntick;
                        break;
                }

                sb.append(template.outerDiv(resources.style().outer(), image));
            }
        }

        @Override
        public SafeHtml getTick() {
            return imgTick;
        }

        @Override
        public SafeHtml getHalfTick() {
            return imgHalfTick;
        }

        @Override
        public SafeHtml getUntick() {
            return imgUntick;
        }

        @Override
        public SafeHtml getHTML(final SafeHtml image) {
            return template.outerDiv(resources.style().outer(), image);
        }
    }

    private static final Appearance DEFAULT_APPEARANCE = new DefaultAppearance();

    private final Appearance appearance;
    private final boolean dependsOnSelection;
    private final boolean handlesSelection;
    private final boolean clickable;

    public static TickBoxCell create(final boolean dependsOnSelection, final boolean handlesSelection) {
        return create(DEFAULT_APPEARANCE, dependsOnSelection, handlesSelection, true);
    }

    public static TickBoxCell create(final Appearance appearance, final boolean dependsOnSelection, final boolean handlesSelection) {
        return create(appearance, dependsOnSelection, handlesSelection, true);
    }

    public static TickBoxCell create(final Appearance appearance, final boolean dependsOnSelection, final boolean handlesSelection, final boolean clickable) {
        final Set<String> consumedEvents = new HashSet<>();
        if (clickable) {
            consumedEvents.add("click");
        }

        return new TickBoxCell(appearance, dependsOnSelection, handlesSelection, consumedEvents);
    }

    /**
     * Construct a new {@link CheckboxCell} that optionally controls selection.
     *
     * @param dependsOnSelection true if the cell depends on the selection state
     * @param handlesSelection   true if the cell modifies the selection state
     */
    private TickBoxCell(final Appearance appearance, final boolean dependsOnSelection, final boolean handlesSelection,
                        final Set<String> consumedEvents) {
        super(consumedEvents);
        this.appearance = appearance;
        this.dependsOnSelection = dependsOnSelection;
        this.handlesSelection = handlesSelection;
        this.clickable = consumedEvents.contains("click");
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

            final Element target = event.getEventTarget().cast();
            if ("IMG".equalsIgnoreCase(target.getTagName()) && clickable && "click".equals(type) && (event.getButton() & NativeEvent.BUTTON_LEFT) != 0) {
                TickBoxState state = value;
                SafeHtml image = appearance.getTick();

                // Toggle the value if the enter key was pressed and the cell
                // handles selection or doesn't depend on selection. If the cell
                // depends on selection but doesn't handle selection, then
                // ignore the enter key and let the SelectionEventManager
                // determine which keys will trigger a change.
                if (handlesSelection() || !dependsOnSelection()) {
                    switch (value) {
                        case TICK:
                            state = TickBoxState.UNTICK;
                            image = appearance.getUntick();
                            break;
                        case HALF_TICK:
                            state = TickBoxState.TICK;
                            image = appearance.getTick();
                            break;
                        case UNTICK:
                            state = TickBoxState.TICK;
                            image = appearance.getTick();
                            break;
                    }

                    // Update the tick image immediately.
                    final SafeHtml html = appearance.getHTML(image);
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

    @Override
    public void render(final Context context, final TickBoxState value, final SafeHtmlBuilder sb) {
        appearance.render(this, context, value, sb);
    }
}
