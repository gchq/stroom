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
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.client.component.DocumentChooserController;
import stroom.explorer.shared.NodeFlag;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Label;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DocSelectionController] attached to a `DropDownBox`: a drop-down showing
/// the chosen document, which opens Stroom's explorer popup to choose another. The popup's tree is
/// fetched from an [ExplorerFixture].
public final class DocSelectionStories {

    private static final String ON_CHANGE = "onChange";
    private static final String BOX_WIDTH = "340px";

    private DocSelectionStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // The explorer popup always offers a "None" row.
        registry.component("Widgets/Selectors/DocSelectionBox", DocSelectionStories.class)
                .layout(StoryLayout.CENTERED)
                // Click the box (or press Enter) to open the explorer popup, open a folder, pick a
                // document and press OK or double-click it
                .story("Default", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionController box = box(context, harness, null);
                    final Label echo = echo("Nothing picked yet — click the box.");
                    harness.addRegistration(box.addSelectionListener(selected -> {
                        final DocRef docRef = selected;
                        echo.setText(docRef == null
                                ? "Picked: (none) — - (-)"
                                : "Picked: " + docRef.getName() + " — " + docRef.getType()
                                  + " (" + docRef.getUuid() + ")");
                    }));
                    return frame(harness, box.getBox(), echo);
                })
                // Pre-selected - the field starts with a chosen document name, and the popup offers a
                // "None" row to clear it
                .story("WithSelection", DocSelectionStories::withSelection)
                // The dialog from the keyboard: Enter opens it, modal, with the focus in its filter;
                // Down goes to the chosen document, and moving chooses; Tab stays in the dialog;
                // Escape cancels (putting back what was chosen) and Enter on a document chooses it,
                // each returning the focus to the picker
                .story("KeysInTheDialog", DocSelectionStories::withSelection)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query picker = play.querySelector(".drop-down");
                    play.tab();
                    play.expect(picker).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.getByRole("searchbox", "Filter documents")).toHaveFocus());
                    play.expect(screen.getByRole("dialog")).toHaveAttribute("aria-modal", "true");
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.querySelector(":focus")).toHaveTextContent("Ingest"));
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.querySelector(":focus")).toHaveTextContent("Normalise"));
                    play.waitFor(() -> play.expect(screen.getByText("Normalise chosen")).toBeInTheDocument());
                    // Tab from Cancel goes back to the first tab stop (the title bar's window options) and
                    // on to the filter; Shift+Tab from the window options goes to Cancel
                    play.tab();
                    play.tab();
                    play.expect(screen.getByRole("button", StroomDom.button("Cancel"))).toHaveFocus();
                    play.tab();
                    play.expect(screen.getByRole("button", "Window Options")).toHaveFocus();
                    play.tab();
                    play.expect(screen.getByRole("searchbox", "Filter documents")).toHaveFocus();
                    play.tab(true);
                    play.tab(true);
                    play.expect(screen.getByRole("button", StroomDom.button("Cancel"))).toHaveFocus();
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByRole("dialog")).toBeNull());
                    play.expect(picker).toHaveFocus();
                    play.expect(play.getByText("Current value: \"Ingest\"")).toBeInTheDocument();
                    // Reopened, it starts on the value, not on what was cancelled
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.getByRole("searchbox", "Filter documents")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.querySelector(":focus")).toHaveTextContent("Ingest"));
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.querySelector(":focus")).toHaveTextContent("Normalise"));
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.queryByRole("dialog")).toBeNull());
                    play.waitFor(() -> play.expect(play.getByText("Current value: \"Normalise\""))
                            .toBeInTheDocument());
                    play.expect(picker).toHaveFocus();
                })
                // Warning + disabled - a document that can't be found is a problem with the field,
                // shown under it (briefly, with the full message as its tooltip and for screen
                // readers); the disabled box doesn't open the popup
                .story("WarningAndDisabled", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionController warning = box(context, harness, null);
                    // The warning comes from Stroom checking the document (`decorate` finds no
                    // such feed), so its message is Stroom's
                    warning.setSelectedEntityReference(new DocRef("Feed", "deleted-feed", "Deleted Feed"), true);
                    final DocSelectionController disabled = box(context, harness, null);
                    disabled.setSelectedEntityReference(new DocRef("Pipeline", "locked-pipeline", "Locked Pipeline"),
                            false);
                    disabled.setEnabled(false);
                    final FlowPanel column = frame(harness, warning.getBox(), null);
                    column.getElement().getStyle().setProperty("display", "flex");
                    column.getElement().getStyle().setProperty("flexDirection", "column");
                    column.getElement().getStyle().setProperty("gap", "16px");
                    column.add(disabled.getBox());
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    // BEM (drop-down.css), with no old classes and no warning button
                    play.expect(play.querySelectorAll(".drop-down")).toHaveLength(2);
                    play.expect(play.querySelectorAll("[class*=dropDownView]")).toHaveLength(0);
                    play.expect(play.queryByTitle("Show Warnings")).toBeNull();
                    // Once Stroom has checked the document, the picker is invalid and described by the
                    // message under it, shown briefly with the full message for screen readers
                    final Query deleted = play.getByRole("button", TextMatch.startingWith("Deleted Feed"));
                    play.waitFor(() -> play.expect(deleted).toHaveAttribute("aria-invalid", "true"));
                    play.expect(play.querySelector(".drop-down__feedback")).toHaveTextContent(TextMatch.containing(
                            "'Deleted Feed' can't be found, or you don't have permission to see it."));
                    play.expect(deleted).toHaveAccessibleDescription(TextMatch.containing("doesn't exist"));
                    play.expect(play.querySelector(".drop-down__feedback")).toHaveAttribute("title",
                            "Feed 'Deleted Feed' (deleted-feed) doesn't exist or you do not have permission "
                            + "to view it.\nSelect a different feed or speak to your administrator.");
                    // The disabled picker has no problem
                    play.expect(play.getByRole("button", TextMatch.startingWith("Locked Pipeline")))
                            .not().toHaveAttribute("aria-invalid");
                })
                // In a form group, the problem is the group's feedback, as any field's is
                .story("ProblemInAFormGroup", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionController box = box(context, harness, null);
                    final FormGroup formGroup = new FormGroup();
                    formGroup.setIdentity("feed");
                    formGroup.setLabel("Feed");
                    formGroup.add(box.getBox());
                    box.setSelectedEntityReference(new DocRef("Feed", "deleted-feed", "Deleted Feed"), true);
                    return frame(harness, formGroup, null);
                })
                .withPlay(play -> {
                    final Query picker = play.getByRole("button", TextMatch.startingWith("Feed"));
                    play.waitFor(() -> play.expect(picker).toHaveAttribute("aria-invalid", "true"));
                    play.expect(play.querySelector(".form-group__feedback")).toHaveTextContent(TextMatch.containing(
                            "'Deleted Feed' can't be found, or you don't have permission to see it."));
                    play.expect(picker).toHaveAccessibleDescription(TextMatch.containing("doesn't exist"));
                    // The picker's own feedback line stays hidden
                    play.expect(play.querySelector(".drop-down__feedback")).not().toBeVisible();
                })
                // The node flags filter (e.g. data sources) is sent with the popup's tree fetch
                .story("NodeFlagsForwarded", context -> {
                    final ScreenHarness harness = harness(context, new ExplorerFixture());
                    final DocSelectionController box = box(context, harness, null);
                    box.setNodeFlags(NodeFlag.DATA_SOURCE);
                    box.setItemType("Data Source");
                    harness.add(box.getBox());
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    // Open the popup, which fetches the tree with the node flags filter
                    play.fireEvent().mouseDown(play.querySelector(".drop-down"), EventInit.create());
                    // The request is Stroom's, checked with the request spy
                    // (NodeFlag DATA_SOURCE is sent as its short form, "D")
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("\"nodeFlags\":[\"D\"]")));
                })
                // Type-filtered popup - only Feed and Pipeline documents (and their folders) are shown
                .story("TypeFiltered", context -> {
                    final ScreenHarness harness = harness(context, TreeFixtures.fixtureTree());
                    final DocSelectionController box = box(context, harness, null);
                    box.setIncludedTypes("Feed", "Pipeline");
                    return frame(harness, box.getBox(), echo("Popup shows only Feeds & Pipelines."));
                });
    }

    private static Widget withSelection(final StoryContext context) {
        final ExplorerFixture fixture = TreeFixtures.fixtureTree();
        final ScreenHarness harness = harness(context, fixture);
        final DocSelectionController box = box(context, harness, "Choose Pipeline");
        box.setItemType("Pipeline");
        box.setSelectedEntityReference(fixture.toDocRef("Ingest"), false);
        final Label echo = echo("Current value: \"Ingest\"");
        harness.addRegistration(box.addSelectionListener(selected -> echo.setText(
                "Current value: \"" + (selected == null
                        ? "(none)"
                        : selected.getName()) + "\"")));
        return frame(harness, box.getBox(), echo);
    }

    private static ScreenHarness harness(final StoryContext context, final ExplorerFixture fixture) {
        return ScreenHarness.create(context, fixtures(fixture));
    }

    private static RestFixtures fixtures(final ExplorerFixture fixture) {
        return TreeFixtures.explorerRoutes(fixture)
                // Stroom checks a document exists (and gets its current name) with `decorate`;
                // only the stories' "Deleted Feed" is checked, and it doesn't exist
                .post(TreeFixtures.DECORATE, RestReply.json("null"))
                .build();
    }

    private static DocSelectionController box(final StoryContext context,
                                                final ScreenHarness harness,
                                                final String caption) {
        final DocumentChooserController popup = SelectorWidgets.documentChooser(harness);
        if (caption != null) {
            popup.setCaption(caption);
        }
        final DocSelectionController box = SelectorWidgets.docSelectionBox(harness, popup);
        harness.own(box);
        final Spy onChange = context.fn(ON_CHANGE);
        harness.addRegistration(box.addSelectionListener(selected -> onChange.call(selected == null
                ? null
                : selected.getUuid())));
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
