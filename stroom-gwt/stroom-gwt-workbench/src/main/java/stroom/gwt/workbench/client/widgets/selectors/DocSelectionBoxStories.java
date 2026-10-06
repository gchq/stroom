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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.client.presenter.ExplorerPopupPresenter;
import stroom.explorer.shared.NodeFlag;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DocSelectionBoxPresenter], matching `Widgets/Selectors/DocSelectionBox`
/// in the React Storybook: a drop-down showing the chosen document, which opens Stroom's explorer
/// popup to choose another. The popup's tree is fetched from an [ExplorerFixture] of the React
/// stories' tree.
public final class DocSelectionBoxStories {

    private static final String ON_CHANGE = "onChange";
    private static final String BOX_WIDTH = "340px";

    private DocSelectionBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's onClick (a legacy hook) has no equivalent. The explorer popup always offers a
        // "None" row in Stroom (React's includeNullSelection).
        registry.component("Widgets/Selectors/DocSelectionBox", DocSelectionBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Click the box (or press Enter) to open the explorer popup, open a folder, pick a
                // document and press OK or double-click it
                .story("Default", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionBoxPresenter box = box(context, harness, null);
                    final Label echo = echo("Nothing picked yet — click the box.");
                    harness.addRegistration(box.addDataSelectionHandler(event -> {
                        final DocRef docRef = event.getSelectedItem();
                        echo.setText(docRef == null
                                ? "Picked: (none) — - (-)"
                                : "Picked: " + docRef.getName() + " — " + docRef.getType()
                                  + " (" + docRef.getUuid() + ")");
                    }));
                    return frame(harness, box.getWidget(), echo);
                })
                // Pre-selected - the field starts with a chosen document name, and the popup offers a
                // "None" row to clear it
                .story("WithSelection", context -> {
                    final ExplorerFixture fixture = TreeFixtures.fixtureTree();
                    final ScreenHarness harness = harness(context, fixture);
                    final DocSelectionBoxPresenter box = box(context, harness, "Choose Pipeline");
                    box.setItemType("Pipeline");
                    box.setSelectedEntityReference(fixture.toDocRef("Ingest"), false);
                    final Label echo = echo("Current value: \"Ingest\"");
                    harness.addRegistration(box.addDataSelectionHandler(event -> echo.setText(
                            "Current value: \"" + (event.getSelectedItem() == null
                                    ? "(none)"
                                    : event.getSelectedItem().getName()) + "\"")));
                    return frame(harness, box.getWidget(), echo);
                })
                // Warning + disabled - the alert icon appears when the document can't be found (click
                // it for the message); the disabled box doesn't open the popup
                .story("WarningAndDisabled", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionBoxPresenter warning = box(context, harness, null);
                    // Differs from React: the warning comes from Stroom checking the document
                    // (`decorate` finds no such feed), so its message is Stroom's, not the story's
                    warning.setSelectedEntityReference(new DocRef("Feed", "deleted-feed", "Deleted Feed"), true);
                    final DocSelectionBoxPresenter disabled = box(context, harness, null);
                    disabled.setSelectedEntityReference(new DocRef("Pipeline", "locked-pipeline", "Locked Pipeline"),
                            false);
                    disabled.setEnabled(false);
                    final FlowPanel column = frame(harness, warning.getWidget(), null);
                    column.getElement().getStyle().setProperty("display", "flex");
                    column.getElement().getStyle().setProperty("flexDirection", "column");
                    column.getElement().getStyle().setProperty("gap", "16px");
                    column.add(disabled.getWidget());
                    return harness.asWidget();
                })
                // The node flags filter (e.g. data sources) is sent with the popup's tree fetch
                .story("NodeFlagsForwarded", context -> {
                    final ScreenHarness harness = harness(context, new ExplorerFixture());
                    final DocSelectionBoxPresenter box = box(context, harness, null);
                    box.setNodeFlags(NodeFlag.DATA_SOURCE);
                    box.setItemType("Data Source");
                    harness.add(box.getWidget());
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    // Open the popup, which fetches the tree with the node flags filter
                    play.fireEvent().mouseDown(play.querySelector(".dropDownView-container"), EventInit.create());
                    // Differs from React: the request is Stroom's, checked with the request spy
                    // (NodeFlag DATA_SOURCE is sent as its short form, "D")
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("\"nodeFlags\":[\"D\"]")));
                })
                // Type-filtered popup - only Feed and Pipeline documents (and their folders) are shown
                .story("TypeFiltered", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionBoxPresenter box = box(context, harness, null);
                    box.setIncludedTypes("Feed", "Pipeline");
                    return frame(harness, box.getWidget(), echo("Popup shows only Feeds & Pipelines."));
                });
    }

    private static ScreenHarness harness(final StoryContext context, final ExplorerFixture fixture) {
        return ScreenHarness.create(context, TreeFixtures.explorerRoutes(fixture)
                // Stroom checks a document exists (and gets its current name) with `decorate`;
                // only the stories' "Deleted Feed" is checked, and it doesn't exist
                .post(TreeFixtures.DECORATE, RestReply.json("null"))
                .build());
    }

    private static DocSelectionBoxPresenter box(final StoryContext context,
                                                final ScreenHarness harness,
                                                final String caption) {
        final ExplorerPopupPresenter popup = SelectorWidgets.explorerPopup(harness);
        if (caption != null) {
            popup.setCaption(caption);
        }
        final DocSelectionBoxPresenter box = SelectorWidgets.docSelectionBox(harness, popup);
        harness.unbindOnCleanUp(box);
        box.bind();
        final Spy onChange = context.fn(ON_CHANGE);
        harness.addRegistration(box.addDataSelectionHandler(event -> onChange.call(event.getSelectedItem() == null
                ? null
                : event.getSelectedItem().getUuid())));
        return box;
    }

    /// Equivalent of `<div style={{width: 340}}>{box}<p ...>{echo}</p></div>`.
    private static FlowPanel frame(final ScreenHarness harness, final Widget box, final Label echo) {
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("width", BOX_WIDTH);
        frame.add(box);
        if (echo != null) {
            frame.add(echo);
        }
        harness.add(frame);
        return frame;
    }

    /// Equivalent of `<p style={{fontSize: 12, marginTop: 8}}>`.
    private static Label echo(final String text) {
        final Label echo = new Label(text);
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        echo.getElement().getStyle().setProperty("marginTop", "8px");
        return echo;
    }
}
