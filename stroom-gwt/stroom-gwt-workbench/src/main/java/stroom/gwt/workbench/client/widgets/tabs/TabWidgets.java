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


package stroom.gwt.workbench.client.widgets.tabs;

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.StoryContent;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.svg.shared.SvgImage;
import stroom.widget.tab.client.TabData;
import stroom.widget.text.client.Html;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Widgets shared by the `Widgets/Tabs/*` stories.
final class TabWidgets {

    private TabWidgets() {
        // Static utility
    }

    /// Creates a harness with no REST fixtures, for its event bus, popup manager and Stroom's
    /// `Menu` listening on its bus, so that a tab bar's overflow selector (which fires a
    /// `ShowMenuEvent`) shows Stroom's real menu, on the page's body as in Stroom.
    ///
    /// @param context The story's context.
    /// @return The harness.
    static ScreenHarness menuHarness(final StoryContext context) {
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .withoutStartupFixtures()
                .build();
        return harness;
    }

    /// A tab's content: the text, padded.
    ///
    /// @param text The text.
    /// @param padding The CSS padding, e.g. `16px`.
    /// @param fontSize The CSS font size, or null to inherit it.
    /// @return The pane.
    static Widget pane(final String text, final String padding, final String fontSize) {
        final Html pane = new Html();
        pane.setText(text);
        pane.getElement().getStyle().setProperty("padding", padding);
        if (fontSize != null) {
            pane.getElement().getStyle().setProperty("fontSize", fontSize);
        }
        return pane;
    }

    /// Checks that a bar's overflow selector, once shown, looks like the bar's tabs: as high and
    /// in line with them, its arrows and its count on their labels' line, and inside the bar. (Its
    /// hover and focus ring, which need real input, are checked in `test-runner/lib/real-input/tabs.mjs`.)
    ///
    /// @param play        The play.
    /// @param barSelector The bar's CSS selector, e.g. `.tab-bar--curved`.
    static void expectSelectorLikeTabs(final Play play, final String barSelector) {
        final Query bar = play.querySelector(barSelector);
        final Query selector = play.querySelector(barSelector + " .tab-selector");
        play.waitFor(() -> play.expect(play.querySelector(barSelector + " .tab-selector:not(.tab-selector--hidden)"))
                .toBeInTheDocument());
        final Query tab = play.querySelector(barSelector + " .tab-bar__tab:not(.tab-bar__tab--hidden)");
        final Query label = play.within(tab).querySelector(".tab-bar__label");
        final Query arrows = play.within(selector).querySelector(".tab-selector__arrows svg");
        final Query count = play.within(selector).querySelector(".tab-selector__count");

        play.expect("the selector is as high as a tab", () -> near(selector.height().get(), tab.height().get(), 0.5))
                .toEqual(true);
        play.expect("the selector is in line with the tabs", () -> near(selector.top().get(), tab.top().get(), 0.5))
                .toEqual(true);
        // On the labels' line: the middle of the count's text and of the arrows, as of a label's text
        play.expect("the count is on the labels' line", () -> near(
                textMiddle(count.element().get()), textMiddle(label.element().get()), 1.5)).toEqual(true);
        play.expect("the arrows are on the labels' line", () -> near(
                (arrows.top().get() + arrows.bottom().get()) / 2, textMiddle(label.element().get()), 1.5))
                .toEqual(true);
        play.expect("the selector ends in the bar", () -> selector.right().get() <= bar.right().get())
                .toEqual(true);
    }

    private static boolean near(final double value, final double expected, final double within) {
        return Math.abs(value - expected) <= within;
    }

    /// @param element An element.
    /// @return What its `::before` draws (its computed `content`, e.g. `"|"` with the quotes), or
    ///         `none`.
    static native String beforeContent(Element element) /*-{
        return element.ownerDocument.defaultView.getComputedStyle(element, '::before').content;
    }-*/;

    // The vertical middle of an element's text, as drawn (not its box, which padding moves)
    private static native double textMiddle(Element element) /*-{
        var range = element.ownerDocument.createRange();
        range.selectNodeContents(element);
        var rect = range.getBoundingClientRect();
        return (rect.top + rect.bottom) / 2;
    }-*/;

    /// A controller showing a widget, as tab content.
    ///
    /// @param eventBus The event bus.
    /// @param content The content.
    /// @return The controller.
    static StoryContent content(final EventBus eventBus, final Widget content) {
        return new StoryContent(eventBus, content);
    }


    // --------------------------------------------------------------------------------


    /// A story tab, as Stroom's [TabData].
    static final class StoryTab implements TabData {

        private final String id;
        private final String label;
        private final String type;
        private final SvgImage icon;
        private final boolean closeable;
        private boolean dirty;

        /// @param id        The tab's id.
        /// @param label     The label.
        /// @param type      The document type.
        /// @param icon      The icon, or null for none.
        /// @param closeable Whether the tab has a close button.
        StoryTab(final String id,
                 final String label,
                 final String type,
                 final SvgImage icon,
                 final boolean closeable) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.icon = icon;
            this.closeable = closeable;
        }

        /// Marks the tab as dirty, which Stroom's document tabs show with a `* ` prefix on the
        /// label (e.g. `DocumentEditorHost.getLabel()`).
        ///
        /// @return This tab.
        StoryTab dirty() {
            dirty = true;
            return this;
        }

        /// @return The tab's id.
        String getId() {
            return id;
        }

        @Override
        public SvgImage getIcon() {
            return icon;
        }

        @Override
        public String getLabel() {
            return dirty
                    ? "* " + label
                    : label;
        }

        @Override
        public boolean isCloseable() {
            return closeable;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
