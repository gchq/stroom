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

import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.tab.client.presenter.LinkTabsPresenter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.LinkTabsLayoutViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Stories for Stroom's [LinkTabsPresenter] (with its [LinkTabsLayoutViewImpl]), matching
/// `Widgets/Tabs/LinkTabsWidget` in the React Storybook: a link tab bar over the selected tab's
/// content.
public final class LinkTabsWidgetStories {

    private static final String ON_SELECT_TAB = "onSelectTab";

    private LinkTabsWidgetStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Tabs/LinkTabsWidget", LinkTabsWidgetStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Three tabs with content switching
                .story("Basic", context -> {
                    final ScreenHarness harness = TabWidgets.menuHarness(context);
                    final EventBus eventBus = harness.getEventBus();
                    final LinkTabsPresenter presenter = new LinkTabsPresenter(eventBus,
                            new LinkTabsLayoutViewImpl(GWT.create(LinkTabsLayoutViewImpl.Binder.class)));
                    harness.unbindOnCleanUp(presenter);
                    presenter.bind();

                    // Differs from React: each Stroom tab has its own content (a layer), rather than
                    // one content element whose text follows the selected tab
                    final List<String> labels = List.of("Overview", "Settings", "Advanced");
                    TabData first = null;
                    for (final String label : labels) {
                        final TabData tab = presenter.addTab(label, TabWidgets.content(eventBus,
                                TabWidgets.pane("Content for the " + label + " tab.", "12px", null)));
                        if (first == null) {
                            first = tab;
                        }
                    }
                    presenter.changeSelectedTab(first);

                    final Spy onSelectTab = context.fn(ON_SELECT_TAB);
                    harness.addRegistration(presenter.getView().getTabBar().addSelectionHandler(event ->
                            onSelectTab.call(labels.indexOf(event.getSelectedItem().getLabel()))));

                    final FlowPanel frame = new FlowPanel();
                    frame.getElement().getStyle().setProperty("height", "220px");
                    frame.add(presenter.getWidget());
                    harness.add(frame);
                    return harness.asWidget();
                });
    }
}
