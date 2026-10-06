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

package stroom.gwt.workbench.client.widgets.display;

import stroom.data.client.presenter.CopyTextUtil;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.menu.client.presenter.Menu;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.menu.client.presenter.MenuViewImpl;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Stories for Stroom's copyable text ([CopyTextUtil], as rendered by `CopyTextCell` and in
/// e.g. the dashboard's current selection panel), matching `Widgets/Display/CopyText` in the
/// React Storybook.
///
/// There is no copy text widget in GWT: Stroom's `CurrentSelectionPresenter` renders
/// [CopyTextUtil#render] into an `HTML` widget and passes its mouse downs to
/// [CopyTextUtil#onClick], as the stories do. A left click on the copy icon copies the value,
/// on the insert icon inserts it, and a right click shows Stroom's menu (Copy, and Insert if
/// there is an insert handler), which needs Stroom's [Menu] listening on the event bus and a
/// popup manager, so the stories use a [ScreenHarness] (it makes no REST requests).
public final class CopyTextStories {

    private static final String ON_INSERT = "onInsert";

    private CopyTextStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's value arg is only a default for its Controls; the stories render fixed values
        registry.component("Widgets/Display/CopyText", CopyTextStories.class)
                .layout(StoryLayout.CENTERED)
                // Copy only: hover to show the copy icon; a left click copies, a right click shows a menu
                .story("Basic", context -> {
                    final ScreenHarness harness = harness(context);
                    harness.add(minWidth(copyText(harness, "a1b2c3d4-1234-5678-9abc-def012345678", false, null)));
                    return harness.asWidget();
                })
                // With an insert icon, reporting the inserted value below
                .story("WithInsert", CopyTextStories::withInsert)
                // A long value: the tooltip's preview is truncated at 30 characters
                .story("LongValue", context -> {
                    final ScreenHarness harness = harness(context);
                    // React shows the insert icon with no onInsert, so the icon does nothing and the
                    // menu has no Insert item; GWT's CopyTextUtil is the same with no insert handler
                    harness.add(minWidth(copyText(harness,
                            "this is a very long value that will be truncated in the copy tooltip preview",
                            true,
                            null)));
                    return harness.asWidget();
                });
    }

    private static Widget withInsert(final StoryContext context) {
        final ScreenHarness harness = harness(context);
        final Spy onInsert = context.fn(ON_INSERT);
        final InlineLabel inserted = StoryPanels.note("Click the insert icon…", "#888", "0.8rem");
        inserted.getElement().getStyle().setProperty("color", "var(--text-color--low-contrast, #888)");
        final Widget copyText = copyText(harness, "my-feed-name", true, value -> {
            onInsert.call(value);
            inserted.setText("Inserted: " + value);
        });
        final FlowPanel column = StoryPanels.column(8, copyText, inserted);
        column.getElement().getStyle().setProperty("minWidth", "320px");
        harness.add(column);
        return harness.asWidget();
    }

    private static ScreenHarness harness(final StoryContext context) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
        // Shows ShowMenuEvent's menu, as Stroom's Menu singleton does
        new Menu(harness.getEventBus(), () -> menuPresenter(harness.getEventBus()));
        return harness;
    }

    private static MenuPresenter menuPresenter(final EventBus eventBus) {
        return new MenuPresenter(eventBus, new MenuViewImpl(), () -> menuPresenter(eventBus));
    }

    /// The copyable text, as `CurrentSelectionPresenter` shows it.
    private static Widget copyText(final ScreenHarness harness,
                                   final String value,
                                   final boolean showInsert,
                                   final Consumer<String> insertHandler) {
        final HTML html = new HTML(CopyTextUtil.render(value, showInsert));
        html.addDomHandler(event -> CopyTextUtil.onClick(event.getNativeEvent(), harness.getHasHandlers(),
                insertHandler), MouseDownEvent.getType());
        return html;
    }

    /// `<div style={{minWidth: 320}}>`.
    private static Widget minWidth(final Widget widget) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("minWidth", "320px");
        panel.add(widget);
        return panel;
    }
}
