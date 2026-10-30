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


package stroom.gwt.workbench.client.app.main;

import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.component.CreateDocumentController;
import stroom.explorer.shared.ExplorerNode;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/CreateDocumentDialog`, showing Stroom's real [CreateDocumentController] (the
/// 'New ...' and 'Save As' dialog) with fake REST replies.
///
/// The folder picker's tree comes from `POST /explorer/v2/fetchExplorerNodes` (the shared
/// [ExplorerFixture]). The dialog is shown by firing `ShowCreateDocumentDialogEvent`, as the
/// explorer's 'New' menu and 'Save As' do, with the controller (from GIN) registered as its handler
/// in place of its listener.
public final class CreateDocumentDialogStories {

    // GWT's folder picker is an DocumentPicker: a quick filter above the tree
    private static final String QUICK_FILTER = ".quick-filter__input";
    // The explorer tree's selected item
    private static final String TREE_SELECTED_ROW = "[role='treeitem'][aria-selected='true']";

    private CreateDocumentDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/CreateDocumentDialog", CreateDocumentDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The "New Dictionary" dialog: a growing folder picker, then Name and Permissions
                .story("Default", context -> {
                    final ExplorerFixture tree = TreeFixtures.fixtureTree();
                    return render(context, tree, "New Dictionary", null, "");
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Select the parent folder");
                    // GWT's `max form` layout: three form groups, the folder picker growing
                    // (dock-max) while Name / Permissions are natural height (dock-min).
                    // The dialog is Stroom's CreateDocument; its form is found from its caption
                    final Play dialog = screen.within(screen.within(screen.getByText("New Dictionary")
                            .closest(StroomDom.DIALOG)).querySelector(".max.form"));
                    play.expect(dialog.querySelectorAll(":scope > .form-group")).toHaveLength(3);
                    play.expect(dialog.querySelectorAll(":scope > .form-group").nth(0)).toHaveClass("dock-max");
                    play.expect(dialog.querySelectorAll(":scope > .form-group").nth(1)).toHaveClass("dock-min");
                    // The folder picker is GWT's DocumentPicker: its quick filter is inside the
                    // picker's form group. The picker's border classes are checked by the explorer
                    // tree widget stories
                    final Play picker = dialog.within(dialog.querySelectorAll(":scope > .form-group").nth(0));
                    play.expect(picker.querySelector(QUICK_FILTER)).toBeInTheDocument();
                    picker.findByText("System");
                    ContentStorySupport.expectNoProblems(play);
                })
                // OK with no name says why under the Name field, rather than in an alert that a screen
                // reader doesn't read (#5409)
                .story("NoName", context -> {
                    // Given a document, the picker selects its folder, so the folder is chosen and
                    // only the name is missing
                    final ExplorerFixture tree = TreeFixtures.fixtureTree();
                    return render(context, tree, "New Dictionary", tree.toExplorerNode("TEST_FEED"), "");
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText("New Dictionary").closest(StroomDom.DIALOG));
                    final Query feeds = dialog.findByText("Feeds");
                    play.waitFor(() -> play.expect(feeds.closest(TREE_SELECTED_ROW)).toBeInTheDocument());
                    final Query name = dialog.getByRole("textbox", "Name");
                    play.expect(name).toHaveAttribute("aria-required", "true");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // Marked invalid, described by the message (read before its help) and focused
                    play.waitFor(() -> play.expect(name).toHaveAttribute("aria-invalid", "true"));
                    play.expect(name).toHaveAccessibleDescription(
                            TextMatch.startingWith("You must provide a name for the new dictionary"));
                    play.expect(name).toHaveFocus();
                    play.expect(dialog.getByText("You must provide a name for the new dictionary"))
                            .toBeInTheDocument();
                    // Typing a name clears it
                    play.type(name, "My Dictionary");
                    play.waitFor(() -> play.expect(name).not().toHaveAttribute("aria-invalid"));
                    play.expect(dialog.queryByText("You must provide a name for the new dictionary")).toBeNull();
                    ContentStorySupport.expectNoProblems(play);
                })
                // Save As gives the picker the source document; GWT's selectParentIfNotFound selects
                // the folder that contains it
                .story("RevealsParentOfADocument", context -> {
                    final ExplorerFixture tree = new ExplorerFixture(ExplorerFixture.folder("System",
                            ExplorerFixture.folder("Reports",
                                    ExplorerFixture.doc("Monthly", "Dictionary"))));
                    return render(context, tree, "Save 'Monthly' as", tree.toExplorerNode("Monthly"), "Monthly");
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // The picker expanded down to the containing folder and selected it
                    final Query reports = screen.findByText("Reports");
                    play.waitFor(() -> play.expect(reports.closest(TREE_SELECTED_ROW))
                            .toBeInTheDocument());
                    // The document itself is not offered by a folder-only picker
                    final Play picker = screen.within(screen.getByText("Select the parent folder")
                            .closest(".form-group"));
                    play.expect(picker.queryByText("Monthly")).toBeNull();
                    // The name is seeded
                    play.expect(screen.getByDisplayValue("Monthly")).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context,
                                 final ExplorerFixture tree,
                                 final String caption,
                                 final ExplorerNode selected,
                                 final String initialName) {
        final RestFixtures fixtures = TreeFixtures.explorerRoutes(tree).build();
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        // As DocumentDialogScreens would
        harness.addRegistration(harness.getEventBus()
                .addHandler(ShowCreateDocumentDialogEvent.getType(), event ->
                        injector.getCreateDocumentController().show(harness.getAppRoot(), event)));
        harness.afterStartUp(() -> ShowCreateDocumentDialogEvent.fire(harness.getHasHandlers(), caption, selected,
                "Dictionary", initialName, false, node -> {
                }));
        return harness.asWidget();
    }
}
