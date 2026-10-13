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
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.svg.shared.SvgImage;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.ViewImpl;

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
        final HTML pane = new HTML();
        pane.setText(text);
        pane.getElement().getStyle().setProperty("padding", padding);
        if (fontSize != null) {
            pane.getElement().getStyle().setProperty("fontSize", fontSize);
        }
        return pane;
    }

    /// A presenter showing a widget, as tab content: Stroom's tab panels show [MyPresenterWidget]s
    /// (which are layers) in their layer containers.
    ///
    /// @param eventBus The event bus.
    /// @param content The content.
    /// @return The presenter.
    static ContentPresenter content(final EventBus eventBus, final Widget content) {
        return new ContentPresenter(eventBus, new ContentView(content));
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
        /// label (e.g. `DocTabPresenter.getLabel()`).
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


    // --------------------------------------------------------------------------------


    /// Tab content: a presenter (and so a layer) showing a widget.
    static final class ContentPresenter extends MyPresenterWidget<ContentView> {

        private ContentPresenter(final EventBus eventBus, final ContentView view) {
            super(eventBus, view);
        }
    }


    // --------------------------------------------------------------------------------


    /// The view of [ContentPresenter].
    static final class ContentView extends ViewImpl {

        private final Widget widget;

        private ContentView(final Widget widget) {
            this.widget = widget;
        }

        @Override
        public Widget asWidget() {
            return widget;
        }
    }
}
