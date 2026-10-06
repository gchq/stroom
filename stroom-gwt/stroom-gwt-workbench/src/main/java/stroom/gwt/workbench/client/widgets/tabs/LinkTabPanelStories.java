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

import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.HasToolbar;
import stroom.entity.client.presenter.LinkTabPanelPresenter;
import stroom.entity.client.view.LinkTabPanelViewImpl;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.tabs.TabWidgets.StoryTab;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Stories for Stroom's [LinkTabPanelPresenter] (with its [LinkTabPanelViewImpl]), matching
/// `Widgets/Tabs/LinkTabPanel` in the React Storybook: a link tab bar, an optional toolbar and the
/// selected tab's content.
///
/// The presenter is abstract (Stroom's document and data screens extend it), so the stories
/// extend it with tabs showing fixed content. The toolbars are handled as Stroom's
/// `DocTabPresenter` does: its own toolbar, then any toolbars of a content presenter that
/// [HasToolbar].
public final class LinkTabPanelStories {

    private static final String PANE_PADDING = "16px";
    private static final String PANE_FONT_SIZE = "0.85rem";

    private LinkTabPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Tabs/LinkTabPanel", LinkTabPanelStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Basic tab panel with internal selection state
                .story("Basic", context -> panel(context, sampleTabs(), null, null, false))
                // With a global toolbar (Save / Save As) between the tab bar and content
                .story("WithToolbar", context -> panel(context, sampleTabs(), null,
                        toolbar(SvgButton.create(SvgPresets.SAVE.enabled(true)),
                                SvgButton.create(SvgPresets.SAVE_AS.enabled(true))), false))
                // Many tabs in a narrow bar - the chevron overflow selector (») appears
                .story("Overflow", context -> {
                    final List<String> many = new ArrayList<>();
                    for (int i = 1; i <= 12; i++) {
                        many.add("Tab " + i);
                    }
                    return panel(context, many, null, null, true);
                })
                // A tab with per-tab extra toolbar widgets, shown only when that tab is active
                .story("PerTabToolbar", context -> panel(context,
                        List.of("Data", "Query"),
                        "Query",
                        toolbar(SvgButton.create(SvgPresets.SAVE.enabled(true))), false));
    }

    private static List<String> sampleTabs() {
        return List.of("Data", "Info", "Related", "Meta");
    }

    private static String text(final String label, final boolean perTabToolbar) {
        if (perTabToolbar) {
            return "Data".equals(label)
                    ? "Data pane — no extra toolbar."
                    : "Query pane — has its own extra toolbar buttons.";
        }
        switch (label) {
            case "Data":
                return "Data content here — metadata rows, source events, etc.";
            case "Info":
                return "Information about this stream — feed, pipeline, dates.";
            case "Related":
                return "Related streams linked to this meta record.";
            case "Meta":
                return "Raw meta attributes and error markers.";
            default:
                return "Content for " + label.toLowerCase() + ".";
        }
    }

    /// Equivalent of `<div style={{height: 260}}><LinkTabPanel .../></div>`.
    ///
    /// @param labels The tab labels.
    /// @param extraToolbarTab The label of the tab with its own toolbar (a Save query button), or
    ///                        null for none.
    /// @param toolbar The panel's toolbar, or null for none.
    /// @param narrow Whether the panel is in a narrow (320px) bordered box.
    private static Widget panel(final StoryContext context,
                                final List<String> labels,
                                final String extraToolbarTab,
                                final ButtonPanel toolbar,
                                final boolean narrow) {
        final ScreenHarness harness = TabWidgets.menuHarness(context);
        final EventBus eventBus = harness.getEventBus();
        final Map<TabData, PresenterWidget<?>> contents = new HashMap<>();
        final List<TabData> tabs = new ArrayList<>();
        for (final String label : labels) {
            final TabData tab = new StoryTab(label, label, null, null, false);
            tabs.add(tab);
            final Widget pane = TabWidgets.pane(text(label, extraToolbarTab != null), PANE_PADDING, PANE_FONT_SIZE);
            if (label.equals(extraToolbarTab)) {
                contents.put(tab, new ToolbarContentPresenter(eventBus, pane,
                        SvgButton.create(SvgPresets.SAVE.with("Save query", true))));
            } else {
                contents.put(tab, TabWidgets.content(eventBus, pane));
            }
        }

        final StoryLinkTabPanelPresenter presenter = new StoryLinkTabPanelPresenter(eventBus,
                new LinkTabPanelViewImpl(GWT.create(LinkTabPanelViewImpl.Binder.class)), contents, toolbar);
        harness.unbindOnCleanUp(presenter);
        presenter.bind();
        harness.addRegistration(presenter.getView().getTabBar()
                .addShowMenuHandler(event -> eventBus.fireEvent(event)));
        for (final TabData tab : tabs) {
            presenter.addTab(tab);
        }
        presenter.selectTab(tabs.get(0));

        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("height", "260px");
        if (narrow) {
            style.setProperty("width", "320px");
            style.setProperty("border", "1px solid var(--panel__border-color, #ccc)");
        }
        frame.add(presenter.getWidget());
        harness.add(frame);
        return harness.asWidget();
    }

    private static ButtonPanel toolbar(final SvgButton... buttons) {
        final ButtonPanel toolbar = new ButtonPanel();
        for (final SvgButton button : buttons) {
            toolbar.addButton(button);
        }
        return toolbar;
    }


    // --------------------------------------------------------------------------------


    /// A link tab panel with fixed content for each tab.
    private static final class StoryLinkTabPanelPresenter extends LinkTabPanelPresenter {

        private final Map<TabData, PresenterWidget<?>> contents;
        private final ButtonPanel toolbar;

        private StoryLinkTabPanelPresenter(final EventBus eventBus,
                                           final LinkTabPanelViewImpl view,
                                           final Map<TabData, PresenterWidget<?>> contents,
                                           final ButtonPanel toolbar) {
            super(eventBus, view);
            this.contents = contents;
            this.toolbar = toolbar;
        }

        @Override
        protected void getContent(final TabData tab, final ContentCallback callback) {
            callback.onReady(contents.get(tab));
        }

        @Override
        protected TabData getPermissionsTab() {
            // No permissions tab
            return null;
        }

        @Override
        protected void afterSelectTab(final PresenterWidget<?> content) {
            // As DocTabPresenter updates the toolbar for the selected content
            getView().clearToolbar();
            if (toolbar != null) {
                getView().addToolbar(toolbar);
            }
            if (content instanceof HasToolbar) {
                for (final Widget widget : ((HasToolbar) content).getToolbars()) {
                    getView().addToolbar(widget);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// Tab content with its own toolbar.
    private static final class ToolbarContentPresenter
            extends MyPresenterWidget<TabWidgets.ContentView>
            implements HasToolbar {

        private final ButtonPanel extraToolbar;

        private ToolbarContentPresenter(final EventBus eventBus, final Widget pane, final SvgButton button) {
            super(eventBus, TabWidgets.content(eventBus, pane).getView());
            extraToolbar = new ButtonPanel();
            extraToolbar.addButton(button);
        }

        @Override
        public List<Widget> getToolbars() {
            return List.of(extraToolbar);
        }
    }
}
